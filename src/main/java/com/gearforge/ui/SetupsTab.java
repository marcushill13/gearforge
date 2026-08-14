package com.gearforge.ui;

import com.gearforge.bank.BankFilterService;
import com.gearforge.data.BankModel;
import com.gearforge.data.EquipmentSlot;
import com.gearforge.data.EquipmentStats;
import com.gearforge.data.GearItem;
import com.gearforge.data.ItemCanonicalizer;
import com.gearforge.data.ItemStatEngine;
import com.gearforge.data.PlayerLevels;
import com.gearforge.data.PlayerModel;
import com.gearforge.data.Storage;
import com.gearforge.dps.CombatContext;
import com.gearforge.dps.CombatStyle;
import com.gearforge.dps.Scoring;
import com.gearforge.optimizer.UpgradeFinder;
import com.gearforge.optimizer.UpgradeSuggestion;
import com.gearforge.setups.InventorySetupsImporter;
import com.gearforge.setups.ItemRequirement;
import com.gearforge.setups.Setup;
import com.gearforge.setups.SetupCodec;
import com.gearforge.setups.SetupSource;
import com.gearforge.setups.SetupStore;
import com.gearforge.setups.SetupValidator;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Saved loadouts.
 * <p>
 * Showing a setup in the bank filters it there — it never withdraws, deposits or equips anything.
 * Editing is per slot: take what you are wearing, or accept an upgrade the plugin found in your bank.
 */
@Singleton
class SetupsTab extends JPanel
{
	private final ClientThread clientThread;
	private final ScheduledExecutorService executor;
	private final BankModel bankModel;
	private final ItemStatEngine statEngine;
	private final ItemCanonicalizer canonicalizer;
	private final SetupStore setupStore;
	private final BankFilterService bankFilterService;
	private final PlayerModel playerModel;
	private final UpgradeFinder upgradeFinder;
	private final Scoring scoring;
	private final InventorySetupsImporter importer;
	private final ItemManager itemManager;

	private final JPanel list = new JPanel();

	private static final int INVENTORY_COLUMNS = 4;
	private static final int INVENTORY_ROWS = 7;

	/** Which setups are expanded for editing. */
	private final Set<String> expanded = new HashSet<>();

	@Inject
	private SetupsTab(
		ClientThread clientThread,
		ScheduledExecutorService executor,
		BankModel bankModel,
		ItemStatEngine statEngine,
		ItemCanonicalizer canonicalizer,
		SetupStore setupStore,
		BankFilterService bankFilterService,
		PlayerModel playerModel,
		UpgradeFinder upgradeFinder,
		Scoring scoring,
		InventorySetupsImporter importer,
		ItemManager itemManager)
	{
		this.clientThread = clientThread;
		this.executor = executor;
		this.bankModel = bankModel;
		this.statEngine = statEngine;
		this.canonicalizer = canonicalizer;
		this.setupStore = setupStore;
		this.bankFilterService = bankFilterService;
		this.playerModel = playerModel;
		this.upgradeFinder = upgradeFinder;
		this.scoring = scoring;
		this.importer = importer;
		this.itemManager = itemManager;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(buildControls(), BorderLayout.NORTH);

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel holder = new JPanel(new BorderLayout());
		holder.setBackground(ColorScheme.DARK_GRAY_COLOR);
		holder.add(list, BorderLayout.NORTH);

		JScrollPane scroll = new JScrollPane(
			holder, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(scroll, BorderLayout.CENTER);
	}

	private JPanel buildControls()
	{
		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
		controls.setBackground(ColorScheme.DARK_GRAY_COLOR);
		controls.setBorder(BorderFactory.createEmptyBorder(4, 0, 12, 0));

		JButton fromCurrent = Cards.button("Save what I'm wearing");
		fromCurrent.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		fromCurrent.addActionListener(event -> saveCurrentGear());
		controls.add(fromCurrent);

		controls.add(Cards.gap(4));

		JButton paste = Cards.button("Paste a share code");
		paste.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		paste.setToolTipText("Rebuild a setup someone shared with you");
		paste.addActionListener(event -> pasteShareCode());
		controls.add(paste);

		controls.add(Cards.gap(4));

		// Shown unconditionally. This panel is built once, so gating it on a check made at construction
		// hid the button from anyone who installed Inventory Setups or made their first loadout later —
		// the feature simply looked absent. Checking when it is clicked always reflects reality.
		JButton importSetups = Cards.button("Import from Inventory Setups");
		importSetups.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		importSetups.setToolTipText("Copy your Inventory Setups loadouts across. Nothing there is changed.");
		importSetups.addActionListener(event -> importFromInventorySetups());
		controls.add(importSetups);

		return controls;
	}

	/**
	 * The primary creation path from the design: capture what you have on rather than build a setup
	 * piece by piece.
	 */
	private void saveCurrentGear()
	{
		clientThread.invoke(() ->
		{
			Map<EquipmentSlot, GearItem> worn = wornGear();

			Setup setup = Setup.named("New setup", SetupSource.CURRENT_GEAR);
			worn.forEach((slot, item) -> setup.put(slot, item.getItemId()));
			setup.setInventoryFrom(bankModel.getInventoryOrder());

			SwingUtilities.invokeLater(() -> promptAndSave(setup, "Worn gear"));
		});
	}

	/**
	 * Saves a setup after asking for a name. Shared by this tab and the BiS tab's save button.
	 */
	void promptAndSave(Setup setup, String suggestedName)
	{
		if (setup.getEquipment().isEmpty())
		{
			JOptionPane.showMessageDialog(this,
				"Couldn't read your worn equipment. Log in and make sure you're wearing something, "
					+ "then try again.",
				"GearForge", JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		String name = JOptionPane.showInputDialog(this, "Name this setup", suggestedName);
		if (name == null || name.trim().isEmpty())
		{
			return;
		}

		setup.setName(name.trim());
		setupStore.add(setup);
		rebuild();
	}

	/**
	 * Copies loadouts across from Inventory Setups. Nothing there is modified or removed, so both
	 * plugins keep working.
	 */
	private void importFromInventorySetups()
	{
		List<Setup> found = importer.readAll();

		if (found.isEmpty())
		{
			JOptionPane.showMessageDialog(this,
				"No Inventory Setups loadouts found.\n\nThis reads the setups that plugin has already "
					+ "saved, so it needs Inventory Setups installed with at least one setup in it.",
				"GearForge", JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		int choice = JOptionPane.showConfirmDialog(this,
			"Import " + found.size() + (found.size() == 1 ? " setup" : " setups")
				+ " from Inventory Setups?\nYour Inventory Setups data won't be changed.",
			"GearForge", JOptionPane.YES_NO_OPTION);

		if (choice != JOptionPane.YES_OPTION)
		{
			return;
		}

		for (Setup setup : found)
		{
			setupStore.add(setup);
		}

		rebuild();
	}

	private void copyShareCode(Setup setup)
	{
		String code = SetupCodec.encode(setup);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code), null);

		JOptionPane.showMessageDialog(this,
			"Share code copied to your clipboard.", "GearForge", JOptionPane.INFORMATION_MESSAGE);
	}

	private void pasteShareCode()
	{
		String code = JOptionPane.showInputDialog(this, "Paste a GearForge share code");
		if (code == null || code.trim().isEmpty())
		{
			return;
		}

		Setup shared = SetupCodec.decode(code);
		if (shared == null)
		{
			JOptionPane.showMessageDialog(this,
				"That doesn't look like a GearForge share code.", "GearForge",
				JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		setupStore.add(shared);
		rebuild();
	}

	void rebuild()
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(this::rebuild);
			return;
		}

		// Item names and levels need the client thread; the upgrade search does not. Running it there
		// for every saved setup is part of what made gear swaps stutter.
		clientThread.invoke(() ->
		{
			List<GearItem> ownedGear = bankModel.hasBankData()
				? statEngine.resolveOwnedGear(bankModel)
				: Collections.emptyList();
			PlayerLevels levels = playerModel.snapshot();
			Map<Integer, String> inventoryNames = resolveInventoryNames();

			executor.execute(() ->
			{
				SetupsView view = buildView(ownedGear, levels, inventoryNames);
				SwingUtilities.invokeLater(() -> render(view));
			});
		});
	}

	/**
	 * Names for every item a saved setup carries in its inventory.
	 * <p>
	 * Must run on the client thread. An inventory holds things that are not equipment — logs, seeds,
	 * runes — so their names cannot come from the resolved gear list and have to be looked up here,
	 * before the rest of the view is built off-thread.
	 */
	private Map<Integer, String> resolveInventoryNames()
	{
		Map<Integer, String> names = new HashMap<>();

		for (Setup setup : setupStore.all())
		{
			for (ItemRequirement requirement : setup.getInventory())
			{
				if (requirement != null)
				{
					names.computeIfAbsent(requirement.getItemId(),
						id -> itemManager.getItemComposition(id).getName());
				}
			}
		}

		return names;
	}

	/**
	 * Everything the panel needs. Pure computation, so it is safe off the client thread.
	 */
	private SetupsView buildView(
		List<GearItem> ownedGear, PlayerLevels levels, Map<Integer, String> inventoryNames)
	{
		SetupsView view = new SetupsView();

		bankModel.ownedItems().forEach((id, owned) -> view.quantities.put(id, owned.getQuantity()));

		// Before the early return: a setup's inventory should still read as item names when there is no
		// bank data to resolve gear from.
		view.names.putAll(inventoryNames);

		if (ownedGear.isEmpty())
		{
			return view;
		}

		view.worn.putAll(wornGear(ownedGear));

		for (GearItem item : ownedGear)
		{
			view.names.put(item.getItemId(), item.getName());
		}

		// The shared builder, so an upgrade suggestion is measured the same way the BiS tab measures a
		// setup. This was assembled by hand here — slash, Ice Barrage, no target — so every fix to the
		// model since has been missing from the upgrade nudges entirely.
		CombatContext template = scoring.contextFor(
			CombatStyle.SLASH, levels, null, Scoring.noPrayers(), Scoring.noPotions());

		for (Setup setup : setupStore.all())
		{
			view.inventoryStatuses.put(setup.getId(),
				SetupValidator.validateInventory(setup, view.quantities, canonicalizer::variantGroup));

			Map<EquipmentSlot, UpgradeSuggestion> bySlot = new EnumMap<>(EquipmentSlot.class);
			for (UpgradeSuggestion suggestion : upgradeFinder.find(setup, ownedGear, template))
			{
				bySlot.putIfAbsent(suggestion.getSlot(), suggestion);
				view.names.put(suggestion.getReplacement().getItemId(), suggestion.getReplacement().getName());
			}

			view.upgrades.put(setup.getId(), bySlot);
		}

		return view;
	}

	private Map<EquipmentSlot, GearItem> wornGear()
	{
		return wornGear(statEngine.resolveOwnedGear(bankModel));
	}

	private static Map<EquipmentSlot, GearItem> wornGear(List<GearItem> ownedGear)
	{
		Map<EquipmentSlot, GearItem> worn = new EnumMap<>(EquipmentSlot.class);

		for (GearItem item : ownedGear)
		{
			if (!item.getLocations().contains(Storage.EQUIPMENT))
			{
				continue;
			}

			EquipmentSlot slot = EquipmentSlot.fromSlotIndex(item.getStats().getSlot());
			if (slot != null)
			{
				worn.put(slot, item);
			}
		}

		return worn;
	}

	private void render(SetupsView view)
	{
		list.removeAll();

		// Rendered here rather than in the fixed controls: the controls are built once, so a plugin
		// toggled after GearForge started would never have been noticed.
		if (bankFilterService.isBankTagLayoutsEnabled())
		{
			list.add(Cards.warning("Turn off the Bank Tag Layouts plugin. It overrides GearForge's "
				+ "bank arrangement, so your filtered bank won't be laid out as an equipment doll."));
			list.add(Cards.gap(8));
		}

		if (setupStore.isEmpty())
		{
			list.add(Cards.muted("No setups yet. Save what you're wearing, or build one on the BiS tab "
				+ "and save it from there."));
			finish();
			return;
		}

		for (Setup setup : new ArrayList<>(setupStore.all()))
		{
			list.add(setupCard(setup, view));
			list.add(Cards.gap(6));
		}

		finish();
	}

	private JPanel setupCard(Setup setup, SetupsView view)
	{
		Map<EquipmentSlot, SetupValidator.Status> statuses =
			SetupValidator.validate(setup, view.quantities, canonicalizer::variantGroup);

		Map<Integer, SetupValidator.Status> inventoryStatuses = view.inventoryStatuses
			.getOrDefault(setup.getId(), Collections.emptyMap());

		// Both halves have to be counted: the total includes inventory, so counting only equipment
		// reported a healthy setup as "6 of 20 — missing".
		int satisfied = SetupValidator.satisfiedCount(statuses)
			+ SetupValidator.countSatisfied(inventoryStatuses.values());

		boolean active = bankFilterService.isShowing(setup);
		boolean open = expanded.contains(setup.getId());

		Map<EquipmentSlot, UpgradeSuggestion> upgrades =
			view.upgrades.getOrDefault(setup.getId(), new EnumMap<>(EquipmentSlot.class));

		JPanel inner = Cards.card();
		inner.add(Cards.title(setup.getName()));
		inner.add(Cards.gap(4));

		// Only mention what is actually missing. A running tally like "6 of 20" reads as an alarm even
		// when nothing is wrong.
		int missing = setup.size() - satisfied;
		if (missing <= 0)
		{
			inner.add(Cards.muted("Everything's in your bank"));
		}
		else
		{
			JLabel shortfall = new JLabel("<html><body style='width:140px'>Missing " + missing + ": "
				+ Cards.escape(describeMissing(setup, statuses, inventoryStatuses, view)) + "</body></html>");
			shortfall.setFont(FontManager.getRunescapeSmallFont());
			shortfall.setForeground(ColorScheme.BRAND_ORANGE);
			shortfall.setAlignmentX(Component.LEFT_ALIGNMENT);
			inner.add(shortfall);
		}

		if (!upgrades.isEmpty() && !open)
		{
			inner.add(Cards.gap(4));
			JLabel hint = new JLabel("<html><body style='width:130px'>"
				+ upgrades.size() + (upgrades.size() == 1 ? " upgrade" : " upgrades")
				+ " available — open to apply</body></html>");
			hint.setFont(FontManager.getRunescapeSmallFont());
			hint.setForeground(ColorScheme.BRAND_ORANGE);
			hint.setAlignmentX(Component.LEFT_ALIGNMENT);
			inner.add(hint);
		}

		if (open)
		{
			inner.add(Cards.gap(8));
			inner.add(Cards.sectionLabel("Slots"));

			// Every slot, including empty ones — an empty slot is something you may want to fill.
			for (EquipmentSlot slot : EquipmentSlot.values())
			{
				inner.add(slotRow(setup, slot, setup.getEquipment().get(slot),
					upgrades.get(slot), view.worn.get(slot), view));
				inner.add(Cards.gap(2));
			}

			inner.add(Cards.gap(8));
			inner.add(Cards.sectionLabel("Inventory (" + setup.inventoryCount() + ")"));
			inner.add(Cards.gap(2));
			inner.add(inventoryGrid(setup, view));

			inner.add(Cards.gap(6));

			JButton updateAll = Cards.button("Update to what I'm wearing");
			updateAll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
			updateAll.setToolTipText("Replace this setup's gear and inventory with what you have now");
			updateAll.addActionListener(event -> updateFromWorn(setup));
			inner.add(updateAll);

			inner.add(Cards.gap(3));

			JButton updateInventory = Cards.button("Update inventory only");
			updateInventory.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
			updateInventory.setToolTipText("Replace just the inventory with what you're carrying");
			updateInventory.addActionListener(event -> updateInventoryFromCarried(setup));
			inner.add(updateInventory);

			inner.add(Cards.gap(3));

			JButton share = Cards.button("Copy share code");
			share.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
			share.setToolTipText("Copy a code someone else can paste to rebuild this setup");
			share.addActionListener(event -> copyShareCode(setup));
			inner.add(share);
		}

		inner.add(Cards.gap(6));
		inner.add(buttons(setup, active, open));

		JPanel card = Cards.accentCard(active ? ColorScheme.BRAND_ORANGE : ColorScheme.DARK_GRAY_COLOR);
		card.add(inner, BorderLayout.CENTER);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	/**
	 * One slot: what the setup asks for, plus the two ways to change it — accept the upgrade found in
	 * your bank, or take whatever you have on right now.
	 */
	private JPanel slotRow(
		Setup setup,
		EquipmentSlot slot,
		@Nullable ItemRequirement requirement,
		@Nullable UpgradeSuggestion upgrade,
		@Nullable GearItem worn,
		SetupsView view)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(28, 28));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		if (requirement != null)
		{
			itemManager.getImage(requirement.getItemId()).addTo(icon);
		}
		row.add(icon, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(row.getBackground());

		String itemName = requirement == null
			? "(empty)"
			: view.names.getOrDefault(requirement.getItemId(), "Item " + requirement.getItemId());

		JLabel name = new JLabel(itemName);
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		name.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(name);

		JLabel detail = new JLabel(upgrade == null
			? slot.getDisplayName()
			: String.format("%s · +%.1f%%", slot.getDisplayName(), upgrade.getGain() * 100.0));
		detail.setFont(FontManager.getRunescapeSmallFont());
		detail.setForeground(upgrade == null ? ColorScheme.MEDIUM_GRAY_COLOR : ColorScheme.BRAND_ORANGE);
		detail.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(detail);

		row.add(text, BorderLayout.CENTER);
		row.add(slotButtons(setup, slot, upgrade, worn), BorderLayout.EAST);
		return row;
	}

	private JPanel slotButtons(
		Setup setup, EquipmentSlot slot, @Nullable UpgradeSuggestion upgrade, @Nullable GearItem worn)
	{
		JPanel buttons = new JPanel(new GridLayout(1, 0, 2, 0));
		buttons.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		if (upgrade != null)
		{
			JButton apply = Cards.button("▲");
			apply.setToolTipText("Use " + upgrade.getReplacement().getName()
				+ String.format(" instead (+%.1f%% DPS)", upgrade.getGain() * 100.0));
			apply.addActionListener(event ->
			{
				setup.put(slot, upgrade.getReplacement().getItemId());
				setupStore.persist();
				rebuild();
			});
			buttons.add(apply);
		}

		if (worn != null)
		{
			JButton takeWorn = Cards.button("⤓");
			takeWorn.setToolTipText("Set this slot to what you're wearing: " + worn.getName());
			takeWorn.addActionListener(event ->
			{
				setup.put(slot, worn.getItemId());
				setupStore.persist();
				rebuild();
			});
			buttons.add(takeWorn);
		}

		return buttons;
	}

	/**
	 * The inventory as a 4x7 grid of icons, mirroring the in-game layout so it reads at a glance
	 * rather than as 28 rows of text.
	 */
	private JPanel inventoryGrid(Setup setup, SetupsView view)
	{
		JPanel grid = new JPanel(new GridLayout(INVENTORY_ROWS, INVENTORY_COLUMNS, 1, 1));
		grid.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		grid.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);

		List<ItemRequirement> inventory = setup.getInventory();
		Map<Integer, SetupValidator.Status> statuses = view.inventoryStatuses
			.getOrDefault(setup.getId(), new LinkedHashMap<>());

		for (int slot = 0; slot < INVENTORY_ROWS * INVENTORY_COLUMNS; slot++)
		{
			ItemRequirement requirement = slot < inventory.size() ? inventory.get(slot) : null;

			JLabel cell = new JLabel();
			cell.setHorizontalAlignment(SwingConstants.CENTER);
			cell.setPreferredSize(new Dimension(26, 26));
			cell.setOpaque(true);
			cell.setBackground(ColorScheme.DARK_GRAY_COLOR);

			if (requirement != null)
			{
				itemManager.getImage(requirement.getItemId()).addTo(cell);

				String name = view.names.getOrDefault(requirement.getItemId(),
					"Item " + requirement.getItemId());

				SetupValidator.Status status = statuses.get(slot);
				cell.setToolTipText(status == SetupValidator.Status.MISSING
					? name + " — missing"
					: name);

				if (status == SetupValidator.Status.MISSING)
				{
					cell.setBorder(BorderFactory.createLineBorder(ColorScheme.PROGRESS_ERROR_COLOR));
				}
			}

			grid.add(cell);
		}

		grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
		return grid;
	}

	private void updateInventoryFromCarried(Setup setup)
	{
		clientThread.invoke(() ->
		{
			int[] carried = bankModel.getInventoryOrder();

			SwingUtilities.invokeLater(() ->
			{
				setup.setInventoryFrom(carried);
				setupStore.persist();
				rebuild();
			});
		});
	}

	private void updateFromWorn(Setup setup)
	{
		clientThread.invoke(() ->
		{
			Map<EquipmentSlot, GearItem> worn = wornGear();
			int[] carried = bankModel.getInventoryOrder();

			SwingUtilities.invokeLater(() ->
			{
				if (worn.isEmpty())
				{
					JOptionPane.showMessageDialog(this,
						"Couldn't read your worn equipment. Log in and make sure you're wearing something, "
							+ "then try again.",
						"GearForge", JOptionPane.INFORMATION_MESSAGE);
					return;
				}

				setup.getEquipment().clear();
				worn.forEach((slot, item) -> setup.put(slot, item.getItemId()));
				setup.setInventoryFrom(carried);
				setupStore.persist();
				rebuild();
			});
		});
	}

	private JPanel buttons(Setup setup, boolean active, boolean open)
	{
		JPanel row = new JPanel(new GridLayout(1, 3, 3, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

		JButton edit = Cards.button(open ? "Close" : "Edit");
		edit.setToolTipText("Show each slot, with upgrades you can apply");
		edit.addActionListener(event ->
		{
			if (!expanded.remove(setup.getId()))
			{
				expanded.add(setup.getId());
			}

			rebuild();
		});
		row.add(edit);

		JButton activate = Cards.button(active ? "Clear" : "Show");
		activate.setToolTipText(active
			? "Stop filtering the bank"
			: "Filter your bank to just this setup's items");
		activate.addActionListener(event ->
		{
			if (active)
			{
				setupStore.deactivate();
				bankFilterService.clearFilter();
			}
			else
			{
				setupStore.activate(setup);
				bankFilterService.applySetup(setup);
			}

			rebuild();
		});
		row.add(activate);

		JButton delete = Cards.button("Delete");
		delete.addActionListener(event ->
		{
			int choice = JOptionPane.showConfirmDialog(this,
				"Delete \"" + setup.getName() + "\"?", "GearForge", JOptionPane.YES_NO_OPTION);

			if (choice == JOptionPane.YES_OPTION)
			{
				setupStore.remove(setup.getId());
				expanded.remove(setup.getId());
				rebuild();
			}
		});
		row.add(delete);

		return row;
	}

	/**
	 * Names what is missing across both gear and inventory. Inventory items are named rather than
	 * numbered, because "slot 14" means nothing to anyone.
	 */
	private static String describeMissing(
		Setup setup,
		Map<EquipmentSlot, SetupValidator.Status> equipment,
		Map<Integer, SetupValidator.Status> inventory,
		SetupsView view)
	{
		List<String> missing = new ArrayList<>();

		for (Map.Entry<EquipmentSlot, SetupValidator.Status> entry : equipment.entrySet())
		{
			if (entry.getValue() == SetupValidator.Status.MISSING)
			{
				missing.add(entry.getKey().getDisplayName().toLowerCase());
			}
		}

		for (Map.Entry<Integer, SetupValidator.Status> entry : inventory.entrySet())
		{
			if (entry.getValue() != SetupValidator.Status.MISSING)
			{
				continue;
			}

			ItemRequirement requirement = setup.getInventory().get(entry.getKey());
			String name = view.names.get(requirement.getItemId());

			// The same item in several slots only needs saying once.
			if (name != null && !missing.contains(name))
			{
				missing.add(name);
			}
		}

		return String.join(", ", missing);
	}

	private static String describeLastUsed(Setup setup)
	{
		if (setup.getLastUsedMillis() <= 0)
		{
			return "";
		}

		long days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - setup.getLastUsedMillis());
		if (days < 1)
		{
			return " · used today";
		}

		return " · used " + days + (days == 1 ? " day ago" : " days ago");
	}

	private void finish()
	{
		list.revalidate();
		list.repaint();
	}

	/**
	 * A snapshot of everything the panel renders from, so the client thread is touched once per rebuild.
	 */
	private static final class SetupsView
	{
		private final Map<Integer, Integer> quantities = new HashMap<>();
		private final Map<Integer, String> names = new HashMap<>();
		private final Map<EquipmentSlot, GearItem> worn = new EnumMap<>(EquipmentSlot.class);
		private final Map<String, Map<EquipmentSlot, UpgradeSuggestion>> upgrades = new HashMap<>();
		private final Map<String, Map<Integer, SetupValidator.Status>> inventoryStatuses = new HashMap<>();
	}
}
