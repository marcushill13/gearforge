package com.gearforge;

import com.gearforge.data.EquipmentSlot;
import com.gearforge.data.EquipmentStats;
import com.gearforge.data.GearItem;
import com.gearforge.data.ItemCategories;
import com.gearforge.data.Storage;
import com.gearforge.dps.CombatContext;
import com.gearforge.dps.CombatStyle;
import com.gearforge.dps.DpsEngine;
import com.gearforge.dps.PoweredStaff;
import com.gearforge.dps.SetEffectRegistry;
import com.gearforge.dps.Spell;
import com.gearforge.dps.Target;
import com.gearforge.optimizer.DpsOptimizer;
import com.gearforge.optimizer.ScoredSetup;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Effects reaching the thing that scores them.
 * <p>
 * Several were modelled, tested in isolation, and then never applied, because the call that computes
 * DPS dropped the spell on the floor and because a scripted edit of mine quietly removed the powered
 * staff wiring. A unit test of an effect proves the arithmetic; it does not prove the effect is
 * plugged in. These check the plug.
 */
public class EffectsReachScoringTest
{
	private final DpsOptimizer optimizer =
		new DpsOptimizer(new DpsEngine(), new SetEffectRegistry(), new ItemCategories(new Gson()));

	/** Fires a second hit on bolt, blast and wave spells. */
	private static final int TWINFLAME_STAFF = 30634;

	/**
	 * The twinflame staff doubles bolt, blast and wave spells only — so while it is held, a Fire Wave
	 * beats the Fire Surge that would otherwise be chosen. Picking the spell before the weapon meant
	 * the staff could never be given a spell it was able to double, so its effect never applied.
	 */
	@Test
	public void theTwinflameStaffChangesWhichSpellIsWorthCasting()
	{
		// Against a fire-weak target a Fire Wave is worth 30 before the staff, and 42 with it doubled,
		// against Ice Barrage's flat 30. Weakness and the staff compound, which is where it matters.
		Target fireWeak = Target.builder()
			.name("Weak")
			.defenceLevel(100)
			.magicLevel(100)
			.defensiveBonuses(EquipmentStats.builder().build())
			.weaknessElement(Spell.Element.FIRE)
			.weaknessSeverity(50)
			.build();

		Spell chosen = Spell.bestForTwinflame(fireWeak, 99, true);

		assertTrue("A doubled spell should be chosen", chosen.firesTwice());
		assertEquals(Spell.FIRE_WAVE, chosen);
	}

	/**
	 * The staff does not change the answer everywhere, and must not pretend to. At 99 Magic against
	 * something with no weakness, a doubled Fire Wave is 28 and an Ice Barrage is 30.
	 */
	@Test
	public void theStaffDoesNotOverrideAStrongerSpell()
	{
		Target plain = Target.builder()
			.name("Plain")
			.defenceLevel(100)
			.magicLevel(100)
			.defensiveBonuses(EquipmentStats.builder().build())
			.build();

		assertEquals(Spell.ICE_BARRAGE, Spell.bestFor(plain, 99, true));
		assertEquals(Spell.ICE_BARRAGE, Spell.bestForTwinflame(plain, 99, true));
	}

	@Test
	public void onlyBoltBlastAndWaveAreDoubled()
	{
		assertTrue(Spell.FIRE_WAVE.firesTwice());
		assertTrue(Spell.FIRE_BLAST.firesTwice());
		assertTrue(Spell.FIRE_BOLT.firesTwice());

		assertFalse("A surge is not doubled", Spell.FIRE_SURGE.firesTwice());
		assertFalse("Nothing ancient is doubled", Spell.ICE_BARRAGE.firesTwice());
		assertFalse("A strike is not doubled", Spell.FIRE_STRIKE.firesTwice());
	}

	/**
	 * A staff held is a staff scored. This is the wiring that a scripted edit removed without failing
	 * a single test, because every powered staff test checked the formula rather than the plumbing.
	 */
	@Test
	public void aPoweredStaffIsScoredAsItsOwnWeapon()
	{
		GearItem trident = magicWeapon(ItemID.TOTS_CHARGED, "Trident of the seas");
		GearItem plainStaff = magicWeapon(ItemID.BATTLESTAFF, "Battlestaff");

		List<ScoredSetup> results = optimizer.best(
			Arrays.asList(trident, plainStaff), magicContext(), false, 1);

		assertFalse(results.isEmpty());

		// A trident attacks four times faster than a five-tick cast and hits for its own maximum, so
		// it must win. If the wiring is gone it is scored as an Ice Barrage cast and ties.
		assertEquals("Trident of the seas",
			results.get(0).getSetup().get(EquipmentSlot.WEAPON).getName());
		assertEquals(PoweredStaff.TRIDENT_OF_THE_SEAS, PoweredStaff.forItem(ItemID.TOTS_CHARGED));
	}

	/**
	 * Dragon javelins carry 150 ranged strength — more than any thrown weapon — so crediting them to a
	 * knife that cannot fire them dwarfed every honest setup.
	 */
	@Test
	public void aThrownWeaponIsNotGivenJavelins()
	{
		GearItem knife = new GearItem(ItemID.RUNE_KNIFE, "Rune knife", 1,
			EquipmentStats.builder()
				.arange(40).rangedStrength(24)
				.slot(EquipmentSlot.WEAPON.getSlotIndex()).speed(3).build(),
			EnumSet.of(Storage.BANK));

		GearItem javelin = new GearItem(19484, "Dragon javelin", 100,
			EquipmentStats.builder()
				.rangedStrength(150)
				.slot(EquipmentSlot.AMMO.getSlotIndex()).build(),
			EnumSet.of(Storage.BANK));

		CombatContext ranged = CombatContext.builder()
			.rangedLevel(99)
			.style(CombatStyle.RANGED)
			.equipment(EquipmentStats.builder().build())
			.target(Target.dummy())
			.weaponSpeedTicks(3)
			.build();

		List<ScoredSetup> results = optimizer.best(Arrays.asList(knife, javelin), ranged, false, 1);

		assertFalse(results.isEmpty());
		assertNull("A knife cannot fire a javelin",
			results.get(0).getSetup().get(EquipmentSlot.AMMO));
	}

	private static GearItem magicWeapon(int id, String name)
	{
		return new GearItem(id, name, 1,
			EquipmentStats.builder()
				.amagic(25)
				.slot(EquipmentSlot.WEAPON.getSlotIndex())
				.speed(4)
				.build(),
			EnumSet.of(Storage.BANK));
	}

	private static CombatContext magicContext()
	{
		return CombatContext.builder()
			.magicLevel(99)
			.style(CombatStyle.MAGIC)
			.equipment(EquipmentStats.builder().build())
			.target(Target.dummy())
			.baseSpellDamage(30)
			.weaponSpeedTicks(5)
			.build();
	}
}
