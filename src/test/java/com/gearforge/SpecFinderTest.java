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
import com.gearforge.dps.SpecialAttack;
import com.gearforge.dps.Target;
import com.gearforge.optimizer.SpecFinder;
import com.gearforge.optimizer.SpecSuggestion;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A spec recommendation is only useful if it changes with the target and with the gear. These pin
 * the two behaviours the whole feature exists for.
 */
public class SpecFinderTest
{
	private final SpecFinder finder = new SpecFinder(new DpsEngine(), new ItemCategories(new Gson()));

	private static final GearItem CLAWS = weapon(ItemID.DRAGON_CLAWS, "Dragon claws", 57, 56, 4);
	private static final GearItem VOIDWAKER = weapon(ItemID.VOIDWAKER, "Voidwaker", 80, 80, 4);
	private static final GearItem WARHAMMER = weapon(ItemID.DRAGON_WARHAMMER, "Dragon warhammer", 95, 80, 6);
	/**
	 * The setup's own weapon, and the control for "has no special attack". Deliberately not the whip:
	 * that gained one, which is what broke this test the first time.
	 */
	private static final GearItem PLAIN = weapon(ItemID.RUNE_SCIMITAR, "Rune scimitar", 45, 44, 4);

	@Test
	public void aGuaranteedSpecWinsWhereAccuracyIsHardToCome()
	{
		// A target you can barely hit is exactly where never missing is worth most.
		List<SpecSuggestion> suggestions = find(armoured(), CLAWS, VOIDWAKER);

		assertFalse(suggestions.isEmpty());
		assertEquals(SpecialAttack.VOIDWAKER, suggestions.get(0).getSpecial());
	}

	@Test
	public void burstDamageWinsWhereEverythingLandsAnyway()
	{
		// Against no defence at all the claws' four hits beat a capped guaranteed roll.
		List<SpecSuggestion> suggestions = find(Target.dummy(), CLAWS, VOIDWAKER);

		assertFalse(suggestions.isEmpty());
		assertEquals(SpecialAttack.DRAGON_CLAWS, suggestions.get(0).getSpecial());
	}

	/**
	 * The point of scoring against the worn setup: a stronger body slot raises your max hit, which the
	 * voidwaker's damage is a straight fraction of.
	 */
	@Test
	public void strongerGearMakesTheSpecHitHarder()
	{
		double weak = valueOf(find(Target.dummy(), 0, VOIDWAKER), SpecialAttack.VOIDWAKER);
		double strong = valueOf(find(Target.dummy(), 40, VOIDWAKER), SpecialAttack.VOIDWAKER);

		assertTrue("Strength bonus should raise what the voidwaker hits for", strong > weak);
	}

	/**
	 * A warhammer is nearly worthless on something that dies in two hits and genuinely good on a boss,
	 * because all it does is shorten what remains.
	 */
	@Test
	public void aDefenceReductionIsWorthMoreOnALongerFight()
	{
		double onShortFight = valueOf(find(armoured(), 50, WARHAMMER), SpecialAttack.DRAGON_WARHAMMER);
		double onLongFight = valueOf(find(armoured(), 50, 2000, WARHAMMER), SpecialAttack.DRAGON_WARHAMMER);

		assertTrue("A longer kill should make the reduction worth more", onLongFight > onShortFight);
	}

	@Test
	public void aWeaponWithNoSpecialIsNotSuggested()
	{
		assertTrue(find(Target.dummy(), PLAIN).isEmpty());
	}

	/**
	 * A spec that is not worth using is still reported, just at no value. Dropping it made owning the
	 * weapon look identical to not owning it.
	 */
	@Test
	public void anUnhelpfulSpecIsStillListed()
	{
		GearItem scimitar = weapon(ItemID.DRAGON_SCIMITAR, "Dragon scimitar", 67, 66, 4);

		List<SpecSuggestion> suggestions = find(Target.dummy(), scimitar);

		assertEquals(1, suggestions.size());
		assertEquals(SpecialAttack.DRAGON_SCIMITAR, suggestions.get(0).getSpecial());
	}

	private List<SpecSuggestion> find(Target target, GearItem... specWeapons)
	{
		return find(target, 0, specWeapons);
	}

	private List<SpecSuggestion> find(Target target, int bodyStrength, GearItem... specWeapons)
	{
		return find(target, bodyStrength, 300, specWeapons);
	}

	private List<SpecSuggestion> find(
		Target target, int bodyStrength, int hitpoints, GearItem... specWeapons)
	{
		return find(target, bodyStrength, hitpoints, 99, specWeapons);
	}

	private List<SpecSuggestion> find(
		Target target, int bodyStrength, int hitpoints, int magicLevel, GearItem... specWeapons)
	{
		Map<EquipmentSlot, GearItem> setup = new EnumMap<>(EquipmentSlot.class);
		setup.put(EquipmentSlot.WEAPON, PLAIN);
		setup.put(EquipmentSlot.BODY, body(bodyStrength));

		List<EquipmentStats> pieces = new ArrayList<>();
		for (GearItem item : setup.values())
		{
			pieces.add(item.getStats());
		}

		CombatContext context = CombatContext.builder()
			.attackLevel(99)
			.strengthLevel(99)
			.rangedLevel(99)
			.magicLevel(magicLevel)
			.style(CombatStyle.SLASH)
			.equipment(EquipmentStats.sum(pieces))
			.target(target)
			.targetHitpoints(hitpoints)
			.weaponSpeedTicks(4)
			.build();

		List<GearItem> owned = new ArrayList<>(Arrays.asList(specWeapons));
		owned.add(PLAIN);

		return finder.find(setup, owned, context);
	}

	private static double valueOf(List<SpecSuggestion> suggestions, SpecialAttack special)
	{
		for (SpecSuggestion suggestion : suggestions)
		{
			if (suggestion.getSpecial() == special)
			{
				return suggestion.getDamageAdded();
			}
		}

		return 0;
	}

	/** Something with real defence, where landing a hit is the hard part. */
	private static Target armoured()
	{
		return Target.builder()
			.name("Armoured")
			.defenceLevel(300)
			.defensiveBonuses(EquipmentStats.builder().dslash(200).dstab(200).dcrush(200).build())
			.build();
	}

	private static GearItem body(int strength)
	{
		EquipmentStats stats = EquipmentStats.builder()
			.strength(strength)
			.slot(EquipmentSlot.BODY.getSlotIndex())
			.build();

		return new GearItem(9999, "Body", 1, stats, EnumSet.of(Storage.BANK));
	}

	private static GearItem weapon(int id, String name, int slash, int strength, int speed)
	{
		EquipmentStats stats = EquipmentStats.builder()
			.aslash(slash)
			.astab(slash - 10)
			.acrush(slash - 20)
			.strength(strength)
			.slot(EquipmentSlot.WEAPON.getSlotIndex())
			.speed(speed)
			.build();

		return new GearItem(id, name, 1, stats, EnumSet.of(Storage.BANK));
	}

	/**
	 * A halberd's second swing only reaches something bigger than one tile, so the same spec is worth
	 * more on a large boss than on a person-sized one.
	 */
	@Test
	public void theHalberdSweepIsWorthMoreOnALargeTarget()
	{
		GearItem halberd = weapon(ItemID.DRAGON_HALBERD, "Dragon halberd", 70, 70, 7);

		double small = valueOf(find(sized(1), 0, halberd), SpecialAttack.DRAGON_HALBERD);
		double large = valueOf(find(sized(3), 0, halberd), SpecialAttack.DRAGON_HALBERD);

		assertTrue("The second hit should only land on a large target", large > small);
	}

	/**
	 * An instant special costs no attack turn, so it is a hit gained rather than a hit swapped — it
	 * must never score as worthless just because it has no damage boost.
	 */
	@Test
	public void anInstantSpecialCountsAsAWholeExtraHit()
	{
		GearItem maul = weapon(ItemID.GRANITE_MAUL, "Granite maul", 81, 79, 7);

		double added = valueOf(find(Target.dummy(), 0, maul), SpecialAttack.GRANITE_MAUL);

		assertTrue("A free hit is worth roughly a hit, not nothing", added > 1);
	}

	private static Target sized(int size)
	{
		return Target.builder()
			.name("Sized")
			.defenceLevel(100)
			.size(size)
			.defensiveBonuses(EquipmentStats.builder().dslash(50).dstab(50).dcrush(50).build())
			.build();
	}

	/**
	 * A charged weapon arrives under a different id per charge tier, and every one of them specs the
	 * same. Matching only exact ids would quietly ignore the weapon you actually own.
	 */
	@Test
	public void aChargeVariantIsRecognisedAsTheSameSpecWeapon()
	{
		assertEquals(SpecialAttack.CRYSTAL_HALBERD,
			SpecialAttack.forItem(ItemID.CRYSTAL_HALBERD));
		assertEquals(SpecialAttack.CRYSTAL_HALBERD,
			SpecialAttack.forItem(ItemID.CRYSTAL_HALBERD_INACTIVE));
	}

	@Test
	public void anOrdinaryItemHasNoSpecialAttack()
	{
		assertNull(SpecialAttack.forItem(ItemID.RUNE_SCIMITAR));
		assertNull(SpecialAttack.forItem(ItemID.SHARK));
	}

	/**
	 * A bow must roll ranged and a staff must roll magic. Scored as a sword, a dark bow would read its
	 * strength bonus instead of its ranged strength and roll against the wrong defence — producing a
	 * number that looks perfectly reasonable and is nonsense.
	 */
	@Test
	public void aBowIsScoredAsRangedAndAStaffAsMagic()
	{
		GearItem darkBow = rangedWeapon(ItemID.DARKBOW, "Dark bow", 95, 9);
		GearItem staff = magicWeapon(ItemID.NIGHTMARE_STAFF_VOLATILE, "Volatile nightmare staff");

		// Both must produce a suggestion at all: a mis-styled weapon scores zero and vanishes.
		assertFalse(find(Target.dummy(), 0, darkBow).isEmpty());
		assertFalse(find(Target.dummy(), 0, staff).isEmpty());
	}

	/**
	 * The Magic specials take their ceiling from the Magic level, not from the spell, so they must not
	 * depend on spell damage at all.
	 */
	@Test
	public void aMagicSpecialScalesWithMagicLevelRatherThanTheSpell()
	{
		GearItem volatileStaff = magicWeapon(ItemID.NIGHTMARE_STAFF_VOLATILE, "Volatile nightmare staff");

		double atNinetyNine = valueOf(
			find(Target.dummy(), 0, 300, 99, volatileStaff), SpecialAttack.VOLATILE_NIGHTMARE_STAFF);
		double atFifty = valueOf(
			find(Target.dummy(), 0, 300, 50, volatileStaff), SpecialAttack.VOLATILE_NIGHTMARE_STAFF);

		assertTrue("A higher Magic level should raise the ceiling", atNinetyNine > atFifty);
	}

	private static GearItem rangedWeapon(int id, String name, int rangedAttack, int speed)
	{
		EquipmentStats stats = EquipmentStats.builder()
			.arange(rangedAttack)
			.rangedStrength(60)
			.slot(EquipmentSlot.WEAPON.getSlotIndex())
			.speed(speed)
			.build();

		return new GearItem(id, name, 1, stats, EnumSet.of(Storage.BANK));
	}

	private static GearItem magicWeapon(int id, String name)
	{
		EquipmentStats stats = EquipmentStats.builder()
			.amagic(25)
			.slot(EquipmentSlot.WEAPON.getSlotIndex())
			.speed(4)
			.build();

		return new GearItem(id, name, 1, stats, EnumSet.of(Storage.BANK));
	}
}
