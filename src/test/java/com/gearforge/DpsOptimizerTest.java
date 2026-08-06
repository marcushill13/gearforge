package com.gearforge;

import com.gearforge.data.EquipmentSlot;
import com.gearforge.data.EquipmentStats;
import com.gearforge.data.GearItem;
import com.gearforge.data.ItemCategories;
import com.gearforge.data.Storage;
import com.google.gson.Gson;
import com.gearforge.dps.CombatContext;
import com.gearforge.dps.CombatStyle;
import com.gearforge.dps.DpsEngine;
import com.gearforge.dps.SetEffectRegistry;
import com.gearforge.dps.Target;
import com.gearforge.optimizer.Constraints;
import com.gearforge.optimizer.DpsOptimizer;
import com.gearforge.optimizer.ScoredSetup;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DpsOptimizerTest
{
	private final DpsOptimizer optimizer =
		new DpsOptimizer(new DpsEngine(), new SetEffectRegistry(), new ItemCategories(new Gson()));

	private static CombatContext melee()
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

	@Test
	public void picksTheHigherDpsWeapon()
	{
		List<GearItem> owned = Arrays.asList(
			weapon(1, "Bronze sword", 5, 5, 4, false),
			weapon(2, "Abyssal whip", 82, 82, 4, false));

		List<ScoredSetup> results = optimizer.best(owned, melee(), false, 3);

		assertFalse(results.isEmpty());
		assertEquals("Abyssal whip", results.get(0).getSetup().get(EquipmentSlot.WEAPON).getName());
	}

	@Test
	public void neverPairsATwoHanderWithAShield()
	{
		List<GearItem> owned = Arrays.asList(
			weapon(1, "Big two-hander", 120, 120, 5, true),
			weapon(2, "Small one-hander", 10, 10, 4, false),
			armour(3, "Strong shield", EquipmentSlot.SHIELD, 40, 20));

		for (ScoredSetup result : optimizer.best(owned, melee(), false, 3))
		{
			assertTrue(Constraints.isValid(result.getSetup()));
		}
	}

	@Test
	public void findsVoidEvenThoughVoidPiecesHaveWeakRawStats()
	{
		// This is the reason the candidate pool includes set effect items regardless of their stats.
		// Void's own bonuses are nil, so a pure top-N-by-stat filter would prune the whole set and
		// the 1.1x multiplier would never be found.
		List<GearItem> owned = new ArrayList<>(SetEffectRegistryTest.fullVoidMelee());
		owned.add(weapon(1, "Abyssal whip", 82, 82, 4, false));
		owned.add(armour(50, "Slightly better helm", EquipmentSlot.HEAD, 1, 1));
		owned.add(armour(51, "Slightly better body", EquipmentSlot.BODY, 1, 1));
		owned.add(armour(52, "Slightly better legs", EquipmentSlot.LEGS, 1, 1));
		owned.add(armour(53, "Slightly better gloves", EquipmentSlot.GLOVES, 1, 1));

		ScoredSetup best = optimizer.best(owned, melee(), false, 1).get(0);

		assertEquals(ItemID.GAME_PEST_MELEE_HELM, best.getSetup().get(EquipmentSlot.HEAD).getItemId());
		assertEquals(ItemID.PEST_VOID_KNIGHT_TOP, best.getSetup().get(EquipmentSlot.BODY).getItemId());
		assertTrue(best.getNotes().stream().anyMatch(note -> note.contains("Void melee")));
	}

	@Test
	public void resultsAreOrderedByDpsDescending()
	{
		List<GearItem> owned = Arrays.asList(
			weapon(1, "Bronze sword", 5, 5, 4, false),
			weapon(2, "Rune scimitar", 45, 44, 4, false),
			weapon(3, "Abyssal whip", 82, 82, 4, false));

		List<ScoredSetup> results = optimizer.best(owned, melee(), false, 3);

		assertEquals(3, results.size());
		for (int i = 1; i < results.size(); i++)
		{
			assertTrue(results.get(i - 1).getScore().getDps() >= results.get(i).getScore().getDps());
		}
	}

	@Test
	public void returnsNothingWhenNoWeaponIsOwned()
	{
		List<GearItem> noWeapons = Collections.singletonList(
			armour(1, "Rune platebody", EquipmentSlot.BODY, 0, 0));

		assertTrue(optimizer.best(noWeapons, melee(), false, 3).isEmpty());
	}

	@Test
	public void junkWeaponsWithNoAttackSpeedAreNeverChosen()
	{
		// A monkey greegree really is speed -1 in the item data, and 29 novelty items are speed 0.
		// Coerced to a one-tick weapon they score as overwhelmingly best-in-slot, which is exactly what
		// happened in the wild: "Ninja monkey greegree, +218% DPS".
		GearItem greegree = weapon(1, "Ninja monkey greegree", 0, 0, -1, false);
		GearItem crate = weapon(2, "Crate of fish", 0, 0, 0, false);
		GearItem whip = weapon(3, "Abyssal whip", 82, 82, 4, false);

		List<ScoredSetup> results = optimizer.best(Arrays.asList(greegree, crate, whip), melee(), false, 3);

		assertFalse(results.isEmpty());
		assertEquals("Abyssal whip", results.get(0).getSetup().get(EquipmentSlot.WEAPON).getName());

		for (ScoredSetup result : results)
		{
			String chosen = result.getSetup().get(EquipmentSlot.WEAPON).getName();
			assertTrue("junk weapon chosen: " + chosen, chosen.equals("Abyssal whip"));
		}
	}

	@Test
	public void aWeaponWithNoSpeedScoresZeroRatherThanInfiniteDps()
	{
		DpsEngine engine = new DpsEngine();
		double dps = engine.score(CombatContext.builder()
			.attackLevel(99).strengthLevel(99)
			.style(CombatStyle.SLASH)
			.equipment(EquipmentStats.builder().aslash(50).strength(50).slot(3).build())
			.weaponSpeedTicks(0)
			.target(Target.dummy())
			.build()).getDps();

		assertEquals(0.0, dps, 1e-9);
	}

	@Test
	public void emptyBankDegradesGracefully()
	{
		assertTrue(optimizer.best(Collections.emptyList(), melee(), false, 3).isEmpty());
	}

	@Test
	public void leavesASlotEmptyRatherThanWearingSomethingHarmful()
	{
		List<GearItem> owned = Arrays.asList(
			weapon(1, "Abyssal whip", 82, 82, 4, false),
			armour(2, "Cursed helm", EquipmentSlot.HEAD, -50, -50));

		ScoredSetup best = optimizer.best(owned, melee(), false, 1).get(0);
		assertNull(best.getSetup().get(EquipmentSlot.HEAD));
	}

	@Test
	public void slayerHelmOnlyChangesTheAnswerWhenOnTask()
	{
		List<GearItem> owned = Arrays.asList(
			weapon(1, "Abyssal whip", 82, 82, 4, false),
			SetEffectRegistryTest.piece(ItemID.SLAYER_HELM_I, EquipmentSlot.HEAD));

		double offTask = optimizer.best(owned, melee(), false, 1).get(0).getScore().getDps();
		double onTask = optimizer.best(owned, melee(), true, 1).get(0).getScore().getDps();

		assertTrue(onTask > offTask);
	}

	@Test
	public void producesAScoreBreakdownForDisplay()
	{
		List<GearItem> owned = Collections.singletonList(weapon(1, "Abyssal whip", 82, 82, 4, false));

		ScoredSetup best = optimizer.best(owned, melee(), false, 1).get(0);

		assertNotNull(best.getScore());
		assertTrue(best.getScore().getDps() > 0);
		assertTrue(best.getScore().getMaxHit() > 0);
		assertTrue(best.getScore().getHitChance() > 0 && best.getScore().getHitChance() <= 1);
		assertEquals(4, best.getScore().getAttackSpeedTicks());
	}

	@SuppressWarnings("SameParameterValue")
	private static GearItem weapon(int id, String name, int slash, int strength, int speed, boolean twoHanded)
	{
		EquipmentStats stats = EquipmentStats.builder()
			.aslash(slash)
			.strength(strength)
			.slot(EquipmentSlot.WEAPON.getSlotIndex())
			.twoHanded(twoHanded)
			.speed(speed)
			.build();

		return new GearItem(id, name, 1, stats, EnumSet.of(Storage.BANK));
	}

	private static GearItem armour(int id, String name, EquipmentSlot slot, int slash, int strength)
	{
		EquipmentStats stats = EquipmentStats.builder()
			.aslash(slash)
			.strength(strength)
			.slot(slot.getSlotIndex())
			.build();

		return new GearItem(id, name, 1, stats, EnumSet.of(Storage.BANK));
	}
}
