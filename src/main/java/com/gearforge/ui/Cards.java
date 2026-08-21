package com.gearforge.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Shared building blocks so the two tabs look like one plugin rather than two.
 */
final class Cards
{
	/** Left accent strip width on a card, in pixels. */
	private static final int ACCENT = 3;

	/** Red rather than the brand orange, so a warning does not read as ordinary highlighting. */
	private static final Color WARNING_COLOR = new Color(220, 90, 70);

	/**
	 * Secondary text. RuneLite's MEDIUM_GRAY_COLOR on the panel background is grey-on-grey and was
	 * reported as hard to read; this keeps the hierarchy while staying legible.
	 */
	private static final Color MUTED_TEXT = new Color(170, 170, 170);

	/**
	 * Rows sit inside a scroll pane, and a trailing value would otherwise be clipped by the scrollbar.
	 */
	static final int SCROLLBAR_ALLOWANCE = 12;

	/** Padding inside a card, per side. */
	static final int CARD_PADDING = 8;

	/** The panel's own border, per side, set in GearForgePanel. */
	private static final int PANEL_BORDER = 8;

	/**
	 * How wide anything in the panel may actually be.
	 * <p>
	 * RuneLite gives a plugin panel {@link PluginPanel#PANEL_WIDTH} pixels. The panel's own border
	 * takes eight from each side, and once a tab is long enough to scroll — which every one of them
	 * is — the scrollbar takes more off the right. Everything else here is derived from this rather
	 * than guessed at, because guessing is what produced a panel that kept not fitting.
	 */
	static final int CONTENT_WIDTH =
		PluginPanel.PANEL_WIDTH - 2 * PANEL_BORDER - SCROLLBAR_ALLOWANCE;

	/**
	 * How wide wrapped text may be. Card padding on both sides, the accent strip a card may carry, and
	 * two pixels of slack for a border this arithmetic has not accounted for.
	 */
	static final int TEXT_WIDTH = CONTENT_WIDTH - 2 * CARD_PADDING - ACCENT - 2;

	/** The icon on the left of a row, and the gap after it. */
	static final int ROW_ICON_WIDTH = 36;

	/** Horizontal padding inside a row, per side. */
	static final int ROW_PADDING = 6;

	/** The gap a row's BorderLayout puts between its icon, its text and its value. */
	static final int ROW_GAP = 6;

	/** Reserved for the figure at the right of a row, wide enough for any of them. */
	static final int ROW_VALUE_WIDTH = 44;

	/**
	 * How wide text may be inside a row that also carries an icon on one side and a value on the
	 * other. What is left after everything fixed has taken its share.
	 */
	static final int IN_ROW_TEXT_WIDTH = CONTENT_WIDTH - 2 * ROW_PADDING - ROW_ICON_WIDTH
		- 2 * ROW_GAP - ROW_VALUE_WIDTH;

	private Cards()
	{
	}

	/**
	 * How many real pixels Swing gives one CSS pixel.
	 * <p>
	 * Swing's HTML renderer does not treat {@code width:140px} as 140 pixels — it scales it, by about a
	 * third on a standard display. Every wrapped label in this panel was therefore a third wider than
	 * the number written next to it, which is why the widths kept looking right in the source and
	 * wrong on screen. Measured once rather than assumed, since the factor follows the display.
	 */
	private static final double CSS_PIXEL = measureCssPixel();

	private static double measureCssPixel()
	{
		JLabel probe = new JLabel("<html><body style='width:100px'>x</body></html>");
		probe.setFont(FontManager.getRunescapeSmallFont());

		int measured = probe.getPreferredSize().width;
		return measured > 0 ? measured / 100.0 : 1.0;
	}

	/**
	 * Text that wraps within a given number of real pixels.
	 */
	private static String wrapped(String text, int pixels)
	{
		int css = Math.max(1, (int) Math.floor(pixels / CSS_PIXEL));
		return "<html><body style='width:" + css + "px'>" + escape(text) + "</body></html>";
	}

	/**
	 * Wrapped text at the panel's text width, for callers that need their own font or colour on it.
	 */
	static String wrappedText(String text)
	{
		return wrapped(text, TEXT_WIDTH);
	}

	/**
	 * Stops a component insisting on more width than the panel has.
	 * <p>
	 * A BoxLayout hands each child the container's width, but never less than the child's own minimum
	 * — and a minimum comes from the text inside, so one long item name lays its row out wider than
	 * the panel and everything to the right of it goes off the edge. The scroll pane has no horizontal
	 * bar, so there is no way to reach it. Every row, dropdown and button strip is clamped through
	 * here, which is what makes "it does not fit" a thing that cannot happen rather than a thing to
	 * keep noticing.
	 */
	static <T extends JComponent> T fitToPanel(T component)
	{
		component.setMinimumSize(new Dimension(0, component.getMinimumSize().height));
		return component;
	}

	/**
	 * The same, for a component whose height should also be pinned to what it needs — a row in a
	 * vertical list, which BoxLayout would otherwise stretch to fill the viewport.
	 */
	static <T extends JComponent> T fitRow(T row)
	{
		int height = row.getPreferredSize().height;
		row.setMinimumSize(new Dimension(0, height));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		return row;
	}

	/**
	 * A padded block with a subtle darker background — the unit the panel is composed from.
	 */
	static JPanel card()
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		return card;
	}

	/**
	 * A card with a coloured strip down the left, used for the headline result.
	 */
	static JPanel accentCard(Color accent)
	{
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		wrapper.setBorder(BorderFactory.createMatteBorder(0, ACCENT, 0, 0, accent));
		wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
		return wrapper;
	}

	/**
	 * Section label — small, uppercase, muted. Used to break the panel into scannable groups.
	 */
	static JLabel sectionLabel(String text)
	{
		JLabel label = new JLabel(text.toUpperCase());
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(MUTED_TEXT);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
		// Clipped rather than allowed to widen the panel: a label's minimum is the width of its text,
		// and a BoxLayout honours that minimum even when the container is narrower.
		return fitToPanel(label);
	}

	/**
	 * The one number that matters, rendered large.
	 */
	static JLabel headline(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 16f));
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return fitToPanel(label);
	}

	/**
	 * A setup or section title — larger than body text so a list of setups scans by name.
	 */
	static JLabel title(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 13f));
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return fitToPanel(label);
	}

	/**
	 * A prominent warning block — a coloured strip and bold text, for things the player needs to act
	 * on rather than merely notice.
	 */
	static JPanel warning(String text)
	{
		JPanel inner = new JPanel();
		inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
		inner.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		inner.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JLabel label = new JLabel(wrapped(text, TEXT_WIDTH));
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(WARNING_COLOR);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		inner.add(label);

		JPanel card = new JPanel(new BorderLayout());
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createMatteBorder(0, ACCENT, 0, 0, WARNING_COLOR));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(inner, BorderLayout.CENTER);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	/**
	 * The secondary text colour, for callers that need it on a component this class does not build.
	 */
	static Color mutedColor()
	{
		return MUTED_TEXT;
	}

	static JLabel body(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return fitToPanel(label);
	}

	/**
	 * Body text sized to sit inside a row that already has an icon on one side and a value on the
	 * other.
	 */
	static JLabel bodyInRow(String text)
	{
		JLabel label = new JLabel(wrapped(text, IN_ROW_TEXT_WIDTH));
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	/**
	 * Muted text sized to sit inside a row that already has an icon on one side and a value on the
	 * other. The full-width version forces the row wider than the panel, which pushes the value off the
	 * edge entirely.
	 */
	static JLabel mutedInRow(String text)
	{
		JLabel label = new JLabel(wrapped(text, IN_ROW_TEXT_WIDTH));
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(MUTED_TEXT);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	static JLabel muted(String text)
	{
		JLabel label = new JLabel(wrapped(text, TEXT_WIDTH));
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(MUTED_TEXT);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	/**
	 * A labelled form field stacked vertically, which reads better than side-by-side at this width.
	 */
	static JPanel field(String label, JComponent input)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel caption = sectionLabel(label);
		panel.add(caption);

		input.setAlignmentX(Component.LEFT_ALIGNMENT);
		input.setMaximumSize(new Dimension(Integer.MAX_VALUE, input.getPreferredSize().height));
		panel.add(input);

		return panel;
	}

	/**
	 * A vertical spacer that is left-aligned like everything else.
	 * <p>
	 * {@link Box#createVerticalStrut} defaults to centre alignment, and a Y_AXIS BoxLayout containing
	 * a mix of alignments indents its children instead of filling the panel — which is what made the
	 * whole sidebar look squashed toward one side.
	 */
	static Component gap(int height)
	{
		Component strut = Box.createVerticalStrut(height);
		((JComponent) strut).setAlignmentX(Component.LEFT_ALIGNMENT);
		return strut;
	}

	/**
	 * A compact button that matches the sidebar rather than the platform look-and-feel.
	 */
	static JButton button(String text)
	{
		JButton button = new JButton(text);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		// Tight horizontal padding: at 225px, three buttons in a row have barely 60px each, and
		// generous padding is what truncated "Off task" into "Off...".
		button.setBorder(BorderFactory.createEmptyBorder(4, 3, 4, 3));
		button.setFocusPainted(false);
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		return fitToPanel(button);
	}

	/**
	 * A titled section that expands when clicked, like the panels in the wiki's DPS calculator.
	 * <p>
	 * Used instead of a dropdown where the options benefit from being seen at once — prayers as a grid
	 * of icons, potions as a scrollable list — rather than hidden one-at-a-time behind a combo box.
	 *
	 * @param content shown when expanded; starts hidden
	 * @param decorate given the header button, so callers can hang an icon on it — the sprite and item
	 *                 image loaders are asynchronous, so the icon cannot simply be passed in
	 */
	static JPanel expandable(String title, JComponent content, Consumer<JButton> decorate)
	{
		JPanel section = new JPanel();
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
		section.setBackground(ColorScheme.DARK_GRAY_COLOR);
		section.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton header = button(title + "   +");
		header.setHorizontalAlignment(SwingConstants.LEFT);
		header.setIconTextGap(8);
		// Tall enough that a skill icon sits in the row rather than being clipped by it.
		header.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
		header.setPreferredSize(new Dimension(0, 30));
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		header.setMinimumSize(new Dimension(0, 30));
		decorate.accept(header);

		content.setVisible(false);
		content.setAlignmentX(Component.LEFT_ALIGNMENT);

		header.addActionListener(event ->
		{
			boolean opening = !content.isVisible();
			content.setVisible(opening);
			header.setText(title + (opening ? "   −" : "   +"));
			section.revalidate();
			section.repaint();
		});

		section.add(header);
		section.add(content);
		return section;
	}

	/**
	 * A dropdown styled to match the sidebar. The default Swing combo renders light-on-light here and
	 * stands out badly against RuneLite's dark panels.
	 */
	static <T> JComboBox<T> comboBox(T[] items)
	{
		JComboBox<T> combo = new JComboBox<>(items);
		combo.setFont(FontManager.getRunescapeSmallFont());
		combo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		combo.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		combo.setFocusable(false);
		combo.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
		combo.setRenderer(new DarkListRenderer());
		combo.setAlignmentX(Component.LEFT_ALIGNMENT);

		// A combo sizes itself to its longest entry. With a thousand monsters in the list that is far
		// wider than the panel, and the box was laid out past the edge of it.
		int height = combo.getPreferredSize().height;
		combo.setPreferredSize(new Dimension(CONTENT_WIDTH, height));
		combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		combo.setMinimumSize(new Dimension(0, height));
		return combo;
	}

	/**
	 * Two or more mutually exclusive options as side-by-side buttons — clearer than a dropdown when
	 * there are only a couple of choices, and it shows the alternative without a click.
	 */
	static JPanel segmented(String[] labels, int selectedIndex, IntConsumer onSelect)
	{
		JPanel row = new JPanel(new GridLayout(1, labels.length, 4, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		row.setMinimumSize(new Dimension(0, 26));

		ButtonGroup group = new ButtonGroup();

		for (int i = 0; i < labels.length; i++)
		{
			final int index = i;
			JToggleButton option = new JToggleButton(labels[i]);
			option.setFont(FontManager.getRunescapeSmallFont());
			option.setFocusPainted(false);
			option.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
			option.setSelected(i == selectedIndex);
			// A grid divides the width evenly, but only down to the widest label's minimum, so two long
			// options together were enough to push the strip past the panel.
			fitToPanel(option);
			paintToggle(option);

			option.addItemListener(event ->
			{
				paintToggle(option);
				if (option.isSelected())
				{
					onSelect.accept(index);
				}
			});

			group.add(option);
			row.add(option);
		}

		return row;
	}

	private static void paintToggle(JToggleButton option)
	{
		option.setBackground(option.isSelected()
			? ColorScheme.BRAND_ORANGE
			: ColorScheme.DARKER_GRAY_COLOR);
		option.setForeground(option.isSelected()
			? ColorScheme.DARKER_GRAY_COLOR
			: ColorScheme.LIGHT_GRAY_COLOR);
	}

	/**
	 * Keeps the dropdown popup dark instead of the default white list.
	 */
	private static final class DarkListRenderer extends DefaultListCellRenderer
	{
		@Override
		public Component getListCellRendererComponent(
			JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
		{
			super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

			setFont(FontManager.getRunescapeSmallFont());
			setBackground(isSelected ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR);
			setForeground(isSelected ? ColorScheme.DARKER_GRAY_COLOR : ColorScheme.LIGHT_GRAY_COLOR);
			setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
			return this;
		}
	}

	/**
	 * Item names come from the game and can contain characters that would break the HTML wrapper.
	 */
	static String escape(String text)
	{
		return text
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;");
	}
}
