package com.gearforge.optimizer;

import com.gearforge.data.EquipmentSlot;
import com.gearforge.data.GearItem;
import java.util.Map;

/**
 * Structural rules about what can be worn together.
 * <p>
 * Only rules that can be decided from data we actually have are implemented. Equipment level and
 * quest requirements are deliberately absent: RuneLite's item data carries no requirements, so
 * enforcing them needs a generated dataset. Until that exists the optimizer can suggest an item the
 * player owns but cannot yet equip — see the README for the caveat rather than guessing at limits.
 */
public final class Constraints
{
	private Constraints()
	{
	}

	public static boolean isTwoHanded(GearItem item)
	{
		return item != null && item.getStats().isTwoHanded();
	}

	/**
	 * Whether an item can actually be attacked with.
	 * <p>
	 * Some weapon-slot items carry a nonsensical attack speed — a monkey greegree is {@code -1}, and
	 * around 29 novelty items are {@code 0}. Left unchecked those become one-tick weapons and score as
	 * wildly best-in-slot. Anything without a positive speed is not a weapon.
	 */
	public static boolean isUsableWeapon(GearItem item)
	{
		return item != null && item.getStats().getSpeed() > 0;
	}

	/**
	 * A two-handed weapon occupies the shield slot as well, so the two can never coexist.
	 */
	public static boolean isValid(Map<EquipmentSlot, GearItem> setup)
	{
		GearItem weapon = setup.get(EquipmentSlot.WEAPON);
		GearItem shield = setup.get(EquipmentSlot.SHIELD);

		return !(isTwoHanded(weapon) && shield != null);
	}

	/**
	 * Whether an item can occupy the slot it claims. Guards against a caller putting a helmet in the
	 * boots slot when assembling setups by hand.
	 */
	public static boolean fitsSlot(GearItem item, EquipmentSlot slot)
	{
		return item != null && item.getStats().getSlot() == slot.getSlotIndex();
	}
}
