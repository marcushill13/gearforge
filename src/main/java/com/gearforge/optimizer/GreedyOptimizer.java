package com.gearforge.optimizer;

import com.gearforge.data.EquipmentSlot;
import com.gearforge.data.GearItem;
import com.gearforge.data.GearStat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Singleton;

/**
 * Best setup for a single slot-independent bonus — defence against one style, or prayer.
 * <p>
 * For these stats each slot really is independent, so picking the best item per slot is not a
 * heuristic, it is optimal. The one exception is the weapon and shield pair: a two-handed weapon
 * blocks the shield, so both arrangements are costed and the better one wins. Picking the highest
 * weapon first and discovering the shield is now unavailable is exactly the bug this avoids.
 * <p>
 * Offensive DPS is <em>not</em> slot-independent and must not use this — that is what
 * {@code DpsOptimizer} is for.
 */
@Singleton
public class GreedyOptimizer
{
	public OptimizerResult best(Collection<GearItem> owned, GearStat stat)
	{
		Map<EquipmentSlot, List<GearItem>> bySlot = groupBySlot(owned);
		Map<EquipmentSlot, GearItem> chosen = new EnumMap<>(EquipmentSlot.class);
		List<String> reasons = new ArrayList<>();
		double total = 0.0;

		for (EquipmentSlot slot : EquipmentSlot.values())
		{
			if (slot == EquipmentSlot.WEAPON || slot == EquipmentSlot.SHIELD)
			{
				continue;
			}

			GearItem best = bestIn(bySlot.get(slot), stat, null);
			if (best != null)
			{
				chosen.put(slot, best);
				total += best.statValue(stat);
			}
		}

		total += chooseWeaponAndShield(bySlot, stat, chosen, reasons);

		return new OptimizerResult(chosen, total, reasons);
	}

	/**
	 * Costs "best one-hander plus best shield" against "best two-hander" and takes the winner.
	 *
	 * @return the combined value contributed by the weapon and shield slots
	 */
	private double chooseWeaponAndShield(
		Map<EquipmentSlot, List<GearItem>> bySlot,
		GearStat stat,
		Map<EquipmentSlot, GearItem> chosen,
		List<String> reasons)
	{
		List<GearItem> weapons = bySlot.get(EquipmentSlot.WEAPON);

		GearItem oneHanded = bestIn(weapons, stat, item -> !Constraints.isTwoHanded(item));
		GearItem twoHanded = bestIn(weapons, stat, Constraints::isTwoHanded);
		GearItem shield = bestIn(bySlot.get(EquipmentSlot.SHIELD), stat, null);

		double withShield = valueOf(oneHanded, stat) + valueOf(shield, stat);
		double withTwoHander = valueOf(twoHanded, stat);

		if (withTwoHander > withShield)
		{
			chosen.put(EquipmentSlot.WEAPON, twoHanded);
			if (shield != null)
			{
				reasons.add(twoHanded.getName() + " beats " + oneHanded(oneHanded)
					+ " with " + shield.getName() + ", even losing the shield slot");
			}

			return withTwoHander;
		}

		if (oneHanded != null)
		{
			chosen.put(EquipmentSlot.WEAPON, oneHanded);
		}

		if (shield != null)
		{
			chosen.put(EquipmentSlot.SHIELD, shield);
			if (twoHanded != null)
			{
				reasons.add("Kept the shield slot — " + shield.getName()
					+ " is worth more than upgrading to " + twoHanded.getName());
			}
		}

		return withShield;
	}

	private static String oneHanded(@Nullable GearItem item)
	{
		return item == null ? "an empty weapon slot" : item.getName();
	}

	/**
	 * The highest-scoring candidate, or null if none is worth wearing. Items scoring zero or less are
	 * rejected: an empty slot contributes nothing, so a negative item is strictly worse than nothing.
	 */
	@Nullable
	private static GearItem bestIn(
		@Nullable Collection<GearItem> candidates,
		GearStat stat,
		@Nullable java.util.function.Predicate<GearItem> filter)
	{
		if (candidates == null)
		{
			return null;
		}

		GearItem best = null;
		for (GearItem candidate : candidates)
		{
			if (filter != null && !filter.test(candidate))
			{
				continue;
			}

			if (candidate.statValue(stat) <= 0)
			{
				continue;
			}

			if (best == null || isBetter(candidate, best, stat))
			{
				best = candidate;
			}
		}

		return best;
	}

	/**
	 * Ties break on name then id, so repeated runs return the same setup.
	 */
	private static boolean isBetter(GearItem candidate, GearItem incumbent, GearStat stat)
	{
		int byValue = Double.compare(candidate.statValue(stat), incumbent.statValue(stat));
		if (byValue != 0)
		{
			return byValue > 0;
		}

		int byName = candidate.getName().compareTo(incumbent.getName());
		return byName != 0 ? byName < 0 : candidate.getItemId() < incumbent.getItemId();
	}

	private static double valueOf(@Nullable GearItem item, GearStat stat)
	{
		return item == null ? 0.0 : item.statValue(stat);
	}

	private static Map<EquipmentSlot, List<GearItem>> groupBySlot(Collection<GearItem> owned)
	{
		Map<EquipmentSlot, List<GearItem>> bySlot = new EnumMap<>(EquipmentSlot.class);

		for (GearItem item : owned)
		{
			EquipmentSlot slot = EquipmentSlot.fromSlotIndex(item.getStats().getSlot());
			if (slot != null)
			{
				bySlot.computeIfAbsent(slot, key -> new ArrayList<>()).add(item);
			}
		}

		return bySlot;
	}
}
