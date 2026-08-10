package com.gearforge;

import com.gearforge.data.EquipmentStats;
import com.gearforge.dps.CombatContext;
import com.gearforge.dps.CombatStyle;
import com.gearforge.dps.DpsEngine;
import com.gearforge.dps.PoweredStaff;
import com.gearforge.dps.Target;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A powered staff carries its own attack and casts nothing. GearForge assumed Ice Barrage for every
 * magic setup, which judged a trident on a spell it cannot cast.
 */
public class PoweredStaffTest
{
	private final DpsEngine engine = new DpsEngine();

	@Test
	public void eachStaffScalesFromTheMagicLevel()
	{
		assertEquals(28, PoweredStaff.TRIDENT_OF_THE_SEAS.maxHit(99));
		assertEquals(31, PoweredStaff.TRIDENT_OF_THE_SWAMP.maxHit(99));
		assertEquals(33, PoweredStaff.SANGUINESTI_STAFF.maxHit(99));
		assertEquals(34, PoweredStaff.TUMEKENS_SHADOW.maxHit(99));
		assertEquals(24, PoweredStaff.WARPED_SCEPTRE.maxHit(99));
	}

	@Test
	public void aLowerMagicLevelHitsForLess()
	{
		assertTrue(PoweredStaff.SANGUINESTI_STAFF.maxHit(99) > PoweredStaff.SANGUINESTI_STAFF.maxHit(75));

		// Never below one, however low the level.
		assertEquals(1, PoweredStaff.TRIDENT_OF_THE_SEAS.maxHit(1));
	}

	@Test
	public void chargedAndUnchargedFormsBothResolve()
	{
		assertEquals(PoweredStaff.SANGUINESTI_STAFF, PoweredStaff.forItem(ItemID.SANGUINESTI_STAFF));
		assertEquals(PoweredStaff.SANGUINESTI_STAFF,
			PoweredStaff.forItem(ItemID.SANGUINESTI_STAFF_UNCHARGED));
		assertEquals(PoweredStaff.TRIDENT_OF_THE_SEAS, PoweredStaff.forItem(ItemID.TOTS_CHARGED));

		assertNull(PoweredStaff.forItem(ItemID.ABYSSAL_WHIP));
	}

	/**
	 * Tumeken's shadow triples every magic bonus worn, which is most of what it is for and would
	 * otherwise leave it looking barely better than a sanguinesti.
	 */
	@Test
	public void theShadowTriplesMagicDamageBonuses()
	{
		int withShadow = engine.maxHit(context(PoweredStaff.TUMEKENS_SHADOW, 20), 0);
		int withSang = engine.maxHit(context(PoweredStaff.SANGUINESTI_STAFF, 20), 0);

		// Same 20% bonus on the gear, but the shadow reads it as 60%.
		assertEquals(34 + 34 * 60 / 100, withShadow);
		assertEquals(33 + 33 * 20 / 100, withSang);
	}

	/**
	 * The whole point: the staff's own maximum replaces the assumed spell entirely.
	 */
	@Test
	public void theStaffsMaximumReplacesTheAssumedSpell()
	{
		int barrageAssumption = engine.maxHit(context(null, 0), 0);
		int trident = engine.maxHit(context(PoweredStaff.TRIDENT_OF_THE_SEAS, 0), 0);

		assertEquals(30, barrageAssumption);
		assertEquals(28, trident);
	}

	private static CombatContext context(PoweredStaff staff, int magicDamagePercent)
	{
		return CombatContext.builder()
			.magicLevel(99)
			.style(CombatStyle.MAGIC)
			.equipment(EquipmentStats.builder().magicDamage(magicDamagePercent).build())
			.target(Target.dummy())
			.baseSpellDamage(30)
			.poweredStaffType(staff)
			.poweredStaff(staff != null)
			.weaponSpeedTicks(4)
			.build();
	}
}
