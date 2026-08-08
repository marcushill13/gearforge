package com.gearforge.data;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The equipment slots that real items actually occupy.
 * <p>
 * {@code slotIndex} matches {@link net.runelite.api.EquipmentInventorySlot#getSlotIdx()} and the
 * {@code slot} field returned by {@link net.runelite.client.game.ItemEquipmentStats#getSlot()}.
 * The purely cosmetic slots (arms, hair, jaw) are deliberately absent — no equippable item uses them.
 * <p>
 * Every constant carries a {@link SerializedName} equal to its display name, and it has to. A saved
 * setup keys its equipment map by this enum, and Gson writes map keys with {@code String.valueOf} —
 * that is {@link #toString()}, the display name — while reading them through the enum adapter, which
 * matches on {@link #name()}. Without the annotation the two disagree, so "Hands" and "Feet" both
 * read back as null, Gson throws {@code duplicate key: null}, and the whole file is discarded. That
 * silently emptied every saved setup on restart. {@code SetupStoreTest} pins the round trip; the
 * enum name is kept as an alternate so nothing already on disk is lost.
 */
@Getter
@RequiredArgsConstructor
public enum EquipmentSlot
{
	@SerializedName(value = "Head", alternate = "HEAD")
	HEAD(0, "Head"),

	@SerializedName(value = "Cape", alternate = "CAPE")
	CAPE(1, "Cape"),

	@SerializedName(value = "Neck", alternate = "AMULET")
	AMULET(2, "Neck"),

	@SerializedName(value = "Weapon", alternate = "WEAPON")
	WEAPON(3, "Weapon"),

	@SerializedName(value = "Body", alternate = "BODY")
	BODY(4, "Body"),

	@SerializedName(value = "Shield", alternate = "SHIELD")
	SHIELD(5, "Shield"),

	@SerializedName(value = "Legs", alternate = "LEGS")
	LEGS(7, "Legs"),

	@SerializedName(value = "Hands", alternate = "GLOVES")
	GLOVES(9, "Hands"),

	@SerializedName(value = "Feet", alternate = "BOOTS")
	BOOTS(10, "Feet"),

	@SerializedName(value = "Ring", alternate = "RING")
	RING(12, "Ring"),

	@SerializedName(value = "Ammo", alternate = "AMMO")
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
