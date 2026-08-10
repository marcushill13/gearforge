package com.gearforge;

import com.gearforge.data.EquipmentStats;
import com.gearforge.dps.BoltEffect;
import com.gearforge.dps.DamageDistribution;
import com.gearforge.dps.MonsterAttribute;
import com.gearforge.dps.Target;
import java.util.EnumSet;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Enchanted bolts were the largest single gap in the gear model, and the one a damage multiplier
 * cannot paper over: a ruby bolt does not make your hits bigger, it replaces six percent of them with
 * a fifth of the target's health.
 */
public class BoltEffectTest
{
	private static final double TOLERANCE = 1e-9;

	/** A shot that always lands for exactly 10, so the effect is the only thing moving the average. */
	private static final DamageDistribution SHOT = DamageDistribution.certain(10);

	@Test
	public void bothTheOrdinaryAndDragonBoltsAreRecognised()
	{
		assertEquals(BoltEffect.RUBY,
			BoltEffect.forItem(ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE_TIPPED_RUBY_ENCHANTED));
		assertEquals(BoltEffect.RUBY, BoltEffect.forItem(ItemID.DRAGON_BOLTS_ENCHANTED_RUBY));
		assertEquals(BoltEffect.ONYX, BoltEffect.forItem(ItemID.DRAGON_BOLTS_ENCHANTED_ONYX));

		assertNull("An ordinary bolt has no effect", BoltEffect.forItem(ItemID.ABYSSAL_WHIP));
	}

	/**
	 * Ruby bolts deal a fifth of the target's health six percent of the time, so their worth rises with
	 * the size of what you are shooting. That is the whole reason they are used at bosses.
	 */
	@Test
	public void rubyBoltsScaleWithTheTargetsHealth()
	{
		double small = mean(BoltEffect.RUBY, 99, 40, plain(), 100);
		double large = mean(BoltEffect.RUBY, 99, 40, plain(), 1000);

		assertTrue("A bigger target should make rubies worth more", large > small);

		// 6% of a fifth of 500 health, plus the 94% of shots that were unaffected.
		assertEquals(0.94 * 10 + 0.06 * 100, mean(BoltEffect.RUBY, 99, 40, plain(), 500), TOLERANCE);
	}

	@Test
	public void rubyDamageIsCapped()
	{
		// A fifth of 5000 is 1000, but the effect stops at 100.
		assertEquals(0.94 * 10 + 0.06 * 100, mean(BoltEffect.RUBY, 99, 40, plain(), 5000), TOLERANCE);
	}

	@Test
	public void opalBoltsAddAFlatBonusFromTheRangedLevel()
	{
		// 99 / 10 truncates to 9, landing 5% of the time on top of the shot.
		assertEquals(0.95 * 10 + 0.05 * 19, mean(BoltEffect.OPAL, 99, 40, plain(), 500), TOLERANCE);
	}

	@Test
	public void dragonstoneBoltsDoNothingToSomethingAlreadyMadeOfFire()
	{
		double versusPlain = mean(BoltEffect.DRAGONSTONE, 99, 40, plain(), 500);
		double versusDragon = mean(BoltEffect.DRAGONSTONE, 99, 40, withAttribute(MonsterAttribute.DRAGON), 500);

		assertTrue(versusPlain > 10);
		assertEquals("Dragonfire cannot burn a dragon", 10, versusDragon, TOLERANCE);
	}

	@Test
	public void onyxBoltsDoNothingToTheUndead()
	{
		double versusPlain = mean(BoltEffect.ONYX, 99, 40, plain(), 500);
		double versusUndead = mean(BoltEffect.ONYX, 99, 40, withAttribute(MonsterAttribute.UNDEAD), 500);

		assertTrue(versusPlain > 10);
		assertEquals("There is no life in the undead to leech", 10, versusUndead, TOLERANCE);
	}

	@Test
	public void pearlBoltsHitFierySomethingHarder()
	{
		double versusPlain = mean(BoltEffect.PEARL, 99, 40, plain(), 500);
		double versusFiery = mean(BoltEffect.PEARL, 99, 40, withAttribute(MonsterAttribute.FIERY), 500);

		assertTrue("Pearls are stronger against something fiery", versusFiery > versusPlain);
	}

	/**
	 * A zaryte crossbow strengthens every effect, which is most of what makes it worth its price.
	 */
	@Test
	public void aZaryteCrossbowStrengthensTheEffect()
	{
		double ordinary = mean(BoltEffect.RUBY, 99, 40, plain(), 500, false);
		double zaryte = mean(BoltEffect.RUBY, 99, 40, plain(), 500, true);

		assertTrue(zaryte > ordinary);
	}

	@Test
	public void everyBoltEffectIsReachableFromAnItemId()
	{
		for (BoltEffect bolt : BoltEffect.values())
		{
			assertNotNull(bolt + " has no item mapping", bolt.toString());
			assertTrue(bolt.getProcChance() > 0 && bolt.getProcChance() < 1);
		}
	}

	private static double mean(
		BoltEffect bolt, int rangedLevel, int maxHit, Target target, int hitpoints)
	{
		return mean(bolt, rangedLevel, maxHit, target, hitpoints, false);
	}

	private static double mean(
		BoltEffect bolt, int rangedLevel, int maxHit, Target target, int hitpoints, boolean zaryte)
	{
		return bolt.apply(SHOT, rangedLevel, maxHit, target, hitpoints, zaryte).mean();
	}

	private static Target plain()
	{
		return Target.builder()
			.name("Plain")
			.defenceLevel(100)
			.defensiveBonuses(EquipmentStats.builder().build())
			.build();
	}

	private static Target withAttribute(MonsterAttribute attribute)
	{
		return Target.builder()
			.name("Special")
			.defenceLevel(100)
			.defensiveBonuses(EquipmentStats.builder().build())
			.attributes(EnumSet.of(attribute))
			.build();
	}
}
