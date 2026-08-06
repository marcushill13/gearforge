package com.gearforge;

import com.gearforge.data.EquipmentSlot;
import com.gearforge.setups.InventorySetupsImporter;
import com.gearforge.setups.Setup;
import com.gearforge.setups.SetupSource;
import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * The sample below is a real Inventory Setups record, taken verbatim from a live RuneLite config, so
 * the importer is tested against the format as it actually ships rather than an invented one.
 */
public class InventorySetupsImporterTest
{
	private final InventorySetupsImporter importer =
		new InventorySetupsImporter(null, new Gson());

	private static final String BIRDHOUSE = "{\"inv\":["
		+ "{\"id\":8792},{\"id\":8792},{\"id\":8792},{\"id\":6333},{\"id\":6333},{\"id\":6333},"
		+ "{\"id\":6333},{\"id\":6333},{\"id\":2347},{\"id\":8794},{\"id\":1755},"
		+ "{\"id\":557,\"q\":11312},{\"id\":556,\"q\":34523},{\"id\":563,\"q\":1962},"
		+ "null,null,null,null,null,null,null,null,null,null,null,null,null,null],"
		+ "\"eq\":[{\"id\":3751},null,{\"id\":1704},{\"id\":772},{\"id\":10551},{\"id\":33101},null,"
		+ "{\"id\":1079},null,{\"id\":11972},{\"id\":4131},null,{\"id\":2572},{\"id\":20232}],"
		+ "\"name\":\"Birdhouse\",\"hc\":\"#FFFF0000\",\"fb\":true}";

	@Test
	public void importsTheNameAndMarksItAsImported()
	{
		Setup setup = importer.parse(BIRDHOUSE);

		assertNotNull(setup);
		assertEquals("Birdhouse", setup.getName());
		assertEquals(SetupSource.IMPORTED, setup.getSource());
	}

	@Test
	public void equipmentArrayIsIndexedByEquipmentSlot()
	{
		Setup setup = importer.parse(BIRDHOUSE);
		assertNotNull(setup);

		// Index 0 is head, 3 is weapon, 13 is ammo — a mis-indexed import would silently put the
		// weapon on your head, so these are pinned explicitly.
		assertEquals(3751, setup.getEquipment().get(EquipmentSlot.HEAD).getItemId());
		assertEquals(1704, setup.getEquipment().get(EquipmentSlot.AMULET).getItemId());
		assertEquals(772, setup.getEquipment().get(EquipmentSlot.WEAPON).getItemId());
		assertEquals(10551, setup.getEquipment().get(EquipmentSlot.BODY).getItemId());
		assertEquals(1079, setup.getEquipment().get(EquipmentSlot.LEGS).getItemId());
		assertEquals(20232, setup.getEquipment().get(EquipmentSlot.AMMO).getItemId());

		// Null entries stay empty rather than shifting everything after them.
		assertNull(setup.getEquipment().get(EquipmentSlot.CAPE));
	}

	@Test
	public void inventoryKeepsOrderAndDuplicates()
	{
		Setup setup = importer.parse(BIRDHOUSE);
		assertNotNull(setup);

		assertEquals(14, setup.inventoryCount());
		assertEquals(8792, setup.getInventory().get(0).getItemId());
		assertEquals(8792, setup.getInventory().get(2).getItemId());
		assertEquals(6333, setup.getInventory().get(3).getItemId());
		assertEquals(563, setup.getInventory().get(13).getItemId());

		// Trailing nulls are trimmed, so the list stops after the last real item.
		assertEquals(14, setup.getInventory().size());
	}

	@Test
	public void countsEquipmentAndInventoryTogether()
	{
		Setup setup = importer.parse(BIRDHOUSE);
		assertNotNull(setup);

		// 10 equipped pieces plus 14 inventory items. The cape slot is empty in this record, and
		// indices 6, 8 and 11 are the cosmetic arms/hair/jaw slots that no real item occupies.
		assertEquals(10, setup.getEquipment().size());
		assertEquals(24, setup.size());
	}

	@Test
	public void malformedOrEmptyRecordsAreSkippedRatherThanCrashing()
	{
		assertNull(importer.parse("not json at all"));
		assertNull(importer.parse("{}"));
		assertNull(importer.parse("{\"name\":\"Empty\",\"inv\":[],\"eq\":[]}"));
	}

	@Test
	public void aSetupWithOnlyInventoryStillImports()
	{
		Setup setup = importer.parse(
			"{\"inv\":[{\"id\":385},null,{\"id\":385}],\"eq\":[],\"name\":\"Food\"}");

		assertNotNull(setup);
		assertEquals(2, setup.inventoryCount());
		assertEquals("Food", setup.getName());
	}
}
