package com.gearforge.ui;

import com.gearforge.data.GearItem;
import com.gearforge.data.GearStat;
import com.gearforge.data.Storage;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.StringJoiner;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * One row in the ranked list: icon, name, where it is, and how it compares to what you have on.
 */
class ItemRow extends JPanel
{
	private static final Dimension ICON_SIZE = new Dimension(32, 32);

	/**
	 * @param wornValue the value of this stat on the item currently in that slot, or null if the slot
	 *                  is empty or the player is not logged in
	 */
	ItemRow(GearItem item, GearStat stat, ItemManager itemManager, @Nullable Double wornValue)
	{
		boolean isWorn = item.getLocations().contains(Storage.EQUIPMENT);

		setLayout(new BorderLayout(8, 0));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		// Extra room on the right so the value is not clipped by the scroll pane's scrollbar.
		setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6 + Cards.SCROLLBAR_ALLOWANCE));
		setAlignmentX(LEFT_ALIGNMENT);
		// Without a ceiling, BoxLayout stretches the last row to fill the viewport.
		setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

		JLabel icon = new JLabel();
		icon.setPreferredSize(ICON_SIZE);
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		itemManager.getImage(item.getItemId()).addTo(icon);
		add(icon, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(getBackground());

		JLabel name = new JLabel(item.getName());
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(isWorn ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
		name.setAlignmentX(LEFT_ALIGNMENT);
		text.add(name);

		text.add(buildSubtitle(item, stat, wornValue, isWorn));
		add(text, BorderLayout.CENTER);

		JLabel value = new JLabel(stat.format(item.statValue(stat)));
		value.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD));
		value.setForeground(ColorScheme.BRAND_ORANGE);
		value.setHorizontalAlignment(SwingConstants.RIGHT);
		add(value, BorderLayout.EAST);

		setToolTipText(item.getName() + " — " + stat.getDisplayName() + " " + stat.format(item.statValue(stat)));
	}

	/**
	 * "Bank" plus, when something is worn in this slot, how much better or worse this item is.
	 */
	private JPanel buildSubtitle(GearItem item, GearStat stat, @Nullable Double wornValue, boolean isWorn)
	{
		JPanel subtitle = new JPanel();
		subtitle.setLayout(new BoxLayout(subtitle, BoxLayout.X_AXIS));
		subtitle.setBackground(getBackground());
		subtitle.setAlignmentX(LEFT_ALIGNMENT);

		JLabel where = new JLabel(isWorn ? "Worn" : describeLocations(item));
		where.setFont(FontManager.getRunescapeSmallFont());
		where.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		subtitle.add(where);

		if (!isWorn && wornValue != null)
		{
			double delta = item.statValue(stat) - wornValue;
			if (delta != 0)
			{
				subtitle.add(Box.createHorizontalStrut(4));

				JLabel comparison = new JLabel(stat.format(delta) + " vs worn");
				comparison.setFont(FontManager.getRunescapeSmallFont());
				comparison.setForeground(delta > 0
					? ColorScheme.PROGRESS_COMPLETE_COLOR
					: ColorScheme.PROGRESS_ERROR_COLOR);
				subtitle.add(comparison);
			}
		}

		subtitle.add(Box.createHorizontalGlue());
		return subtitle;
	}

	private static String describeLocations(GearItem item)
	{
		StringJoiner joiner = new StringJoiner(", ");
		for (Storage storage : item.getLocations())
		{
			joiner.add(storage.toString());
		}

		String places = joiner.toString();
		return item.getQuantity() > 1
			? places + " (" + item.getQuantity() + ")"
			: places;
	}
}
