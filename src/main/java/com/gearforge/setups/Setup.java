package com.gearforge.setups;

import com.gearforge.data.EquipmentSlot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A saved loadout.
 * <p>
 * Equipment only for now — the inventory grid, rune pouch and quiver from the design are not built
 * yet. A plain class with a no-arg constructor because this is persisted with Gson.
 */
@Data
@NoArgsConstructor
public class Setup
{
	/** String rather than UUID so the persisted JSON stays readable and trivially portable. */
	private String id = UUID.randomUUID().toString();

	private String name = "New setup";
	private SetupSource source = SetupSource.MANUAL;
	private Map<EquipmentSlot, ItemRequirement> equipment = new LinkedHashMap<>();

	/**
	 * The inventory, slot by slot. Up to 28 entries with nulls for empty slots — position is preserved
	 * because where something sits in your inventory is part of the setup.
	 */
	private List<ItemRequirement> inventory = new ArrayList<>();

	private long lastUsedMillis;

	/**
	 * Item ids whose upgrade suggestion has been dismissed, so a deliberately suboptimal setup stops
	 * nagging. Permanent per setup.
	 */
	private Set<Integer> dismissedUpgrades = new HashSet<>();

	public static Setup named(String name, SetupSource source)
	{
		Setup setup = new Setup();
		setup.setName(name);
		setup.setSource(source);
		return setup;
	}

	public void put(EquipmentSlot slot, int itemId)
	{
		equipment.put(slot, ItemRequirement.of(itemId));
	}

	/**
	 * Replaces the inventory from a slot-indexed array, where {@code -1} means empty.
	 */
	public void setInventoryFrom(int[] slots)
	{
		inventory = new ArrayList<>();
		for (int itemId : slots)
		{
			inventory.add(itemId < 0 ? null : ItemRequirement.of(itemId));
		}

		trimTrailingEmptySlots();
	}

	/**
	 * Trailing empties carry no information and only bloat the saved JSON.
	 */
	private void trimTrailingEmptySlots()
	{
		int last = inventory.size() - 1;
		while (last >= 0 && inventory.get(last) == null)
		{
			inventory.remove(last);
			last--;
		}
	}

	/**
	 * @return how many inventory slots actually hold something
	 */
	public int inventoryCount()
	{
		int count = 0;
		for (ItemRequirement requirement : inventory)
		{
			if (requirement != null)
			{
				count++;
			}
		}

		return count;
	}

	/**
	 * Total items the setup asks for, across equipment and inventory.
	 */
	public int size()
	{
		return equipment.size() + inventoryCount();
	}
}
