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

	/**
	 * The reported bug, and the reason the wiki is read at all: blood moon armour needs 75 Strength,
	 * osrsbox-db has never heard of it, and an account without the Strength was being told it was
	 * best in slot.
	 */
	@Test
	public void gearReleasedAfterTheOldDatasetIsStillEnforced()
	{
		PlayerLevels noStrength = PlayerLevels.builder()
			.attack(99).strength(70).defence(99).ranged(99).magic(99)
			.prayer(99).hitpoints(99).slayer(99)
			.build();

		for (int id : new int[]{ItemID.BLOOD_MOON_HELM, ItemID.BLOOD_MOON_CHESTPLATE,
			ItemID.BLOOD_MOON_TASSETS})
		{
			assertTrue("id " + id + " should have known requirements", requirements.isKnown(id));
			assertEquals(Integer.valueOf(75), requirements.requirementsFor(id).get("strength"));
			assertFalse("id " + id + " needs 75 Strength", requirements.canEquip(id, noStrength));
		}
	}

	@Test
	public void theRestOfTheModernBankIsCoveredToo()
	{
		assertEquals(Integer.valueOf(80), requirements.requirementsFor(ItemID.TORVA_HELM).get("defence"));
		assertEquals(Integer.valueOf(80), requirements.requirementsFor(ItemID.MASORI_BODY).get("ranged"));
		assertEquals(Integer.valueOf(85), requirements.requirementsFor(ItemID.TUMEKENS_SHADOW).get("magic"));
		assertEquals(Integer.valueOf(78), requirements.requirementsFor(ItemID.OATHPLATE_HELM).get("defence"));
		assertEquals(Integer.valueOf(75), requirements.requirementsFor(ItemID.VOIDWAKER).get("attack"));
		assertEquals(Integer.valueOf(82), requirements.requirementsFor(ItemID.OSMUMTENS_FANG).get("attack"));
	}

	/**
	 * Where the two sources disagree the wiki is the current one: osrsbox has the kodai wand at 75
	 * Magic and the bow of faerdhinen at 75 Ranged, and both are eighty.
	 */
	@Test
	public void aStaleRequirementDoesNotWinOverTheCurrentOne()
	{
		assertEquals(Integer.valueOf(80), requirements.requirementsFor(ItemID.KODAI_WAND).get("magic"));
		assertEquals(Integer.valueOf(80), requirements.requirementsFor(ItemID.BOW_OF_FAERDHINEN).get("ranged"));
	}

	/**
	 * An ornament kit does not lower a requirement. Recoloured and ornamented copies are left out of
	 * both sources, and falling back to the family is what keeps them from reading as unrestricted.
	 */
	@Test
	public void anOrnamentedCopyInheritsTheRequirementOfWhatItIs()
	{
		assertEquals(Integer.valueOf(60),
			requirements.requirementsFor(ItemID.DRAGON_CLAWS_ORNAMENT).get("attack"));
		assertEquals(Integer.valueOf(10),
			requirements.requirementsFor(ItemID.SLAYER_HELM_ARAXYTE).get("defence"));
	}

	@Test
	public void aLowLevelAccountIsBlockedFromHighTierGear()
	{
		PlayerLevels fresh = levels(1, 1, 1);

		assertFalse(requirements.canEquip(ItemID.ABYSSAL_WHIP, fresh));
		assertFalse(requirements.canEquip(ItemID.DRAGON_SCIMITAR, fresh));
	}
}
