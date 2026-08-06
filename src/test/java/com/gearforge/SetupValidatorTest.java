package com.gearforge;

import com.gearforge.data.EquipmentSlot;
import com.gearforge.setups.ItemRequirement;
import com.gearforge.setups.Setup;
import com.gearforge.setups.SetupSource;
import com.gearforge.setups.SetupValidator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SetupValidatorTest
{
	private static final IntUnaryOperator NO_FAMILIES = id -> id;

	/** Ids 101-103 are one variant family; everything else is its own. */
	private static final IntUnaryOperator FAMILIES = id -> id >= 101 && id <= 103 ? 100 : id;

	private static Map<Integer, Integer> owned(int... ids)
	{
		Map<Integer, Integer> owned = new HashMap<>();
		for (int id : ids)
		{
			owned.put(id, 1);
		}

		return owned;
	}

	@Test
	public void exactMatchIsHave()
	{
		assertEquals(
			SetupValidator.Status.HAVE,
			SetupValidator.check(ItemRequirement.of(5), owned(5), NO_FAMILIES));
	}

	@Test
	public void nothingOwnedIsMissing()
	{
		assertEquals(
			SetupValidator.Status.MISSING,
			SetupValidator.check(ItemRequirement.of(5), owned(6, 7), NO_FAMILIES));
	}

	@Test
	public void aDifferentVariantCountsAsVariantNotMissing()
	{
		// You asked for the undegraded piece and own a degraded one — that is worth saying, but it is
		// not the same as not having it.
		assertEquals(
			SetupValidator.Status.VARIANT,
			SetupValidator.check(ItemRequirement.of(101), owned(103), FAMILIES));
	}

	@Test
	public void exactMatchWinsOverVariantMatch()
	{
		assertEquals(
			SetupValidator.Status.HAVE,
			SetupValidator.check(ItemRequirement.of(101), owned(101, 103), FAMILIES));
	}

	@Test
	public void variantsAreIgnoredWhenTheRequirementIsNotFuzzy()
	{
		ItemRequirement strict = new ItemRequirement(101, 1, false);

		assertEquals(
			SetupValidator.Status.MISSING,
			SetupValidator.check(strict, owned(103), FAMILIES));
	}

	@Test
	public void quantityIsRespected()
	{
		Map<Integer, Integer> two = new HashMap<>();
		two.put(5, 2);

		assertEquals(
			SetupValidator.Status.HAVE,
			SetupValidator.check(new ItemRequirement(5, 2, true), two, NO_FAMILIES));
		assertEquals(
			SetupValidator.Status.MISSING,
			SetupValidator.check(new ItemRequirement(5, 3, true), two, NO_FAMILIES));
	}

	@Test
	public void validatesAWholeSetupAndCountsWhatIsSatisfied()
	{
		Setup setup = Setup.named("Test", SetupSource.MANUAL);
		setup.put(EquipmentSlot.HEAD, 1);
		setup.put(EquipmentSlot.BODY, 2);
		setup.put(EquipmentSlot.WEAPON, 101);

		Map<EquipmentSlot, SetupValidator.Status> statuses =
			SetupValidator.validate(setup, owned(1, 103), FAMILIES);

		assertEquals(SetupValidator.Status.HAVE, statuses.get(EquipmentSlot.HEAD));
		assertEquals(SetupValidator.Status.MISSING, statuses.get(EquipmentSlot.BODY));
		assertEquals(SetupValidator.Status.VARIANT, statuses.get(EquipmentSlot.WEAPON));

		// A variant counts as satisfied — you can go and get it.
		assertEquals(2, SetupValidator.satisfiedCount(statuses));
	}

	@Test
	public void inventoryDuplicatesNeedTheFullQuantity()
	{
		// Four sharks asked for, one owned: every one of those slots is missing, not just three.
		Setup setup = Setup.named("Food", SetupSource.MANUAL);
		setup.setInventoryFrom(new int[]{10, 10, 10, 10});

		Map<Integer, Integer> onlyOne = new HashMap<>();
		onlyOne.put(10, 1);

		Map<Integer, SetupValidator.Status> statuses =
			SetupValidator.validateInventory(setup, onlyOne, NO_FAMILIES);

		assertEquals(4, statuses.size());
		assertEquals(0, SetupValidator.countSatisfied(statuses.values()));

		Map<Integer, Integer> allFour = new HashMap<>();
		allFour.put(10, 4);
		assertEquals(4, SetupValidator.countSatisfied(
			SetupValidator.validateInventory(setup, allFour, NO_FAMILIES).values()));
	}

	@Test
	public void inventoryKeepsSlotPositionsIncludingGaps()
	{
		Setup setup = Setup.named("Gapped", SetupSource.MANUAL);
		setup.setInventoryFrom(new int[]{5, -1, 7, -1});

		// Trailing empties are dropped, but the gap in the middle is real and preserved.
		assertEquals(3, setup.getInventory().size());
		assertEquals(null, setup.getInventory().get(1));
		assertEquals(2, setup.inventoryCount());

		Map<Integer, SetupValidator.Status> statuses =
			SetupValidator.validateInventory(setup, owned(5, 7), NO_FAMILIES);

		assertEquals(2, statuses.size());
		assertEquals(SetupValidator.Status.HAVE, statuses.get(0));
		assertEquals(SetupValidator.Status.HAVE, statuses.get(2));
	}

	@Test
	public void setupSizeCountsEquipmentAndInventoryTogether()
	{
		Setup setup = Setup.named("Both", SetupSource.MANUAL);
		setup.put(EquipmentSlot.WEAPON, 1);
		setup.setInventoryFrom(new int[]{2, 3, -1});

		assertEquals(3, setup.size());
	}

	@Test
	public void anEmptySetupValidatesToNothingRatherThanFailing()
	{
		Setup empty = Setup.named("Empty", SetupSource.MANUAL);
		assertEquals(0, SetupValidator.validate(empty, owned(1, 2), FAMILIES).size());
	}
}
