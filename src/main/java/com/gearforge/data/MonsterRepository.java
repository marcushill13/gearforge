package com.gearforge.data;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * The bosses GearForge knows about.
 * <p>
 * Ships as a generated resource — see {@code scripts/generate-monsters.mjs}. Sourced from the OSRS
 * Wiki, which is CC BY-NC-SA: attribution is required and the licence is non-commercial, so the
 * credit line must stay on the BiS tab and in the README, and the plugin must remain free.
 */
@Slf4j
@Singleton
public class MonsterRepository
{
	private static final String RESOURCE = "/com/gearforge/monsters.json";

	/** What a fight is called, against what its monsters are called. */
	private static final Map<String, String[]> ALIASES = buildAliases();

	private static Map<String, String[]> buildAliases()
	{
		Map<String, String[]> aliases = new LinkedHashMap<>();
		aliases.put("royal titans", new String[]{"branda", "eldric"});
		aliases.put("dagannoth kings", new String[]{"dagannoth rex", "dagannoth prime", "dagannoth supreme"});
		aliases.put("dks", new String[]{"dagannoth rex", "dagannoth prime", "dagannoth supreme"});
		aliases.put("desert treasure", new String[]{"vardorvis", "duke sucellus", "leviathan", "whisperer"});
		aliases.put("gauntlet", new String[]{"crystalline hunllef", "corrupted hunllef"});
		return aliases;
	}

	private final List<Monster> monsters;
	private final String attribution;

	@Inject
	public MonsterRepository(Gson gson)
	{
		MonsterFile file = load(gson);
		this.monsters = collapseDuplicates(
			file.monsters == null ? Collections.emptyList() : file.monsters);
		this.attribution = file.attribution == null ? "" : file.attribution;
	}

	/**
	 * Removes entries the DPS engine cannot tell apart.
	 * <p>
	 * The wiki data lists a row per NPC id, so one monster arrives many times over: "Armoured zombie
	 * (Zemouregal's Fort) (Melee)" appears four times, and the ranged form beside it has the same
	 * defence, the same bonuses and the same attributes. Scrolling past twenty rows that all score
	 * identically is just noise.
	 * <p>
	 * Two entries collapse when they share a name and every stat the engine reads — defence, magic,
	 * bonuses, size, hitpoints and attributes. Combat level is deliberately not part of that: the
	 * Ankou variants differ by level but not by anything that changes DPS, so they fold into one.
	 * <p>
	 * A form is only worth showing when it distinguishes something. Once a name has collapsed to a
	 * single entry, its version suffix is dropped, so "Zulrah (Tanzanite)" keeps its form — the colours
	 * really do defend differently — while "Ankou (Level 75)" becomes plain "Ankou".
	 */
	private static List<Monster> collapseDuplicates(List<Monster> loaded)
	{
		Map<String, Monster> bossForms = new LinkedHashMap<>();
		Map<String, Integer> formsPerBoss = new HashMap<>();
		Map<String, Monster> ordinary = new LinkedHashMap<>();

		for (Monster monster : loaded)
		{
			// Ordinary monsters collapse to one row per name, full stop. Twenty armoured zombies that
			// differ only in which room they stand in is not a choice anyone wants to scroll past.
			if (!monster.isBoss())
			{
				ordinary.merge(baseName(monster.getName()), monster, MonsterRepository::toughest);
				continue;
			}

			if (bossForms.putIfAbsent(monster.getName() + '|' + combatSignature(monster), monster) == null)
			{
				formsPerBoss.merge(monster.getName(), 1, Integer::sum);
			}
		}

		List<Monster> collapsed = new ArrayList<>(bossForms.values());
		for (Monster monster : collapsed)
		{
			if (formsPerBoss.getOrDefault(monster.getName(), 0) == 1)
			{
				monster.setVersion("");
			}
		}

		for (Map.Entry<String, Monster> entry : ordinary.entrySet())
		{
			Monster monster = entry.getValue();
			monster.setName(entry.getKey());
			monster.setVersion("");
			collapsed.add(monster);
		}

		collapsed = disambiguate(collapsed);
		collapsed.sort(Comparator.comparing(Monster::displayName, String.CASE_INSENSITIVE_ORDER));

		log.debug("Collapsed {} monster entries to {}", loaded.size(), collapsed.size());
		return Collections.unmodifiableList(collapsed);
	}

	/**
	 * An ordinary monster's name with any trailing qualifier removed.
	 * <p>
	 * The source bakes the location into the name rather than the form, so the armoured zombie arrives
	 * as four separate monsters — "(Defender of Varrock)", "(The Curse of Arrav)", "(Zemouregal's Base)"
	 * and "(Zemouregal's Fort)" — each with its own set of forms underneath. Which quest a zombie
	 * belongs to does not change how you kill it, so the qualifier goes and the four become one.
	 * <p>
	 * Bosses never reach here: their qualifiers are the whole point.
	 */
	private static String baseName(String name)
	{
		int qualifier = name.lastIndexOf(" (");
		if (qualifier <= 0 || !name.endsWith(")"))
		{
			return name;
		}

		return name.substring(0, qualifier);
	}

	/**
	 * Which of two forms of the same ordinary monster to keep.
	 * <p>
	 * The tougher one, so the DPS estimate reads low rather than high. Being told you kill something
	 * slower than you really do is the harmless direction to be wrong in.
	 */
	private static Monster toughest(Monster left, Monster right)
	{
		if (left.getDefenceLevel() != right.getDefenceLevel())
		{
			return left.getDefenceLevel() > right.getDefenceLevel() ? left : right;
		}

		return left.getHitpoints() >= right.getHitpoints() ? left : right;
	}

	/**
	 * Settles rows that still read identically.
	 * <p>
	 * A handful of monsters defend differently but carry no form to say so — Jhallan is one, listed
	 * twice with nothing between the two rows. Two entries a player cannot choose between are worse
	 * than one, so combat level is borrowed as the label where it separates them, and where even that
	 * matches the row really is a duplicate and only the first is kept.
	 */
	private static List<Monster> disambiguate(List<Monster> collapsed)
	{
		Map<String, List<Monster>> byDisplayName = new LinkedHashMap<>();
		for (Monster monster : collapsed)
		{
			byDisplayName.computeIfAbsent(monster.displayName(), name -> new ArrayList<>()).add(monster);
		}

		List<Monster> settled = new ArrayList<>();
		for (List<Monster> sharing : byDisplayName.values())
		{
			if (sharing.size() == 1)
			{
				settled.add(sharing.get(0));
				continue;
			}

			Set<Integer> levels = new HashSet<>();
			for (Monster monster : sharing)
			{
				levels.add(monster.getCombatLevel());
			}

			if (levels.size() != sharing.size())
			{
				settled.add(sharing.get(0));
				continue;
			}

			for (Monster monster : sharing)
			{
				monster.setVersion("Level " + monster.getCombatLevel());
				settled.add(monster);
			}
		}

		return settled;
	}

	/**
	 * Everything {@link Monster#toTarget()} reads. Two monsters with the same signature score the same,
	 * so listing both only makes the picker longer.
	 */
	private static String combatSignature(Monster monster)
	{
		List<String> attributes = new ArrayList<>(
			monster.getAttributes() == null ? Collections.emptyList() : monster.getAttributes());
		attributes.sort(String.CASE_INSENSITIVE_ORDER);

		Monster.Defensive defensive = monster.getDefensive();

		return monster.getDefenceLevel() + ":" + monster.getMagicLevel() + ":" + monster.getMagicAttack()
			+ ":" + monster.getHitpoints() + ":" + monster.getSize() + ":" + monster.isSlayerMonster()
			+ ":" + defensive.getStab() + ":" + defensive.getSlash() + ":" + defensive.getCrush()
			+ ":" + defensive.getMagic() + ":" + defensive.getRanged()
			+ ":" + String.join(",", attributes);
	}

	private static MonsterFile load(Gson gson)
	{
		try (InputStream stream = MonsterRepository.class.getResourceAsStream(RESOURCE))
		{
			if (stream == null)
			{
				log.warn("Monster data resource missing; no targets will be selectable");
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
			log.warn("Could not read monster data; no targets will be selectable", e);
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
			String name = monster.displayName().toLowerCase(Locale.ROOT);

			if (needle.isEmpty() || name.contains(needle) || matchesAlias(needle, name))
			{
				matches.add(monster);
			}
		}

		return matches;
	}

	/**
	 * Names people search by that no monster is actually called.
	 * <p>
	 * The Royal Titans are two separate NPCs, Branda and Eldric, so searching what the fight is called
	 * found nothing at all.
	 */
	private static boolean matchesAlias(String needle, String name)
	{
		for (Map.Entry<String, String[]> alias : ALIASES.entrySet())
		{
			if (!alias.getKey().startsWith(needle))
			{
				continue;
			}

			for (String member : alias.getValue())
			{
				if (name.contains(member))
				{
					return true;
				}
			}
		}

		return false;
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
