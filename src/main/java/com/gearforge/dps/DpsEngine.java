package com.gearforge.dps;

import com.gearforge.data.EquipmentStats;
import javax.inject.Singleton;

/**
 * Core OSRS combat maths.
 * <p>
 * Formulas are transcribed from the OSRS Wiki — see {@code docs/combat-formulas.md} for the sources
 * and for the three places the project spec disagrees with the game. Every step floors exactly where
 * the game floors; moving or dropping a floor changes results by a visible margin.
 * <p>
 * This scores a whole setup rather than a slot, because slot-greedy picking is provably wrong once
 * two-handed weapons, ammo and set effects are involved.
 */
@Singleton
public class DpsEngine
{
	private static final double TICK_SECONDS = 0.6;

	public SetupScore score(CombatContext context)
	{
		EquipmentStats equipment = context.getEquipment();
		CombatStyle style = context.getStyle();

		int effectiveAttack = effectiveAttackLevel(context);
		int attackRoll = attackRoll(effectiveAttack, style.attackBonusOf(equipment), context.getAccuracyMultiplier());
		int defenceRoll = defenceRoll(context);
		double hitChance = hitChance(attackRoll, defenceRoll);

		int effectiveStrength = effectiveStrengthLevel(context);
		int maxHit = maxHit(context, effectiveStrength);

		// A weapon with no positive attack speed cannot be attacked with. Coercing it to a minimum
		// would turn bad item data into a one-tick weapon that scores as best-in-slot.
		int speedTicks = context.getWeaponSpeedTicks() <= 0 ? 0 : attackSpeedTicks(context);
		double dps = speedTicks <= 0
			? 0.0
			: damagePerAttack(context, hitChance, maxHit) / (speedTicks * TICK_SECONDS);

		return SetupScore.builder()
			.dps(dps)
			.hitChance(hitChance)
			.maxHit(maxHit)
			.attackRoll(attackRoll)
			.defenceRoll(defenceRoll)
			.effectiveAttackLevel(effectiveAttack)
			.effectiveStrengthLevel(effectiveStrength)
			.attackSpeedTicks(speedTicks)
			.build();
	}

	/**
	 * What one attack deals on average, including anything that cannot be written as a multiplier.
	 * <p>
	 * For an ordinary attack this is the closed-form average exactly. An enchanted bolt is not
	 * ordinary: it fires a fraction of the time, often ignores defence, and sometimes rolls its own
	 * damage instead of yours, so it has to be applied to the distribution.
	 */
	private double damagePerAttack(CombatContext context, double hitChance, int maxHit)
	{
		BoltEffect bolt = context.getBoltEffect();
		if (bolt == null)
		{
			return averageDamage(hitChance, maxHit);
		}

		DamageDistribution shot = DamageDistribution.roll(hitChance, maxHit);
		return bolt.apply(
			shot,
			effectiveStrengthLevel(context),
			maxHit,
			context.getTarget(),
			context.getTargetHitpoints(),
			context.isZaryteCrossbow()).mean();
	}

	/**
	 * Melee and ranged apply the void multiplier after the style add and the +8. Magic applies it
	 * before — that asymmetry is real, not a transcription slip.
	 */
	public int effectiveAttackLevel(CombatContext context)
	{
		CombatPrayer prayer = context.getPrayer();
		AttackStyle attackStyle = context.getAttackStyle();
		double voidMultiplier = context.getVoidSet().accuracyMultiplier();

		switch (context.getStyle())
		{
			case RANGED:
			{
				int boosted = floor((context.getRangedLevel() + context.getRangedBoost()) * prayer.getRangedAttack());
				return floor((boosted + attackStyle.getRangedAdd() + 8) * voidMultiplier);
			}
			case MAGIC:
			{
				int boosted = floor((context.getMagicLevel() + context.getMagicBoost()) * prayer.getMagic());
				int styleAdd = context.isPoweredStaff() ? attackStyle.getMagicAdd() : 0;
				return floor(boosted * voidMultiplier + styleAdd + 8);
			}
			default:
			{
				int boosted = floor((context.getAttackLevel() + context.getAttackBoost()) * prayer.getAttack());
				return floor((boosted + attackStyle.getMeleeAttackAdd() + 8) * voidMultiplier);
			}
		}
	}

	/**
	 * @return effective strength, or 0 for magic where max hit comes from the spell instead.
	 */
	public int effectiveStrengthLevel(CombatContext context)
	{
		CombatPrayer prayer = context.getPrayer();
		AttackStyle attackStyle = context.getAttackStyle();
		double voidMultiplier = context.getVoidSet().strengthMultiplier();

		switch (context.getStyle())
		{
			case RANGED:
			{
				int boosted = floor((context.getRangedLevel() + context.getRangedBoost()) * prayer.getRangedStrength());
				return floor((boosted + attackStyle.getRangedAdd() + 8) * voidMultiplier);
			}
			case MAGIC:
				return 0;
			default:
			{
				int boosted = floor((context.getStrengthLevel() + context.getStrengthBoost()) * prayer.getStrength());
				return floor((boosted + attackStyle.getMeleeStrengthAdd() + 8) * voidMultiplier);
			}
		}
	}

	public int attackRoll(int effectiveAttackLevel, int equipmentAttackBonus, double accuracyMultiplier)
	{
		long roll = (long) effectiveAttackLevel * (equipmentAttackBonus + 64);
		return floor(roll * accuracyMultiplier);
	}

	/**
	 * Magic rolls against the target's Magic level, everything else against its Defence level.
	 * Using Defence level for magic is a common and costly mistake.
	 */
	public int defenceRoll(CombatContext context)
	{
		Target target = context.getTarget();
		EquipmentStats defensive = target.getDefensiveBonuses();
		CombatStyle style = context.getStyle();

		int level = style.isMagic() ? target.getMagicLevel() : target.getDefenceLevel();
		int bonus = style.defenceBonusOf(defensive);

		return (level + 9) * (bonus + 64);
	}

	public double hitChance(int attackRoll, int defenceRoll)
	{
		if (attackRoll > defenceRoll)
		{
			return 1.0 - (defenceRoll + 2.0) / (2.0 * (attackRoll + 1.0));
		}

		return attackRoll / (2.0 * (defenceRoll + 1.0));
	}

	public int maxHit(CombatContext context, int effectiveStrengthLevel)
	{
		EquipmentStats equipment = context.getEquipment();

		int base;
		if (context.getStyle().isMagic())
		{
			PoweredStaff staff = context.getPoweredStaffType();

			// A powered staff has its own attack and casts nothing, so a spell's damage is not its
			// starting point. Assuming Ice Barrage for these scored a trident on a spell it cannot cast.
			int spellDamage = staff == null
				? context.getBaseSpellDamage()
				: staff.maxHit(context.getMagicLevel() + context.getMagicBoost());

			double magicBonus = equipment.getMagicDamage() / 100.0
				* (staff == null ? 1.0 : staff.magicDamageMultiplier());

			base = (int) Math.floor(spellDamage * (1.0 + magicBonus));
		}
		else
		{
			int strengthBonus = context.getStyle().isRanged()
				? equipment.getRangedStrength()
				: equipment.getStrength();

			// Integer arithmetic throughout; the +320/640 form is exactly floor(0.5 + x/640).
			long numerator = (long) effectiveStrengthLevel * (strengthBonus + 64) + 320;
			base = (int) (numerator / 640);
		}

		// Added after the multiplier, so it does not scale with the rest of the gear.
		return Math.max(0, floor(base * context.getDamageMultiplier()) + context.getFlatMaxHit());
	}

	public int attackSpeedTicks(CombatContext context)
	{
		return Math.max(1, context.getWeaponSpeedTicks() + context.getAttackStyle().getSpeedModifier());
	}

	/**
	 * The {@code 1/(maxHit+1)} term is not a fudge: a damage roll of 0 on a successful hit becomes 1,
	 * which shifts the mean of the uniform roll up by exactly that much.
	 */
	public double averageDamage(double hitChance, int maxHit)
	{
		if (maxHit <= 0)
		{
			return 0.0;
		}

		return hitChance * (maxHit / 2.0 + 1.0 / (maxHit + 1.0));
	}

	private static int floor(double value)
	{
		return (int) Math.floor(value);
	}
}
