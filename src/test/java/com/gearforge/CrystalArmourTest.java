package com.gearforge;

import com.gearforge.data.EquipmentStats;
import com.gearforge.data.ItemCategories;
import com.gearforge.data.Monster;
import com.gearforge.data.MonsterRepository;
import com.gearforge.data.Reachability;
import com.gearforge.dps.CombatStyle;
import com.gearforge.dps.SetEffectRegistry;
import com.gearforge.dps.SetEffects;
import com.gearforge.dps.Target;
import com.google.gson.Gson;
import com.gearforge.data.GearItem;
import com.gearforge.data.Storage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Two gaps that between them produced a visibly wrong answer at Zulrah: a melee setup recommended for
 * something melee cannot reach, and a bow of faerdhinen losing to rune knives because the armour that
 * makes the bow worth using was not modelled.
 */
public class CrystalArmourTest
{
	private static final double EXACT = 1e-9;

	private final SetEffectRegistry registry = new SetEffectRegistry();

	@Test
	public void theFullSetIsWorthThirtyPercentAccuracyAndFifteenDamage()
	{
		SetEffects effects = apply(ItemID.BOW_OF_FAERDHINEN,
			ItemID.CRYSTAL_HELMET, ItemID.CRYSTAL_CHESTPLATE, ItemID.CRYSTAL_PLATELEGS);

		assertEquals(1.30, effects.getAccuracyMultiplier(), EXACT);
		assertEquals(1.15, effects.getDamageMultiplier(), EXACT);
	}

	@Test
	public void piecesCountIndividually()
	{
		SetEffects bodyOnly = apply(ItemID.BOW_OF_FAERDHINEN, ItemID.CRYSTAL_CHESTPLATE);

		assertEquals(1.15, bodyOnly.getAccuracyMultiplier(), EXACT);
		assertEquals(1.075, bodyOnly.getDamageMultiplier(), EXACT);
	}

	/**
	 * The armour is dead weight without the bow, which is exactly why it must not be applied to a setup
	 * that happens to be wearing it with something else.
	 */
	@Test
	public void theArmourDoesNothingWithoutACrystalBow()
	{
		SetEffects effects = apply(ItemID.MAGIC_SHORTBOW,
			ItemID.CRYSTAL_HELMET, ItemID.CRYSTAL_CHESTPLATE, ItemID.CRYSTAL_PLATELEGS);

		assertEquals(1.0, effects.getAccuracyMultiplier(), EXACT);
		assertEquals(1.0, effects.getDamageMultiplier(), EXACT);
	}

	@Test
	public void chargeVariantsCountAsTheSameArmour()
	{
		SetEffects effects = apply(ItemID.BOW_OF_FAERDHINEN_INACTIVE,
			ItemID.CRYSTAL_HELMET_INACTIVE, ItemID.CRYSTAL_CHESTPLATE_INACTIVE,
			ItemID.CRYSTAL_PLATELEGS_INACTIVE);

		assertEquals(1.30, effects.getAccuracyMultiplier(), EXACT);
	}

	/**
	 * Zulrah is a reach problem, not a style problem. An ordinary weapon cannot get there; a halberd
	 * can, and people melee it that way. Banning melee outright deleted a correct answer.
	 */
	@Test
	public void zulrahNeedsAHalberdRatherThanNoMeleeAtAll()
	{
		Monster zulrah = named("Zulrah");

		assertTrue("Melee is possible at Zulrah with the right weapon",
			Reachability.meleeIsPossible(zulrah));
		assertTrue(Reachability.requiresReach(zulrah));

		assertFalse("A whip cannot reach Zulrah", Reachability.meleeCanReach(zulrah, false));
		assertTrue("A halberd can", Reachability.meleeCanReach(zulrah, true));
	}

	/**
	 * A kraken is in the water — no reach helps.
	 */
	@Test
	public void somethingUnreachableStaysUnreachableWhateverTheWeapon()
	{
		Monster kraken = new Monster();
		kraken.setName("Kraken");

		assertFalse(Reachability.meleeIsPossible(kraken));
		assertFalse(Reachability.meleeCanReach(kraken, true));
	}

	@Test
	public void anOrdinaryMonsterIsReachableByAnything()
	{
		Monster crab = new Monster();
		crab.setName("Ammonite Crab");

		assertTrue(Reachability.meleeIsPossible(crab));
		assertFalse(Reachability.requiresReach(crab));
		assertTrue(Reachability.meleeCanReach(crab, false));
	}

	/**
	 * The polearms are what makes the reach rule work, so the classification has to actually find them.
	 */
	@Test
	public void halberdsAreClassifiedAsHavingReach()
	{
		ItemCategories categories = new ItemCategories(new Gson());

		assertTrue(categories.hasReach(ItemID.DRAGON_HALBERD));
		assertTrue(categories.hasReach(ItemID.CRYSTAL_HALBERD));
		assertFalse(categories.hasReach(ItemID.ABYSSAL_WHIP));
	}

	private static Monster named(String name)
	{
		return new MonsterRepository(new Gson()).all().stream()
			.filter(monster -> monster.getName().equalsIgnoreCase(name))
			.findFirst()
			.orElseThrow(() -> new AssertionError(name + " missing from the monster data"));
	}

	private SetEffects apply(int... itemIds)
	{
		List<GearItem> equipped = new ArrayList<>();
		for (int id : itemIds)
		{
			equipped.add(new GearItem(id, "Item " + id, 1,
				EquipmentStats.builder().build(), EnumSet.of(Storage.BANK)));
		}

		return registry.evaluate(equipped, CombatStyle.RANGED, target(), false);
	}

	private static Target target()
	{
		return Target.builder()
			.name("Zulrah")
			.defenceLevel(300)
			.magicLevel(300)
			.defensiveBonuses(EquipmentStats.builder().build())
			.build();
	}
}
