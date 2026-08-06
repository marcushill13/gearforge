package com.gearforge.ui;

import com.gearforge.GearForgeConfig;
import com.gearforge.data.BankModel;
import com.gearforge.data.EquipmentSlot;
import com.gearforge.data.GearItem;
import com.gearforge.data.GearStat;
import com.gearforge.data.ItemCanonicalizer;
import com.gearforge.data.ItemStatEngine;
import com.gearforge.data.SlotRanker;
import com.gearforge.data.Storage;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Pick a slot and a bonus, see everything you own for it, best first.
 */
@Singleton
class SlotsTab extends JPanel
{
	/**
	 * Long lists are slow to build in Swing and nobody scrolls past the top few anyway.
	 */
	private static final int MAX_ROWS = 100;

	private final ClientThread clientThread;
	private final BankModel bankModel;
	private final ItemStatEngine statEngine;
	private final ItemCanonicalizer canonicalizer;
	private final ItemManager itemManager;
	private final GearForgeConfig config;

	private final JComboBox<EquipmentSlot> slotPicker = Cards.comboBox(EquipmentSlot.values());
	private final JComboBox<GearStat> statPicker = Cards.comboBox(GearStat.values());
	private final JPanel results = new JPanel();
	private final JLabel status = new JLabel();

	@Inject
	private SlotsTab(
		ClientThread clientThread,
		BankModel bankModel,
		ItemStatEngine statEngine,
		ItemCanonicalizer canonicalizer,
		ItemManager itemManager,
		GearForgeConfig config)
	{
		this.clientThread = clientThread;
		this.bankModel = bankModel;
		this.statEngine = statEngine;
		this.canonicalizer = canonicalizer;
		this.itemManager = itemManager;
		this.config = config;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(buildPickers(), BorderLayout.NORTH);

		results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
		results.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// NORTH inside a plain panel stops BoxLayout rows stretching to fill the viewport.
		JPanel resultsHolder = new JPanel(new BorderLayout());
		resultsHolder.setBackground(ColorScheme.DARK_GRAY_COLOR);
		resultsHolder.add(results, BorderLayout.NORTH);

		JScrollPane scroll = new JScrollPane(
			resultsHolder,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
			JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(scroll, BorderLayout.CENTER);

		slotPicker.setSelectedItem(EquipmentSlot.WEAPON);
		statPicker.setSelectedItem(GearStat.SLASH_ATTACK);
		slotPicker.addActionListener(e -> rebuild());
		statPicker.addActionListener(e -> rebuild());
	}

	private JPanel buildPickers()
	{
		JPanel pickers = new JPanel();
		pickers.setLayout(new BoxLayout(pickers, BoxLayout.Y_AXIS));
		pickers.setBackground(ColorScheme.DARK_GRAY_COLOR);
		pickers.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

		pickers.add(Cards.field("Gear slot", slotPicker));
		pickers.add(Cards.gap(8));
		pickers.add(Cards.field("Rank by", statPicker));
		pickers.add(Cards.gap(8));

		status.setFont(FontManager.getRunescapeSmallFont());
		status.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		status.setAlignmentX(Component.LEFT_ALIGNMENT);
		pickers.add(status);

		return pickers;
	}

	/**
	 * Recomputes the ranking. Safe to call from either thread; the read happens on the client thread
	 * because item names come from the item cache, and the render happens on the EDT.
	 */
	void rebuild()
	{
		// Callers include the client thread (container events), so hop to the EDT before touching
		// any Swing state.
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(this::rebuild);
			return;
		}

		if (!bankModel.hasBankData())
		{
			showMessage("Open your bank once to get started.");
			return;
		}

		EquipmentSlot slot = (EquipmentSlot) slotPicker.getSelectedItem();
		GearStat stat = (GearStat) statPicker.getSelectedItem();
		if (slot == null || stat == null)
		{
			return;
		}

		boolean groupVariants = config.groupVariants();
		boolean hideZeroBonus = config.hideZeroBonus();

		clientThread.invoke(() ->
		{
			List<GearItem> owned = statEngine.resolveOwnedGear(bankModel);
			List<GearItem> ranked = SlotRanker.rank(
				owned, slot, stat, groupVariants, hideZeroBonus, canonicalizer::variantGroup);

			SlotView view = new SlotView(
				ranked,
				owned.isEmpty(),
				wornValue(owned, slot, stat),
				ranked.isEmpty()
					? SlotRanker.suggestStats(SlotRanker.positiveCounts(owned, slot), stat, 2)
					: Collections.emptyList());

			SwingUtilities.invokeLater(() -> render(view, slot, stat));
		});
	}

	/**
	 * The value of this stat on whatever is currently equipped in the slot, or null if the slot is
	 * empty. This is what every row is compared against.
	 */
	@Nullable
	private static Double wornValue(List<GearItem> owned, EquipmentSlot slot, GearStat stat)
	{
		for (GearItem item : owned)
		{
			if (item.getStats().getSlot() == slot.getSlotIndex()
				&& item.getLocations().contains(Storage.EQUIPMENT))
			{
				return item.statValue(stat);
			}
		}

		return null;
	}

	private void render(SlotView view, EquipmentSlot slot, GearStat stat)
	{
		results.removeAll();

		if (view.nothingResolved)
		{
			// Distinguish "stats have not downloaded yet" from "you own nothing for this slot" —
			// RuneLite fetches its equipment stat table after startup.
			showMessage("No equippable items found yet. Item stats may still be loading.");
			return;
		}

		if (view.ranked.isEmpty())
		{
			showMessage(describeEmptyResult(slot, stat, view.suggestions));
			return;
		}

		int shown = Math.min(view.ranked.size(), MAX_ROWS);
		for (int i = 0; i < shown; i++)
		{
			results.add(new ItemRow(view.ranked.get(i), stat, itemManager, view.wornValue));
			results.add(Box.createVerticalStrut(2));
		}

		List<GearItem> ranked = view.ranked;

		status.setText(shown < ranked.size()
			? "Top " + shown + " of " + ranked.size() + " items"
			: ranked.size() + (ranked.size() == 1 ? " item" : " items"));

		results.revalidate();
		results.repaint();
	}

	private void showMessage(String message)
	{
		results.removeAll();
		status.setText("");

		results.add(Cards.muted(message));

		results.revalidate();
		results.repaint();
	}

	/**
	 * An empty ranking is usually the wrong question rather than an empty bank — arrows carry ranged
	 * strength, not ranged attack, for instance. Point at what this slot actually has.
	 */
	private static String describeEmptyResult(EquipmentSlot slot, GearStat stat, List<GearStat> suggestions)
	{
		String message = "Nothing you own gives " + stat.getDisplayName().toLowerCase()
			+ " in the " + slot.getDisplayName().toLowerCase() + " slot.";

		if (suggestions.isEmpty())
		{
			return message;
		}

		StringJoiner joiner = new StringJoiner(" or ");
		for (GearStat suggestion : suggestions)
		{
			joiner.add(suggestion.getDisplayName().toLowerCase());
		}

		return message + " Try " + joiner + ".";
	}

	/**
	 * Everything one rebuild produced, so it can cross the thread boundary in one piece.
	 */
	private static final class SlotView
	{
		private final List<GearItem> ranked;
		private final boolean nothingResolved;
		@Nullable
		private final Double wornValue;
		private final List<GearStat> suggestions;

		private SlotView(List<GearItem> ranked, boolean nothingResolved, @Nullable Double wornValue,
			List<GearStat> suggestions)
		{
			this.ranked = ranked;
			this.nothingResolved = nothingResolved;
			this.wornValue = wornValue;
			this.suggestions = suggestions;
		}
	}
}
