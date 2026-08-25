package com.gearforge.ui;

import java.awt.Component;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/**
 * A placeholder for raid gear, which is not built yet.
 * <p>
 * It ships empty on purpose. A raid is not one fight scored against one monster, which is all the BiS
 * tab knows how to do — it is a sequence of rooms with different defences, different styles and a
 * fixed number of switches you can carry between them. Pretending the existing scoring answers that
 * would be worse than saying it does not.
 */
@Singleton
class RaidsTab extends JPanel
{
	/** What the tab is called. The label says outright that it is not finished. */
	static final String TITLE = "Raids (TBD)";

	RaidsTab()
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(4, 0, 4, Cards.SCROLLBAR_ALLOWANCE));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		add(Cards.sectionLabel("Not built yet"));
		add(Cards.muted("Chambers of Xeric, Theatre of Blood and Tombs of Amascut are what this tab is "
			+ "for. Nothing here works yet — it is listed so you can see it coming."));

		add(Cards.gap(10));
		add(Cards.sectionLabel("What it is meant to do"));
		add(planned("Gear per room, not per raid — Olm's hands and his head are not the same fight."));
		add(planned("Which switches are worth the inventory space they cost."));
		add(planned("The scoring the BiS tab already does, run against every room in order."));

		add(Cards.gap(10));
		add(Cards.muted("Suggestions are welcome before it is written rather than after — the Discord "
			+ "is in the plugin's hub listing."));
	}

	private static Component planned(String text)
	{
		return Cards.muted("· " + text);
	}
}
