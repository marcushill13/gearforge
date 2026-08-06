package com.gearforge.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The equipment slots that real items actually occupy.
 * <p>
 * {@code slotIndex} matches {@link net.runelite.api.EquipmentInventorySlot#getSlotIdx()} and the
 * {@code slot} field returned by {@link net.runelite.client.game.ItemEquipmentStats#getSlot()}.
 * The purely cosmetic slots (arms, hair, jaw) are deliberately absent — no equippable item uses them.
 */
@Getter
@RequiredArgsConstructor
public enum EquipmentSlot
{
	HEAD(0, "Head"),
	CAPE(1, "Cape"),
	AMULET(2, "Neck"),
	WEAPON(3, "Weapon"),
	BODY(4, "Body"),
	SHIELD(5, "Shield"),
	LEGS(7, "Legs"),
	GLOVES(9, "Hands"),
	BOOTS(10, "Feet"),
	RING(12, "Ring"),
	AMMO(13, "Ammo");

	private final int slotIndex;
	private final String displayName;

	/**
	 * @return the slot with this index, or null if the index is a cosmetic slot or out of range.
	 */
	public static EquipmentSlot fromSlotIndex(int slotIndex)
	{
		for (EquipmentSlot slot : values())
		{
			if (slot.slotIndex == slotIndex)
			{
				return slot;
			}
		}

		return null;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
