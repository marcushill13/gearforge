package com.gearforge.optimizer;

import com.gearforge.data.EquipmentSlot;
import com.gearforge.data.EquipmentStats;
import com.gearforge.data.GearItem;
import com.gearforge.data.ItemCategories;
import com.gearforge.dps.CombatContext;
import com.gearforge.dps.CombatStyle;
import com.gearforge.dps.DpsEngine;
import com.gearforge.dps.SetEffectRegistry;
import com.gearforge.dps.SetEffects;
import com.gearforge.dps.Target;
import com.gearforge.setups.ItemRequirement;
import com.gearforge.setups.Setup;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Finds items already in the player's bank that would improve a saved setup.
 * <p>
 * Deliberately a <em>single-slot</em> search rather than a re-optimisation. Re-running the optimizer
 * would just return the best-in-slot setup, which the BiS tab already does; the value of a nudge is
 * that it is small and actionable — one item you own and forgot about.
 * <p>
 * A setup carries no target, so upgrades are judged against a fixed benchmark rather than a boss.
 * That benchmark deliberately <em>has</em> defence: against a zero-defence dummy accuracy is already
 * near 100%, so an accuracy upgrade would score as worthless and only strength upgrades would ever
 * surface. Judging against something that can actually be missed makes both count.
 */
@Singleton
public class UpgradeFinder
{
	/**
	 * Below this, a suggestion is noise rather than an upgrade.
	 * <p>
	 * Not lower: near the accuracy crossover a single point of attack bonus is already worth ~0.7%, so
	 * a tighter threshold would fill the panel with "swap for a helm with +1 slash".
	 */
	private static final double MIN_GAIN = 0.01;

	/**
	 * A mid-tier target — roughly a mid-level boss. Not a real monster, and not meant to be: it is a
	 * fixed yardstick for comparing two items, not a DPS figure to quote.
	 */
	private static final Target BENCHMARK = Target.builder()
		.name("Benchmark")
		.defenceLevel(100)
		.magicLevel(100)
		.defensiveBonuses(EquipmentStats.builder()
			.dstab(100).dslash(100).dcrush(100).dmagic(100).drange(100)
			.build())
		.build();

	private final DpsEngine engine;
	private final SetEffectRegistry setEffects;
	private final ItemCategories itemCategories;

	@Inject
	public UpgradeFinder(DpsEngine engine, SetEffectRegistry setEffects, ItemCategories itemCategories)
	{
		this.engine = engine;
		this.setEffects = setEffects;
		this.itemCategories = itemCategories;
	}

	/**
	 * @param setup    the saved setup to improve
	 * @param owned    every equippable item the player has
	 * @param template levels and target; equipment and speed are filled in per candidate
	 * @return suggestions worth showing, biggest gain first
	 */
	public List<UpgradeSuggestion> find(Setup setup, Collection<GearItem> owned, CombatContext template)
	{
		Map<EquipmentSlot, GearItem> baseline = resolve(setup, owned);

		GearItem weapon = baseline.get(EquipmentSlot.WEAPON);
		if (weapon == null)
		{
			// Without a weapon there is no DPS to compare against.
			return new ArrayList<>();
		}

		CombatStyle style = inferStyle(weapon);
		double baselineDps = score(baseline, template, style);
		if (baselineDps <= 0)
		{
			return new ArrayList<>();
		}

		List<UpgradeSuggestion> suggestions = new ArrayList<>();

		for (EquipmentSlot slot : EquipmentSlot.values())
		{
			UpgradeSuggestion best = bestSwapFor(slot, baseline, owned, template, style, baselineDps, setup);
			if (best != null)
			{
				suggestions.add(best);
			}
		}

		suggestions.sort((a, b) -> Double.compare(b.getGain(), a.getGain()));
		return suggestions;
	}

	@Nullable
	private UpgradeSuggestion bestSwapFor(
		EquipmentSlot slot,
		Map<EquipmentSlot, GearItem> baseline,
		Collection<GearItem> owned,
		CombatContext template,
		CombatStyle style,
		double baselineDps,
		Setup setup)
	{
		GearItem current = baseline.get(slot);
		UpgradeSuggestion best = null;

		for (GearItem candidate : owned)
		{
			if (candidate.getStats().getSlot() != slot.getSlotIndex())
			{
				continue;
			}

			if (current != null && candidate.getItemId() == current.getItemId())
			{
				continue;
			}

			if (setup.getDismissedUpgrades().contains(candidate.getItemId()))
			{
				continue;
			}

			if (slot == EquipmentSlot.WEAPON && !Constraints.isUsableWeapon(candidate))
			{
				continue;
			}

			// Never suggest ammo the setup's weapon cannot fire.
			GearItem weapon = baseline.get(EquipmentSlot.WEAPON);
			if (slot == EquipmentSlot.AMMO
				&& weapon != null
				&& !itemCategories.ammoFits(weapon.getItemId(), candidate.getItemId()))
			{
				continue;
			}

			Map<EquipmentSlot, GearItem> swapped = new EnumMap<>(baseline);
			swapped.put(slot, candidate);

			// A two-hander cannot coexist with a shield; dropping the shield is part of the trade.
			if (slot == EquipmentSlot.WEAPON && Constraints.isTwoHanded(candidate))
			{
				swapped.remove(EquipmentSlot.SHIELD);
			}
			else if (slot == EquipmentSlot.SHIELD && Constraints.isTwoHanded(swapped.get(EquipmentSlot.WEAPON)))
			{
				continue;
			}

			double gain = (score(swapped, template, style) - baselineDps) / baselineDps;
			if (gain >= MIN_GAIN && (best == null || gain > best.getGain()))
			{
				best = new UpgradeSuggestion(slot, candidate, gain);
			}
		}

		return best;
	}

	/**
	 * A setup records no combat style, so it is taken from whichever bonus the weapon leans on.
	 */
	private static CombatStyle inferStyle(GearItem weapon)
	{
		EquipmentStats stats = weapon.getStats();

		CombatStyle best = CombatStyle.SLASH;
		int bestBonus = Integer.MIN_VALUE;

		for (CombatStyle style : CombatStyle.values())
		{
			int bonus = style.attackBonusOf(stats);
			if (bonus > bestBonus)
			{
				bestBonus = bonus;
				best = style;
			}
		}

		return best;
	}

	/**
	 * Maps the setup's item ids onto the items the player actually owns. Items no longer owned leave
	 * the slot empty rather than failing.
	 */
	private static Map<EquipmentSlot, GearItem> resolve(Setup setup, Collection<GearItem> owned)
	{
		Map<EquipmentSlot, GearItem> resolved = new EnumMap<>(EquipmentSlot.class);

		for (Map.Entry<EquipmentSlot, ItemRequirement> entry : setup.getEquipment().entrySet())
		{
			for (GearItem item : owned)
			{
				if (item.getItemId() == entry.getValue().getItemId())
				{
					resolved.put(entry.getKey(), item);
					break;
				}
			}
		}

		return resolved;
	}

	private double score(Map<EquipmentSlot, GearItem> setup, CombatContext template, CombatStyle style)
	{
		GearItem weapon = setup.get(EquipmentSlot.WEAPON);
		if (weapon == null)
		{
			return 0.0;
		}

		List<EquipmentStats> pieces = new ArrayList<>();
		for (GearItem item : setup.values())
		{
			pieces.add(item.getStats());
		}

		SetEffects effects = setEffects.evaluate(setup.values(), style, BENCHMARK, false);

		CombatContext context = template.toBuilder()
			.style(style)
			.target(BENCHMARK)
			.equipment(EquipmentStats.sum(pieces))
			.weaponSpeedTicks(weapon.getStats().getSpeed())
			.voidSet(effects.getVoidSet())
			.accuracyMultiplier(effects.getAccuracyMultiplier())
			.damageMultiplier(effects.getDamageMultiplier())
			.build();

		return engine.score(context).getDps();
	}
}
