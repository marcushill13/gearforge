package com.gearforge;

import com.gearforge.data.EquipmentStats;
import com.gearforge.dps.AttackStyle;
import com.gearforge.dps.CombatContext;
import com.gearforge.dps.CombatPrayer;
import com.gearforge.dps.CombatStyle;
import com.gearforge.dps.DpsEngine;
import com.gearforge.dps.SetupScore;
import com.gearforge.dps.Target;
import com.gearforge.dps.VoidSet;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Reference tests for the combat maths.
 * <p>
 * Two kinds of assertion here, kept distinct on purpose:
 * <ul>
 *   <li><b>External anchors</b> — max hits that are independently known and checkable in game
 *       (unarmed 11, whip 25, dragon scimitar 22 at 99 Strength). If the formula chain is wrong
 *       anywhere, these break.</li>
 *   <li><b>Conformance</b> — each step matches the wiki formula, with the arithmetic worked out in
 *       the comment so a future reader can re-derive it without trusting the implementation.</li>
 * </ul>
 * See {@code docs/combat-formulas.md} for sources.
 */
public class DpsEngineTest
{
	private static final double TOLERANCE = 1e-4;

	private final DpsEngine engine = new DpsEngine();

	private static final EquipmentStats WHIP = EquipmentStats.builder()
		.aslash(82).strength(82).speed(4).slot(3).build();

	private static final EquipmentStats DRAGON_SCIMITAR = EquipmentStats.builder()
		.astab(8).aslash(67).acrush(-2).strength(66).speed(4).slot(3).build();

	private static final EquipmentStats UNARMED = EquipmentStats.builder().speed(4).slot(3).build();

	// ---------------------------------------------------------------- external anchors

	@Test
	public void unarmedMaxHitAt99Strength()
	{
		// effStr = 99 + 3 (aggressive) + 8 = 110
		// max    = floor((110 * (0 + 64) + 320) / 640) = floor(7360 / 640) = 11
		assertEquals(11, melee(UNARMED, AttackStyle.AGGRESSIVE, CombatPrayer.NONE).getMaxHit());
	}

	@Test
	public void whipMaxHitAt99Strength()
	{
		// The whip has no aggressive style; lash is controlled, so +1 rather than +3.
		// effStr = 99 + 1 + 8 = 108
		// max    = floor((108 * (82 + 64) + 320) / 640) = floor(16088 / 640) = 25
		assertEquals(25, melee(WHIP, AttackStyle.CONTROLLED, CombatPrayer.NONE).getMaxHit());
	}

	@Test
	public void dragonScimitarMaxHitAt99Strength()
	{
		// effStr = 99 + 3 + 8 = 110
		// max    = floor((110 * (66 + 64) + 320) / 640) = floor(14620 / 640) = 22
		assertEquals(22, melee(DRAGON_SCIMITAR, AttackStyle.AGGRESSIVE, CombatPrayer.NONE).getMaxHit());
	}

	// ---------------------------------------------------------------- effective levels

	@Test
	public void prayerAppliesToTheLevelBeforeStyleAndPlusEight()
	{
		// This is the spec's error and the Maximum-melee-hit page's error, pinned.
		// Correct:   floor(99 * 1.23) = 121, then +3 +8 = 132
		// Incorrect: (99 + 3 + 8) * 1.23 = floor(135.3) = 135
		CombatContext context = meleeContext(WHIP, AttackStyle.AGGRESSIVE, CombatPrayer.PIETY);
		assertEquals(132, engine.effectiveStrengthLevel(context));
	}

	@Test
	public void styleBonusIsAdditiveNotMultiplicative()
	{
		CombatContext aggressive = meleeContext(WHIP, AttackStyle.AGGRESSIVE, CombatPrayer.NONE);
		CombatContext defensive = meleeContext(WHIP, AttackStyle.DEFENSIVE, CombatPrayer.NONE);

		// Exactly 3 apart, not 3x apart.
		assertEquals(3, engine.effectiveStrengthLevel(aggressive) - engine.effectiveStrengthLevel(defensive));
	}

	@Test
	public void meleeVoidMultipliesAfterTheStyleAddAndPlusEight()
	{
		// floor((99 + 3 + 8) * 1.1) = floor(121.0) = 121
		CombatContext context = CombatContext.builder()
			.strengthLevel(99).attackLevel(99)
			.style(CombatStyle.SLASH)
			.attackStyle(AttackStyle.AGGRESSIVE)
			.voidSet(VoidSet.MELEE)
			.equipment(WHIP)
			.weaponSpeedTicks(4)
			.build();

		assertEquals(121, engine.effectiveStrengthLevel(context));
	}

	@Test
	public void rigourBoostsRangedStrengthMoreThanRangedAccuracy()
	{
		// Rigour is 1.20 on ranged attack but 1.23 on ranged strength — collapsing them is a classic
		// source of wrong numbers.
		CombatContext context = CombatContext.builder()
			.rangedLevel(99)
			.style(CombatStyle.RANGED)
			.attackStyle(AttackStyle.ACCURATE)
			.prayer(CombatPrayer.RIGOUR)
			.equipment(EquipmentStats.builder().arange(100).rangedStrength(50).speed(5).build())
			.weaponSpeedTicks(5)
			.build();

		assertEquals(129, engine.effectiveAttackLevel(context));    // floor(99*1.20)=118, +3+8
		assertEquals(132, engine.effectiveStrengthLevel(context));  // floor(99*1.23)=121, +3+8
	}

	@Test
	public void eliteRangedVoidOnlyImprovesStrength()
	{
		CombatContext context = CombatContext.builder()
			.rangedLevel(99)
			.style(CombatStyle.RANGED)
			.attackStyle(AttackStyle.ACCURATE)
			.voidSet(VoidSet.ELITE_RANGED)
			.equipment(EquipmentStats.builder().arange(100).rangedStrength(50).speed(5).build())
			.weaponSpeedTicks(5)
			.build();

		assertEquals(121, engine.effectiveAttackLevel(context));    // floor(110 * 1.10)
		assertEquals(123, engine.effectiveStrengthLevel(context));  // floor(110 * 1.125) = 123
	}

	@Test
	public void magicAppliesVoidBeforeTheStyleAddUnlikeMeleeAndRanged()
	{
		// floor(99 * 1.45 + 3 + 8) = floor(154.55) = 154
		// If void were applied last, as melee does, this would be floor(110 * 1.45) = 159.
		CombatContext context = magicContext(true, AttackStyle.ACCURATE, VoidSet.MAGIC);
		assertEquals(154, engine.effectiveAttackLevel(context));
	}

	@Test
	public void spellbookCastsGetNoStyleBonus()
	{
		// Only powered staves get the style add.
		CombatContext staff = magicContext(true, AttackStyle.ACCURATE, VoidSet.NONE);
		CombatContext spell = magicContext(false, AttackStyle.ACCURATE, VoidSet.NONE);

		assertEquals(110, engine.effectiveAttackLevel(staff));  // 99 + 3 + 8
		assertEquals(107, engine.effectiveAttackLevel(spell));  // 99 + 0 + 8
	}

	// ---------------------------------------------------------------- rolls and chance

	@Test
	public void magicDefenceRollUsesTheTargetsMagicLevelNotItsDefenceLevel()
	{
		Target target = Target.builder()
			.name("Test")
			.defenceLevel(50)
			.magicLevel(200)
			.defensiveBonuses(EquipmentStats.builder().dmagic(100).drange(999).build())
			.build();

		CombatContext context = CombatContext.builder()
			.magicLevel(99)
			.style(CombatStyle.MAGIC)
			.equipment(EquipmentStats.builder().amagic(100).build())
			.target(target)
			.weaponSpeedTicks(5)
			.baseSpellDamage(30)
			.build();

		// (200 + 9) * (100 + 64) = 34276. Using defence level would give (50+9)*164 = 9676.
		assertEquals(34276, engine.defenceRoll(context));
	}

	@Test
	public void hitChanceUsesTheHigherBranchWhenAttackExceedsDefence()
	{
		// 1 - (10000 + 2) / (2 * (20000 + 1)) = 1 - 10002/40002
		assertEquals(1.0 - 10002.0 / 40002.0, engine.hitChance(20000, 10000), TOLERANCE);
	}

	@Test
	public void hitChanceUsesTheLowerBranchWhenDefenceMeetsOrExceedsAttack()
	{
		// 5000 / (2 * (10000 + 1))
		assertEquals(5000.0 / 20002.0, engine.hitChance(5000, 10000), TOLERANCE);
		// Equal rolls take the else branch too.
		assertEquals(100.0 / 202.0, engine.hitChance(100, 100), TOLERANCE);
	}

	// ---------------------------------------------------------------- damage and speed

	@Test
	public void averageDamageIncludesTheZeroBecomesOneCorrection()
	{
		// maxHit/2 + 1/(maxHit+1) = 12.5 + 1/26
		assertEquals(12.5 + 1.0 / 26.0, engine.averageDamage(1.0, 25), TOLERANCE);
	}

	@Test
	public void averageDamageIsZeroWhenNothingCanBeHit()
	{
		assertEquals(0.0, engine.averageDamage(1.0, 0), TOLERANCE);
	}

	@Test
	public void rapidIsOneTickFasterAndOtherStylesAreNot()
	{
		assertEquals(4, engine.attackSpeedTicks(rangedContext(AttackStyle.RAPID)));
		assertEquals(5, engine.attackSpeedTicks(rangedContext(AttackStyle.ACCURATE)));
		assertEquals(5, engine.attackSpeedTicks(rangedContext(AttackStyle.LONGRANGE)));
	}

	@Test
	public void magicMaxHitAppliesMagicDamagePercent()
	{
		// Ice barrage base 30, with +15% magic damage: floor(30 * 1.15) = floor(34.5) = 34
		CombatContext context = CombatContext.builder()
			.magicLevel(99)
			.style(CombatStyle.MAGIC)
			.equipment(EquipmentStats.builder().amagic(100).magicDamage(15f).build())
			.baseSpellDamage(30)
			.weaponSpeedTicks(5)
			.build();

		assertEquals(34, engine.score(context).getMaxHit());
	}

	// ---------------------------------------------------------------- end to end

	@Test
	public void whipAgainstADummyProducesTheExpectedDpsChain()
	{
		SetupScore score = melee(WHIP, AttackStyle.CONTROLLED, CombatPrayer.NONE);

		// effAtk = 99 + 1 + 8 = 108; atkRoll = 108 * (82 + 64) = 15768
		assertEquals(15768, score.getAttackRoll());
		// dummy: (0 + 9) * (0 + 64) = 576
		assertEquals(576, score.getDefenceRoll());

		double expectedHitChance = 1.0 - 578.0 / (2.0 * 15769.0);
		assertEquals(expectedHitChance, score.getHitChance(), TOLERANCE);

		double expectedDps = expectedHitChance * (12.5 + 1.0 / 26.0) / (4 * 0.6);
		assertEquals(expectedDps, score.getDps(), TOLERANCE);
	}

	@Test
	public void higherDefenceTargetsLowerDpsWithoutChangingMaxHit()
	{
		SetupScore vsDummy = melee(WHIP, AttackStyle.CONTROLLED, CombatPrayer.NONE);

		Target armoured = Target.builder()
			.name("Armoured")
			.defenceLevel(200)
			.defensiveBonuses(EquipmentStats.builder().dslash(300).build())
			.build();

		SetupScore vsArmoured = engine.score(CombatContext.builder()
			.attackLevel(99).strengthLevel(99)
			.style(CombatStyle.SLASH)
			.attackStyle(AttackStyle.CONTROLLED)
			.equipment(WHIP)
			.weaponSpeedTicks(4)
			.target(armoured)
			.build());

		assertEquals(vsDummy.getMaxHit(), vsArmoured.getMaxHit());
		assertTrue(vsArmoured.getDps() < vsDummy.getDps());
		assertTrue(vsArmoured.getHitChance() > 0.0);
	}

	@Test
	public void equipmentStatsSumAddsBonusesAndTakesTheWeaponSpeed()
	{
		EquipmentStats helm = EquipmentStats.builder().aslash(0).dslash(30).strength(0).slot(0).build();
		EquipmentStats total = EquipmentStats.sum(Arrays.asList(WHIP, helm));

		assertEquals(82, total.getAslash());
		assertEquals(82, total.getStrength());
		assertEquals(30, total.getDslash());
		assertEquals(4, total.getSpeed());
	}

	// ---------------------------------------------------------------- helpers

	private SetupScore melee(EquipmentStats weapon, AttackStyle style, CombatPrayer prayer)
	{
		return engine.score(meleeContext(weapon, style, prayer));
	}

	private static CombatContext meleeContext(EquipmentStats weapon, AttackStyle style, CombatPrayer prayer)
	{
		return CombatContext.builder()
			.attackLevel(99)
			.strengthLevel(99)
			.style(CombatStyle.SLASH)
			.attackStyle(style)
			.prayer(prayer)
			.equipment(weapon)
			.weaponSpeedTicks(weapon.getSpeed())
			.build();
	}

	private static CombatContext magicContext(boolean poweredStaff, AttackStyle style, VoidSet voidSet)
	{
		return CombatContext.builder()
			.magicLevel(99)
			.style(CombatStyle.MAGIC)
			.attackStyle(style)
			.voidSet(voidSet)
			.poweredStaff(poweredStaff)
			.equipment(EquipmentStats.builder().amagic(100).build())
			.baseSpellDamage(30)
			.weaponSpeedTicks(4)
			.build();
	}

	private static CombatContext rangedContext(AttackStyle style)
	{
		return CombatContext.builder()
			.rangedLevel(99)
			.style(CombatStyle.RANGED)
			.attackStyle(style)
			.equipment(EquipmentStats.builder().arange(100).rangedStrength(50).speed(5).build())
			.weaponSpeedTicks(5)
			.build();
	}
}
