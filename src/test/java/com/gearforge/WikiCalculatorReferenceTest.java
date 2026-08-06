package com.gearforge;

import com.gearforge.data.EquipmentStats;
import com.gearforge.dps.AttackStyle;
import com.gearforge.dps.CombatContext;
import com.gearforge.dps.CombatStyle;
import com.gearforge.dps.DpsEngine;
import com.gearforge.dps.Target;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Cross-validation against the OSRS Wiki's own DPS calculator.
 * <p>
 * Every expected value here is lifted from that project's test suite
 * ({@code src/tests/calc/BasicRolls.test.ts} in weirdgloop/osrs-dps-calc), and the item stats are
 * from its equipment dataset. This is the independent check the rest of the DPS suite lacks: those
 * tests confirm the implementation matches the formulas I transcribed, whereas these confirm the
 * formulas themselves produce the same numbers as the reference calculator.
 * <p>
 * The reference scenarios use the accurate attack style, which is why every case adds +3.
 */
public class WikiCalculatorReferenceTest
{
	private final DpsEngine engine = new DpsEngine();

	/** slash +82, strength +82, speed 4. */
	private static final EquipmentStats ABYSSAL_WHIP = EquipmentStats.builder()
		.aslash(82).strength(82).speed(4).slot(3).build();

	/** ranged +128, ranged strength +106, speed 5. */
	private static final EquipmentStats BOW_OF_FAERDHINEN = EquipmentStats.builder()
		.arange(128).rangedStrength(106).speed(5).slot(3).build();

	/** magic +15, speed 4. */
	private static final EquipmentStats TRIDENT_OF_THE_SEAS = EquipmentStats.builder()
		.amagic(15).speed(4).slot(3).build();

	// ------------------------------------------------------------------ melee

	@Test
	public void whipAttackRollMatchesTheWikiCalculator()
	{
		assertEquals(1752, attackRoll(melee(1, 1)));
		assertEquals(16060, attackRoll(melee(99, 1)));
	}

	@Test
	public void whipMaxHitMatchesTheWikiCalculator()
	{
		assertEquals(2, maxHit(melee(1, 1)));
		assertEquals(24, maxHit(melee(1, 99)));
	}

	// ------------------------------------------------------------------ ranged

	@Test
	public void bowOfFaerdhinenAttackRollMatchesTheWikiCalculator()
	{
		assertEquals(2304, attackRoll(ranged(1)));
		assertEquals(21120, attackRoll(ranged(99)));
	}

	@Test
	public void bowOfFaerdhinenMaxHitMatchesTheWikiCalculator()
	{
		assertEquals(3, maxHit(ranged(1)));
		assertEquals(29, maxHit(ranged(99)));
	}

	// ------------------------------------------------------------------ magic

	@Test
	public void tridentAttackRollMatchesTheWikiCalculator()
	{
		// Powered staves get the accurate style bonus; a spellbook cast would not, and these numbers
		// only line up with it applied.
		assertEquals(948, attackRoll(magic(1)));
		assertEquals(8690, attackRoll(magic(99)));
	}

	// ------------------------------------------------------------------ helpers

	private int attackRoll(CombatContext context)
	{
		return engine.attackRoll(
			engine.effectiveAttackLevel(context),
			context.getStyle().attackBonusOf(context.getEquipment()),
			context.getAccuracyMultiplier());
	}

	private int maxHit(CombatContext context)
	{
		return engine.maxHit(context, engine.effectiveStrengthLevel(context));
	}

	private static CombatContext melee(int attackLevel, int strengthLevel)
	{
		return CombatContext.builder()
			.attackLevel(attackLevel)
			.strengthLevel(strengthLevel)
			.style(CombatStyle.SLASH)
			.attackStyle(AttackStyle.ACCURATE)
			.equipment(ABYSSAL_WHIP)
			.weaponSpeedTicks(4)
			.target(Target.dummy())
			.build();
	}

	private static CombatContext ranged(int rangedLevel)
	{
		return CombatContext.builder()
			.rangedLevel(rangedLevel)
			.style(CombatStyle.RANGED)
			.attackStyle(AttackStyle.ACCURATE)
			.equipment(BOW_OF_FAERDHINEN)
			.weaponSpeedTicks(5)
			.target(Target.dummy())
			.build();
	}

	private static CombatContext magic(int magicLevel)
	{
		return CombatContext.builder()
			.magicLevel(magicLevel)
			.style(CombatStyle.MAGIC)
			.attackStyle(AttackStyle.ACCURATE)
			.poweredStaff(true)
			.equipment(TRIDENT_OF_THE_SEAS)
			.weaponSpeedTicks(4)
			.target(Target.dummy())
			.build();
	}
}
