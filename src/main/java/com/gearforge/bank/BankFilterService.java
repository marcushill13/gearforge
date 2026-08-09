package com.gearforge.bank;

import com.gearforge.data.EquipmentSlot;
import com.gearforge.data.BankModel;
import com.gearforge.data.ItemCanonicalizer;
import com.gearforge.setups.ItemRequirement;
import com.gearforge.setups.Setup;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.banktags.BankTag;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;

/**
 * Filters the bank down to the active setup's items and arranges them like the equipment screen.
 * <p>
 * This works through RuneLite's own Bank Tags plugin rather than reimplementing bank filtering: we
 * register a virtual tag whose membership is "is this item in the active setup", save a layout for
 * it, then ask Bank Tags to open it. Nothing is written to the user's own tags and no item is moved —
 * the bank shows a filtered, arranged view, and every click remains theirs.
 * <p>
 * <b>Why these are looked up rather than injected.</b> Every RuneLite plugin gets its own Guice child
 * injector. {@code BankTagsService} is bound inside Bank Tags' injector and {@code LayoutManager}
 * depends on {@code BankTagsConfig}, which lives there too — injecting either makes Guice fail to
 * construct this plugin, which removes GearForge from the plugin list with no visible error. Both are
 * reached instead through the running plugin's own injector. {@link TagManager} needs only root-level
 * dependencies, so it injects normally.
 */
@Slf4j
@Singleton
public class BankFilterService
{
	/**
	 * Namespaced so it cannot collide with a tag the player created.
	 */
	private static final String TAG = "gearforge";

	/** The third-party Bank Tag Layouts plugin, which renders tag layouts itself. */
	private static final String BANK_TAG_LAYOUTS_GROUP = "banktaglayouts";
	private static final String BANK_TAG_LAYOUTS_LAYOUT_PREFIX = "layout_";

	private static final int EMPTY = -1;

	/** Five rows for the equipment doll, a blank row, then seven rows of inventory. */
	private static final int DOLL_ROWS = 5;
	private static final int INVENTORY_START_ROW = 6;
	private static final int INVENTORY_COLUMNS = 4;
	private static final int INVENTORY_ROWS = 7;
	private static final int ROWS = INVENTORY_START_ROW + INVENTORY_ROWS;
	private static final int LAYOUT_SIZE = ROWS * BankTagsPlugin.BANK_ITEMS_PER_ROW;

	/**
	 * Where each slot sits in the bank grid, mirroring the in-game equipment screen:
	 *
	 * <pre>
	 *        head
	 *  cape  neck  ammo
	 * weapon body shield
	 *        legs
	 * hands  feet  ring
	 * </pre>
	 */
	private static final Map<EquipmentSlot, Integer> DOLL_POSITIONS = new EnumMap<>(EquipmentSlot.class);

	static
	{
		int perRow = BankTagsPlugin.BANK_ITEMS_PER_ROW;
		DOLL_POSITIONS.put(EquipmentSlot.HEAD, 1);
		DOLL_POSITIONS.put(EquipmentSlot.CAPE, perRow);
		DOLL_POSITIONS.put(EquipmentSlot.AMULET, perRow + 1);
		DOLL_POSITIONS.put(EquipmentSlot.AMMO, perRow + 2);
		DOLL_POSITIONS.put(EquipmentSlot.WEAPON, 2 * perRow);
		DOLL_POSITIONS.put(EquipmentSlot.BODY, 2 * perRow + 1);
		DOLL_POSITIONS.put(EquipmentSlot.SHIELD, 2 * perRow + 2);
		DOLL_POSITIONS.put(EquipmentSlot.LEGS, 3 * perRow + 1);
		DOLL_POSITIONS.put(EquipmentSlot.GLOVES, 4 * perRow);
		DOLL_POSITIONS.put(EquipmentSlot.BOOTS, 4 * perRow + 1);
		DOLL_POSITIONS.put(EquipmentSlot.RING, 4 * perRow + 2);
	}

	private final ClientThread clientThread;
	private final TagManager tagManager;
	private final PluginManager pluginManager;
	private final ConfigManager configManager;
	private final ItemCanonicalizer canonicalizer;
	private final BankModel bankModel;

	private boolean registered;

	/** The setup currently shown, which may be an unsaved preview from the BiS tab. */
	@Nullable
	private Setup shownSetup;

	/** Accepted ids for {@link #shownSetup}, variant families already expanded. */
	private Set<Integer> shownIds = Collections.emptySet();

	@Inject
	private BankFilterService(
		ClientThread clientThread,
		TagManager tagManager,
		PluginManager pluginManager,
		ConfigManager configManager,
		ItemCanonicalizer canonicalizer,
		BankModel bankModel)
	{
		this.clientThread = clientThread;
		this.tagManager = tagManager;
		this.pluginManager = pluginManager;
		this.configManager = configManager;
		this.canonicalizer = canonicalizer;
		this.bankModel = bankModel;
	}

	/**
	 * Item ids to filter and highlight. Precomputed rather than derived per rendered item, because the
	 * highlight overlay runs for every visible item every frame.
	 */
	public Set<Integer> shownIds()
	{
		return shownIds;
	}

	/**
	 * @return true if this exact setup is the one currently shown
	 */
	public boolean isShowing(Setup setup)
	{
		return shownSetup != null && shownSetup.getId().equals(setup.getId());
	}

	public boolean isShowingAnything()
	{
		return shownSetup != null;
	}

	/**
	 * The running Bank Tags plugin, which implements the service.
	 *
	 * @return null if Bank Tags is not loaded, in which case filtering is unavailable
	 */
	@Nullable
	private Plugin bankTagsPlugin()
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (plugin instanceof BankTagsService)
			{
				return plugin;
			}
		}

		return null;
	}

	/**
	 * Resolves a class from Bank Tags' own injector, where its plugin-private bindings exist.
	 *
	 * @return the instance, or null if Bank Tags is not loaded or the binding is unavailable
	 */
	@Nullable
	private <T> T fromBankTags(Class<T> type)
	{
		Plugin plugin = bankTagsPlugin();
		if (plugin == null || plugin.getInjector() == null)
		{
			return null;
		}

		try
		{
			return plugin.getInjector().getInstance(type);
		}
		catch (RuntimeException e)
		{
			log.debug("Could not resolve {} from Bank Tags", type.getSimpleName(), e);
			return null;
		}
	}

	/**
	 * Registers the virtual tag. Membership is resolved live from the store, so the tag automatically
	 * follows whichever setup is active without needing re-registration.
	 */
	public void register()
	{
		if (registered)
		{
			return;
		}

		BankTag tag = itemId -> shownIds.contains(itemId);
		tagManager.registerTag(TAG, tag);
		registered = true;
	}

	public void unregister()
	{
		if (!registered)
		{
			return;
		}

		clearFilter();

		// Leave no layout entry behind in the user's Bank Tags config.
		LayoutManager layoutManager = fromBankTags(LayoutManager.class);
		if (layoutManager != null)
		{
			layoutManager.removeLayout(TAG);
		}

		configManager.unsetConfiguration(BANK_TAG_LAYOUTS_GROUP, BANK_TAG_LAYOUTS_LAYOUT_PREFIX + TAG);

		tagManager.unregisterTag(TAG);
		registered = false;
	}

	/**
	 * Shows only this setup's items in the bank, arranged as an equipment doll.
	 * <p>
	 * The setup need not be saved — the BiS tab previews its result this way.
	 */
	public void applySetup(Setup setup)
	{
		shownSetup = setup;
		shownIds = acceptedIds(setup);

		Plugin plugin = bankTagsPlugin();
		if (plugin == null)
		{
			log.debug("Bank Tags is not available; cannot filter the bank");
			return;
		}

		BankTagsService service = (BankTagsService) plugin;

		clientThread.invoke(() ->
		{
			// The layout must be saved first: openBankTag reads it back via loadLayout.
			saveLayoutFor(setup);

			// Hide the tag name — this is GearForge's view, not a tag the player made and can rename.
			// OPTION_NO_LAYOUT is deliberately absent, otherwise the arrangement is discarded.
			service.openBankTag(TAG, BankTagsService.OPTION_HIDE_TAG_NAME);
		});
	}

	public void clearFilter()
	{
		shownSetup = null;
		shownIds = Collections.emptySet();

		Plugin plugin = bankTagsPlugin();
		if (plugin != null)
		{
			clientThread.invoke(((BankTagsService) plugin)::closeBankTag);
		}
	}

	/**
	 * Re-applies whatever is currently shown, e.g. when the bank is reopened.
	 */
	public void reapply()
	{
		if (shownSetup != null)
		{
			applySetup(shownSetup);
		}
	}

	/**
	 * Every id that satisfies the setup, expanding variant families so a degraded or imbued version
	 * still shows up.
	 */
	private Set<Integer> acceptedIds(Setup setup)
	{
		Set<Integer> ids = new HashSet<>();
		Map<Integer, BankModel.OwnedQuantity> owned = bankModel.ownedItems();

		List<ItemRequirement> all = new ArrayList<>(setup.getEquipment().values());
		all.addAll(setup.getInventory());

		for (ItemRequirement requirement : all)
		{
			if (requirement == null)
			{
				continue;
			}

			ids.add(requirement.getItemId());

			// Only variants actually owned. Tagging the whole family put a slot in the bank for every
			// charge state of a skills necklace, five of which the player has never had.
			if (requirement.isFuzzy())
			{
				for (int variant : canonicalizer.variantsOf(requirement.getItemId()))
				{
					if (owned.containsKey(variant))
					{
						ids.add(variant);
					}
				}
			}
		}

		return ids;
	}

	/**
	 * Writes the equipment-doll arrangement for this setup. Best effort — if Bank Tags cannot be
	 * reached the filter still works, just in bank order.
	 */
	private void saveLayoutFor(Setup setup)
	{
		LayoutManager layoutManager = fromBankTags(LayoutManager.class);
		if (layoutManager == null)
		{
			return;
		}

		int[] positions = new int[LAYOUT_SIZE];
		Arrays.fill(positions, EMPTY);

		for (Map.Entry<EquipmentSlot, ItemRequirement> entry : setup.getEquipment().entrySet())
		{
			Integer position = DOLL_POSITIONS.get(entry.getKey());
			if (position != null)
			{
				positions[position] = entry.getValue().getItemId();
			}
		}

		// The inventory sits below the doll, four wide, mirroring the in-game grid.
		List<ItemRequirement> inventory = setup.getInventory();
		int perRow = BankTagsPlugin.BANK_ITEMS_PER_ROW;

		for (int slot = 0; slot < inventory.size() && slot < INVENTORY_COLUMNS * INVENTORY_ROWS; slot++)
		{
			ItemRequirement requirement = inventory.get(slot);
			if (requirement == null)
			{
				continue;
			}

			int row = INVENTORY_START_ROW + (slot / INVENTORY_COLUMNS);
			int column = slot % INVENTORY_COLUMNS;
			int position = row * perRow + column;

			// A duplicate stack would overwrite the earlier one; leave the first placement standing.
			if (positions[position] == EMPTY)
			{
				positions[position] = requirement.getItemId();
			}
		}

		layoutManager.saveLayout(new Layout(TAG, positions));
		writeBankTagLayoutsCompatibility(positions);
	}

	/**
	 * @return whether bank filtering can work at all, so the UI can say so rather than doing nothing
	 */
	public boolean isAvailable()
	{
		return bankTagsPlugin() != null;
	}

	/**
	 * Whether the third-party Bank Tag Layouts plugin is installed and switched on.
	 * <p>
	 * Detected through config rather than by inspecting plugin classes, since that plugin is not on
	 * our classpath and RuneLite forbids reflection.
	 */
	public boolean isBankTagLayoutsEnabled()
	{
		return "true".equals(configManager.getConfiguration("runelite", "banktaglayoutsplugin"));
	}

	/**
	 * Also writes the arrangement in Bank Tag Layouts' own format.
	 * <p>
	 * That plugin renders bank layouts itself and overrides the native one, which previously left
	 * GearForge's equipment doll flattened into a single packed row. Writing the same arrangement in
	 * its format means it renders ours instead of generating its own. Only our private tag's key is
	 * touched — the player's own tags are never written to — and the key is removed on shutdown.
	 * <p>
	 * Best effort: if that plugin regenerates the layout anyway, the panel warns the player instead.
	 */
	private void writeBankTagLayoutsCompatibility(int[] positions)
	{
		if (!isBankTagLayoutsEnabled())
		{
			return;
		}

		StringBuilder layout = new StringBuilder();
		for (int position = 0; position < positions.length; position++)
		{
			if (positions[position] == EMPTY)
			{
				continue;
			}

			if (layout.length() > 0)
			{
				layout.append(',');
			}

			layout.append(positions[position]).append(':').append(position);
		}

		configManager.setConfiguration(
			BANK_TAG_LAYOUTS_GROUP, BANK_TAG_LAYOUTS_LAYOUT_PREFIX + TAG, layout.toString());
	}
}
