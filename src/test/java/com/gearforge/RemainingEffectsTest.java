package com.gearforge;

import com.gearforge.data.EquipmentSlot;
import com.gearforge.data.EquipmentStats;
import com.gearforge.data.GearItem;
import com.gearforge.data.Storage;
import com.gearforge.dps.CombatContext;
import com.gearforge.dps.CombatStyle;
import com.gearforge.dps.DpsEngine;
import com.gearforge.dps.MonsterAttribute;
import com.gearforge.dps.PoweredStaff;
import com.gearforge.dps.SetEffectRegistry;
import com.gearforge.dps.SetEffects;
import com.gearforge.dps.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The tail of the gear model: the staves that hit for a fixed amount, the salamanders that scale like
 * a strength bonus, and the three items that change how the accuracy roll is made rather than what
 * goes into it.
 */
public class RemainingEffectsTest
{
	private static final double TOLERANCE = 1e-9;

	private final SetEffectRegistry registry = new SetEffectRegistry();
	private final DpsEngine engine = new DpsEngine();

	@Test
	public void theCrystalStavesHitForAFixedAmount()
	{
		// Entirely independent of the Magic level, which is what makes them unusual.
		assertEquals(23, PoweredStaff.CRYSTAL_STAFF_BASIC.maxHit(99));
		assertEquals(23, PoweredStaff.CRYSTAL_STAFF_BASIC.maxHit(1));
		assertEquals(31, PoweredStaff.CRYSTAL_STAFF_ATTUNED.maxHit(75));
		assertEquals(39, PoweredStaff.CRYSTAL_STAFF_PERFECTED.maxHit(99));
	}

	@Test
	public void theCorruptedStavesMatchTheirCrystalCounterparts()
	{
		assertEquals(PoweredStaff.CRYSTAL_STAFF_BASIC, PoweredStaff.forItem(23852));
		assertEquals(PoweredStaff.CRYSTAL_STAFF_ATTUNED, PoweredStaff.forItem(23853));
		assertEquals(PoweredStaff.CRYSTAL_STAFF_PERFECTED, PoweredStaff.forItem(23854));
	}

	@Test
	public void salamandersScaleLikeAStrengthBonus()
	{
		// Tecu is the strongest breath, black next, then red, orange and the swamp lizard.
		assertTrue(PoweredStaff.TECU_SALAMANDER.maxHit(99) > PoweredStaff.BLACK_SALAMANDER.maxHit(99));
		assertTrue(PoweredStaff.BLACK_SALAMANDER.maxHit(99) > PoweredStaff.RED_SALAMANDER.maxHit(99));
		assertTrue(PoweredStaff.RED_SALAMANDER.maxHit(99) > PoweredStaff.ORANGE_SALAMANDER.maxHit(99));
		// The orange and the swamp lizard both land on 19 at 99 Magic; their bonuses are close enough
		// that the truncation swallows the difference.
		assertTrue(PoweredStaff.ORANGE_SALAMANDER.maxHit(99) >= PoweredStaff.SWAMP_LIZARD.maxHit(99));

		// The same formula the melee maximum uses, with the breath's bonus in place of gear.
		assertEquals((99 * (104 + 64) + 320) / 640, PoweredStaff.TECU_SALAMANDER.maxHit(99));
	}

	@Test
	public void theRemainingSceptresAreRecognised()
	{
		assertEquals(PoweredStaff.THAMMARONS_SCEPTRE, PoweredStaff.forItem(22555));
		assertEquals(PoweredStaff.ACCURSED_SCEPTRE, PoweredStaff.forItem(27665));
		assertEquals(PoweredStaff.EYE_OF_AYAK, PoweredStaff.forItem(31113));
		assertEquals(PoweredStaff.STARTER_STAFF, PoweredStaff.forItem(22335));
		assertNotNull(PoweredStaff.forItem(28796));
	}

	/**
	 * A brimstone ring lowers the target's magic defence a quarter of the time. That is not an accuracy
	 * multiplier — it is a different roll — so it had to reach the engine rather than the registry.
	 */
	@Test
	public void theBrimstoneRingRaisesMagicAccuracy()
	{
		double without = engine.score(magicContext(false, false)).getHitChance();
		double with = engine.score(magicContext(true, false)).getHitChance();

		assertTrue("The ring should land more often", with > without);
	}

	/**
	 * Osmumten's fang rerolls a miss. The result is not one minus the chance of missing twice, because
	 * both rolls share a defence roll.
	 */
	@Test
	public void aRerolledMissLandsMoreOftenButNotIndependently()
	{
		double single = engine.score(magicContext(false, false)).getHitChance();
		double rerolled = engine.score(magicContext(false, true)).getHitChance();

		assertTrue(rerolled > single);
		assertTrue("Two shared rolls beat one, but not as much as two independent ones",
			rerolled < 1 - (1 - single) * (1 - single) + TOLERANCE);
	}

	@Test
	public void theTomeOfWaterRaisesTheAssumedBarrage()
	{
		SetEffects effects = evaluate(CombatStyle.MAGIC, 25574);

		assertEquals(1.10, effects.getDamageMultiplier(), TOLERANCE);
	}

	@Test
	public void theColossalBladeGrowsWithTheTarget()
	{
		SetEffects small = evaluateAgainstSize(CombatStyle.SLASH, 1, ItemID.GIANTS_FOUNDRY_COLOSSAL_BLADE);
		SetEffects large = evaluateAgainstSize(CombatStyle.SLASH, 5, ItemID.GIANTS_FOUNDRY_COLOSSAL_BLADE);

		assertEquals(2, small.getFlatMaxHit());
		assertEquals(10, large.getFlatMaxHit());
	}

	private SetEffects evaluate(CombatStyle style, int... itemIds)
	{
		return evaluateAgainstSize(style, 1, itemIds);
	}

	private SetEffects evaluateAgainstSize(CombatStyle style, int size, int... itemIds)
	{
		List<GearItem> worn = new ArrayList<>();
		for (int id : itemIds)
		{
			worn.add(new GearItem(id, "Item " + id, 1,
				EquipmentStats.builder().build(), EnumSet.of(Storage.BANK)));
		}

		Target target = Target.builder()
			.name("Target")
			.defenceLevel(100)
			.magicLevel(100)
			.size(size)
			.defensiveBonuses(EquipmentStats.builder().build())
			.attributes(EnumSet.noneOf(MonsterAttribute.class))
			.build();

		return registry.evaluate(worn, style, target, false);
	}

	private static CombatContext magicContext(boolean brimstone, boolean reroll)
	{
		return CombatContext.builder()
			.magicLevel(99)
			.style(CombatStyle.MAGIC)
			.equipment(EquipmentStats.builder().amagic(60).build())
			.target(Target.builder()
				.name("Target")
				.defenceLevel(150)
				.magicLevel(150)
				.defensiveBonuses(EquipmentStats.builder().dmagic(80).build())
				.build())
			.baseSpellDamage(30)
			.weaponSpeedTicks(5)
			.brimstoneRing(brimstone)
			.rerollsMisses(reroll)
			.build();
	}
}
