package com.gearforge.data;

import com.gearforge.dps.CombatStyle;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Which monsters cannot be hit with a melee weapon at all.
 * <p>
 * Recommending a melee setup for Zulrah is not a small inaccuracy — it is an answer the player cannot
 * act on, and it displaces the answer they wanted.
 * <p>
 * This is a hand-kept list because the data does not exist anywhere to read. The wiki's monster
 * export records what a monster attacks <em>with</em>, not what can reach it, and the wiki's own DPS
 * calculator does not model reachability either — it simply lets you pick a style and trusts you.
 * GearForge picks the style itself, so it has to know.
 * <p>
 * Deliberately short and confined to monsters where melee is impossible rather than merely unwise.
 * A wrong entry here silently removes a correct recommendation, so the bar for adding one is that
 * melee genuinely cannot land.
 */
public final class Reachability
{
	/**
	 * Matched on the base name, so every form and level of these is covered.
	 */
	private static final Set<String> NO_MELEE = new HashSet<>(Arrays.asList(
		// Coiled out of reach for the whole fight.
		"zulrah",

		// In the water; only ranged and magic reach it.
		"kraken",
		"cave kraken",

		// Airborne. Kree'arra's bodyguards are aviansies too and are equally unreachable.
		"kree'arra",
		"aviansie",
		"wingman skree",
		"flockleader geerin",
		"flight kilisa",

		// Suspended above the arena.
		"zalcano"
	));

	private Reachability()
	{
	}

	/**
	 * @return true if this style can actually attack this monster
	 */
	public static boolean canAttack(Monster monster, CombatStyle style)
	{
		if (monster == null || !style.isMelee())
		{
			return true;
		}

		return !NO_MELEE.contains(monster.getName().toLowerCase(Locale.ROOT));
	}

	/**
	 * Why a style was excluded, for the panel to show rather than silently dropping it.
	 */
	public static String reason(Monster monster)
	{
		return monster.getName() + " cannot be attacked with melee.";
	}
}
