package com.gearforge.optimizer;

import com.gearforge.data.EquipmentSlot;
import com.gearforge.data.GearItem;
import com.gearforge.data.EquipmentStats;
import com.gearforge.dps.CombatStyle;
import com.gearforge.dps.SetupScore;
import com.gearforge.dps.Spell;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * A candidate setup with its score and the effects that applied to it.
 */
public final class ScoredSetup
{
	/**
	 * Highest DPS first, then the better offensive stats, then the setup that survives better.
	 * <p>
	 * Whole setups tie on DPS constantly, because max hit is an integer: two more strength usually
	 * changes nothing after truncation. Breaking those ties on defence alone was wrong — it handed the
	 * legs slot to Dharok's over oathplate, when oathplate is +2 strength and +12 slash and simply
	 * better. Offence has to come first, because a stat that does not move the maximum today still
	 * moves it with a different boost or a different weapon.
	 * <p>
	 * Defence remains the decider when offence is genuinely identical, which is the fighter torso and
	 * oathplate chest case that started all this.
	 */
	public static final Comparator<ScoredSetup> BY_DPS_DESC =
		Comparator.comparingDouble((ScoredSetup setup) -> setup.getScore().getDps()).reversed()
			.thenComparing(Comparator.comparingInt(ScoredSetup::offensiveValue).reversed())
			.thenComparing(Comparator.comparingInt(ScoredSetup::defensiveValue).reversed());

	private final Map<EquipmentSlot, GearItem> setup;
	private final SetupScore score;
	private final CombatStyle style;
	private final List<String> notes;
	private final Spell spell;

	ScoredSetup(
		Map<EquipmentSlot, GearItem> setup, SetupScore score, CombatStyle style, List<String> notes)
	{
		this(setup, score, style, notes, null);
	}

	ScoredSetup(
		Map<EquipmentSlot, GearItem> setup, SetupScore score, CombatStyle style, List<String> notes,
		@Nullable Spell spell)
	{
		this.setup = new EnumMap<>(setup);
		this.score = score;
		this.style = style;
		this.notes = notes;
		this.spell = spell;
	}

	/**
	 * The spell this setup was scored casting, or null for anything that is not magic.
	 */
	@Nullable
	public Spell getSpell()
	{
		return spell;
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
	 * The offensive stats that matter for the style this setup was scored with. Used only to settle DPS
	 * ties, where the integer maximum has swallowed a real difference.
	 */
	private int offensiveValue()
	{
		int total = 0;
		for (GearItem item : setup.values())
		{
			EquipmentStats stats = item.getStats();
			total += style.attackBonusOf(stats);

			if (style.isRanged())
			{
				total += stats.getRangedStrength();
			}
			else if (style.isMagic())
			{
				total += (int) (stats.getMagicDamage() * 10);
			}
			else
			{
				total += stats.getStrength();
			}
		}

		return total;
	}

	/**
	 * Total defence across every worn piece, used only to settle DPS ties.
	 */
	private int defensiveValue()
	{
		int total = 0;
		for (GearItem item : setup.values())
		{
			total += DpsOptimizer.defensiveValue(item.getStats());
		}

		return total;
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
