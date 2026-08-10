package com.gearforge;

import com.gearforge.data.EquipmentStats;
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

	@Test
	public void zulrahCannotBeMeleed()
	{
		Monster zulrah = new MonsterRepository(new Gson()).all().stream()
			.filter(monster -> monster.getName().equalsIgnoreCase("Zulrah"))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Zulrah missing from the monster data"));

		assertFalse(Reachability.canAttack(zulrah, CombatStyle.SLASH));
		assertFalse(Reachability.canAttack(zulrah, CombatStyle.STAB));
		assertFalse(Reachability.canAttack(zulrah, CombatStyle.CRUSH));

		// The styles that can reach it must be left alone.
		assertTrue(Reachability.canAttack(zulrah, CombatStyle.RANGED));
		assertTrue(Reachability.canAttack(zulrah, CombatStyle.MAGIC));
	}

	@Test
	public void anOrdinaryMonsterIsReachableByEverything()
	{
		Monster crab = new Monster();
		crab.setName("Ammonite Crab");

		for (CombatStyle style : CombatStyle.values())
		{
			assertTrue(style + " should reach a crab", Reachability.canAttack(crab, style));
		}
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
