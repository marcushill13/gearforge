package com.gearforge.optimizer;

import com.gearforge.data.EquipmentSlot;
import com.gearforge.data.GearItem;
import com.gearforge.dps.SetupScore;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A candidate setup with its score and the effects that applied to it.
 */
public final class ScoredSetup
{
	/** Highest DPS first. */
	public static final Comparator<ScoredSetup> BY_DPS_DESC =
		Comparator.comparingDouble((ScoredSetup setup) -> setup.getScore().getDps()).reversed();

	private final Map<EquipmentSlot, GearItem> setup;
	private final SetupScore score;
	private final List<String> notes;

	ScoredSetup(Map<EquipmentSlot, GearItem> setup, SetupScore score, List<String> notes)
	{
		this.setup = new EnumMap<>(setup);
		this.score = score;
		this.notes = notes;
	}

	public Map<EquipmentSlot, GearItem> getSetup()
	{
		return Collections.unmodifiableMap(setup);
	}

	public SetupScore getScore()
	{
		return score;
	}

	/**
	 * Why this setup scores what it does, e.g. "Salve (ei): target is undead".
	 */
	public List<String> getNotes()
	{
		return Collections.unmodifiableList(notes);
	}

	/**
	 * Identifies the combination of items, so near-identical setups can be de-duplicated.
	 */
	String signature()
	{
		StringBuilder builder = new StringBuilder();
		for (Map.Entry<EquipmentSlot, GearItem> entry : setup.entrySet())
		{
			builder.append(entry.getKey().name()).append(':').append(entry.getValue().getItemId()).append('|');
		}

		return builder.toString();
	}
}
