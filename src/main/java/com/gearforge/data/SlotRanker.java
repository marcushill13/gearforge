package com.gearforge.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;

/**
 * Ranks owned items for one slot by one bonus.
 * <p>
 * Deliberately pure and free of RuneLite types so it can be unit tested without a client. The variant
 * grouping function is passed in for the same reason.
 */
public final class SlotRanker
{
	private SlotRanker()
	{
	}

	/**
	 * @param owned            every equippable item the player owns
	 * @param slot             the slot to rank
	 * @param stat             the bonus to rank by
	 * @param groupVariants    collapse variant families to their single best member
	 * @param hideNonPositive  drop items whose value in this stat is zero or negative
	 * @param variantGrouper   maps an item id to its variant family id
	 * @return items for that slot, best first
	 */
	public static List<GearItem> rank(
		Collection<GearItem> owned,
		EquipmentSlot slot,
		GearStat stat,
		boolean groupVariants,
		boolean hideNonPositive,
		IntUnaryOperator variantGrouper)
	{
		List<GearItem> candidates = new ArrayList<>();
		for (GearItem item : owned)
		{
			if (item.getStats().getSlot() != slot.getSlotIndex())
			{
				continue;
			}

			if (hideNonPositive && item.statValue(stat) <= 0)
			{
				continue;
			}

			candidates.add(item);
		}

		if (groupVariants)
		{
			candidates = keepBestPerVariantFamily(candidates, stat, variantGrouper);
		}

		candidates.sort(rankingOrder(stat));
		return candidates;
	}

	/**
	 * Counts, for one slot, how many owned items give a positive value in each bonus.
	 * <p>
	 * Used to turn a dead end into a signpost: an empty ranking can then say which bonus is actually
	 * worth looking at for that slot rather than just showing nothing.
	 */
	public static Map<GearStat, Integer> positiveCounts(Collection<GearItem> owned, EquipmentSlot slot)
	{
		Map<GearStat, Integer> counts = new EnumMap<>(GearStat.class);

		for (GearItem item : owned)
		{
			if (item.getStats().getSlot() != slot.getSlotIndex())
			{
				continue;
			}

			for (GearStat stat : GearStat.values())
			{
				if (item.statValue(stat) > 0)
				{
					counts.merge(stat, 1, Integer::sum);
				}
			}
		}

		return counts;
	}

	/**
	 * The bonuses with the most owned items for a slot, best first, excluding one already being viewed.
	 */
	public static List<GearStat> suggestStats(Map<GearStat, Integer> counts, GearStat exclude, int limit)
	{
		return counts.entrySet().stream()
			.filter(e -> e.getKey() != exclude && e.getValue() > 0)
			.sorted(Map.Entry.<GearStat, Integer>comparingByValue().reversed())
			.limit(limit)
			.map(Map.Entry::getKey)
			.collect(Collectors.toList());
	}

	/**
	 * Keeps only the highest-scoring member of each variant family. Picking the best member rather than
	 * an arbitrary one means grouping can never hide a genuinely better item.
	 */
	private static List<GearItem> keepBestPerVariantFamily(
		List<GearItem> candidates,
		GearStat stat,
		IntUnaryOperator variantGrouper)
	{
		Map<Integer, GearItem> best = new LinkedHashMap<>();
		Comparator<GearItem> order = rankingOrder(stat);

		for (GearItem item : candidates)
		{
			int family = variantGrouper.applyAsInt(item.getItemId());
			GearItem incumbent = best.get(family);
			if (incumbent == null || order.compare(item, incumbent) < 0)
			{
				best.put(family, item);
			}
		}

		return new ArrayList<>(best.values());
	}

	/**
	 * Best first. Ties break on name then id so the list is stable between rebuilds.
	 */
	private static Comparator<GearItem> rankingOrder(GearStat stat)
	{
		return Comparator
			.comparingDouble((GearItem item) -> item.statValue(stat)).reversed()
			.thenComparing(GearItem::getName)
			.thenComparingInt(GearItem::getItemId);
	}
}
