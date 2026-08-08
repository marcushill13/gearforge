package com.gearforge.data;

import lombok.Builder;
import lombok.Value;

/**
 * A snapshot of the levels that gate equipment, taken on the client thread so the rest of the code
 * can use it from anywhere.
 */
@Value
@Builder
public class PlayerLevels
{
	int attack;
	int strength;
	int defence;
	int ranged;
	int magic;
	int prayer;
	int hitpoints;
	int slayer;

	/** Only the Moonlight potion needs this — its boost scales with Herblore. */
	int herblore;

	/** Whether the player currently has a slayer task assigned. */
	boolean onSlayerTask;

	/**
	 * @param skill lowercase skill name as it appears in the requirements dataset
	 * @return the player's level in that skill, or 0 for a skill we do not track
	 */
	public int levelOf(String skill)
	{
		switch (skill)
		{
			case "attack":
				return attack;
			case "strength":
				return strength;
			case "defence":
				return defence;
			case "ranged":
				return ranged;
			case "magic":
				return magic;
			case "prayer":
				return prayer;
			case "hitpoints":
				return hitpoints;
			case "slayer":
				return slayer;
			default:
				return 0;
		}
	}

	/**
	 * A maxed account — used when no requirement filtering is wanted.
	 */
	public static PlayerLevels maxed()
	{
		return PlayerLevels.builder()
			.attack(99).strength(99).defence(99).ranged(99).magic(99)
			.prayer(99).hitpoints(99).slayer(99).herblore(99)
			.build();
	}
}
