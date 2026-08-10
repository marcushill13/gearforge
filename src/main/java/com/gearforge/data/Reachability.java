package com.gearforge.data;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Monsters that melee can only reach with a long weapon.
 * <p>
 * Zulrah is the example: it sits a tile beyond an ordinary weapon's reach, so a whip cannot touch it
 * — but a halberd can, and people do melee it that way. Banning melee outright is as wrong as
 * allowing it, because it deletes a correct answer instead of a wrong one.
 * <p>
 * So this is a reach question, not a style question. Polearms reach two tiles; everything else reaches
 * one. The category comes from the equipment data at build time rather than being guessed at here.
 * <p>
 * The list is hand-kept because no dataset carries it. The wiki's monster export records what a
 * monster attacks <em>with</em>, not what reaches it, and the wiki's own calculator does not model
 * this at all — there the player picks a style and takes responsibility for it. GearForge picks the
 * style itself, so it has to be right.
 */
public final class Reachability
{
	/**
	 * Monsters an ordinary melee weapon cannot reach, matched on the base name so every form counts.
	 */
	private static final Set<String> NEEDS_REACH = new HashSet<>(Arrays.asList(
		"zulrah"
	));

	/**
	 * Monsters melee cannot touch at any reach, because there is nothing standing on the ground to hit.
	 */
	private static final Set<String> NO_MELEE = new HashSet<>(Arrays.asList(
		// In the water, and out of reach of anything.
		"kraken",
		"cave kraken",

		// Airborne for the whole fight. Kree'arra's bodyguards are aviansies and are equally unreachable.
		"kree'arra",
		"aviansie",
		"wingman skree",
		"flockleader geerin",
		"flight kilisa",

		// Suspended above the arena and only damaged indirectly.
		"zalcano"
	));

	private Reachability()
	{
	}

	/**
	 * @param polearm whether the melee weapon in question reaches two tiles
	 * @return whether a melee weapon of this reach can attack this monster
	 */
	public static boolean meleeCanReach(Monster monster, boolean polearm)
	{
		if (monster == null)
		{
			return true;
		}

		String name = monster.getName().toLowerCase(Locale.ROOT);

		if (NO_MELEE.contains(name))
		{
			return false;
		}

		return polearm || !NEEDS_REACH.contains(name);
	}

	/**
	 * Whether melee is possible at all here, with the right weapon.
	 */
	public static boolean meleeIsPossible(Monster monster)
	{
		return monster == null || !NO_MELEE.contains(monster.getName().toLowerCase(Locale.ROOT));
	}

	/**
	 * Whether reaching this monster in melee requires a long weapon.
	 */
	public static boolean requiresReach(Monster monster)
	{
		return monster != null && NEEDS_REACH.contains(monster.getName().toLowerCase(Locale.ROOT));
	}

	/**
	 * Said plainly in the panel rather than a style silently disappearing.
	 */
	public static String reason(Monster monster)
	{
		if (requiresReach(monster))
		{
			return monster.getName() + " can only be meleed with a halberd or another weapon that "
				+ "reaches two tiles.";
		}

		return monster.getName() + " cannot be attacked with melee.";
	}
}
