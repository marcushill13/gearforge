package com.gearforge;

import com.gearforge.data.ItemRequirements;
import com.gearforge.data.PlayerLevels;
import com.google.gson.Gson;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Guards the generated requirements resource as well as the lookup logic — if the resource goes
 * missing or its shape changes, these fail rather than the plugin silently offering unusable gear.
 */
public class ItemRequirementsTest
{
	private final ItemRequirements requirements = new ItemRequirements(new Gson());

	private static PlayerLevels levels(int attack, int defence, int ranged)
	{
		return PlayerLevels.builder()
			.attack(attack).strength(attack).defence(defence).ranged(ranged).magic(1)
			.prayer(1).hitpoints(10).slayer(1)
			.build();
	}

	@Test
	public void theGeneratedResourceIsPresentAndPopulated()
	{
		// A representative spread rather than a single item, so a truncated resource is caught.
		assertTrue(requirements.isKnown(ItemID.ABYSSAL_WHIP));
		assertTrue(requirements.isKnown(ItemID.DRAGON_SCIMITAR));
	}

	@Test
	public void whipNeeds70Attack()
	{
		assertEquals(Integer.valueOf(70), requirements.requirementsFor(ItemID.ABYSSAL_WHIP).get("attack"));

		assertFalse(requirements.canEquip(ItemID.ABYSSAL_WHIP, levels(69, 99, 99)));
		assertTrue(requirements.canEquip(ItemID.ABYSSAL_WHIP, levels(70, 99, 99)));
	}

	@Test
	public void shortfallIsDescribedInGameTerms()
	{
		assertEquals("Needs 70 Attack", requirements.describeShortfall(ItemID.ABYSSAL_WHIP, levels(50, 99, 99)));
		assertNull(requirements.describeShortfall(ItemID.ABYSSAL_WHIP, levels(99, 99, 99)));
	}

	@Test
	public void unknownItemsAreTreatedAsEquippableRatherThanHidden()
	{
		// The source dataset is stale, so newer gear is absent. Hiding a player's best weapon because
		// of a data gap is worse than mentioning one they might not be able to wield.
		int unknown = 999999;

		assertFalse(requirements.isKnown(unknown));
		assertTrue(requirements.canEquip(unknown, levels(1, 1, 1)));
		assertTrue(requirements.requirementsFor(unknown).isEmpty());
	}

	@Test
	public void aLowLevelAccountIsBlockedFromHighTierGear()
	{
		PlayerLevels fresh = levels(1, 1, 1);

		assertFalse(requirements.canEquip(ItemID.ABYSSAL_WHIP, fresh));
		assertFalse(requirements.canEquip(ItemID.DRAGON_SCIMITAR, fresh));
	}
}
