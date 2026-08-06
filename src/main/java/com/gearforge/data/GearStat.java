package com.gearforge.data;

import java.util.function.ToDoubleFunction;
import lombok.Getter;

/**
 * A single rankable bonus. Names match what the in-game equipment stats screen calls them.
 */
@Getter
public enum GearStat
{
	STAB_ATTACK("Stab attack", EquipmentStats::getAstab),
	SLASH_ATTACK("Slash attack", EquipmentStats::getAslash),
	CRUSH_ATTACK("Crush attack", EquipmentStats::getAcrush),
	MAGIC_ATTACK("Magic attack", EquipmentStats::getAmagic),
	RANGED_ATTACK("Ranged attack", EquipmentStats::getArange),

	STAB_DEFENCE("Stab defence", EquipmentStats::getDstab),
	SLASH_DEFENCE("Slash defence", EquipmentStats::getDslash),
	CRUSH_DEFENCE("Crush defence", EquipmentStats::getDcrush),
	MAGIC_DEFENCE("Magic defence", EquipmentStats::getDmagic),
	RANGED_DEFENCE("Ranged defence", EquipmentStats::getDrange),

	STRENGTH("Melee strength", EquipmentStats::getStrength),
	RANGED_STRENGTH("Ranged strength", EquipmentStats::getRangedStrength),
	MAGIC_DAMAGE("Magic damage", EquipmentStats::getMagicDamage, true),
	PRAYER("Prayer", EquipmentStats::getPrayer);

	private final String displayName;
	private final ToDoubleFunction<EquipmentStats> extractor;
	private final boolean percentage;

	GearStat(String displayName, ToDoubleFunction<EquipmentStats> extractor)
	{
		this(displayName, extractor, false);
	}

	GearStat(String displayName, ToDoubleFunction<EquipmentStats> extractor, boolean percentage)
	{
		this.displayName = displayName;
		this.extractor = extractor;
		this.percentage = percentage;
	}

	public double valueOf(EquipmentStats stats)
	{
		return extractor.applyAsDouble(stats);
	}

	/**
	 * Formats a value the way the game does — always signed, percentages suffixed.
	 */
	public String format(double value)
	{
		String number;
		if (percentage)
		{
			// Fractional magic damage exists (e.g. 0.2%), so only show a decimal when there is one.
			number = value == Math.rint(value)
				? String.valueOf((long) value)
				: String.valueOf(Math.round(value * 10.0) / 10.0);
			number += "%";
		}
		else
		{
			number = String.valueOf((long) value);
		}

		return value >= 0 ? "+" + number : number;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
