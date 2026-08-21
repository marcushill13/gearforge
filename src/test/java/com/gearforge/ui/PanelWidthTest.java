package com.gearforge.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.PluginPanel;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * The panel is 225 pixels wide and nothing may exceed it.
 * <p>
 * This kept going wrong for one reason: a BoxLayout gives each child the container's width, but never
 * less than that child's own <em>minimum</em>, and a minimum is computed from the text inside. One
 * long monster name or item name therefore lays its row out wider than the panel, and since the
 * scroll pane has no horizontal bar, whatever is on the right simply cannot be reached. Padding never
 * fixed it, which is why it kept coming back.
 * <p>
 * Rather than checking a screenshot again, the widths are asserted here.
 */
public class PanelWidthTest
{
	/** Nothing a monster or item is called is longer than this. */
	private static final String LONG_NAME =
		"Dagannoth Rex (Deep Wilderness, superior, on task, imbued)";

	@Test
	public void theWidthBudgetAddsUp()
	{
		assertTrue("The content width has to fit the panel RuneLite gives us",
			Cards.CONTENT_WIDTH <= PluginPanel.PANEL_WIDTH);

		// A row is an icon, some text and a value, and the three of them plus the padding are the width.
		int row = 2 * Cards.ROW_PADDING + Cards.ROW_ICON_WIDTH + 2 * Cards.ROW_GAP
			+ Cards.IN_ROW_TEXT_WIDTH + Cards.ROW_VALUE_WIDTH;

		assertTrue("A row of " + row + " does not fit " + Cards.CONTENT_WIDTH,
			row <= Cards.CONTENT_WIDTH);

		// Wrapped text sits inside a card, which has padding of its own on both sides.
		assertTrue("Wrapped text must fit inside a card",
			Cards.TEXT_WIDTH + 2 * Cards.CARD_PADDING <= Cards.CONTENT_WIDTH);
	}

	@Test
	public void noBuildingBlockDemandsMoreWidthThanThePanelHas()
	{
		assertFits("muted", Cards.muted(LONG_NAME + " " + LONG_NAME));
		assertFits("body in row", Cards.bodyInRow(LONG_NAME));
		assertFits("muted in row", Cards.mutedInRow(LONG_NAME));
		assertFits("warning", Cards.warning(LONG_NAME + " " + LONG_NAME));
		assertFits("button", Cards.button("A button with a very long label indeed"));
		assertFits("section label", Cards.sectionLabel(LONG_NAME));
		assertFits("body", Cards.body(LONG_NAME));
		assertFits("title", Cards.title(LONG_NAME));
		assertFits("headline", Cards.headline(LONG_NAME));
		assertFits("segmented", Cards.segmented(
			new String[]{"A long first option", "A long second option"}, 0, index -> { }));
	}

	/**
	 * The dropdown sizes itself to its longest entry, and the monster list has a thousand of them.
	 */
	@Test
	public void aDropdownOfLongNamesStillFits()
	{
		assertFits("combo box", Cards.comboBox(new String[]{"Short", LONG_NAME, LONG_NAME + LONG_NAME}));
	}

	/**
	 * The layout itself, not just the declared sizes: laid out at the panel's width, the value on the
	 * right of a row has to still be on screen.
	 */
	@Test
	public void aRowWithALongNameKeepsItsValueOnScreen()
	{
		JPanel row = new JPanel(new BorderLayout(Cards.ROW_GAP, 0));

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(Cards.ROW_ICON_WIDTH, 32));
		row.add(icon, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.add(new JLabel(LONG_NAME));
		text.add(Cards.mutedInRow(LONG_NAME));
		Cards.fitToPanel(text);
		row.add(text, BorderLayout.CENTER);

		JLabel value = new JLabel("+13.6");
		value.setPreferredSize(new Dimension(Cards.ROW_VALUE_WIDTH, 16));
		row.add(value, BorderLayout.EAST);

		Cards.fitRow(row);
		assertFits("row", row);

		row.setSize(Cards.CONTENT_WIDTH, row.getPreferredSize().height);
		row.doLayout();

		for (Component child : row.getComponents())
		{
			assertTrue("A child ran to " + (child.getX() + child.getWidth())
					+ ", past the panel's " + Cards.CONTENT_WIDTH,
				child.getX() + child.getWidth() <= Cards.CONTENT_WIDTH);
		}

		assertTrue("The value must keep its full width",
			value.getWidth() >= Cards.ROW_VALUE_WIDTH);
	}

	private static void assertFits(String what, javax.swing.JComponent component)
	{
		assertTrue(what + " has a minimum width of " + component.getMinimumSize().width
				+ ", more than the panel's " + Cards.CONTENT_WIDTH,
			component.getMinimumSize().width <= Cards.CONTENT_WIDTH);
	}
}
