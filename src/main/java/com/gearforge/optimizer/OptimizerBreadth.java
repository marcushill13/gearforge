package com.gearforge.optimizer;

import com.gearforge.data.ItemCategories;
import com.gearforge.dps.DpsEngine;
import com.gearforge.dps.SetEffectRegistry;

/**
 * Exposes the optimizer's search breadth so it can be measured.
 * <p>
 * The pre-filter keeps only the best few items per slot before searching, which is a guess at what
 * might win. A wrong guess is invisible — the item simply never enters the search — so the cost of
 * that guess has to be measured rather than assumed. This is the seam that lets a test do it.
 */
public final class OptimizerBreadth
{
	/** The shipped breadth. */
	public static final int CANDIDATES_PER_SLOT = DpsOptimizer.candidatesPerSlotDefault();

	private OptimizerBreadth()
	{
	}

	public static DpsOptimizer withBreadth(
		DpsEngine engine, SetEffectRegistry setEffects, ItemCategories itemCategories, int candidatesPerSlot)
	{
		return new DpsOptimizer(engine, setEffects, itemCategories, candidatesPerSlot);
	}
}
