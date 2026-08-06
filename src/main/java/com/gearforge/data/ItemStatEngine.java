package com.gearforge.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

/**
 * Turns owned item ids into items with resolved bonuses.
 * <p>
 * RuneLite loads its equipment stat table asynchronously after startup, so {@link #statsFor(int)}
 * returns null both for items that have no bonuses and for every item before that load completes.
 * Callers must treat an empty result as "not ready yet" rather than "you own nothing".
 */
@Singleton
public class ItemStatEngine
{
	private final ItemManager itemManager;

	@Inject
	private ItemStatEngine(ItemManager itemManager)
	{
		this.itemManager = itemManager;
	}

	/**
	 * @return the item's equipment bonuses, or null if it is not equippable or stats have not loaded.
	 */
	@Nullable
	public EquipmentStats statsFor(int itemId)
	{
		ItemStats stats = itemManager.getItemStats(itemId);
		if (stats == null || !stats.isEquipable())
		{
			return null;
		}

		ItemEquipmentStats equipment = stats.getEquipment();
		if (equipment == null)
		{
			return null;
		}

		return EquipmentStats.builder()
			.astab(equipment.getAstab())
			.aslash(equipment.getAslash())
			.acrush(equipment.getAcrush())
			.amagic(equipment.getAmagic())
			.arange(equipment.getArange())
			.dstab(equipment.getDstab())
			.dslash(equipment.getDslash())
			.dcrush(equipment.getDcrush())
			.dmagic(equipment.getDmagic())
			.drange(equipment.getDrange())
			.strength(equipment.getStr())
			.rangedStrength(equipment.getRstr())
			.magicDamage(equipment.getMdmg())
			.prayer(equipment.getPrayer())
			.slot(equipment.getSlot())
			.twoHanded(equipment.isTwoHanded())
			.speed(equipment.getAspeed())
			.build();
	}

	/**
	 * Resolves everything the player owns into equippable items with bonuses and names.
	 * <p>
	 * Must be called on the client thread — item names come from {@link ItemComposition}.
	 *
	 * @return every owned item that is equippable; non-equippable items are dropped.
	 */
	public List<GearItem> resolveOwnedGear(BankModel bankModel)
	{
		Map<Integer, BankModel.OwnedQuantity> owned = bankModel.ownedItems();
		List<GearItem> gear = new ArrayList<>();

		for (Map.Entry<Integer, BankModel.OwnedQuantity> entry : owned.entrySet())
		{
			int itemId = entry.getKey();
			EquipmentStats stats = statsFor(itemId);
			if (stats == null)
			{
				continue;
			}

			if (EquipmentSlot.fromSlotIndex(stats.getSlot()) == null)
			{
				// A cosmetic-only slot. Nothing to rank it against.
				continue;
			}

			ItemComposition composition = itemManager.getItemComposition(itemId);
			BankModel.OwnedQuantity quantity = entry.getValue();
			gear.add(new GearItem(
				itemId,
				composition.getName(),
				quantity.getQuantity(),
				stats,
				quantity.getLocations()));
		}

		return gear;
	}
}
