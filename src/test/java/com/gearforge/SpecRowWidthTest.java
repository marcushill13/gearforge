package com.gearforge;

import com.gearforge.dps.SpecialAttack;
import java.awt.Canvas;
import java.awt.FontMetrics;
import net.runelite.client.ui.FontManager;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * The damage figure sits in a column of fixed width, so the width has to be checked rather than
 * assumed — a figure clipped to its first character reads as a broken number, which is exactly how
 * it was reported.
 */
public class SpecRowWidthTest
{
	private static final int VALUE_WIDTH = 44;
	private static final int TEXT_WIDTH = 92;

	private final FontMetrics bold = new Canvas().getFontMetrics(FontManager.getRunescapeBoldFont());
	private final FontMetrics small = new Canvas().getFontMetrics(FontManager.getRunescapeSmallFont());

	@Test
	public void everyDamageFigureFitsItsColumn()
	{
		for (double added : new double[]{0, 0.4, 1.4, 9.9, 13.6, 99.9, 100, 486.2})
		{
			String shown = added < 0.05 ? "\u2014"
				: added >= 100 ? String.format("+%.0f", added) : String.format("+%.1f", added);

			assertTrue(shown + " needs " + bold.stringWidth(shown) + "px of " + VALUE_WIDTH,
				bold.stringWidth(shown) <= VALUE_WIDTH);
		}
	}

	@Test
	public void everySpecDescriptionFitsTheTextColumn()
	{
		for (SpecialAttack special : SpecialAttack.values())
		{
			// Wrapping is fine; a single word wider than the column is not, since it cannot break.
			for (String word : special.describe().split(" "))
			{
				assertTrue(special.getDisplayName() + ": '" + word + "' is "
						+ small.stringWidth(word) + "px of " + TEXT_WIDTH,
					small.stringWidth(word) <= TEXT_WIDTH);
			}

			assertTrue(special.getDisplayName() + " name is " + bold.stringWidth(special.getDisplayName()),
				bold.stringWidth(special.getDisplayName()) > 0);
		}

		assertTrue(small.stringWidth("average hit 486.2") <= TEXT_WIDTH);
	}
}
