package com.gearforge.setups;

import com.gearforge.data.EquipmentSlot;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * Checks a setup against what the player owns.
 * <p>
 * Pure and RuneLite-free so it can be tested without a client. Charges and doses are not checked:
 * the spec is explicit that unknown charges must read as neutral rather than as a warning, and we
 * have no charge data at all yet, so claiming to check them would be worse than not checking.
 */
public final class SetupValidator
{
	private SetupValidator()
	{
	}

	public enum Status
	{
		/** Exactly the item asked for. */
		HAVE("Have it"),
		/** A different member of the same family — a degraded or imbued version. */
		VARIANT("Different version"),
		/** Not in the bank, inventory or worn. */
		MISSING("Missing");

		private final String displayName;

		Status(String displayName)
		{
			this.displayName = displayName;
		}

		@Override
		public String toString()
		{
			return displayName;
		}
	}

	/**
	 * @param owned          canonical item id to quantity owned
	 * @param variantGrouper maps an item id to its variant family id
	 */
	public static Map<EquipmentSlot, Status> validate(
		Setup setup,
		Map<Integer, Integer> owned,
		IntUnaryOperator variantGrouper)
	{
		Map<EquipmentSlot, Status> statuses = new EnumMap<>(EquipmentSlot.class);

		for (Map.Entry<EquipmentSlot, ItemRequirement> entry : setup.getEquipment().entrySet())
		{
			statuses.put(entry.getKey(), check(entry.getValue(), owned, variantGrouper));
		}

		return statuses;
	}

	public static Status check(
		ItemRequirement requirement,
		Map<Integer, Integer> owned,
		IntUnaryOperator variantGrouper)
	{
		int needed = Math.max(1, requirement.getQuantity());

		if (owned.getOrDefault(requirement.getItemId(), 0) >= needed)
		{
			return Status.HAVE;
		}

		if (requirement.isFuzzy())
		{
			int wantedFamily = variantGrouper.applyAsInt(requirement.getItemId());

			for (Map.Entry<Integer, Integer> entry : owned.entrySet())
			{
				if (entry.getValue() >= needed
					&& variantGrouper.applyAsInt(entry.getKey()) == wantedFamily)
				{
					return Status.VARIANT;
				}
			}
		}

		return Status.MISSING;
	}

	/**
	 * Validates the inventory, accounting for duplicates.
	 * <p>
	 * A setup asking for four sharks is only satisfied if four are owned, so requirements are totalled
	 * per item before being compared — checking each slot independently would call one shark enough.
	 *
	 * @return a status per occupied inventory slot, indexed as the setup stores them
	 */
	public static Map<Integer, Status> validateInventory(
		Setup setup,
		Map<Integer, Integer> owned,
		IntUnaryOperator variantGrouper)
	{
		Map<Integer, Integer> needed = new HashMap<>();
		List<ItemRequirement> inventory = setup.getInventory();

		for (ItemRequirement requirement : inventory)
		{
			if (requirement != null)
			{
				needed.merge(requirement.getItemId(), Math.max(1, requirement.getQuantity()), Integer::sum);
			}
		}

		Map<Integer, Status> statuses = new LinkedHashMap<>();

		for (int slot = 0; slot < inventory.size(); slot++)
		{
			ItemRequirement requirement = inventory.get(slot);
			if (requirement == null)
			{
				continue;
			}

			ItemRequirement total = new ItemRequirement(
				requirement.getItemId(), needed.get(requirement.getItemId()), requirement.isFuzzy());

			statuses.put(slot, check(total, owned, variantGrouper));
		}

		return statuses;
	}

	/**
	 * @return how many of the setup's items the player has in some form
	 */
	public static int satisfiedCount(Map<EquipmentSlot, Status> statuses)
	{
		return countSatisfied(statuses.values());
	}

	public static int countSatisfied(Collection<Status> statuses)
	{
		int count = 0;
		for (Status status : statuses)
		{
			if (status != Status.MISSING)
			{
				count++;
			}
		}

		return count;
	}
}
