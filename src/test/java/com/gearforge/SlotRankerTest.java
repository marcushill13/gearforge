package com.gearforge;

import com.gearforge.data.EquipmentSlot;
import com.gearforge.data.EquipmentStats;
import com.gearforge.data.GearItem;
import com.gearforge.data.GearStat;
import com.gearforge.data.SlotRanker;
import com.gearforge.data.Storage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SlotRankerTest
{
	private static final IntUnaryOperator NO_GROUPING = id -> id;

	@Test
	public void ranksBestFirst()
	{
		List<GearItem> owned = Arrays.asList(
			amulet(1, "Amulet of glory", 10),
			amulet(2, "Amulet of fury", 15),
			amulet(3, "Amulet of strength", 10));

		List<GearItem> ranked = SlotRanker.rank(
			owned, EquipmentSlot.AMULET, GearStat.SLASH_ATTACK, false, true, NO_GROUPING);

		assertEquals(Arrays.asList("Amulet of fury", "Amulet of glory", "Amulet of strength"), names(ranked));
	}

	@Test
	public void excludesItemsFromOtherSlots()
	{
		List<GearItem> owned = Arrays.asList(
			amulet(1, "Amulet of fury", 15),
			item(2, "Rune platebody", EquipmentSlot.BODY, 0, 0));

		List<GearItem> ranked = SlotRanker.rank(
			owned, EquipmentSlot.AMULET, GearStat.SLASH_ATTACK, false, true, NO_GROUPING);

		assertEquals(Collections.singletonList("Amulet of fury"), names(ranked));
	}

	@Test
	public void hidesNonPositiveBonusesWhenAsked()
	{
		List<GearItem> owned = Arrays.asList(
			amulet(1, "Amulet of fury", 15),
			amulet(2, "Amulet of nothing", 0),
			amulet(3, "Cursed amulet", -5));

		List<GearItem> hidden = SlotRanker.rank(
			owned, EquipmentSlot.AMULET, GearStat.SLASH_ATTACK, false, true, NO_GROUPING);
		assertEquals(Collections.singletonList("Amulet of fury"), names(hidden));

		List<GearItem> shown = SlotRanker.rank(
			owned, EquipmentSlot.AMULET, GearStat.SLASH_ATTACK, false, false, NO_GROUPING);
		assertEquals(3, shown.size());
		assertEquals("Cursed amulet", shown.get(2).getName());
	}

	@Test
	public void groupingKeepsTheBestMemberOfEachFamily()
	{
		// Every degradation state maps to family 100; the undegraded one is strongest.
		List<GearItem> owned = Arrays.asList(
			item(101, "Ahrim's robetop", EquipmentSlot.BODY, 0, 30),
			item(102, "Ahrim's robetop 75", EquipmentSlot.BODY, 0, 25),
			item(103, "Ahrim's robetop 25", EquipmentSlot.BODY, 0, 10),
			item(201, "Mystic robe top", EquipmentSlot.BODY, 0, 20));

		IntUnaryOperator families = id -> id >= 101 && id <= 103 ? 100 : id;

		List<GearItem> ranked = SlotRanker.rank(
			owned, EquipmentSlot.BODY, GearStat.MAGIC_ATTACK, true, true, families);

		assertEquals(Arrays.asList("Ahrim's robetop", "Mystic robe top"), names(ranked));
	}

	@Test
	public void groupingNeverHidesAStrongerVariant()
	{
		// The stronger member is listed second — grouping must still surface it, not the first seen.
		List<GearItem> owned = Arrays.asList(
			item(101, "Ring of suffering", EquipmentSlot.RING, 0, 0, 20),
			item(102, "Ring of suffering (i)", EquipmentSlot.RING, 0, 0, 40));

		List<GearItem> ranked = SlotRanker.rank(
			owned, EquipmentSlot.RING, GearStat.MAGIC_DEFENCE, true, true, id -> 100);

		assertEquals(1, ranked.size());
		assertEquals("Ring of suffering (i)", ranked.get(0).getName());
	}

	@Test
	public void rankingIsStableForTiedItems()
	{
		List<GearItem> owned = new ArrayList<>(Arrays.asList(
			amulet(3, "Zebra amulet", 10),
			amulet(1, "Apple amulet", 10),
			amulet(2, "Mango amulet", 10)));

		List<String> first = names(SlotRanker.rank(
			owned, EquipmentSlot.AMULET, GearStat.SLASH_ATTACK, false, true, NO_GROUPING));

		Collections.reverse(owned);
		List<String> second = names(SlotRanker.rank(
			owned, EquipmentSlot.AMULET, GearStat.SLASH_ATTACK, false, true, NO_GROUPING));

		assertEquals(first, second);
		assertEquals(Arrays.asList("Apple amulet", "Mango amulet", "Zebra amulet"), first);
	}

	@Test
	public void emptyBankRanksToEmptyListRatherThanFailing()
	{
		List<GearItem> ranked = SlotRanker.rank(
			Collections.emptyList(), EquipmentSlot.WEAPON, GearStat.STAB_ATTACK, true, true, NO_GROUPING);

		assertTrue(ranked.isEmpty());
	}

	@Test
	public void magicDamageFormatsAsAPercentage()
	{
		assertEquals("+5%", GearStat.MAGIC_DAMAGE.format(5.0));
		assertEquals("+0.2%", GearStat.MAGIC_DAMAGE.format(0.2));
		assertEquals("+15", GearStat.STAB_ATTACK.format(15));
		assertEquals("-5", GearStat.STAB_ATTACK.format(-5));
	}

	@Test
	public void arrowsRankEmptyOnRangedAttackAndSuggestRangedStrength()
	{
		// Real OSRS data: arrows carry ranged strength and no ranged attack at all, so asking for
		// ranged attack in the ammo slot correctly finds nothing. The suggestion is what rescues it.
		List<GearItem> owned = Arrays.asList(
			arrow(892, "Rune arrow", 49),
			arrow(11212, "Dragon arrow", 60),
			arrow(882, "Bronze arrow", 7));

		List<GearItem> ranked = SlotRanker.rank(
			owned, EquipmentSlot.AMMO, GearStat.RANGED_ATTACK, true, true, NO_GROUPING);
		assertTrue(ranked.isEmpty());

		List<GearStat> suggestions = SlotRanker.suggestStats(
			SlotRanker.positiveCounts(owned, EquipmentSlot.AMMO), GearStat.RANGED_ATTACK, 2);
		assertEquals(Collections.singletonList(GearStat.RANGED_STRENGTH), suggestions);

		// And the stat they should have picked does rank them.
		List<GearItem> byStrength = SlotRanker.rank(
			owned, EquipmentSlot.AMMO, GearStat.RANGED_STRENGTH, true, true, NO_GROUPING);
		assertEquals(Arrays.asList("Dragon arrow", "Rune arrow", "Bronze arrow"), names(byStrength));
	}

	@Test
	public void positiveCountsIgnoresOtherSlots()
	{
		List<GearItem> owned = Arrays.asList(
			arrow(892, "Rune arrow", 49),
			amulet(1, "Amulet of fury", 15));

		assertEquals(
			Integer.valueOf(1),
			SlotRanker.positiveCounts(owned, EquipmentSlot.AMMO).get(GearStat.RANGED_STRENGTH));
		assertEquals(
			null,
			SlotRanker.positiveCounts(owned, EquipmentSlot.AMMO).get(GearStat.SLASH_ATTACK));
	}

	@Test
	public void suggestionsAreEmptyWhenTheSlotHasNothingAtAll()
	{
		List<GearStat> suggestions = SlotRanker.suggestStats(
			SlotRanker.positiveCounts(Collections.emptyList(), EquipmentSlot.AMMO),
			GearStat.RANGED_ATTACK,
			2);

		assertTrue(suggestions.isEmpty());
	}

	private static GearItem arrow(int id, String name, int rangedStrength)
	{
		EquipmentStats stats = EquipmentStats.builder()
			.rangedStrength(rangedStrength)
			.slot(EquipmentSlot.AMMO.getSlotIndex())
			.build();

		return new GearItem(id, name, 1000, stats, EnumSet.of(Storage.BANK));
	}

	private static List<String> names(List<GearItem> items)
	{
		return items.stream().map(GearItem::getName).collect(Collectors.toList());
	}

	private static GearItem amulet(int id, String name, int slashAttack)
	{
		return item(id, name, EquipmentSlot.AMULET, slashAttack, 0);
	}

	private static GearItem item(int id, String name, EquipmentSlot slot, int slashAttack, int magicAttack)
	{
		return item(id, name, slot, slashAttack, magicAttack, 0);
	}

	private static GearItem item(int id, String name, EquipmentSlot slot, int slashAttack, int magicAttack, int magicDefence)
	{
		EquipmentStats stats = EquipmentStats.builder()
			.aslash(slashAttack)
			.amagic(magicAttack)
			.dmagic(magicDefence)
			.slot(slot.getSlotIndex())
			.build();

		return new GearItem(id, name, 1, stats, EnumSet.of(Storage.BANK));
	}
}
