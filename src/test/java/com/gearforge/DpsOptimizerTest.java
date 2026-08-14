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
	@Test
	public void rangedNeverRecommendsAMeleeWeapon()
	{
		// The bug this pins: with no bow owned, a longsword was returned as the ranged best-in-slot.
		// It contributes no ranged attack and no ranged strength, so it scored badly rather than being
		// rejected — and badly still wins when it is the only candidate left.
		GearItem longsword = weapon(ItemID.ADAMANT_LONGSWORD, "Adamant longsword", 25, 24, 5, false);
		GearItem arrows = ammo(ItemID.ADAMANT_ARROW, "Adamant arrow", 31);

		CombatContext ranged = melee().toBuilder().style(CombatStyle.RANGED).build();

		assertTrue("a sword is not a ranged setup",
			optimizer.best(Arrays.asList(longsword, arrows), ranged, false, 3).isEmpty());
	}

	@Test
	public void rangedPicksTheBowWhenOneIsOwned()
	{
		GearItem longsword = weapon(ItemID.ADAMANT_LONGSWORD, "Adamant longsword", 25, 24, 5, false);
		GearItem shortbow = rangedWeapon(ItemID.MAGIC_SHORTBOW, "Magic shortbow", 69, 4);
		GearItem arrows = ammo(ItemID.ADAMANT_ARROW, "Adamant arrow", 31);

		CombatContext ranged = melee().toBuilder().style(CombatStyle.RANGED).build();
		List<ScoredSetup> best = optimizer.best(Arrays.asList(longsword, shortbow, arrows), ranged, false, 1);

		assertFalse(best.isEmpty());
		assertEquals(ItemID.MAGIC_SHORTBOW, best.get(0).getSetup().get(EquipmentSlot.WEAPON).getItemId());
	}

	@Test
	public void meleeNeverRecommendsABow()
	{
		GearItem shortbow = rangedWeapon(ItemID.MAGIC_SHORTBOW, "Magic shortbow", 69, 4);
		GearItem whip = weapon(ItemID.ABYSSAL_WHIP, "Abyssal whip", 82, 82, 4, false);

		ScoredSetup best = optimizer.best(Arrays.asList(shortbow, whip), melee(), false, 1).get(0);
		assertEquals(ItemID.ABYSSAL_WHIP, best.getSetup().get(EquipmentSlot.WEAPON).getItemId());
	}

	private static GearItem rangedWeapon(int id, String name, int rangedAttack, int speed)
	{
		EquipmentStats stats = EquipmentStats.builder()
			.arange(rangedAttack)
			.slot(EquipmentSlot.WEAPON.getSlotIndex())
			.twoHanded(true)
			.speed(speed)
			.build();

		return new GearItem(id, name, 1, stats, EnumSet.of(Storage.BANK));
	}

	private static GearItem ammo(int id, String name, int rangedStrength)
	{
		EquipmentStats stats = EquipmentStats.builder()
			.rangedStrength(rangedStrength)
			.slot(EquipmentSlot.AMMO.getSlotIndex())
			.build();

		return new GearItem(id, name, 1000, stats, EnumSet.of(Storage.BANK));
	}

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

	/**
	 * The reported bug: for stab and crush, oathplate chest and the fighter torso are offensively
	 * identical — both +0 attack in the style, both +4 strength — so DPS ties exactly. The tie used to
	 * fall through to the item name, and "Fighter torso" wins the alphabet. Defence decides it now.
	 */
	@Test
	public void aDpsTieGoesToTheBetterDefensivePiece()
	{
		List<GearItem> owned = Arrays.asList(
			weapon(1, "Abyssal whip", 82, 82, 4, false),
			// Real numbers: the torso is +0 attack everywhere, the chest is +16 slash and nothing else.
			// Under a crush context neither contributes accuracy, and both give +4 strength.
			defensiveBody(2, "Fighter torso", 4, 61, 60, 62),
			defensiveBody(3, "Oathplate chest", 4, 105, 128, 100));

		CombatContext crush = melee().toBuilder().style(CombatStyle.CRUSH).build();
		List<ScoredSetup> results = optimizer.best(owned, crush, false, 3);

		assertFalse(results.isEmpty());
		assertEquals("Oathplate chest",
			results.get(0).getSetup().get(EquipmentSlot.BODY).getName());
	}

	private static GearItem defensiveBody(int id, String name, int strength, int dstab, int dslash, int dcrush)
	{
		EquipmentStats stats = EquipmentStats.builder()
			.strength(strength)
			.dstab(dstab)
			.dslash(dslash)
			.dcrush(dcrush)
			.slot(EquipmentSlot.BODY.getSlotIndex())
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

	/**
	 * A thrown weapon is thrown from the hand. Bolts worn beside knives do nothing in game, but their
	 * ranged strength was being summed into the setup — which is how rune knives came to out-score a
	 * bow of faerdhinen in a real player's bank.
	 */
	@Test
	public void aThrownWeaponIsNotCreditedWithAmmoItCannotFire()
	{
		List<GearItem> owned = Arrays.asList(
			rangedWeapon(ItemID.RUNE_KNIFE, "Rune knife", 40, 3),
			ammo(ItemID.DRAGON_BOLTS_ENCHANTED_RUBY, "Ruby dragon bolts (e)", 122));

		CombatContext ranged = melee().toBuilder()
			.style(CombatStyle.RANGED)
			.rangedLevel(99)
			.build();

		List<ScoredSetup> results = optimizer.best(owned, ranged, false, 1);

		assertFalse(results.isEmpty());
		assertNull("Bolts cannot be worn to feed a thrown weapon",
			results.get(0).getSetup().get(EquipmentSlot.AMMO));
	}

	/**
	 * The rule has to stay narrow: a bow still needs its arrows.
	 */
	@Test
	public void aBowKeepsItsArrows()
	{
		List<GearItem> owned = Arrays.asList(
			rangedWeapon(ItemID.MAGIC_SHORTBOW, "Magic shortbow", 40, 4),
			ammo(ItemID.RUNE_ARROW, "Rune arrow", 49));

		CombatContext ranged = melee().toBuilder()
			.style(CombatStyle.RANGED)
			.rangedLevel(99)
			.build();

		List<ScoredSetup> results = optimizer.best(owned, ranged, false, 1);

		assertFalse(results.isEmpty());
		assertNotNull("A bow needs arrows to shoot",
			results.get(0).getSetup().get(EquipmentSlot.AMMO));
	}

	/**
	 * The reported bug: Dharok's platelegs recommended over oathplate legs. Dharok's are tankier and
	 * worse in every way that matters — no strength, no attack — but two strength rarely changes an
	 * integer max hit, so the DPS tied and defence alone handed it to the wrong pair.
	 */
	@Test
	public void aStrictlyBetterOffensivePieceBeatsATankierOne()
	{
		List<GearItem> owned = Arrays.asList(
			weapon(1, "Abyssal whip", 82, 82, 4, false),
			// Real numbers for the two legs.
			legs(2, "Dharok's platelegs", 0, 0, 85, 82, 83),
			legs(3, "Oathplate legs", 12, 2, 75, 100, 73));

		for (CombatStyle style : new CombatStyle[]{CombatStyle.SLASH, CombatStyle.STAB, CombatStyle.CRUSH})
		{
			CombatContext context = melee().toBuilder().style(style).build();
			List<ScoredSetup> results = optimizer.best(owned, context, false, 1);

			assertFalse(results.isEmpty());
			assertEquals("Wrong legs for " + style, "Oathplate legs",
				results.get(0).getSetup().get(EquipmentSlot.LEGS).getName());
		}
	}

	private static GearItem legs(
		int id, String name, int slash, int strength, int dstab, int dslash, int dcrush)
	{
		EquipmentStats stats = EquipmentStats.builder()
			.aslash(slash)
			.strength(strength)
			.dstab(dstab)
			.dslash(dslash)
			.dcrush(dcrush)
			.slot(EquipmentSlot.LEGS.getSlotIndex())
			.build();

		return new GearItem(id, name, 1, stats, EnumSet.of(Storage.BANK));
	}

	/**
	 * A whip's three attack options are all slash. Offering one for a crush setup is not a slightly
	 * wrong answer, it is an impossible one — and the optimizer produced exactly that, because it read
	 * the crush bonus off the item and never asked whether the weapon could swing that way.
	 */
	@Test
	public void aWhipIsNotOfferedForCrush()
	{
		List<GearItem> owned = Arrays.asList(
			weapon(ItemID.ABYSSAL_TENTACLE, "Abyssal tentacle", 90, 86, 4, false),
			weapon(ItemID.GRANITE_MAUL, "Granite maul", 81, 79, 7, false));

		CombatContext crush = melee().toBuilder().style(CombatStyle.CRUSH).build();
		List<ScoredSetup> results = optimizer.best(owned, crush, false, 1);

		assertFalse(results.isEmpty());
		assertEquals("A whip cannot crush", "Granite maul",
			results.get(0).getSetup().get(EquipmentSlot.WEAPON).getName());
	}

	/**
	 * A blowpipe fires the darts loaded into it. Both are weapon-slot items, so the two never met in
	 * the search and the pipe was scored on a fraction of its real strength.
	 */
	@Test
	public void aBlowpipeIsLoadedWithTheBestDart()
	{
		GearItem blowpipe = rangedWeapon(12926, "Toxic blowpipe", 30, 3);
		GearItem knife = rangedWeapon(ItemID.RUNE_KNIFE, "Rune knife", 30, 3);

		CombatContext ranged = melee().toBuilder().style(CombatStyle.RANGED).rangedLevel(99).build();

		// Without a dart in the bank the pipe is only its own strength, and the knife wins.
		List<ScoredSetup> withoutDarts = optimizer.best(
			Arrays.asList(blowpipe, knife), ranged, false, 1);
		assertEquals("Rune knife",
			withoutDarts.get(0).getSetup().get(EquipmentSlot.WEAPON).getName());

		// With dragon darts to load, the pipe should win.
		List<ScoredSetup> withDarts = optimizer.best(
			Arrays.asList(blowpipe, knife, dart(11230, "Dragon dart", 35)), ranged, false, 1);
		assertEquals("Toxic blowpipe",
			withDarts.get(0).getSetup().get(EquipmentSlot.WEAPON).getName());
	}

	private static GearItem dart(int id, String name, int rangedStrength)
	{
		EquipmentStats stats = EquipmentStats.builder()
			.rangedStrength(rangedStrength)
			.slot(EquipmentSlot.WEAPON.getSlotIndex())
			.speed(3)
			.build();

		return new GearItem(id, name, 1, stats, EnumSet.of(Storage.BANK));
	}
}
