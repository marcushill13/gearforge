package com.gearforge.dps;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.api.gameval.ItemID;

/**
 * Enchanted crossbow bolts, which fire an extra effect a fraction of the time.
 * <p>
 * These cannot be expressed as a damage multiplier, which is why they were missing: a ruby bolt does
 * not make your hits bigger, it replaces six percent of them with a hit for a fifth of the target's
 * health that ignores defence entirely. Averaging that into a multiplier gets the number roughly
 * right against one monster and badly wrong against every other.
 * <p>
 * All figures are transcribed from the reference calculator's bolt implementation. The Kandarin Hard
 * diary raises every proc chance by a tenth; it is not assumed here, so these read slightly low for
 * players who have it.
 */
public enum BoltEffect
{
	/** 5% for a flat bonus off the Ranged level, landing whether or not the shot was accurate. */
	OPAL(0.05, ItemID.XBOWS_CROSSBOW_BOLTS_BRONZE_TIPPED_OPAL_ENCHANTED, ItemID.DRAGON_BOLTS_ENCHANTED_OPAL),

	/** 6% for a smaller flat bonus, larger against something fiery. */
	PEARL(0.06, ItemID.XBOWS_CROSSBOW_BOLTS_IRON_TIPPED_PEARL_ENCHANTED, ItemID.DRAGON_BOLTS_ENCHANTED_PEARL),

	/** 10% to ignore defence entirely and roll up to 115% of the max hit. */
	DIAMOND(0.10, ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE_TIPPED_DIAMOND_ENCHANTED, ItemID.DRAGON_BOLTS_ENCHANTED_DIAMOND),

	/** 6% for twice the opal bonus, but only on a shot that already landed, and never against dragons. */
	DRAGONSTONE(0.06, ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE_TIPPED_DRAGONSTONE_ENCHANTED, ItemID.DRAGON_BOLTS_ENCHANTED_DRAGONSTONE),

	/** 11% to roll up to 120% of the max hit on a landed shot. Undead are immune. */
	ONYX(0.11, ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE_TIPPED_ONYX_ENCHANTED, ItemID.DRAGON_BOLTS_ENCHANTED_ONYX),

	/** 6% to deal a fifth of the target's current health, ignoring defence, capped at 100. */
	RUBY(0.06, ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE_TIPPED_RUBY_ENCHANTED, ItemID.DRAGON_BOLTS_ENCHANTED_RUBY);

	private final double procChance;
	private final Set<Integer> itemIds;

	BoltEffect(double procChance, int... itemIds)
	{
		this.procChance = procChance;

		Set<Integer> ids = new HashSet<>();
		for (int id : itemIds)
		{
			ids.add(id);
		}

		this.itemIds = Collections.unmodifiableSet(ids);
	}

	public double getProcChance()
	{
		return procChance;
	}

	@Nullable
	public static BoltEffect forItem(int itemId)
	{
		for (BoltEffect bolt : values())
		{
			if (bolt.itemIds.contains(itemId))
			{
				return bolt;
			}
		}

		return null;
	}

	/**
	 * Applies the effect to a shot's damage.
	 *
	 * @param shot      what the shot would deal without the enchantment
	 * @param rangedLevel  the effective Ranged level, which the flat bonuses scale off
	 * @param maxHit       the shot's own maximum, which the gem effects scale off
	 * @param target       who is being shot, for the immunities and for ruby's health share
	 * @param targetHitpoints the target's health, or 0 when unknown
	 * @param zaryte       whether the crossbow is a zaryte, which strengthens every effect
	 */
	public DamageDistribution apply(
		DamageDistribution shot, int rangedLevel, int maxHit, Target target, int targetHitpoints,
		boolean zaryte)
	{
		switch (this)
		{
			case OPAL:
				return withBonus(shot, rangedLevel / (zaryte ? 9 : 10));

			case PEARL:
			{
				int divisor = target.hasAttribute(MonsterAttribute.FIERY) ? 15 : 20;
				return withBonus(shot, rangedLevel / (zaryte ? divisor - 2 : divisor));
			}

			case DRAGONSTONE:
			{
				// Dragonfire has no purchase on something already made of it.
				if (target.hasAttribute(MonsterAttribute.FIERY) || target.hasAttribute(MonsterAttribute.DRAGON))
				{
					return shot;
				}

				return withBonus(shot, rangedLevel * 2 / (zaryte ? 9 : 10));
			}

			case DIAMOND:
				// Rolls its own damage and ignores defence, so it replaces the shot rather than adding.
				return shot.or(1 - procChance,
					DamageDistribution.uniform(0, maxHit * (zaryte ? 126 : 115) / 100));

			case ONYX:
			{
				if (target.hasAttribute(MonsterAttribute.UNDEAD))
				{
					return shot;
				}

				return shot.or(1 - procChance,
					DamageDistribution.uniform(0, maxHit * (zaryte ? 132 : 120) / 100));
			}

			case RUBY:
			{
				if (targetHitpoints <= 0)
				{
					return shot;
				}

				int cap = zaryte ? 110 : 100;
				int damage = Math.min(cap, targetHitpoints * (zaryte ? 22 : 20) / 100);
				return shot.or(1 - procChance, DamageDistribution.certain(damage));
			}

			default:
				return shot;
		}
	}

	/**
	 * The flat-bonus effects, which add to whatever the shot did rather than replacing it.
	 */
	private DamageDistribution withBonus(DamageDistribution shot, int bonus)
	{
		if (bonus <= 0)
		{
			return shot;
		}

		return shot.or(1 - procChance, shot.plus(DamageDistribution.certain(bonus)));
	}

	/**
	 * Every enchanted bolt id, so the optimizer knows to keep them in the running.
	 */
	public static Set<Integer> allItemIds()
	{
		Set<Integer> ids = new HashSet<>();
		for (BoltEffect bolt : values())
		{
			ids.addAll(bolt.itemIds);
		}

		return ids;
	}

	@Override
	public String toString()
	{
		return name().charAt(0) + name().substring(1).toLowerCase() + " bolts (e)";
	}
}
