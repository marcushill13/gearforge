package com.gearforge;

import com.gearforge.data.Monster;
import com.gearforge.data.PlayerLevels;
import com.gearforge.dps.CombatContext;
import com.gearforge.dps.CombatPrayer;
import com.gearforge.dps.CombatStyle;
import com.gearforge.dps.Potion;
import com.gearforge.dps.Scoring;
import com.gearforge.dps.Spell;
import java.util.EnumSet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * One place where the context is built, because there were three and they drifted. A fix that lands
 * in one of three places is not a fix — that is how the Bosses tab kept recommending a weapon that
 * could not reach its target long after the reach model shipped.
 */
public class ScoringTest
{
	private final Scoring scoring = new Scoring();

	private static final PlayerLevels MAXED = PlayerLevels.builder()
		.attack(99).strength(99).defence(99).ranged(99).magic(99)
		.prayer(99).hitpoints(99).slayer(99).herblore(99)
		.build();

	@Test
	public void everyContextCarriesTheTargetsHitpointsAndSpell()
	{
		Monster boss = new Monster();
		boss.setName("Boss");
		boss.setHitpoints(500);
		boss.setDefenceLevel(200);

		CombatContext melee = scoring.contextFor(
			CombatStyle.SLASH, MAXED, boss, Scoring.noPrayers(), Scoring.noPotions());
		CombatContext magic = scoring.contextFor(
			CombatStyle.MAGIC, MAXED, boss, Scoring.noPrayers(), Scoring.noPotions());

		// Ruby bolts and the defence-reduction specials are worth nothing without the hitpoints.
		assertEquals(500, melee.getTargetHitpoints());

		assertNull("Melee casts nothing", melee.getSpell());
		assertNotNull("Magic must have a spell chosen for it", magic.getSpell());
	}

	/**
	 * Two potions raising the same stat do not stack — the stronger simply wins. Summing them would
	 * invent a boost no player can have.
	 */
	@Test
	public void potionsTakeTheStrongestRatherThanAddingUp()
	{
		CombatContext both = scoring.contextFor(
			CombatStyle.SLASH, MAXED, null, Scoring.noPrayers(),
			EnumSet.of(Potion.SUPER_STRENGTH, Potion.STRENGTH));

		CombatContext strongest = scoring.contextFor(
			CombatStyle.SLASH, MAXED, null, Scoring.noPrayers(), EnumSet.of(Potion.SUPER_STRENGTH));

		assertEquals(strongest.getStrengthBoost(), both.getStrengthBoost());
		assertTrue(both.getStrengthBoost() > 0);
	}

	/**
	 * Several prayers can be chosen, but only one is active in game, so the best for the style applies.
	 */
	@Test
	public void thePrayerThatSuitsTheStyleIsTheOneApplied()
	{
		CombatContext melee = scoring.contextFor(
			CombatStyle.SLASH, MAXED, null,
			EnumSet.of(CombatPrayer.PIETY, CombatPrayer.RIGOUR, CombatPrayer.AUGURY),
			Scoring.noPotions());

		CombatContext ranged = scoring.contextFor(
			CombatStyle.RANGED, MAXED, null,
			EnumSet.of(CombatPrayer.PIETY, CombatPrayer.RIGOUR, CombatPrayer.AUGURY),
			Scoring.noPotions());

		assertEquals(CombatPrayer.PIETY, melee.getPrayer());
		assertEquals(CombatPrayer.RIGOUR, ranged.getPrayer());
	}

	@Test
	public void noSelectionMeansNoPrayerRatherThanAGuess()
	{
		CombatContext bare = scoring.contextFor(
			CombatStyle.SLASH, MAXED, null, Scoring.noPrayers(), Scoring.noPotions());

		assertEquals(CombatPrayer.NONE, bare.getPrayer());
		assertEquals(0, bare.getStrengthBoost());
	}

	/**
	 * Against something with an elemental weakness the spell has to follow the target, not the other
	 * way round — that is the whole reason the choice exists.
	 */
	@Test
	public void theSpellFollowsTheTargetsWeakness()
	{
		Monster fireWeak = new Monster();
		fireWeak.setName("Weak");
		fireWeak.setWeaknessElement("fire");
		fireWeak.setWeaknessSeverity(50);

		CombatContext context = scoring.contextFor(
			CombatStyle.MAGIC, MAXED, fireWeak, Scoring.noPrayers(), Scoring.noPotions());

		assertEquals(Spell.FIRE_SURGE, context.getSpell());
	}
}
