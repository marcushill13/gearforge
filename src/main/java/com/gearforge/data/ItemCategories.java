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
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * What kind of weapon or ammunition an item is.
 * <p>
 * RuneLite's item stats say an item is a weapon but not what type, so nothing there prevents pairing
 * a crossbow with arrows. Ships as a generated resource — see
 * {@code scripts/generate-item-categories.mjs}.
 */
@Slf4j
@Singleton
public class ItemCategories
{
	private static final String RESOURCE = "/com/gearforge/item-categories.json";

	public static final String BOW = "BOW";
	public static final String OGRE_BOW = "OGRE_BOW";
	public static final String CROSSBOW = "CROSSBOW";
	public static final String BALLISTA = "BALLISTA";
	public static final String THROWN = "THROWN";
	public static final String POWERED_STAFF = "POWERED_STAFF";

	public static final String ARROW = "ARROW";
	public static final String BRUTAL = "BRUTAL";
	public static final String BOLT = "BOLT";
	public static final String JAVELIN = "JAVELIN";

	private final Map<Integer, String> categories;

	@Inject
	public ItemCategories(Gson gson)
	{
		this.categories = load(gson);
	}

	private static Map<Integer, String> load(Gson gson)
	{
		try (InputStream stream = ItemCategories.class.getResourceAsStream(RESOURCE))
		{
			if (stream == null)
			{
				log.warn("Item category resource missing; ammo pairing will not be checked");
				return Collections.emptyMap();
			}

			CategoryFile file = gson.fromJson(
				new InputStreamReader(stream, StandardCharsets.UTF_8), CategoryFile.class);

			if (file == null || file.categories == null)
			{
				return Collections.emptyMap();
			}

			log.debug("Loaded {} item categories", file.categories.size());
			return file.categories;
		}
		catch (IOException | JsonSyntaxException e)
		{
			log.warn("Could not read item categories; ammo pairing will not be checked", e);
			return Collections.emptyMap();
		}
	}

	/**
	 * @return the item's category, or null if it is unclassified — melee weapons, staves, and every
	 *     non-weapon fall here, and none of them constrain ammo.
	 */
	@Nullable
	public String categoryOf(int itemId)
	{
		return categories.get(itemId);
	}

	/**
	 * Whether this item is a ranged weapon of any kind.
	 */
	public boolean isRangedWeapon(int itemId)
	{
		String category = categoryOf(itemId);
		return BOW.equals(category)
			|| OGRE_BOW.equals(category)
			|| CROSSBOW.equals(category)
			|| BALLISTA.equals(category)
			|| THROWN.equals(category);
	}

	/**
	 * Whether a weapon can sensibly be used with a combat style.
	 * <p>
	 * Without this the optimizer will happily recommend a longsword as a ranged setup when the player
	 * owns no bow: a melee weapon contributes zero ranged attack and zero ranged strength, so it
	 * scores badly rather than being rejected, and "badly" still wins if nothing else is available.
	 * A wrong-but-confident answer is worse than saying you own nothing suitable.
	 *
	 * @param style the combat style being optimised, as {@code CombatStyle#name()}
	 */
	public boolean suitsStyle(int weaponId, String style)
	{
		boolean ranged = isRangedWeapon(weaponId);

		if ("RANGED".equals(style))
		{
			return ranged;
		}

		// Melee and magic can both be performed with an unclassified weapon — you can cast a spell
		// holding anything — but never with a bow or crossbow.
		return !ranged;
	}

	/**
	 * Whether this weapon needs ammunition to attack at all. A bow with no arrows does nothing.
	 */
	public boolean requiresAmmo(int weaponId)
	{
		String category = categoryOf(weaponId);
		return BOW.equals(category)
			|| OGRE_BOW.equals(category)
			|| CROSSBOW.equals(category)
			|| BALLISTA.equals(category);
	}

	/**
	 * Whether this ammunition can be fired from this weapon.
	 * <p>
	 * Unclassified weapons accept anything: melee weapons, staves and thrown weapons do not draw from
	 * the ammo slot, so constraining them would reject valid setups for no gain.
	 */
	public boolean ammoFits(int weaponId, int ammoId)
	{
		String weapon = categoryOf(weaponId);
		if (weapon == null)
		{
			return true;
		}

		String ammo = categoryOf(ammoId);

		switch (weapon)
		{
			case BOW:
				return ARROW.equals(ammo);
			// Ogre bows fire brutal arrows, and the composite also takes ordinary ones.
			case OGRE_BOW:
				return BRUTAL.equals(ammo) || ARROW.equals(ammo);
			case CROSSBOW:
				return BOLT.equals(ammo);
			// Ballistas are categorised as crossbows upstream but fire javelins.
			case BALLISTA:
				return JAVELIN.equals(ammo);
			default:
				return true;
		}
	}

	private static final class CategoryFile
	{
		int dataVersion;
		String source;
		String generatedAt;
		Map<Integer, String> categories = new HashMap<>();
	}
}
