package com.gearforge;

import com.gearforge.data.EquipmentSlot;
import com.gearforge.data.Monster;
import com.gearforge.data.MonsterRepository;
import com.gearforge.data.SlayerGear;
import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Some slayer monsters cannot be fought without a specific item. A best-in-slot answer that puts a
 * helm of neitiznot where a nose peg belongs is not suboptimal, it is unusable — and the optimizer had
 * no idea these existed.
 */
public class SlayerGearTest
{
	private final MonsterRepository monsters = new MonsterRepository(new Gson());

	@Test
	public void aSpectreDemandsANosePegOrASlayerHelmet()
	{
		SlayerGear.Requirement requirement = SlayerGear.forMonster(named("Aberrant spectre"));

		assertNotNull(requirement);
		assertTrue(requirement.isWorn());
		assertEquals(EquipmentSlot.HEAD, requirement.getSlot());
		assertTrue("A bare nose peg must be accepted", requirement.getAccepted().contains(4168));
		assertTrue("So must a slayer helmet", requirement.getAccepted().contains(11864));
	}

	/**
	 * A slayer helmet does not stop a basilisk's gaze — only the shields do, which is why this one
	 * cannot share the head-slot handling.
	 */
	@Test
	public void aBasiliskDemandsAShieldRatherThanAHelmet()
	{
		SlayerGear.Requirement requirement = SlayerGear.forMonster(named("Basilisk Knight"));

		assertNotNull(requirement);
		assertEquals(EquipmentSlot.SHIELD, requirement.getSlot());
		assertTrue("V's shield", requirement.getAccepted().contains(24266));
		assertTrue("Mirror shield", requirement.getAccepted().contains(4156));
		assertFalse("A slayer helmet is no help here", requirement.getAccepted().contains(11864));
	}

	/**
	 * A carried item takes no slot, so the reasoning is the only place it can be said.
	 */
	@Test
	public void aCarriedItemIsSaidRatherThanWorn()
	{
		SlayerGear.Requirement gargoyle = SlayerGear.forMonster(named("Gargoyle"));

		assertNotNull(gargoyle);
		assertFalse(gargoyle.isWorn());
		assertNull(gargoyle.getSlot());
		assertTrue(gargoyle.getNote().toLowerCase().contains("rock hammer"));

		SlayerGear.Requirement lizard = SlayerGear.forMonster(named("Desert Lizard"));
		assertNotNull(lizard);
		assertTrue(lizard.getNote().toLowerCase().contains("ice cooler"));
	}

	@Test
	public void everyMonsterWithARequirementIsInTheData()
	{
		for (String name : new String[]{
			"Aberrant spectre", "Deviant spectre", "Banshee", "Twisted Banshee", "Dust devil",
			"Basilisk", "Basilisk Knight", "Cave horror", "Killerwatt", "Fever spider",
			"Gargoyle", "Rockslug", "Desert Lizard", "Kurask", "Turoth"})
		{
			assertNotNull(name + " is missing from the monster data", named(name));
			assertNotNull(name + " has no requirement", SlayerGear.forMonster(named(name)));
		}
	}

	@Test
	public void anOrdinaryMonsterDemandsNothing()
	{
		assertNull(SlayerGear.forMonster(named("Zulrah")));

		Monster crab = new Monster();
		crab.setName("Ammonite Crab");
		assertNull(SlayerGear.forMonster(crab));
		assertNull(SlayerGear.forMonster(null));
	}

	private Monster named(String name)
	{
		return monsters.all().stream()
			.filter(monster -> monster.getName().equalsIgnoreCase(name))
			.findFirst()
			.orElse(null);
	}
}
