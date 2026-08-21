package com.gearforge.data;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemVariationMapping;

/**
 * Level requirements for equipping items.
 * <p>
 * RuneLite's item data has no requirements and the OSRS Wiki only states them in prose, so this ships
 * as a generated resource — see {@code scripts/generate-requirements.mjs}.
 * <p>
 * Coverage is incomplete: the source dataset stopped being updated, so items released after that are
 * absent. Absent is treated as <em>unknown</em>, not as <em>unrestricted</em>, and unknown items are
 * still offered to the player with a caveat. Hiding a player's best weapon because a dataset is stale
 * would be a worse failure than mentioning one they cannot yet wield.
 */
@Slf4j
@Singleton
public class ItemRequirements
{
	private static final String RESOURCE = "/com/gearforge/equipment-requirements.json";

	private final Map<Integer, Map<String, Integer>> requirements;

	@Inject
	public ItemRequirements(Gson gson)
	{
		this.requirements = load(gson);
	}

	private static Map<Integer, Map<String, Integer>> load(Gson gson)
	{
		try (InputStream stream = ItemRequirements.class.getResourceAsStream(RESOURCE))
		{
			if (stream == null)
			{
				log.warn("Equipment requirements resource missing; requirement filtering disabled");
				return Collections.emptyMap();
			}

			// A concrete class rather than a TypeToken: Gson resolves the field's generics directly,
			// and it keeps java.lang.reflect out of the plugin entirely.
			RequirementsFile file = gson.fromJson(
				new InputStreamReader(stream, StandardCharsets.UTF_8), RequirementsFile.class);

			if (file == null || file.requirements == null)
			{
				return Collections.emptyMap();
			}

			log.debug("Loaded {} equipment requirement entries", file.requirements.size());
			return file.requirements;
		}
		catch (IOException | JsonSyntaxException e)
		{
			log.warn("Could not read equipment requirements; requirement filtering disabled", e);
			return Collections.emptyMap();
		}
	}

	/**
	 * @return true if we have requirement data for this item at all
	 */
	public boolean isKnown(int itemId)
	{
		return requirements.containsKey(itemId) || requirements.containsKey(family(itemId));
	}

	/**
	 * @return skill name to required level; empty if the item has no requirements or is unknown
	 */
	public Map<String, Integer> requirementsFor(int itemId)
	{
		Map<String, Integer> exact = requirements.get(itemId);
		if (exact != null)
		{
			return exact;
		}

		// An ornament kit does not lower a requirement. Most of what is left unknown after both
		// sources have been read is a recoloured, ornamented, trailblazer or deadman copy of something
		// perfectly well known — dragon claws (or) needs the same 60 Attack the claws do.
		return requirements.getOrDefault(family(itemId), Collections.emptyMap());
	}

	private static int family(int itemId)
	{
		return ItemVariationMapping.map(itemId);
	}

	/**
	 * @return true if the player meets every known requirement. Items with no data return true, since
	 *     unknown is not the same as unmet.
	 */
	public boolean canEquip(int itemId, PlayerLevels levels)
	{
		for (Map.Entry<String, Integer> requirement : requirementsFor(itemId).entrySet())
		{
			if (levels.levelOf(requirement.getKey()) < requirement.getValue())
			{
				return false;
			}
		}

		return true;
	}

	/**
	 * A short, game-native description of what is missing, e.g. "Needs 70 Attack".
	 *
	 * @return the description, or null if the player can already equip it
	 */
	public String describeShortfall(int itemId, PlayerLevels levels)
	{
		for (Map.Entry<String, Integer> requirement : requirementsFor(itemId).entrySet())
		{
			if (levels.levelOf(requirement.getKey()) < requirement.getValue())
			{
				return "Needs " + requirement.getValue() + " " + capitalise(requirement.getKey());
			}
		}

		return null;
	}

	private static String capitalise(String skill)
	{
		return skill.substring(0, 1).toUpperCase() + skill.substring(1);
	}

	/**
	 * Mirrors the generated JSON. Only {@code requirements} is read; the rest is provenance.
	 */
	private static final class RequirementsFile
	{
		int dataVersion;
		String source;
		String generatedAt;
		Map<Integer, Map<String, Integer>> requirements = new HashMap<>();
	}
}
