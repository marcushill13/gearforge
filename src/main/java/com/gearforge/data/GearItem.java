package com.gearforge.data;

import java.util.Set;
import lombok.Value;

/**
 * One equippable item the player owns, with its bonuses resolved.
 */
@Value
public class GearItem
{
	int itemId;
	String name;
	int quantity;
	EquipmentStats stats;
	Set<Storage> locations;

	public double statValue(GearStat stat)
	{
		return stat.valueOf(stats);
	}
}
