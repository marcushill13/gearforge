package com.gearforge;

import com.gearforge.data.EquipmentSlot;
import com.gearforge.data.EquipmentStats;
import com.gearforge.data.GearItem;
import com.gearforge.data.ItemCategories;
import com.gearforge.data.Storage;
import com.gearforge.dps.CombatContext;
import com.gearforge.dps.CombatStyle;
import com.gearforge.dps.DpsEngine;
import com.gearforge.dps.SetEffectRegistry;
import com.gearforge.dps.Target;
import com.gearforge.optimizer.DpsOptimizer;
import com.gearforge.optimizer.OptimizerBreadth;
import com.gearforge.optimizer.ScoredSetup;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * How much does the pre-filter cost in correctness?
 * <p>
 * The optimizer keeps only the best few items per slot by raw offensive stat before searching. That
 * is a guess at what might win, and a wrong guess is invisible: the item simply never enters the
 * search and the answer looks confident either way. This measures how often widening the filter
 * changes the recommendation, and what the extra breadth costs in time.
 */
public class OptimizerBreadthTest
{
	/** A bank with real depth in every slot, which is where a narrow filter can go wrong. */
	private static final int ITEMS_PER_SLOT = 40;

	/**
	 * The correctness contract: the shipped breadth must find what an exhaustive search finds. This is
	 * what was broken — at the old breadth of five it fell short on every bank below.
	 */
	@Test
	public void theShippedBreadthMatchesAnExhaustiveSearch()
	{
		for (int trial = 0; trial < 15; trial++)
		{
			List<GearItem> bank = randomBank(new Random(trial));

			double shipped = bestDps(bank, OptimizerBreadth.CANDIDATES_PER_SLOT);
			double exhaustive = bestDps(bank, ITEMS_PER_SLOT);

			assertEquals("Bank " + trial + ": the shipped breadth missed the best setup",
				exhaustive, shipped, 1e-6);
		}
	}

	/**
	 * The number that decides the shipped breadth: where does the answer stop improving, and what does
	 * each step cost? Printed rather than asserted, because it is a measurement.
	 */
	@Test
	public void reportWhereBreadthStopsMattering()
	{
		int[] breadths = {5, 10, 15, 20, 30, ITEMS_PER_SLOT};
		int trials = 15;

		// Breadth equal to the pool size searches everything, so it is the correct answer to measure
		// the cheaper ones against.
		for (int breadth : breadths)
		{
			int beaten = 0;
			double worst = 0;

			for (int trial = 0; trial < trials; trial++)
			{
				List<GearItem> bank = randomBank(new Random(trial));
				double got = bestDps(bank, breadth);
				double exhaustive = bestDps(bank, ITEMS_PER_SLOT);

				if (got < exhaustive - 1e-6)
				{
					beaten++;
					worst = Math.max(worst, (exhaustive - got) / exhaustive * 100);
				}
			}

			List<GearItem> bank = randomBank(new Random(1));
			for (int i = 0; i < 3; i++)
			{
				bestDps(bank, breadth);
			}
			long nanos = time(() -> bestDps(bank, breadth));

			System.out.printf("breadth %2d: short of exhaustive in %2d/%d banks, worst %.2f%% low, %.0fms%n",
				breadth, beaten, trials, worst, nanos / 1e6);
		}
	}

	/**
	 * The shipped breadth has to stay fast enough to run on every gear change. The panel debounces at
	 * 400ms and this runs off the client thread, so a budget well inside that is the requirement.
	 */
	@Test
	public void theShippedBreadthStaysWithinItsTimeBudget()
	{
		List<GearItem> bank = randomBank(new Random(7));

		// Warm up, so the measurement is not dominated by class loading and JIT.
		for (int i = 0; i < 3; i++)
		{
			bestDps(bank, OptimizerBreadth.CANDIDATES_PER_SLOT);
		}

		long nanos = time(() -> bestDps(bank, OptimizerBreadth.CANDIDATES_PER_SLOT));
		double millis = nanos / 1e6;

		assertTrue("One optimise took " + millis + "ms, which is too slow to run on every gear change",
			millis < 250);
	}

	private static long time(Runnable work)
	{
		long start = System.nanoTime();
		work.run();
		return System.nanoTime() - start;
	}

	private static double bestDps(List<GearItem> bank, int candidatesPerSlot)
	{
		DpsOptimizer optimizer = OptimizerBreadth.withBreadth(
			new DpsEngine(), new SetEffectRegistry(), new ItemCategories(new Gson()), candidatesPerSlot);

		List<ScoredSetup> best = optimizer.best(bank, context(), false, 1);
		return best.isEmpty() ? 0 : best.get(0).getScore().getDps();
	}

	private static CombatContext context()
	{
		return CombatContext.builder()
			.attackLevel(99)
			.strengthLevel(99)
			.style(CombatStyle.SLASH)
			.equipment(EquipmentStats.builder().build())
			.target(Target.builder()
				.name("Benchmark")
				.defenceLevel(150)
				.defensiveBonuses(EquipmentStats.builder().dslash(100).dstab(100).dcrush(100).build())
				.build())
			.weaponSpeedTicks(4)
			.build();
	}

	/**
	 * Items with accuracy and strength traded off against each other, which is exactly the shape the
	 * pre-filter handles badly: something second best at both can beat something first at one.
	 */
	private static List<GearItem> randomBank(Random random)
	{
		List<GearItem> bank = new ArrayList<>();
		int id = 1;

		for (EquipmentSlot slot : EquipmentSlot.values())
		{
			for (int i = 0; i < ITEMS_PER_SLOT; i++)
			{
				int accuracy = random.nextInt(120);
				int strength = 110 - accuracy + random.nextInt(30);

				EquipmentStats.EquipmentStatsBuilder stats = EquipmentStats.builder()
					.aslash(accuracy)
					.astab(Math.max(0, accuracy - 10))
					.acrush(Math.max(0, accuracy - 20))
					.strength(Math.max(0, strength))
					.dslash(random.nextInt(200))
					.dstab(random.nextInt(200))
					.dcrush(random.nextInt(200))
					.slot(slot.getSlotIndex());

				if (slot == EquipmentSlot.WEAPON)
				{
					stats.speed(4 + random.nextInt(3));
				}

				bank.add(new GearItem(id++, slot + " " + i, 1, stats.build(), EnumSet.of(Storage.BANK)));
			}
		}

		return bank;
	}
}
