package com.gearforge.optimizer;

import com.gearforge.data.EquipmentSlot;
import com.gearforge.data.GearItem;
import lombok.Value;

/**
 * One item in a saved setup that could be swapped for something better the player already owns.
 */
@Value
public class UpgradeSuggestion
{
	EquipmentSlot slot;

	/** The better item, which the player owns. */
	GearItem replacement;

	/** Fractional DPS improvement, e.g. 0.021 for +2.1%. */
	double gain;

	public String describe()
	{
		return String.format("Better %s: %s, +%.1f%% DPS",
			slot.getDisplayName().toLowerCase(), replacement.getName(), gain * 100.0);
	}
}
