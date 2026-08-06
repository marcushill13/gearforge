package com.gearforge.ui;

import com.gearforge.data.BankModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/**
 * The GearForge sidebar. Two surfaces so far — Slots and BiS; Setups and Bosses join them here.
 */
@Singleton
public class GearForgePanel extends PluginPanel
{
	private final BankModel bankModel;
	private final SetupsTab setupsTab;
	private final SlotsTab slotsTab;
	private final BisTab bisTab;
	private final BossesTab bossesTab;
	private final JLabel staleness = new JLabel();

	@Inject
	private GearForgePanel(
		BankModel bankModel,
		SetupsTab setupsTab,
		SlotsTab slotsTab,
		BisTab bisTab,
		BossesTab bossesTab)
	{
		super(false);
		this.bankModel = bankModel;
		this.setupsTab = setupsTab;
		this.slotsTab = slotsTab;
		this.bisTab = bisTab;
		this.bossesTab = bossesTab;

		// Saving or filtering from another tab has to show up in the Setups tab straight away.
		bisTab.setOnSetupSaved(setupsTab::rebuild);
		bossesTab.setOnSetupSaved(setupsTab::rebuild);

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JPanel display = new JPanel(new BorderLayout());
		display.setBackground(ColorScheme.DARK_GRAY_COLOR);

		MaterialTabGroup tabGroup = new MaterialTabGroup(display);
		// Two rows of two rather than one row of four. MaterialTabGroup defaults to FlowLayout, which
		// reports a one-row preferred size and then wraps the fourth tab onto a row with no height,
		// making it vanish. A single row of four does fit, but MaterialTab's own padding leaves only
		// ~30px for text and the labels ellipsize — and its padding cannot be reduced, because
		// select()/unselect() overwrite the border to draw the selected underline. Two rows give each
		// tab roughly double the width, which is enough for the full words.
		tabGroup.setLayout(new GridLayout(2, 2, 2, 2));
		tabGroup.setAlignmentX(LEFT_ALIGNMENT);
		tabGroup.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));

		MaterialTab setups = new MaterialTab("Setups", tabGroup, setupsTab);
		MaterialTab slots = new MaterialTab("Search", tabGroup, slotsTab);
		MaterialTab bis = new MaterialTab("BiS", tabGroup, bisTab);
		MaterialTab bosses = new MaterialTab("Bosses", tabGroup, bossesTab);
		tabGroup.addTab(setups);
		tabGroup.addTab(slots);
		tabGroup.addTab(bis);
		tabGroup.addTab(bosses);
		tabGroup.select(setups);

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);
		top.add(buildHeader());
		top.add(tabGroup);
		// Breathing room between the tab row and whatever the tab puts at its top.
		top.add(Box.createVerticalStrut(10));

		add(top, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);
	}

	private JPanel buildHeader()
	{
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

		JLabel title = new JLabel("GearForge");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ColorScheme.BRAND_ORANGE);
		title.setAlignmentX(LEFT_ALIGNMENT);
		header.add(title);

		staleness.setFont(FontManager.getRunescapeSmallFont());
		staleness.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		staleness.setAlignmentX(LEFT_ALIGNMENT);
		header.add(staleness);

		return header;
	}

	/**
	 * Rebuilds everything from the current gear data.
	 */
	public void refresh()
	{
		SwingUtilities.invokeLater(this::updateStaleness);
		setupsTab.rebuild();
		slotsTab.rebuild();
		bisTab.rebuild();
		bossesTab.rebuild();
	}

	private void updateStaleness()
	{
		staleness.setText(bankModel.hasBankData()
			? "Bank data from " + describeAge(System.currentTimeMillis() - bankModel.getBankCapturedAt())
			: "No bank data yet");
	}

	/**
	 * Plain-language age, e.g. "2 hours ago". Never dresses stale data up as current.
	 */
	private static String describeAge(long millis)
	{
		long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
		if (minutes < 1)
		{
			return "just now";
		}

		if (minutes < 60)
		{
			return plural(minutes, "minute") + " ago";
		}

		long hours = TimeUnit.MILLISECONDS.toHours(millis);
		if (hours < 24)
		{
			return plural(hours, "hour") + " ago";
		}

		return plural(TimeUnit.MILLISECONDS.toDays(millis), "day") + " ago";
	}

	private static String plural(long count, String unit)
	{
		return count + " " + (count == 1 ? unit : unit + "s");
	}
}
