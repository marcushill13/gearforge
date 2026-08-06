package com.gearforge;

import com.gearforge.data.EquipmentSlot;
import com.gearforge.data.EquipmentStats;
import com.gearforge.data.GearItem;
import com.gearforge.data.GearStat;
import com.gearforge.data.Storage;
import com.gearforge.optimizer.Constraints;
import com.gearforge.optimizer.GreedyOptimizer;
import com.gearforge.optimizer.OptimizerResult;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GreedyOptimizerTest
{
	private final GreedyOptimizer optimizer = new GreedyOptimizer();

	@Test
	public void picksTheBestItemInEachSlot()
	{
		List<GearItem> owned = Arrays.asList(
			defensive(1, "Iron full helm", EquipmentSlot.HEAD, 10),
			defensive(2, "Rune full helm", EquipmentSlot.HEAD, 30),
			defensive(3, "Rune platebody", EquipmentSlot.BODY, 70));

		OptimizerResult result = optimizer.best(owned, GearStat.STAB_DEFENCE);

		assertEquals("Rune full helm", result.getSetup().get(EquipmentSlot.HEAD).getName());
		assertEquals("Rune platebody", result.getSetup().get(EquipmentSlot.BODY).getName());
		assertEquals(100.0, result.getTotal(), 1e-9);
	}

	@Test
	public void takesTheTwoHanderWhenItBeatsAOneHanderPlusShield()
	{
		List<GearItem> owned = Arrays.asList(
			weapon(1, "Weak one-hander", 5, false),
			weapon(2, "Huge two-hander", 60, true),
			defensive(3, "Small shield", EquipmentSlot.SHIELD, 20));

		OptimizerResult result = optimizer.best(owned, GearStat.STAB_DEFENCE);

		// 60 beats 5 + 20.
		assertEquals("Huge two-hander", result.getSetup().get(EquipmentSlot.WEAPON).getName());
		assertNull(result.getSetup().get(EquipmentSlot.SHIELD));
		assertEquals(60.0, result.getTotal(), 1e-9);
		assertTrue(Constraints.isValid(result.getSetup()));
	}

	@Test
	public void keepsTheShieldWhenTheOneHandedPairWins()
	{
		List<GearItem> owned = Arrays.asList(
			weapon(1, "Decent one-hander", 25, false),
			weapon(2, "Modest two-hander", 30, true),
			defensive(3, "Big shield", EquipmentSlot.SHIELD, 50));

		OptimizerResult result = optimizer.best(owned, GearStat.STAB_DEFENCE);

		// 25 + 50 beats 30. A naive "best weapon first" pass would have taken the two-hander.
		assertEquals("Decent one-hander", result.getSetup().get(EquipmentSlot.WEAPON).getName());
		assertEquals("Big shield", result.getSetup().get(EquipmentSlot.SHIELD).getName());
		assertEquals(75.0, result.getTotal(), 1e-9);
		assertTrue(Constraints.isValid(result.getSetup()));
	}

	@Test
	public void explainsWhyTheShieldSlotWasKeptOrGivenUp()
	{
		OptimizerResult keptShield = optimizer.best(Arrays.asList(
			weapon(1, "Decent one-hander", 25, false),
			weapon(2, "Modest two-hander", 30, true),
			defensive(3, "Big shield", EquipmentSlot.SHIELD, 50)), GearStat.STAB_DEFENCE);

		assertFalse(keptShield.getReasons().isEmpty());
		assertTrue(keptShield.getReasons().get(0).contains("Big shield"));

		OptimizerResult tookTwoHander = optimizer.best(Arrays.asList(
			weapon(1, "Weak one-hander", 5, false),
			weapon(2, "Huge two-hander", 60, true),
			defensive(3, "Small shield", EquipmentSlot.SHIELD, 20)), GearStat.STAB_DEFENCE);

		assertFalse(tookTwoHander.getReasons().isEmpty());
		assertTrue(tookTwoHander.getReasons().get(0).contains("Huge two-hander"));
	}

	@Test
	public void leavesASlotEmptyRatherThanWearingANegativeItem()
	{
		List<GearItem> owned = Arrays.asList(
			defensive(1, "Cursed helm", EquipmentSlot.HEAD, -20),
			defensive(2, "Useless helm", EquipmentSlot.HEAD, 0),
			defensive(3, "Rune platebody", EquipmentSlot.BODY, 70));

		OptimizerResult result = optimizer.best(owned, GearStat.STAB_DEFENCE);

		assertNull(result.getSetup().get(EquipmentSlot.HEAD));
		assertEquals(70.0, result.getTotal(), 1e-9);
	}

	@Test
	public void neverProducesATwoHanderAndShieldTogether()
	{
		// A two-hander good enough to win, with a shield also present.
		List<GearItem> owned = Arrays.asList(
			weapon(1, "Huge two-hander", 100, true),
			defensive(2, "Big shield", EquipmentSlot.SHIELD, 50));

		OptimizerResult result = optimizer.best(owned, GearStat.STAB_DEFENCE);
		assertTrue(Constraints.isValid(result.getSetup()));
	}

	@Test
	public void sparseBankDegradesGracefullyInsteadOfFailing()
	{
		OptimizerResult empty = optimizer.best(Collections.emptyList(), GearStat.MAGIC_DEFENCE);
		assertTrue(empty.isEmpty());
		assertEquals(0.0, empty.getTotal(), 1e-9);

		OptimizerResult oneItem = optimizer.best(
			Collections.singletonList(defensive(1, "Leather boots", EquipmentSlot.BOOTS, 2)),
			GearStat.STAB_DEFENCE);
		assertEquals(1, oneItem.getSetup().size());

		// An item with no bonus in the requested stat is not worth a slot.
		OptimizerResult wrongStat = optimizer.best(
			Collections.singletonList(defensive(1, "Leather boots", EquipmentSlot.BOOTS, 2)),
			GearStat.MAGIC_DEFENCE);
		assertTrue(wrongStat.isEmpty());
	}

	@Test
	public void totalStatsMatchesTheChosenSetup()
	{
		List<GearItem> owned = Arrays.asList(
			defensive(1, "Rune full helm", EquipmentSlot.HEAD, 30),
			defensive(2, "Rune platebody", EquipmentSlot.BODY, 70));

		OptimizerResult result = optimizer.best(owned, GearStat.STAB_DEFENCE);
		assertEquals(100, result.totalStats().getDstab());
	}

	@Test
	public void resultIsDeterministicForTiedItems()
	{
		List<GearItem> owned = Arrays.asList(
			defensive(3, "Zebra helm", EquipmentSlot.HEAD, 30),
			defensive(1, "Apple helm", EquipmentSlot.HEAD, 30));

		String first = optimizer.best(owned, GearStat.STAB_DEFENCE).getSetup().get(EquipmentSlot.HEAD).getName();

		Collections.reverse(owned = new java.util.ArrayList<>(owned));
		String second = optimizer.best(owned, GearStat.STAB_DEFENCE).getSetup().get(EquipmentSlot.HEAD).getName();

		assertEquals(first, second);
		assertEquals("Apple helm", first);
	}

	private static GearItem defensive(int id, String name, EquipmentSlot slot, int stabDefence)
	{
		EquipmentStats stats = EquipmentStats.builder()
			.dstab(stabDefence)
			.slot(slot.getSlotIndex())
			.build();

		return new GearItem(id, name, 1, stats, EnumSet.of(Storage.BANK));
	}

	private static GearItem weapon(int id, String name, int stabDefence, boolean twoHanded)
	{
		EquipmentStats stats = EquipmentStats.builder()
			.dstab(stabDefence)
			.slot(EquipmentSlot.WEAPON.getSlotIndex())
			.twoHanded(twoHanded)
			.speed(4)
			.build();

		return new GearItem(id, name, 1, stats, EnumSet.of(Storage.BANK));
	}
}
