package com.gearforge.dps;

import com.gearforge.data.EquipmentStats;
import com.gearforge.data.Monster;
import com.gearforge.data.PlayerLevels;
import java.util.Collection;
import java.util.Collections;
import javax.annotation.Nullable;
import javax.inject.Singleton;

/**
 * Builds the context every scored setup is measured against.
 * <p>
 * This exists because there were three of these, one per tab, each assembled by hand. They drifted:
 * the reach filter, the chosen spell and the target's hitpoints all shipped on the BiS tab and never
 * reached the others, so the Bosses tab went on recommending a blade of saeldor for Zulrah long after
 * that was fixed, and the Setups tab is still handing out upgrade advice based on a slash attack with
 * Ice Barrage assumed. A fix that only lands in one of three places is not a fix.
 * <p>
 * Prayers and potions arrive as collections. Selecting two that raise the same stat does not add them
 * together — in game the stronger simply wins — so the best of each is taken per stat rather than
 * summed.
 */
@Singleton
public class Scoring
{
	/** A spellbook cast takes five ticks whatever is held. */
	private static final int SPELL_SPEED_TICKS = 5;

	/**
	 * @param target null for a bare dummy, which is what the BiS tab uses before a boss is chosen
	 */
	public CombatContext contextFor(
		CombatStyle style,
		PlayerLevels levels,
		@Nullable Monster target,
		Collection<CombatPrayer> prayers,
		Collection<Potion> potions)
	{
		Target scored = target == null ? Target.dummy() : target.toTarget();

		return CombatContext.builder()
			.attackLevel(levels.getAttack())
			.strengthLevel(levels.getStrength())
			.rangedLevel(levels.getRanged())
			.magicLevel(levels.getMagic())
			.attackBoost(bestBoost(potions, levels, Stat.ATTACK))
			.strengthBoost(bestBoost(potions, levels, Stat.STRENGTH))
			.rangedBoost(bestBoost(potions, levels, Stat.RANGED))
			.magicBoost(bestBoost(potions, levels, Stat.MAGIC))
			.prayer(bestPrayer(prayers, style))
			.style(style)
			.equipment(EquipmentStats.builder().build())
			.target(scored)
			.targetHitpoints(target == null ? 0 : target.getHitpoints())
			.spell(style.isMagic() ? Spell.bestFor(scored, levels.getMagic(), true) : null)
			.poweredStaff(false)
			.weaponSpeedTicks(SPELL_SPEED_TICKS)
			.build();
	}

	/**
	 * The strongest boost to this stat among the potions chosen.
	 * <p>
	 * Two potions raising the same stat do not stack, so this is a maximum and not a sum. A negative is
	 * kept when it is the only effect — the brews trade melee levels away, and hiding that would make
	 * them look free.
	 */
	private static int bestBoost(Collection<Potion> potions, PlayerLevels levels, Stat stat)
	{
		if (potions == null || potions.isEmpty())
		{
			return 0;
		}

		Integer best = null;
		for (Potion potion : potions)
		{
			int boost = stat.of(potion, levels);
			if (best == null || boost > best)
			{
				best = boost;
			}
		}

		return best == null ? 0 : best;
	}

	/**
	 * The strongest prayer for this style among those chosen.
	 * <p>
	 * Combat prayers of the same kind cannot be active together in game, so picking several means "try
	 * these", not "add these". Whichever helps this style most is the one that applies.
	 */
	private static CombatPrayer bestPrayer(Collection<CombatPrayer> prayers, CombatStyle style)
	{
		if (prayers == null || prayers.isEmpty())
		{
			return CombatPrayer.NONE;
		}

		CombatPrayer best = CombatPrayer.NONE;
		double bestValue = 0;

		for (CombatPrayer prayer : prayers)
		{
			double value = value(prayer, style);
			if (value > bestValue)
			{
				bestValue = value;
				best = prayer;
			}
		}

		return best;
	}

	/**
	 * How much a prayer is worth to a style, as accuracy and damage combined. Only used to choose
	 * between several; the engine applies the winner properly.
	 */
	private static double value(CombatPrayer prayer, CombatStyle style)
	{
		if (style.isRanged())
		{
			return prayer.getRangedAttack() + prayer.getRangedStrength() - 2;
		}

		if (style.isMagic())
		{
			return prayer.getMagic() - 1;
		}

		return prayer.getAttack() + prayer.getStrength() - 2;
	}

	private enum Stat
	{
		ATTACK,
		STRENGTH,
		RANGED,
		MAGIC;

		int of(Potion potion, PlayerLevels levels)
		{
			switch (this)
			{
				case ATTACK:
					return potion.attackBoost(levels);
				case STRENGTH:
					return potion.strengthBoost(levels);
				case RANGED:
					return potion.rangedBoost(levels);
				default:
					return potion.magicBoost(levels);
			}
		}
	}

	public static Collection<CombatPrayer> noPrayers()
	{
		return Collections.emptyList();
	}

	public static Collection<Potion> noPotions()
	{
		return Collections.emptyList();
	}
}
