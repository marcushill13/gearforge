package com.gearforge;

import com.gearforge.data.EquipmentSlot;
import com.gearforge.setups.Setup;
import com.gearforge.setups.SetupSource;
import com.google.gson.Gson;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.reflect.TypeToken;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Saved setups have to survive a client restart.
 * <p>
 * They did not: Gson writes an enum map key with {@code String.valueOf}, which is the display name,
 * but reads it back through the enum adapter, which matches on the constant name. "Hands" and "Feet"
 * both resolved to null, Gson rejected the duplicate null key, and the store caught the exception and
 * started fresh — so every setup vanished on the next launch, imported ones included.
 */
public class SetupSerializationTest
{
	private final Gson gson = new Gson();

	@Test
	public void everySlotSurvivesARoundTrip()
	{
		Setup setup = Setup.named("Round trip", SetupSource.IMPORTED);

		int itemId = 100;
		for (EquipmentSlot slot : EquipmentSlot.values())
		{
			setup.put(slot, itemId++);
		}

		Setup restored = gson.fromJson(gson.toJson(setup), Setup.class);
		assertNotNull(restored);

		int expected = 100;
		for (EquipmentSlot slot : EquipmentSlot.values())
		{
			assertNotNull("Lost the " + slot + " slot on reload", restored.getEquipment().get(slot));
			assertEquals(expected++, restored.getEquipment().get(slot).getItemId());
		}
	}

	/**
	 * The pair that actually broke it. Their display names differ from their constant names, which is
	 * what made the mismatch produce two null keys rather than one silently wrong slot.
	 */
	@Test
	public void slotsWhoseDisplayNameDiffersFromTheirConstantNameStillLoad()
	{
		Type mapType = new TypeToken<Map<EquipmentSlot, Integer>>()
		{
		}.getType();

		Map<EquipmentSlot, Integer> legacy =
			gson.fromJson("{\"Hands\":1,\"Feet\":2,\"Neck\":3}", mapType);

		assertEquals(Integer.valueOf(1), legacy.get(EquipmentSlot.GLOVES));
		assertEquals(Integer.valueOf(2), legacy.get(EquipmentSlot.BOOTS));
		assertEquals(Integer.valueOf(3), legacy.get(EquipmentSlot.AMULET));
	}

	/**
	 * Setups written before the fix are on players' disks already, so both spellings have to load.
	 */
	@Test
	public void theConstantNameIsAcceptedTooSoNothingOnDiskIsLost()
	{
		Type mapType = new TypeToken<Map<EquipmentSlot, Integer>>()
		{
		}.getType();

		Map<EquipmentSlot, Integer> byConstantName =
			gson.fromJson("{\"GLOVES\":1,\"BOOTS\":2,\"AMULET\":3}", mapType);

		assertEquals(Integer.valueOf(1), byConstantName.get(EquipmentSlot.GLOVES));
		assertEquals(Integer.valueOf(2), byConstantName.get(EquipmentSlot.BOOTS));
		assertEquals(Integer.valueOf(3), byConstantName.get(EquipmentSlot.AMULET));
	}

	/**
	 * Guards the coupling the fix relies on: the annotation and the display name must stay equal, or
	 * writes and reads drift apart again.
	 */
	@Test
	public void everySlotWritesAKeyItCanReadBack()
	{
		Type mapType = new TypeToken<Map<EquipmentSlot, Integer>>()
		{
		}.getType();

		Map<EquipmentSlot, Integer> written = new LinkedHashMap<>();
		for (EquipmentSlot slot : EquipmentSlot.values())
		{
			written.put(slot, slot.getSlotIndex());
		}

		Map<EquipmentSlot, Integer> read = gson.fromJson(gson.toJson(written, mapType), mapType);
		assertEquals(written, read);
	}
}
