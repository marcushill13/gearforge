package com.gearforge;

import com.gearforge.data.EquipmentSlot;
import com.gearforge.setups.Setup;
import com.gearforge.setups.SetupCodec;
import com.gearforge.setups.SetupSource;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SetupCodecTest
{
	private static Setup sample()
	{
		Setup setup = Setup.named("Vorkath", SetupSource.MANUAL);
		setup.put(EquipmentSlot.HEAD, 3751);
		setup.put(EquipmentSlot.WEAPON, 22978);
		setup.put(EquipmentSlot.AMMO, 892);
		setup.setInventoryFrom(new int[]{385, -1, 385, 2434});
		return setup;
	}

	@Test
	public void roundTripsEverythingThatMatters()
	{
		Setup decoded = SetupCodec.decode(SetupCodec.encode(sample()));

		assertNotNull(decoded);
		assertEquals("Vorkath", decoded.getName());
		assertEquals(3751, decoded.getEquipment().get(EquipmentSlot.HEAD).getItemId());
		assertEquals(22978, decoded.getEquipment().get(EquipmentSlot.WEAPON).getItemId());
		assertEquals(892, decoded.getEquipment().get(EquipmentSlot.AMMO).getItemId());
		assertEquals(4, decoded.getInventory().size());
		assertEquals(385, decoded.getInventory().get(0).getItemId());
		assertNull(decoded.getInventory().get(1));
		assertEquals(2434, decoded.getInventory().get(3).getItemId());
	}

	@Test
	public void aSharedSetupIsMarkedAsShared()
	{
		Setup decoded = SetupCodec.decode(SetupCodec.encode(sample()));
		assertNotNull(decoded);
		assertEquals(SetupSource.SHARED, decoded.getSource());
	}

	@Test
	public void namesWithDelimitersSurviveIntact()
	{
		// A name containing the separators would break a naive format.
		Setup setup = Setup.named("Zulrah | mage, range", SetupSource.MANUAL);
		setup.put(EquipmentSlot.WEAPON, 11235);

		Setup decoded = SetupCodec.decode(SetupCodec.encode(setup));
		assertNotNull(decoded);
		assertEquals("Zulrah | mage, range", decoded.getName());
	}

	@Test
	public void codesAreShortEnoughToPasteIntoChat()
	{
		assertTrue(SetupCodec.encode(sample()).length() < 200);
	}

	@Test
	public void rubbishInputIsRejectedRatherThanThrowing()
	{
		assertNull(SetupCodec.decode(null));
		assertNull(SetupCodec.decode(""));
		assertNull(SetupCodec.decode("hello"));
		assertNull(SetupCodec.decode("GF1|bad"));
		assertNull(SetupCodec.decode("GF9|dg9y|3:22978|"));
		assertNull(SetupCodec.decode("GF1|!!!!|3:22978|"));
		assertNull(SetupCodec.decode("GF1|dg9y|notanumber|"));
		assertNull(SetupCodec.decode("GF1|dg9y||"));
	}

	@Test
	public void anEmptyInventorySectionIsFine()
	{
		Setup setup = Setup.named("Gear only", SetupSource.MANUAL);
		setup.put(EquipmentSlot.WEAPON, 4151);

		Setup decoded = SetupCodec.decode(SetupCodec.encode(setup));
		assertNotNull(decoded);
		assertEquals(1, decoded.size());
		assertTrue(decoded.getInventory().isEmpty());
	}
}
