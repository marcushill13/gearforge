package com.gearforge.data;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.client.config.ConfigManager;

/**
 * What the player owns, and how long ago we last saw it.
 * <p>
 * The bank can only be read while it is open, so it is snapshotted with a timestamp and persisted per
 * RS profile. Inventory and worn equipment are live and are not persisted.
 */
@Slf4j
@Singleton
public class BankModel
{
	private static final String CONFIG_GROUP = "gearforge";
	private static final String SNAPSHOT_KEY = "gearSnapshot";

	/** Marks an empty inventory slot. */
	public static final int EMPTY_SLOT = -1;
	public static final int INVENTORY_SIZE = 28;

	private final ConfigManager configManager;
	private final Gson gson;
	private final ItemCanonicalizer canonicalizer;

	private final Map<Integer, Integer> bank = new HashMap<>();
	private final Map<Integer, Integer> inventory = new HashMap<>();
	private final Map<Integer, Integer> equipment = new HashMap<>();
	private final int[] inventoryOrder = new int[INVENTORY_SIZE];

	private long bankCapturedAt;

	@Inject
	private BankModel(ConfigManager configManager, Gson gson, ItemCanonicalizer canonicalizer)
	{
		this.configManager = configManager;
		this.gson = gson;
		this.canonicalizer = canonicalizer;
	}

	/**
	 * Replaces the bank snapshot from an open bank container and persists it.
	 */
	public void updateBank(ItemContainer container)
	{
		bank.clear();
		collect(container, bank);
		bankCapturedAt = System.currentTimeMillis();
		save();
		log.debug("Bank snapshot updated: {} unique items", bank.size());
	}

	public void updateInventory(ItemContainer container)
	{
		inventory.clear();
		collect(container, inventory);
		captureInventoryOrder(container);
	}

	/**
	 * Records the inventory slot by slot, which the aggregate map cannot express.
	 * <p>
	 * Setups store an ordered inventory, so position matters — an empty slot in the middle is real
	 * information, not something to compact away.
	 */
	private void captureInventoryOrder(@Nullable ItemContainer container)
	{
		Arrays.fill(inventoryOrder, EMPTY_SLOT);

		if (container == null)
		{
			return;
		}

		Item[] items = container.getItems();
		for (int slot = 0; slot < Math.min(items.length, INVENTORY_SIZE); slot++)
		{
			Item item = items[slot];
			if (item != null && item.getQuantity() > 0 && item.getId() >= 0)
			{
				inventoryOrder[slot] = canonicalizer.canonicalize(item.getId());
			}
		}
	}

	/**
	 * @return the inventory by slot, {@value #EMPTY_SLOT} where empty. A copy; safe to keep.
	 */
	public int[] getInventoryOrder()
	{
		return Arrays.copyOf(inventoryOrder, inventoryOrder.length);
	}

	public void updateEquipment(ItemContainer container)
	{
		equipment.clear();
		collect(container, equipment);
	}

	/**
	 * Reads a container into a canonical id to quantity map, discarding empty slots and bank
	 * placeholders (which are always quantity 0).
	 */
	private void collect(@Nullable ItemContainer container, Map<Integer, Integer> into)
	{
		if (container == null)
		{
			return;
		}

		for (Item item : container.getItems())
		{
			int quantity = item.getQuantity();
			if (quantity <= 0 || item.getId() < 0)
			{
				continue;
			}

			int id = canonicalizer.canonicalize(item.getId());
			into.merge(id, quantity, Integer::sum);
		}
	}

	/**
	 * Everything the player owns: bank, inventory and worn gear merged, with quantities summed and the
	 * storages each item was found in.
	 */
	public Map<Integer, OwnedQuantity> ownedItems()
	{
		Map<Integer, OwnedQuantity> owned = new LinkedHashMap<>();
		mergeInto(owned, bank, Storage.BANK);
		mergeInto(owned, inventory, Storage.INVENTORY);
		mergeInto(owned, equipment, Storage.EQUIPMENT);
		return owned;
	}

	private static void mergeInto(Map<Integer, OwnedQuantity> owned, Map<Integer, Integer> source, Storage storage)
	{
		for (Map.Entry<Integer, Integer> entry : source.entrySet())
		{
			owned.compute(entry.getKey(), (id, existing) -> existing == null
				? new OwnedQuantity(entry.getValue(), EnumSet.of(storage))
				: existing.plus(entry.getValue(), storage));
		}
	}

	/**
	 * @return epoch millis of the last bank read, or 0 if the bank has never been seen.
	 */
	public long getBankCapturedAt()
	{
		return bankCapturedAt;
	}

	public boolean hasBankData()
	{
		return bankCapturedAt > 0;
	}

	/**
	 * Loads the persisted snapshot for the currently active RS profile. Safe to call repeatedly; it
	 * clears live state so switching accounts cannot leak one account's gear into another's.
	 */
	public void load()
	{
		bank.clear();
		inventory.clear();
		equipment.clear();
		bankCapturedAt = 0;

		if (configManager.getRSProfileKey() == null)
		{
			return;
		}

		String json = configManager.getRSProfileConfiguration(CONFIG_GROUP, SNAPSHOT_KEY);
		if (json == null || json.isEmpty())
		{
			return;
		}

		try
		{
			GearSnapshot snapshot = gson.fromJson(json, GearSnapshot.class);
			if (snapshot == null || snapshot.getVersion() != GearSnapshot.CURRENT_VERSION)
			{
				log.debug("Discarding gear snapshot from an incompatible version");
				return;
			}

			if (snapshot.getBank() != null)
			{
				bank.putAll(snapshot.getBank());
			}
			bankCapturedAt = snapshot.getCapturedAt();
			log.debug("Loaded bank snapshot: {} unique items", bank.size());
		}
		catch (JsonSyntaxException e)
		{
			log.warn("Could not read the stored gear snapshot, starting fresh", e);
		}
	}

	private void save()
	{
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}

		GearSnapshot snapshot = new GearSnapshot();
		snapshot.setBank(new HashMap<>(bank));
		snapshot.setCapturedAt(bankCapturedAt);
		configManager.setRSProfileConfiguration(CONFIG_GROUP, SNAPSHOT_KEY, gson.toJson(snapshot));
	}

	/**
	 * A quantity owned plus the storages it was found in.
	 */
	public static final class OwnedQuantity
	{
		private final int quantity;
		private final Set<Storage> locations;

		OwnedQuantity(int quantity, Set<Storage> locations)
		{
			this.quantity = quantity;
			this.locations = locations;
		}

		OwnedQuantity plus(int extra, Storage storage)
		{
			Set<Storage> merged = EnumSet.copyOf(locations);
			merged.add(storage);
			return new OwnedQuantity(quantity + extra, merged);
		}

		public int getQuantity()
		{
			return quantity;
		}

		public Set<Storage> getLocations()
		{
			return Collections.unmodifiableSet(locations);
		}
	}
}
