package com.gearforge.bank;

import com.gearforge.GearForgeConfig;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Outlines the active setup's items in the bank, inventory and equipment.
 * <p>
 * Drawing only — this never withdraws, deposits or clicks. It shows you where the items are and you
 * move them yourself.
 * <p>
 * This runs for every visible item every frame, so it does no work beyond a set lookup: the ids to
 * highlight, variant families already expanded, are precomputed by {@link SetupStore} whenever the
 * active setup changes.
 */
@Singleton
public class BankHighlighter extends WidgetItemOverlay
{
	private static final Stroke OUTLINE = new BasicStroke(2f);

	private final BankFilterService bankFilterService;
	private final GearForgeConfig config;

	@Inject
	private BankHighlighter(BankFilterService bankFilterService, GearForgeConfig config)
	{
		this.bankFilterService = bankFilterService;
		this.config = config;

		showOnBank();
		showOnInventory();
		showOnEquipment();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem itemWidget)
	{
		if (!config.highlightBank())
		{
			return;
		}

		Set<Integer> highlighted = bankFilterService.shownIds();
		if (highlighted.isEmpty() || !highlighted.contains(itemId))
		{
			return;
		}

		Rectangle bounds = itemWidget.getCanvasBounds();
		if (bounds == null)
		{
			return;
		}

		graphics.setColor(config.highlightColour());
		graphics.setStroke(OUTLINE);
		graphics.draw(bounds);
	}
}
