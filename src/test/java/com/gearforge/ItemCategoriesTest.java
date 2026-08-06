package com.gearforge;

import com.gearforge.data.ItemCategories;
import com.google.gson.Gson;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Guards the ammo pairing rules, including the two cases where the upstream category is misleading.
 */
public class ItemCategoriesTest
{
	private final ItemCategories categories = new ItemCategories(new Gson());

	@Test
	public void bowsTakeArrowsAndNotBolts()
	{
		assertTrue(categories.ammoFits(ItemID.MAGIC_SHORTBOW, ItemID.RUNE_ARROW));
		assertFalse(categories.ammoFits(ItemID.MAGIC_SHORTBOW, ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE));
	}

	@Test
	public void crossbowsTakeBoltsAndNotArrows()
	{
		assertTrue(categories.ammoFits(ItemID.XBOWS_CROSSBOW_RUNITE, ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE));
		assertFalse(categories.ammoFits(ItemID.XBOWS_CROSSBOW_RUNITE, ItemID.RUNE_ARROW));
	}

	@Test
	public void ballistasTakeJavelinsDespiteBeingCategorisedAsCrossbows()
	{
		// The upstream data calls a ballista a crossbow. A rule written off that field alone would
		// reject the only ammunition it can actually fire.
		assertEquals(ItemCategories.BALLISTA, categories.categoryOf(ItemID.HEAVY_BALLISTA));
		assertTrue(categories.ammoFits(ItemID.HEAVY_BALLISTA, ItemID.DRAGON_JAVELIN));
		assertFalse(categories.ammoFits(ItemID.HEAVY_BALLISTA, ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE));
	}

	@Test
	public void weaponsThatNeedAmmoAreIdentified()
	{
		assertTrue(categories.requiresAmmo(ItemID.MAGIC_SHORTBOW));
		assertTrue(categories.requiresAmmo(ItemID.XBOWS_CROSSBOW_RUNITE));
		assertTrue(categories.requiresAmmo(ItemID.HEAVY_BALLISTA));

		// Melee weapons and thrown weapons draw nothing from the ammo slot.
		assertFalse(categories.requiresAmmo(ItemID.ABYSSAL_WHIP));
	}

	@Test
	public void unclassifiedWeaponsAcceptAnythingRatherThanBlockingValidSetups()
	{
		// A whip is unclassified; constraining its ammo slot would reject legitimate setups such as
		// wearing an assembler for its bonuses.
		assertTrue(categories.ammoFits(ItemID.ABYSSAL_WHIP, ItemID.RUNE_ARROW));
		assertTrue(categories.ammoFits(ItemID.ABYSSAL_WHIP, ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE));
	}
}
