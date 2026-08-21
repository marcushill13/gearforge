package com.gearforge;

import com.gearforge.data.EquipmentSlot;
import com.gearforge.data.EquipmentStats;
import com.gearforge.data.GearItem;
import com.gearforge.data.Storage;
import com.gearforge.dps.CombatStyle;
import com.gearforge.dps.MonsterAttribute;
import com.gearforge.dps.SetEffectRegistry;
import com.gearforge.dps.SetEffects;
import com.gearforge.dps.Target;
import com.gearforge.dps.VoidSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SetEffectRegistryTest
{
	private static final double TOLERANCE = 1e-9;

	private final SetEffectRegistry registry = new SetEffectRegistry();

	private static final Target UNDEAD = Target.builder()
		.name("Undead")
		.defenceLevel(100)
		.defensiveBonuses(EquipmentStats.builder().build())
		.attributes(EnumSet.of(MonsterAttribute.UNDEAD))
		.build();

	@Test
	public void fullVoidMeleeSetIsDetected()
	{
		SetEffects effects = registry.evaluate(fullVoidMelee(), CombatStyle.SLASH, Target.dummy(), false);

		assertEquals(VoidSet.MELEE, effects.getVoidSet());
		assertTrue(effects.getNotes().stream().anyMatch(note -> note.contains("Void melee")));
	}

	@Test
	public void voidDoesNothingWithAPieceMissing()
	{
		List<GearItem> incomplete = fullVoidMelee().subList(0, 3);
		SetEffects effects = registry.evaluate(incomplete, CombatStyle.SLASH, Target.dummy(), false);

		assertEquals(VoidSet.NONE, effects.getVoidSet());
	}

	@Test
	public void voidHelmMustMatchTheStyleBeingUsed()
	{
		// Melee helm while attacking with ranged is not a ranged void set.
		SetEffects effects = registry.evaluate(fullVoidMelee(), CombatStyle.RANGED, Target.dummy(), false);
		assertEquals(VoidSet.NONE, effects.getVoidSet());
	}

	@Test
	public void eliteVoidIsOnlyEliteWhenBothPiecesAreElite()
	{
		List<GearItem> mixed = Arrays.asList(
			piece(ItemID.GAME_PEST_ARCHER_HELM, EquipmentSlot.HEAD),
			piece(ItemID.ELITE_VOID_KNIGHT_TOP, EquipmentSlot.BODY),
			piece(ItemID.PEST_VOID_KNIGHT_ROBES, EquipmentSlot.LEGS),
			piece(ItemID.PEST_VOID_KNIGHT_GLOVES, EquipmentSlot.GLOVES));

		assertEquals(VoidSet.RANGED, registry.evaluate(mixed, CombatStyle.RANGED, Target.dummy(), false).getVoidSet());

		List<GearItem> elite = Arrays.asList(
			piece(ItemID.GAME_PEST_ARCHER_HELM, EquipmentSlot.HEAD),
			piece(ItemID.ELITE_VOID_KNIGHT_TOP, EquipmentSlot.BODY),
			piece(ItemID.ELITE_VOID_KNIGHT_ROBES, EquipmentSlot.LEGS),
			piece(ItemID.PEST_VOID_KNIGHT_GLOVES, EquipmentSlot.GLOVES));

		assertEquals(VoidSet.ELITE_RANGED,
			registry.evaluate(elite, CombatStyle.RANGED, Target.dummy(), false).getVoidSet());
	}

	@Test
	public void salveAppliesOnlyToUndeadTargets()
	{
		List<GearItem> salve = Collections.singletonList(
			piece(ItemID.NZONE_SALVE_AMULET_E, EquipmentSlot.AMULET));

		assertEquals(1.2, registry.evaluate(salve, CombatStyle.SLASH, UNDEAD, false).getDamageMultiplier(), TOLERANCE);
		assertEquals(1.0,
			registry.evaluate(salve, CombatStyle.SLASH, Target.dummy(), false).getDamageMultiplier(), TOLERANCE);
	}

	@Test
	public void slayerHelmAppliesOnlyOnTask()
	{
		List<GearItem> helm = Collections.singletonList(piece(ItemID.SLAYER_HELM_I, EquipmentSlot.HEAD));

		assertEquals(7.0 / 6.0,
			registry.evaluate(helm, CombatStyle.SLASH, Target.dummy(), true).getDamageMultiplier(), TOLERANCE);
		assertEquals(1.0,
			registry.evaluate(helm, CombatStyle.SLASH, Target.dummy(), false).getDamageMultiplier(), TOLERANCE);
	}

	/**
	 * The imbue only ever bought the ranged and magic halves. A plain slayer helmet has always carried
	 * the melee bonus in full, and listing only the imbued id meant every player without a Nightmare
	 * Zone imbue was scored bare-headed on task.
	 */
	@Test
	public void anUnimbuedHelmStillCarriesTheMeleeBonus()
	{
		for (int id : new int[]{ItemID.SLAYER_HELM, ItemID.HARMLESS_BLACK_MASK_10,
			ItemID.SLAYER_HELM_TURQUOISE, ItemID.SLAYER_HELM_ARAXYTE})
		{
			List<GearItem> helm = Collections.singletonList(piece(id, EquipmentSlot.HEAD));

			assertEquals("id " + id + " should give the melee bonus on task", 7.0 / 6.0,
				registry.evaluate(helm, CombatStyle.SLASH, Target.dummy(), true).getDamageMultiplier(),
				TOLERANCE);
		}
	}

	@Test
	public void onlyTheImbuedHelmHelpsRangedAndMagic()
	{
		List<GearItem> plain = Collections.singletonList(
			piece(ItemID.SLAYER_HELM, EquipmentSlot.HEAD));
		List<GearItem> imbued = Collections.singletonList(
			piece(ItemID.SLAYER_HELM_I_HYDRA, EquipmentSlot.HEAD));

		assertEquals(1.0,
			registry.evaluate(plain, CombatStyle.RANGED, Target.dummy(), true).getDamageMultiplier(),
			TOLERANCE);
		assertEquals(1.15,
			registry.evaluate(imbued, CombatStyle.RANGED, Target.dummy(), true).getDamageMultiplier(),
			TOLERANCE);
		assertEquals(1.15,
			registry.evaluate(imbued, CombatStyle.MAGIC, Target.dummy(), true).getDamageMultiplier(),
			TOLERANCE);
	}

	/**
	 * Every colour, every charge, every Soul Wars and Emir's Arena copy. A hydra helm is not a hat.
	 */
	@Test
	public void everySlayerHelmVariantIsRecognised()
	{
		int[] variants = {
			ItemID.SLAYER_HELM_I, ItemID.SLAYER_HELM_I_BLACK, ItemID.SLAYER_HELM_I_GREEN,
			ItemID.SLAYER_HELM_I_RED, ItemID.SLAYER_HELM_I_PURPLE, ItemID.SLAYER_HELM_I_TURQUOISE,
			ItemID.SLAYER_HELM_I_HYDRA, ItemID.SLAYER_HELM_I_TWISTED, ItemID.SLAYER_HELM_I_JAD,
			ItemID.SLAYER_HELM_I_VERZIK, ItemID.SLAYER_HELM_I_ZUK, ItemID.SLAYER_HELM_I_ARAXYTE,
			ItemID.SLAYER_HELM_I_HOODED, ItemID.SW_SLAYER_HELM_I, ItemID.PVPA_SLAYER_HELM_I,
			ItemID.NZONE_BLACK_MASK_1, ItemID.NZONE_BLACK_MASK_10, ItemID.SW_BLACK_MASK_5,
		};

		for (int id : variants)
		{
			List<GearItem> helm = Collections.singletonList(piece(id, EquipmentSlot.HEAD));

			assertEquals("id " + id, 1.15,
				registry.evaluate(helm, CombatStyle.RANGED, Target.dummy(), true).getDamageMultiplier(),
				TOLERANCE);
		}
	}

	/**
	 * Whatever the optimizer is allowed to consider has to include every one of them, or the effect is
	 * modelled and never reached: the head slot's candidates are chosen on raw stats, and a black mask
	 * loses that comparison to almost anything.
	 */
	@Test
	public void everySlayerHelmVariantIsOfferedToTheOptimizer()
	{
		java.util.Set<Integer> relevant = registry.relevantItemIds();

		for (int id : new int[]{ItemID.SLAYER_HELM, ItemID.SLAYER_HELM_I, ItemID.SLAYER_HELM_I_HYDRA,
			ItemID.HARMLESS_BLACK_MASK_10, ItemID.NZONE_BLACK_MASK, ItemID.SLAYER_HELM_ARAXYTE})
		{
			assertTrue("id " + id + " must be a candidate", relevant.contains(id));
		}
	}

	@Test
	public void salveAndSlayerDoNotStack()
	{
		List<GearItem> both = Arrays.asList(
			piece(ItemID.NZONE_SALVE_AMULET_E, EquipmentSlot.AMULET),
			piece(ItemID.SLAYER_HELM_I, EquipmentSlot.HEAD));

		SetEffects effects = registry.evaluate(both, CombatStyle.SLASH, UNDEAD, true);

		// Salve (ei) 1.2 beats slayer 7/6, and the two must not multiply to 1.4.
		assertEquals(1.2, effects.getDamageMultiplier(), TOLERANCE);
		assertEquals(1.2, effects.getAccuracyMultiplier(), TOLERANCE);
		assertTrue(effects.getNotes().stream().anyMatch(note -> note.contains("does not stack")));
	}

	@Test
	public void magicSalveUsesTheMagicMultiplier()
	{
		List<GearItem> salve = Collections.singletonList(
			piece(ItemID.NZONE_SALVE_AMULET, EquipmentSlot.AMULET));

		assertEquals(1.15, registry.evaluate(salve, CombatStyle.MAGIC, UNDEAD, false).getDamageMultiplier(), TOLERANCE);
		assertEquals(7.0 / 6.0,
			registry.evaluate(salve, CombatStyle.SLASH, UNDEAD, false).getDamageMultiplier(), TOLERANCE);
	}

	@Test
	public void nothingEquippedYieldsNoEffects()
	{
		SetEffects effects = registry.evaluate(Collections.emptyList(), CombatStyle.SLASH, Target.dummy(), true);

		assertEquals(1.0, effects.getAccuracyMultiplier(), TOLERANCE);
		assertEquals(1.0, effects.getDamageMultiplier(), TOLERANCE);
		assertEquals(VoidSet.NONE, effects.getVoidSet());
		assertTrue(effects.getNotes().isEmpty());
	}

	@Test
	public void dragonHunterCrossbowHasDifferentAccuracyAndDamage()
	{
		// 30% accuracy but only 25% damage — the damage bonus was cut from 30% and is easy to get wrong.
		List<GearItem> crossbow = Collections.singletonList(
			piece(ItemID.DRAGONHUNTER_XBOW, EquipmentSlot.WEAPON));

		SetEffects vsDragon = registry.evaluate(crossbow, CombatStyle.RANGED, DRACONIC, false);
		assertEquals(1.30, vsDragon.getAccuracyMultiplier(), TOLERANCE);
		assertEquals(1.25, vsDragon.getDamageMultiplier(), TOLERANCE);

		SetEffects vsDummy = registry.evaluate(crossbow, CombatStyle.RANGED, Target.dummy(), false);
		assertEquals(1.0, vsDragon.getAccuracyMultiplier() / 1.30 * vsDummy.getAccuracyMultiplier(), TOLERANCE);
		assertEquals(1.0, vsDummy.getDamageMultiplier(), TOLERANCE);
	}

	@Test
	public void dragonHunterLanceAppliesEquallyToBoth()
	{
		List<GearItem> lance = Collections.singletonList(
			piece(ItemID.DRAGONHUNTER_LANCE, EquipmentSlot.WEAPON));

		SetEffects effects = registry.evaluate(lance, CombatStyle.STAB, DRACONIC, false);
		assertEquals(1.20, effects.getAccuracyMultiplier(), TOLERANCE);
		assertEquals(1.20, effects.getDamageMultiplier(), TOLERANCE);
	}

	@Test
	public void arclightOnlyAppliesToDemons()
	{
		List<GearItem> arclight = Collections.singletonList(piece(ItemID.ARCLIGHT, EquipmentSlot.WEAPON));

		assertEquals(1.70,
			registry.evaluate(arclight, CombatStyle.SLASH, DEMONIC, false).getDamageMultiplier(), TOLERANCE);
		assertEquals(1.0,
			registry.evaluate(arclight, CombatStyle.SLASH, DRACONIC, false).getDamageMultiplier(), TOLERANCE);
	}

	@Test
	public void baneStacksMultiplicativelyWithSlayerUnlikeSalveAndSlayer()
	{
		// Salve and slayer are exclusive, but a bane weapon multiplies on top of whichever won.
		List<GearItem> equipped = Arrays.asList(
			piece(ItemID.DRAGONHUNTER_LANCE, EquipmentSlot.WEAPON),
			piece(ItemID.SLAYER_HELM_I, EquipmentSlot.HEAD));

		SetEffects effects = registry.evaluate(equipped, CombatStyle.STAB, DRACONIC, true);

		assertEquals(7.0 / 6.0 * 1.20, effects.getDamageMultiplier(), TOLERANCE);
	}

	@Test
	public void baneWeaponsAreInTheOptimizerCandidatePool()
	{
		// Without this the optimizer could prune a bane weapon on raw stats and never find the passive.
		for (int id : new int[]{
			ItemID.DRAGONHUNTER_LANCE, ItemID.DRAGONHUNTER_XBOW, ItemID.DRAGONHUNTER_WAND,
			ItemID.ARCLIGHT, ItemID.EMBERLIGHT, ItemID.DARKLIGHT, ItemID.SILVERLIGHT,
			ItemID.BARRONITE_MACE, ItemID.LEAFBLADED_BATTLEAXE,
			ItemID.KERIS_PARTISAN, ItemID.KERIS_PARTISAN_BREACH, ItemID.KERIS_PARTISAN_AMASCUT})
		{
			assertTrue("missing id " + id, registry.relevantItemIds().contains(id));
		}
	}

	@Test
	public void dragonHunterWandBeatsTheOtherDragonbaneWeapons()
	{
		SetEffects wand = registry.evaluate(
			Collections.singletonList(piece(ItemID.DRAGONHUNTER_WAND, EquipmentSlot.WEAPON)),
			CombatStyle.MAGIC, DRACONIC, false);

		assertEquals(7.0 / 4.0, wand.getAccuracyMultiplier(), TOLERANCE);
		assertEquals(7.0 / 5.0, wand.getDamageMultiplier(), TOLERANCE);
	}

	/**
	 * Darklight and silverlight raise the attack roll by 60% and leave damage alone. They were given a
	 * damage bonus here as well, which overstated them against every demon in the game.
	 */
	@Test
	public void theWeakerDemonbaneWeaponsRaiseAccuracyOnly()
	{
		for (int id : new int[]{ItemID.DARKLIGHT, ItemID.SILVERLIGHT})
		{
			SetEffects effects = registry.evaluate(
				Collections.singletonList(piece(id, EquipmentSlot.WEAPON)),
				CombatStyle.SLASH, DEMONIC, false);

			assertEquals(1.60, effects.getAccuracyMultiplier(), TOLERANCE);
			assertEquals(1.0, effects.getDamageMultiplier(), TOLERANCE);
		}

		// Emberlight matches Arclight rather than the older pair.
		SetEffects emberlight = registry.evaluate(
			Collections.singletonList(piece(ItemID.EMBERLIGHT, EquipmentSlot.WEAPON)),
			CombatStyle.SLASH, DEMONIC, false);
		assertEquals(1.70, emberlight.getDamageMultiplier(), TOLERANCE);
	}

	@Test
	public void kerisBoostsDamageAndOnlyBreachingBoostsAccuracy()
	{
		SetEffects plain = registry.evaluate(
			Collections.singletonList(piece(ItemID.KERIS_PARTISAN, EquipmentSlot.WEAPON)),
			CombatStyle.STAB, KALPHITE, false);
		assertEquals(1.33, plain.getDamageMultiplier(), TOLERANCE);
		assertEquals(1.0, plain.getAccuracyMultiplier(), TOLERANCE);

		SetEffects breaching = registry.evaluate(
			Collections.singletonList(piece(ItemID.KERIS_PARTISAN_BREACH, EquipmentSlot.WEAPON)),
			CombatStyle.STAB, KALPHITE, false);
		assertEquals(1.33, breaching.getAccuracyMultiplier(), TOLERANCE);
	}

	@Test
	public void golemAndLeafyBanesApplyToDamageOnly()
	{
		SetEffects mace = registry.evaluate(
			Collections.singletonList(piece(ItemID.BARRONITE_MACE, EquipmentSlot.WEAPON)),
			CombatStyle.CRUSH, GOLEM, false);
		assertEquals(23.0 / 20.0, mace.getDamageMultiplier(), TOLERANCE);
		assertEquals(1.0, mace.getAccuracyMultiplier(), TOLERANCE);
	}

	@Test
	public void baneWeaponsDoNothingAgainstTheWrongMonsterType()
	{
		// A kalphite weapon must not fire on a demon, and vice versa.
		assertEquals(1.0, registry.evaluate(
			Collections.singletonList(piece(ItemID.KERIS_PARTISAN, EquipmentSlot.WEAPON)),
			CombatStyle.STAB, DEMONIC, false).getDamageMultiplier(), TOLERANCE);

		assertEquals(1.0, registry.evaluate(
			Collections.singletonList(piece(ItemID.ARCLIGHT, EquipmentSlot.WEAPON)),
			CombatStyle.SLASH, KALPHITE, false).getDamageMultiplier(), TOLERANCE);
	}

	@Test
	public void twistedBowScalesOffTheTargetsMagic()
	{
		// Vorkath: magic level 148, magic attack 148, not Xerician so the cap is 250.
		//   accuracy: t2 = (444-10)/100 = 4, t3 = (44-100)^2/100 = 31 -> 140+4-31 = 113
		//   damage:   t2 = (444-14)/100 = 4, t3 = (44-140)^2/100 = 92 -> 250+4-92 = 162
		Target vorkath = Target.builder()
			.name("Vorkath")
			.defenceLevel(164)
			.magicLevel(148)
			.magicAttack(148)
			.defensiveBonuses(EquipmentStats.builder().build())
			.attributes(EnumSet.of(MonsterAttribute.DRAGON))
			.build();

		SetEffects effects = registry.evaluate(
			Collections.singletonList(piece(ItemID.TWISTED_BOW, EquipmentSlot.WEAPON)),
			CombatStyle.RANGED, vorkath, false);

		assertEquals(1.13, effects.getAccuracyMultiplier(), TOLERANCE);
		assertEquals(1.62, effects.getDamageMultiplier(), TOLERANCE);
	}

	@Test
	public void twistedBowIsCappedAndXericianTargetsRaiseTheCap()
	{
		// Well past both caps, so each should clamp to its ceiling rather than run away.
		assertEquals(250, Math.max(0, SetEffectRegistry.twistedBowMagic(highMagic(false, 900))));
		assertEquals(350, Math.max(0, SetEffectRegistry.twistedBowMagic(highMagic(true, 900))));
	}

	@Test
	public void twistedBowScalingClampsRatherThanGrowingWithoutLimit()
	{
		// The curve peaks and then falls away; it must never exceed its clamp in either mode.
		for (int magic = 0; magic <= 400; magic += 7)
		{
			assertTrue(SetEffectRegistry.twistedBowScaling(magic, true) <= 1.40 + TOLERANCE);
			assertTrue(SetEffectRegistry.twistedBowScaling(magic, false) <= 2.50 + TOLERANCE);
			assertTrue(SetEffectRegistry.twistedBowScaling(magic, true) >= 0.0);
			assertTrue(SetEffectRegistry.twistedBowScaling(magic, false) >= 0.0);
		}
	}

	@Test
	public void twistedBowDoesNothingWhenNotWorn()
	{
		assertEquals(1.0, registry.evaluate(
			Collections.emptyList(), CombatStyle.RANGED, KALPHITE, false).getDamageMultiplier(), TOLERANCE);
	}

	@Test
	public void inquisitorsCountsPerPieceAndOnlyOnCrush()
	{
		List<GearItem> full = Arrays.asList(
			piece(ItemID.INQUISITORS_HELM, EquipmentSlot.HEAD),
			piece(ItemID.INQUISITORS_BODY, EquipmentSlot.BODY),
			piece(ItemID.INQUISITORS_SKIRT, EquipmentSlot.LEGS));

		// helm 1 + hauberk 2 + skirt 2 = 5, applied as 205/200.
		SetEffects onCrush = registry.evaluate(full, CombatStyle.CRUSH, Target.dummy(), false);
		assertEquals(205.0 / 200.0, onCrush.getAccuracyMultiplier(), TOLERANCE);
		assertEquals(205.0 / 200.0, onCrush.getDamageMultiplier(), TOLERANCE);

		// Worth nothing on any other style, even wearing the whole set.
		assertEquals(1.0,
			registry.evaluate(full, CombatStyle.STAB, Target.dummy(), false).getDamageMultiplier(), TOLERANCE);
		assertEquals(1.0,
			registry.evaluate(full, CombatStyle.SLASH, Target.dummy(), false).getDamageMultiplier(), TOLERANCE);
	}

	@Test
	public void partialInquisitorsStillCounts()
	{
		// Not a full-set-only bonus — two pieces are still worth wearing.
		List<GearItem> partial = Arrays.asList(
			piece(ItemID.INQUISITORS_HELM, EquipmentSlot.HEAD),
			piece(ItemID.INQUISITORS_BODY, EquipmentSlot.BODY));

		assertEquals(203.0 / 200.0,
			registry.evaluate(partial, CombatStyle.CRUSH, Target.dummy(), false).getDamageMultiplier(),
			TOLERANCE);

		assertEquals(202.0 / 200.0,
			registry.evaluate(
				Collections.singletonList(piece(ItemID.INQUISITORS_SKIRT, EquipmentSlot.LEGS)),
				CombatStyle.CRUSH, Target.dummy(), false).getDamageMultiplier(),
			TOLERANCE);
	}

	@Test
	public void meleeWeaponPassivesDoNothingForSpellsOrArrows()
	{
		// Reported from the wild: magic best-in-slot returned Emberlight against Yama, because its
		// demonbane bonus was being applied to a spell cast while merely holding it.
		List<GearItem> emberlight = Collections.singletonList(
			piece(ItemID.EMBERLIGHT, EquipmentSlot.WEAPON));

		assertEquals(1.70,
			registry.evaluate(emberlight, CombatStyle.SLASH, DEMONIC, false).getDamageMultiplier(), TOLERANCE);
		assertEquals(1.0,
			registry.evaluate(emberlight, CombatStyle.MAGIC, DEMONIC, false).getDamageMultiplier(), TOLERANCE);
		assertEquals(1.0,
			registry.evaluate(emberlight, CombatStyle.RANGED, DEMONIC, false).getDamageMultiplier(), TOLERANCE);
	}

	@Test
	public void theTwistedBowOnlyScalesItsOwnShots()
	{
		List<GearItem> tbow = Collections.singletonList(piece(ItemID.TWISTED_BOW, EquipmentSlot.WEAPON));
		Target target = highMagic(false, 250);

		assertTrue(registry.evaluate(tbow, CombatStyle.RANGED, target, false).getDamageMultiplier() > 1.0);
		assertEquals(1.0,
			registry.evaluate(tbow, CombatStyle.MAGIC, target, false).getDamageMultiplier(), TOLERANCE);
		assertEquals(1.0,
			registry.evaluate(tbow, CombatStyle.CRUSH, target, false).getDamageMultiplier(), TOLERANCE);
	}

	@Test
	public void dragonbaneWeaponsOnlyCountOnTheirOwnStyle()
	{
		List<GearItem> lance = Collections.singletonList(
			piece(ItemID.DRAGONHUNTER_LANCE, EquipmentSlot.WEAPON));

		assertEquals(1.20,
			registry.evaluate(lance, CombatStyle.STAB, DRACONIC, false).getDamageMultiplier(), TOLERANCE);
		assertEquals(1.0,
			registry.evaluate(lance, CombatStyle.MAGIC, DRACONIC, false).getDamageMultiplier(), TOLERANCE);
	}

	private static Target highMagic(boolean xerician, int magic)
	{
		return Target.builder()
			.name("Test")
			.magicLevel(magic)
			.magicAttack(magic)
			.defensiveBonuses(EquipmentStats.builder().build())
			.attributes(xerician
				? EnumSet.of(MonsterAttribute.XERICIAN)
				: EnumSet.noneOf(MonsterAttribute.class))
			.build();
	}

	private static final Target KALPHITE = Target.builder()
		.name("Kalphite")
		.defenceLevel(100)
		.defensiveBonuses(EquipmentStats.builder().build())
		.attributes(EnumSet.of(MonsterAttribute.KALPHITE))
		.build();

	private static final Target GOLEM = Target.builder()
		.name("Golem")
		.defenceLevel(100)
		.defensiveBonuses(EquipmentStats.builder().build())
		.attributes(EnumSet.of(MonsterAttribute.GOLEM))
		.build();

	private static final Target DRACONIC = Target.builder()
		.name("Dragon")
		.defenceLevel(100)
		.defensiveBonuses(EquipmentStats.builder().build())
		.attributes(EnumSet.of(MonsterAttribute.DRAGON))
		.build();

	private static final Target DEMONIC = Target.builder()
		.name("Demon")
		.defenceLevel(100)
		.defensiveBonuses(EquipmentStats.builder().build())
		.attributes(EnumSet.of(MonsterAttribute.DEMON))
		.build();

	static List<GearItem> fullVoidMelee()
	{
		return Arrays.asList(
			piece(ItemID.GAME_PEST_MELEE_HELM, EquipmentSlot.HEAD),
			piece(ItemID.PEST_VOID_KNIGHT_TOP, EquipmentSlot.BODY),
			piece(ItemID.PEST_VOID_KNIGHT_ROBES, EquipmentSlot.LEGS),
			piece(ItemID.PEST_VOID_KNIGHT_GLOVES, EquipmentSlot.GLOVES));
	}

	static GearItem piece(int itemId, EquipmentSlot slot)
	{
		EquipmentStats stats = EquipmentStats.builder().slot(slot.getSlotIndex()).build();
		return new GearItem(itemId, "Item " + itemId, 1, stats, EnumSet.of(Storage.BANK));
	}
}
