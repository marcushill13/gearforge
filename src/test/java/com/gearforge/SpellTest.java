package com.gearforge;

import com.gearforge.data.EquipmentStats;
import com.gearforge.data.Monster;
import com.gearforge.data.MonsterRepository;
import com.gearforge.dps.Spell;
import com.gearforge.dps.Target;
import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Magic assumed Ice Barrage for everything. It is the strongest spell in the game and the right guess
 * against something with no elemental weakness — but over a thousand monsters have one, and a
 * matching spell gains that weakness as a share of both accuracy and damage.
 */
public class SpellTest
{
	@Test
	public void theStrongestSpellWinsWhereThereIsNoWeakness()
	{
		Spell best = Spell.bestFor(plain(), 99, true);

		assertEquals(Spell.ICE_BARRAGE, best);
	}

	/**
	 * The case that makes this worth doing: a Fire Surge hits for six less than an Ice Barrage on
	 * paper, and beats it outright against something fire-weak.
	 */
	@Test
	public void anElementalSpellWinsAgainstAMatchingWeakness()
	{
		Spell best = Spell.bestFor(weakTo(Spell.Element.FIRE, 50), 99, true);

		assertEquals(Spell.FIRE_SURGE, best);
		assertEquals(36, Spell.FIRE_SURGE.maxHitAgainst(weakTo(Spell.Element.FIRE, 50)));
		assertEquals(30, Spell.ICE_BARRAGE.maxHitAgainst(weakTo(Spell.Element.FIRE, 50)));
	}

	@Test
	public void theWrongElementGainsNothing()
	{
		Target fireWeak = weakTo(Spell.Element.FIRE, 50);

		assertEquals(22, Spell.WATER_SURGE.maxHitAgainst(fireWeak));
		assertEquals(Spell.WATER_SURGE.getMaxHit(), Spell.WATER_SURGE.maxHitAgainst(fireWeak));
	}

	@Test
	public void aLowerLevelCasterGetsTheBestTheyCanCast()
	{
		Spell atFifty = Spell.bestFor(plain(), 50, true);
		Spell atNinetyNine = Spell.bestFor(plain(), 99, true);

		assertNotNull(atFifty);
		assertTrue("A level 50 spell cannot need more than 50 Magic", atFifty.getMagicLevel() <= 50);
		assertTrue(atNinetyNine.getMaxHit() > atFifty.getMaxHit());
	}

	@Test
	public void withoutAncientsTheBestStandardSpellIsChosen()
	{
		Spell standard = Spell.bestFor(plain(), 99, false);

		assertEquals(Spell.FIRE_SURGE, standard);
	}

	/**
	 * The weakness has to survive the generator, or none of this fires.
	 */
	@Test
	public void theMonsterDataCarriesElementalWeaknesses()
	{
		long withWeakness = new MonsterRepository(new Gson()).all().stream()
			.map(Monster::toTarget)
			.filter(target -> target.getWeaknessElement() != null)
			.count();

		assertTrue("Elemental weaknesses are missing from the monster data", withWeakness > 400);
	}

	@Test
	public void aWeakMonsterResolvesItsElement()
	{
		Monster spectre = new MonsterRepository(new Gson()).all().stream()
			.filter(monster -> monster.getName().equalsIgnoreCase("Aberrant spectre"))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Aberrant spectre missing"));

		assertEquals(Spell.Element.AIR, spectre.toTarget().getWeaknessElement());
		assertEquals(50, spectre.toTarget().getWeaknessSeverity());
		assertEquals(Spell.WIND_SURGE, Spell.bestFor(spectre.toTarget(), 99, true));
	}

	private static Target plain()
	{
		return Target.builder()
			.name("Plain")
			.defenceLevel(100)
			.defensiveBonuses(EquipmentStats.builder().build())
			.build();
	}

	private static Target weakTo(Spell.Element element, int severity)
	{
		return Target.builder()
			.name("Weak")
			.defenceLevel(100)
			.defensiveBonuses(EquipmentStats.builder().build())
			.weaknessElement(element)
			.weaknessSeverity(severity)
			.build();
	}
}
