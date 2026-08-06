package com.gearforge.optimizer;

import com.gearforge.data.EquipmentSlot;
import com.gearforge.data.EquipmentStats;
import com.gearforge.data.GearItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A chosen setup and why it was chosen.
 * <p>
 * The reasons are not diagnostics — the spec requires the BiS tab to always show its reasoning, so
 * they are part of what gets rendered.
 */
public final class OptimizerResult
{
	private final Map<EquipmentSlot, GearItem> setup;
	private final double total;
	private final List<String> reasons;

	public OptimizerResult(Map<EquipmentSlot, GearItem> setup, double total, List<String> reasons)
	{
		this.setup = new EnumMap<>(setup);
		this.total = total;
		this.reasons = new ArrayList<>(reasons);
	}

	/**
	 * The chosen item per slot. Slots the player has nothing useful for are absent rather than null.
	 */
	public Map<EquipmentSlot, GearItem> getSetup()
	{
		return Collections.unmodifiableMap(setup);
	}

	public double getTotal()
	{
		return total;
	}

	public List<String> getReasons()
	{
		return Collections.unmodifiableList(reasons);
	}

	public boolean isEmpty()
	{
		return setup.isEmpty();
	}

	/**
	 * The combined bonuses of everything chosen, for scoring or display.
	 */
	public EquipmentStats totalStats()
	{
		List<EquipmentStats> pieces = new ArrayList<>();
		for (GearItem item : setup.values())
		{
			pieces.add(item.getStats());
		}

		return EquipmentStats.sum(pieces);
	}
}
