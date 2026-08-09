package com.gearforge;

import com.gearforge.data.EquipmentSlot;
import com.gearforge.data.EquipmentStats;
import com.gearforge.data.GearItem;
import com.gearforge.data.Storage;
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
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * A spec recommendation is only useful if it changes with the target and with the gear. These pin
 * the two behaviours the whole feature exists for.
 */
public class SpecFinderTest
{
	private final SpecFinder finder = new SpecFinder(new DpsEngine());

	private static final GearItem CLAWS = weapon(ItemID.DRAGON_CLAWS, "Dragon claws", 57, 56, 4);
	private static final GearItem VOIDWAKER = weapon(ItemID.VOIDWAKER, "Voidwaker", 80, 80, 4);
	private static final GearItem WARHAMMER = weapon(ItemID.DRAGON_WARHAMMER, "Dragon warhammer", 95, 80, 6);
	private static final GearItem WHIP = weapon(ItemID.ABYSSAL_WHIP, "Abyssal whip", 82, 82, 4);

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
		assertTrue(find(Target.dummy(), WHIP).isEmpty());
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
		Map<EquipmentSlot, GearItem> setup = new EnumMap<>(EquipmentSlot.class);
		setup.put(EquipmentSlot.WEAPON, WHIP);
		setup.put(EquipmentSlot.BODY, body(bodyStrength));

		List<EquipmentStats> pieces = new ArrayList<>();
		for (GearItem item : setup.values())
		{
			pieces.add(item.getStats());
		}

		CombatContext context = CombatContext.builder()
			.attackLevel(99)
			.strengthLevel(99)
			.style(CombatStyle.SLASH)
			.equipment(EquipmentStats.sum(pieces))
			.target(target)
			.targetHitpoints(hitpoints)
			.weaponSpeedTicks(4)
			.build();

		List<GearItem> owned = new ArrayList<>(Arrays.asList(specWeapons));
		owned.add(WHIP);

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
}
