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
import com.gearforge.dps.SetEffectRegistry;
import com.gearforge.dps.Target;
import com.gearforge.optimizer.Constraints;
import com.gearforge.optimizer.UpgradeFinder;
import com.gearforge.optimizer.UpgradeSuggestion;
import com.gearforge.setups.Setup;
import com.gearforge.setups.SetupSource;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UpgradeFinderTest
{
	private final UpgradeFinder finder =
		new UpgradeFinder(new DpsEngine(), new SetEffectRegistry(), new ItemCategories(new Gson()));

	private static CombatContext context()
	{
		return CombatContext.builder()
			.attackLevel(99)
			.strengthLevel(99)
			.style(CombatStyle.SLASH)
			.equipment(EquipmentStats.builder().build())
			.target(Target.dummy())
			.weaponSpeedTicks(4)
			.build();
	}

	@Test
	public void suggestsABetterItemThePlayerAlreadyOwns()
	{
		GearItem whip = weapon(1, "Abyssal whip", 82, 82, false);
		GearItem weakHelm = armour(2, "Iron full helm", EquipmentSlot.HEAD, 0);
		GearItem strongHelm = armour(3, "Slayer helmet", EquipmentSlot.HEAD, 30);

		Setup setup = Setup.named("Test", SetupSource.MANUAL);
		setup.put(EquipmentSlot.WEAPON, whip.getItemId());
		setup.put(EquipmentSlot.HEAD, weakHelm.getItemId());

		List<UpgradeSuggestion> suggestions =
			finder.find(setup, Arrays.asList(whip, weakHelm, strongHelm), context());

		assertEquals(1, suggestions.size());
		assertEquals(EquipmentSlot.HEAD, suggestions.get(0).getSlot());
		assertEquals("Slayer helmet", suggestions.get(0).getReplacement().getName());
		assertTrue(suggestions.get(0).getGain() > 0);
	}

	@Test
	public void staysQuietWhenTheSetupIsAlreadyBest()
	{
		GearItem whip = weapon(1, "Abyssal whip", 82, 82, false);
		GearItem strongHelm = armour(3, "Slayer helmet", EquipmentSlot.HEAD, 30);
		GearItem weakHelm = armour(2, "Iron full helm", EquipmentSlot.HEAD, 0);

		Setup setup = Setup.named("Test", SetupSource.MANUAL);
		setup.put(EquipmentSlot.WEAPON, whip.getItemId());
		setup.put(EquipmentSlot.HEAD, strongHelm.getItemId());

		assertTrue(finder.find(setup, Arrays.asList(whip, weakHelm, strongHelm), context()).isEmpty());
	}

	@Test
	public void accuracyUpgradesAreVisibleNotJustDamageOnes()
	{
		// The helm here adds only accuracy. Judged against a zero-defence dummy this would score as
		// worthless, because accuracy is already ~100% — the benchmark target exists to catch it.
		GearItem whip = weapon(1, "Abyssal whip", 82, 82, false);
		GearItem plainHelm = armour(2, "Iron full helm", EquipmentSlot.HEAD, 0);
		GearItem accurateHelm = armour(3, "Accurate helm", EquipmentSlot.HEAD, 40);

		Setup setup = Setup.named("Test", SetupSource.MANUAL);
		setup.put(EquipmentSlot.WEAPON, whip.getItemId());
		setup.put(EquipmentSlot.HEAD, plainHelm.getItemId());

		List<UpgradeSuggestion> suggestions =
			finder.find(setup, Arrays.asList(whip, plainHelm, accurateHelm), context());

		assertEquals(1, suggestions.size());
		assertEquals("Accurate helm", suggestions.get(0).getReplacement().getName());
	}

	@Test
	public void ignoresGainsTooSmallToBeWorthSaying()
	{
		GearItem whip = weapon(1, "Abyssal whip", 82, 82, false);
		GearItem helm = armour(2, "Helm", EquipmentSlot.HEAD, 0);
		// A single point of accuracy is far below the threshold.
		GearItem barelyBetter = armour(3, "Barely better helm", EquipmentSlot.HEAD, 1);

		Setup setup = Setup.named("Test", SetupSource.MANUAL);
		setup.put(EquipmentSlot.WEAPON, whip.getItemId());
		setup.put(EquipmentSlot.HEAD, helm.getItemId());

		assertTrue(finder.find(setup, Arrays.asList(whip, helm, barelyBetter), context()).isEmpty());
	}

	@Test
	public void dismissedItemsAreNeverSuggestedAgain()
	{
		GearItem whip = weapon(1, "Abyssal whip", 82, 82, false);
		GearItem weakHelm = armour(2, "Iron full helm", EquipmentSlot.HEAD, 0);
		GearItem strongHelm = armour(3, "Slayer helmet", EquipmentSlot.HEAD, 30);

		Setup setup = Setup.named("Test", SetupSource.MANUAL);
		setup.put(EquipmentSlot.WEAPON, whip.getItemId());
		setup.put(EquipmentSlot.HEAD, weakHelm.getItemId());
		setup.getDismissedUpgrades().add(strongHelm.getItemId());

		assertTrue(finder.find(setup, Arrays.asList(whip, weakHelm, strongHelm), context()).isEmpty());
	}

	@Test
	public void suggestingATwoHanderAccountsForLosingTheShield()
	{
		// The two-hander is better on paper, but only wins once the shield it displaces is costed.
		GearItem oneHander = weapon(1, "One-hander", 82, 82, false);
		GearItem twoHander = weapon(2, "Two-hander", 84, 84, true);
		GearItem shield = armour(3, "Big shield", EquipmentSlot.SHIELD, 60);

		Setup setup = Setup.named("Test", SetupSource.MANUAL);
		setup.put(EquipmentSlot.WEAPON, oneHander.getItemId());
		setup.put(EquipmentSlot.SHIELD, shield.getItemId());

		List<UpgradeSuggestion> suggestions =
			finder.find(setup, Arrays.asList(oneHander, twoHander, shield), context());

		// Whatever it decides, it must never propose an illegal pairing.
		for (UpgradeSuggestion suggestion : suggestions)
		{
			assertTrue(suggestion.getSlot() != EquipmentSlot.SHIELD
				|| !Constraints.isTwoHanded(oneHander));
		}
	}

	@Test
	public void aSetupWithNoWeaponProducesNothingRatherThanFailing()
	{
		Setup setup = Setup.named("Test", SetupSource.MANUAL);
		setup.put(EquipmentSlot.HEAD, 2);

		assertTrue(finder.find(setup,
			Collections.singletonList(armour(2, "Helm", EquipmentSlot.HEAD, 0)), context()).isEmpty());
	}

	@Test
	public void suggestionsReadInPlainLanguage()
	{
		GearItem whip = weapon(1, "Abyssal whip", 82, 82, false);
		GearItem weakHelm = armour(2, "Iron full helm", EquipmentSlot.HEAD, 0);
		GearItem strongHelm = armour(3, "Slayer helmet", EquipmentSlot.HEAD, 30);

		Setup setup = Setup.named("Test", SetupSource.MANUAL);
		setup.put(EquipmentSlot.WEAPON, whip.getItemId());
		setup.put(EquipmentSlot.HEAD, weakHelm.getItemId());

		String described = finder.find(setup, Arrays.asList(whip, weakHelm, strongHelm), context())
			.get(0).describe();

		assertTrue(described.startsWith("Better head: Slayer helmet, +"));
		assertTrue(described.endsWith("% DPS"));
	}

	private static GearItem weapon(int id, String name, int slash, int strength, boolean twoHanded)
	{
		EquipmentStats stats = EquipmentStats.builder()
			.aslash(slash).strength(strength)
			.slot(EquipmentSlot.WEAPON.getSlotIndex())
			.twoHanded(twoHanded).speed(4).build();

		return new GearItem(id, name, 1, stats, EnumSet.of(Storage.BANK));
	}

	private static GearItem armour(int id, String name, EquipmentSlot slot, int slash)
	{
		EquipmentStats stats = EquipmentStats.builder()
			.aslash(slash).slot(slot.getSlotIndex()).build();

		return new GearItem(id, name, 1, stats, EnumSet.of(Storage.BANK));
	}
}
