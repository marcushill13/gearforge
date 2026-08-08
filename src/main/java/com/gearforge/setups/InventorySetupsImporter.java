package com.gearforge.setups;

import com.gearforge.data.BankModel;
import com.gearforge.data.EquipmentSlot;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Imports loadouts from the Inventory Setups plugin.
 * <p>
 * Zero switching cost is the point: someone with twenty setups already built should not have to
 * rebuild them by hand to try this plugin.
 * <p>
 * Reads Inventory Setups' own stored configuration rather than any shared API — it exposes none.
 * Each setup lives under its own {@code setupsV3_<hash>} key in the {@code inventorysetups} config
 * group, holding JSON of the shape:
 * <pre>
 * {"inv":[{"id":8792},...,null], "eq":[{"id":3751},null,...], "name":"Birdhouse", ...}
 * </pre>
 * {@code eq} is indexed by equipment slot and {@code inv} is 28 ordered inventory slots, which is the
 * same shape GearForge stores, so the mapping is direct.
 * <p>
 * Nothing is written back to Inventory Setups. Importing copies; it never modifies or removes the
 * original, so both plugins can be used side by side.
 */
@Slf4j
@Singleton
public class InventorySetupsImporter
{
	private static final String CONFIG_GROUP = "inventorysetups";
	private static final String SETUP_KEY_PREFIX = "setupsV3_";

	private final ConfigManager configManager;
	private final Gson gson;

	@Inject
	public InventorySetupsImporter(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	/**
	 * Reads every Inventory Setups loadout and converts it. Setups that fail to parse are skipped with
	 * a log line rather than aborting the whole import.
	 */
	public List<Setup> readAll()
	{
		List<Setup> imported = new ArrayList<>();

		for (String key : setupKeys())
		{
			String json = configManager.getConfiguration(CONFIG_GROUP, key);
			if (json == null || json.isEmpty())
			{
				continue;
			}

			Setup setup = parse(json);
			if (setup != null)
			{
				imported.add(setup);
			}
		}

		return imported;
	}

	private List<String> setupKeys()
	{
		List<String> keys = new ArrayList<>();

		for (String key : configManager.getConfigurationKeys(CONFIG_GROUP + "." + SETUP_KEY_PREFIX))
		{
			// getConfigurationKeys returns fully qualified keys; the group prefix has to come off.
			String stripped = key.substring(key.indexOf('.') + 1);
			if (stripped.startsWith(SETUP_KEY_PREFIX))
			{
				keys.add(stripped);
			}
		}

		return keys;
	}

	/**
	 * Converts one stored Inventory Setups record.
	 *
	 * @return the converted setup, or null if it is unreadable or holds nothing
	 */
	@Nullable
	public Setup parse(String json)
	{
		try
		{
			InventorySetupJson source = gson.fromJson(json, InventorySetupJson.class);
			if (source == null)
			{
				return null;
			}

			Setup setup = Setup.named(
				source.name == null || source.name.trim().isEmpty() ? "Imported setup" : source.name,
				SetupSource.IMPORTED);

			if (source.eq != null)
			{
				for (int index = 0; index < source.eq.size(); index++)
				{
					InventorySetupItem item = source.eq.get(index);
					EquipmentSlot slot = EquipmentSlot.fromSlotIndex(index);

					if (item != null && slot != null && item.id >= 0)
					{
						setup.put(slot, item.id);
					}
				}
			}

			if (source.inv != null)
			{
				int[] slots = new int[Math.min(source.inv.size(), BankModel.INVENTORY_SIZE)];
				for (int index = 0; index < slots.length; index++)
				{
					InventorySetupItem item = source.inv.get(index);
					slots[index] = item == null || item.id < 0 ? BankModel.EMPTY_SLOT : item.id;
				}

				setup.setInventoryFrom(slots);
			}

			return setup.size() == 0 ? null : setup;
		}
		catch (JsonSyntaxException e)
		{
			log.warn("Skipping an Inventory Setups entry that could not be read", e);
			return null;
		}
	}

	/**
	 * Mirrors Inventory Setups' stored shape. Only the fields GearForge can use are declared; the rest
	 * (colours, filter flags, layouts) are ignored.
	 */
	private static final class InventorySetupJson
	{
		List<InventorySetupItem> inv;
		List<InventorySetupItem> eq;
		String name;
	}

	private static final class InventorySetupItem
	{
		int id = -1;
	}
}
