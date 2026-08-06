package com.gearforge.dps;

import com.gearforge.data.EquipmentStats;
import java.util.function.ToIntFunction;

/**
 * The attack type being used, which decides both which equipment attack bonus applies and which of
 * the target's defensive bonuses it is rolled against.
 */
public enum CombatStyle
{
	STAB(EquipmentStats::getAstab, EquipmentStats::getDstab),
	SLASH(EquipmentStats::getAslash, EquipmentStats::getDslash),
	CRUSH(EquipmentStats::getAcrush, EquipmentStats::getDcrush),
	RANGED(EquipmentStats::getArange, EquipmentStats::getDrange),
	MAGIC(EquipmentStats::getAmagic, EquipmentStats::getDmagic);

	private final ToIntFunction<EquipmentStats> attackBonus;
	private final ToIntFunction<EquipmentStats> defenceBonus;

	CombatStyle(ToIntFunction<EquipmentStats> attackBonus, ToIntFunction<EquipmentStats> defenceBonus)
	{
		this.attackBonus = attackBonus;
		this.defenceBonus = defenceBonus;
	}

	/**
	 * The attacker's equipment bonus that feeds the attack roll.
	 */
	public int attackBonusOf(EquipmentStats stats)
	{
		return attackBonus.applyAsInt(stats);
	}

	/**
	 * The defender's bonus that feeds the defence roll.
	 */
	public int defenceBonusOf(EquipmentStats stats)
	{
		return defenceBonus.applyAsInt(stats);
	}

	public boolean isMelee()
	{
		return this == STAB || this == SLASH || this == CRUSH;
	}

	public boolean isRanged()
	{
		return this == RANGED;
	}

	public boolean isMagic()
	{
		return this == MAGIC;
	}
}
