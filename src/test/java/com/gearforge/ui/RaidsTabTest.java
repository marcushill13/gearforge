package com.gearforge.ui;

import java.awt.Component;
import javax.swing.JPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * A fourth tab has vanished from this panel once already, by being given a cell narrower than its
 * label. Four tabs need two rows at 225 pixels, and the arithmetic is checked rather than eyeballed.
 */
public class RaidsTabTest
{
	private static final String[] TABS = {"Setups", "Search", "BiS", RaidsTab.TITLE};

	@Test
	public void everyTabLabelFitsItsCell()
	{
		MaterialTabGroup group = new MaterialTabGroup(new JPanel());

		// Two columns, with the grid's two-pixel gap between them.
		int cell = (Cards.CONTENT_WIDTH - 2) / 2;

		for (String label : TABS)
		{
			MaterialTab tab = new MaterialTab(label, group, new JPanel());
			assertTrue(label + " needs " + tab.getPreferredSize().width + "px of " + cell,
				tab.getPreferredSize().width <= cell);
		}
	}

	@Test
	public void theTabSaysItIsNotFinished()
	{
		assertTrue("The label has to carry the caveat, not just the panel behind it",
			RaidsTab.TITLE.toLowerCase().contains("tbd"));
	}

	@Test
	public void theTabItselfFitsThePanel()
	{
		RaidsTab tab = new RaidsTab();

		for (Component child : tab.getComponents())
		{
			assertTrue("A line of the raids tab is " + child.getMinimumSize().width + "px wide",
				child.getMinimumSize().width <= Cards.CONTENT_WIDTH);
		}
	}
}
