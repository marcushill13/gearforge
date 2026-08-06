package com.gearforge.data;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * The bosses GearForge knows about.
 * <p>
 * Ships as a generated resource — see {@code scripts/generate-monsters.mjs}. Sourced from the OSRS
 * Wiki, which is CC BY-NC-SA: attribution is required and the licence is non-commercial, so the
 * credit line must stay in the Bosses tab and the README, and the plugin must remain free.
 */
@Slf4j
@Singleton
public class MonsterRepository
{
	private static final String RESOURCE = "/com/gearforge/monsters.json";

	private final List<Monster> monsters;
	private final String attribution;

	@Inject
	public MonsterRepository(Gson gson)
	{
		MonsterFile file = load(gson);
		this.monsters = file.monsters == null ? Collections.emptyList() : file.monsters;
		this.attribution = file.attribution == null ? "" : file.attribution;
	}

	private static MonsterFile load(Gson gson)
	{
		try (InputStream stream = MonsterRepository.class.getResourceAsStream(RESOURCE))
		{
			if (stream == null)
			{
				log.warn("Monster data resource missing; the Bosses tab will be empty");
				return new MonsterFile();
			}

			MonsterFile file = gson.fromJson(
				new InputStreamReader(stream, StandardCharsets.UTF_8), MonsterFile.class);

			if (file == null)
			{
				return new MonsterFile();
			}

			log.debug("Loaded {} monsters", file.monsters == null ? 0 : file.monsters.size());
			return file;
		}
		catch (IOException | JsonSyntaxException e)
		{
			log.warn("Could not read monster data; the Bosses tab will be empty", e);
			return new MonsterFile();
		}
	}

	public List<Monster> all()
	{
		return Collections.unmodifiableList(monsters);
	}

	/**
	 * Only the curated bosses, for the Bosses tab. The full set stays searchable elsewhere so DPS can
	 * be checked against whatever is actually being fought.
	 */
	public List<Monster> bosses()
	{
		List<Monster> bosses = new ArrayList<>();
		for (Monster monster : monsters)
		{
			if (monster.isBoss())
			{
				bosses.add(monster);
			}
		}

		return bosses;
	}

	/**
	 * @return the first monster with this exact name, or null. Used for the default target.
	 */
	@Nullable
	public Monster byName(String name)
	{
		for (Monster monster : monsters)
		{
			if (monster.getName().equalsIgnoreCase(name))
			{
				return monster;
			}
		}

		return null;
	}

	public boolean isEmpty()
	{
		return monsters.isEmpty();
	}

	/**
	 * Required credit for the wiki data.
	 */
	public String getAttribution()
	{
		return attribution;
	}

	/**
	 * Case-insensitive substring search over name and form. An empty query returns everything, so the
	 * tab shows the full list before the player types.
	 * <p>
	 * Deliberately uncapped: capping here once truncated the list at "E" with nothing on screen to say
	 * so. Any display limit belongs in the UI, where it can be shown to the player.
	 */
	public List<Monster> search(String query)
	{
		return search(query, monsters);
	}

	/**
	 * Same search, restricted to a supplied set — the Bosses tab passes {@link #bosses()}.
	 */
	public List<Monster> search(String query, List<Monster> within)
	{
		String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		List<Monster> matches = new ArrayList<>();

		for (Monster monster : within)
		{
			if (needle.isEmpty() || monster.displayName().toLowerCase(Locale.ROOT).contains(needle))
			{
				matches.add(monster);
			}
		}

		return matches;
	}

	/**
	 * Mirrors the generated JSON.
	 */
	private static final class MonsterFile
	{
		int dataVersion;
		String attribution;
		String source;
		String generatedAt;
		List<Monster> monsters = new ArrayList<>();
	}
}
