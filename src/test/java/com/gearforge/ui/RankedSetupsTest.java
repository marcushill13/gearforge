package com.gearforge.ui;

import com.gearforge.data.EquipmentSlot;
import com.gearforge.data.EquipmentStats;
import com.gearforge.data.GearItem;
import com.gearforge.data.ItemCategories;
import com.gearforge.data.Storage;
import com.gearforge.dps.CombatContext;
import com.gearforge.dps.CombatStyle;
import com.gearforge.dps.DpsEngine;
import com.gearforge.dps.SetEffectRegistry;
import com.gearforge.dps.Target;
import com.gearforge.optimizer.DpsOptimizer;
import com.gearforge.optimizer.ScoredSetup;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * "The others" offered five setups that all scored 2.48 DPS and differed only in which worthless ring
 * had been dropped into an empty slot — an emerald ring, then a ring of dueling(1), then a ring of
 * dueling(5). That is one answer and four ties, and a runner-up that scores the same as the winner
 * tells the reader nothing they can act on.
 */
public class RankedSetupsTest
{
	private final DpsOptimizer optimizer =
		new DpsOptimizer(new DpsEngine(), new SetEffectRegistry(), new ItemCategories(new Gson()));

	@Test
	public void setupsThatScoreTheSameAreOfferedOnce()
	{
		List<GearItem> owned = new ArrayList<>();
		owned.add(weapon());

		// Five rings with no bonuses at all: whichever is worn, the setup scores identically.
		for (int i = 0; i < 5; i++)
		{
			owned.add(uselessRing(30000 + i));
		}

		List<ScoredSetup> best = optimizer.best(owned, context(), false, 5);
		assertTrue("The optimizer should offer the ties in the first place", best.size() > 1);

		List<ScoredSetup> distinct = BisTab.distinctlyScored(best);

		assertEquals("Ties are one answer, not five", 1, distinct.size());
		assertEquals(best.get(0).getScore().getDps(), distinct.get(0).getScore().getDps(), 1e-9);
	}

	/**
	 * A runner-up that genuinely scores less is still a runner-up, and still worth offering.
	 */
	@Test
	public void setupsThatScoreDifferentlyAreAllKept()
	{
		List<GearItem> owned = new ArrayList<>();
		owned.add(weapon());
		owned.add(ring(30100, 20));
		owned.add(ring(30101, 10));
		owned.add(ring(30102, 4));

		List<ScoredSetup> distinct = BisTab.distinctlyScored(optimizer.best(owned, context(), false, 5));

		assertTrue("Different scores are different answers", distinct.size() > 1);
	}

	private static CombatContext context()
	{
		return CombatContext.builder()
			.attackLevel(99)
			.strengthLevel(99)
			.style(CombatStyle.SLASH)
			.equipment(EquipmentStats.builder().build())
			.target(Target.dummy())
			.weaponSpeedTicks(4)
			.build();
	}

	private static GearItem weapon()
	{
		EquipmentStats stats = EquipmentStats.builder()
			.aslash(82).strength(82).speed(4)
			.slot(EquipmentSlot.WEAPON.getSlotIndex())
			.build();

		return new GearItem(4151, "Abyssal whip", 1, stats, EnumSet.of(Storage.BANK));
	}

	private static GearItem uselessRing(int id)
	{
		return ring(id, 0);
	}

	private static GearItem ring(int id, int strength)
	{
		EquipmentStats stats = EquipmentStats.builder()
			.strength(strength)
			.slot(EquipmentSlot.RING.getSlotIndex())
			.build();

		return new GearItem(id, "Ring " + id, 1, stats, EnumSet.of(Storage.BANK));
	}
}
