package com.gearforge.optimizer;

import com.gearforge.data.EquipmentSlot;
import com.gearforge.data.EquipmentStats;
import com.gearforge.data.GearItem;
import com.gearforge.dps.CombatContext;
import com.gearforge.dps.CombatStyle;
import com.gearforge.dps.DamageDistribution;
import com.gearforge.dps.DpsEngine;
import com.gearforge.dps.SpecDamage;
import com.gearforge.dps.SpecialAttack;
import com.gearforge.dps.SetupScore;
import com.gearforge.dps.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Which special attack weapon to bring, given the setup you are already wearing.
 * <p>
 * The hard part is that specs are not comparable in their own terms. Claws are burst damage, the
 * voidwaker is guaranteed damage, and the dragon warhammer is almost no damage at all — its worth is
 * that everything afterwards lands more often. Ranking those against each other needs one currency.
 * <p>
 * The currency here is <b>damage added to the kill</b>. For a damage spec that is simply what it hits
 * for, less the ordinary attack it replaced. For a defence-reduction spec it is the time the lower
 * defence saves you, valued at the damage you would have dealt in that time — which is why a warhammer
 * scores well on a long boss and near zero on a crab, exactly as it should.
 * <p>
 * The setup matters, and that is the point: the spec weapon is swapped into the gear the BiS tab just
 * recommended, so your body slot's strength bonus raises what the claws and the voidwaker hit for. A
 * two-handed spec weapon correctly costs you the shield.
 */
@Singleton
public class SpecFinder
{
	/**
	 * Below this the recommendation is noise. Two specs within a point of each other are a coin flip,
	 * not a decision.
	 */
	private static final double WORTH_SHOWING = 1.0;

	private final DpsEngine engine;

	@Inject
	public SpecFinder(DpsEngine engine)
	{
		this.engine = engine;
	}

	/**
	 * @param setup    the gear being worn, whose weapon the spec weapon replaces
	 * @param owned    everything the player has, searched for spec weapons
	 * @param template the context the setup was scored with — target, levels, prayer, boost
	 * @return the worthwhile specs, best first
	 */
	public List<SpecSuggestion> find(
		Map<EquipmentSlot, GearItem> setup, List<GearItem> owned, CombatContext template)
	{
		SetupScore baseline = engine.score(template);
		if (baseline.getDps() <= 0)
		{
			return Collections.emptyList();
		}

		double normalHit = averageOrdinaryHit(baseline);
		List<SpecSuggestion> suggestions = new ArrayList<>();

		for (GearItem candidate : owned)
		{
			SpecialAttack special = SpecialAttack.forItem(candidate.getItemId());
			if (special == null)
			{
				continue;
			}

			SpecSuggestion suggestion = evaluate(special, candidate, setup, template, baseline, normalHit);
			if (suggestion != null && suggestion.getDamageAdded() >= WORTH_SHOWING)
			{
				suggestions.add(suggestion);
			}
		}

		suggestions.sort(SpecSuggestion.BEST_FIRST);
		return suggestions;
	}

	private SpecSuggestion evaluate(
		SpecialAttack special,
		GearItem weapon,
		Map<EquipmentSlot, GearItem> setup,
		CombatContext template,
		SetupScore baseline,
		double normalHit)
	{
		CombatStyle style = bestStyleFor(weapon);
		CombatContext specContext = template.toBuilder()
			.style(style)
			.equipment(withWeapon(setup, weapon))
			.weaponSpeedTicks(Math.max(1, weapon.getStats().getSpeed()))
			.accuracyMultiplier(template.getAccuracyMultiplier() * special.getAccuracyMultiplier())
			.damageMultiplier(template.getDamageMultiplier() * special.getDamageMultiplier())
			.build();

		SetupScore specScore = engine.score(specContext);
		DamageDistribution damage =
			SpecDamage.of(special, specScore.getHitChance(), specScore.getMaxHit());

		double added = damage.mean() - normalHit;
		String note = null;

		if (special.reducesDefence())
		{
			double uplift = defenceReductionValue(special, template, baseline, specScore.getHitChance());
			added += uplift;

			if (uplift > 0)
			{
				note = String.format("worth %.0f damage over the rest of the kill", uplift);
			}
		}

		return new SpecSuggestion(special, weapon, added, damage.mean(), note);
	}

	/**
	 * What a landed defence reduction is worth, as damage.
	 * <p>
	 * Lower defence means every remaining attack lands more often, so the kill finishes sooner. That
	 * saved time is valued at the damage the setup would have dealt during it. A target with no
	 * hitpoints recorded cannot be valued this way, and scores zero rather than a guess.
	 */
	private double defenceReductionValue(
		SpecialAttack special, CombatContext template, SetupScore baseline, double landChance)
	{
		Target target = template.getTarget();
		if (target == null || template.getTargetHitpoints() <= 0 || target.getDefenceLevel() <= 0)
		{
			return 0;
		}

		int reducedDefence = (int) Math.floor(target.getDefenceLevel() * (1 - special.getDefenceReduction()));
		CombatContext reduced = template.toBuilder()
			.target(target.toBuilder().defenceLevel(reducedDefence).build())
			.build();

		double after = engine.score(reduced).getDps();
		if (after <= baseline.getDps())
		{
			return 0;
		}

		double hitpoints = template.getTargetHitpoints();
		double timeSaved = hitpoints / baseline.getDps() - hitpoints / after;
		return landChance * timeSaved * baseline.getDps();
	}

	/**
	 * The damage an ordinary attack of the current setup deals, which is what the spec replaces.
	 */
	private static double averageOrdinaryHit(SetupScore baseline)
	{
		return DamageDistribution.roll(baseline.getHitChance(), baseline.getMaxHit()).mean();
	}

	/**
	 * The setup's bonuses with the spec weapon in place of its own. A two-hander drops the shield,
	 * because you cannot hold both.
	 */
	private static EquipmentStats withWeapon(Map<EquipmentSlot, GearItem> setup, GearItem weapon)
	{
		Map<EquipmentSlot, GearItem> swapped = new EnumMap<>(EquipmentSlot.class);
		swapped.putAll(setup);
		swapped.put(EquipmentSlot.WEAPON, weapon);

		if (weapon.getStats().isTwoHanded())
		{
			swapped.remove(EquipmentSlot.SHIELD);
		}

		List<EquipmentStats> pieces = new ArrayList<>();
		for (GearItem item : swapped.values())
		{
			pieces.add(item.getStats());
		}

		return EquipmentStats.sum(pieces);
	}

	/**
	 * Which attack style the spec rolls with — whichever the weapon is best at, since a player specs
	 * on the weapon's own strength rather than the style they were already using.
	 */
	private static CombatStyle bestStyleFor(GearItem weapon)
	{
		EquipmentStats stats = weapon.getStats();
		CombatStyle best = CombatStyle.SLASH;
		int bonus = Integer.MIN_VALUE;

		for (CombatStyle style : new CombatStyle[]{CombatStyle.STAB, CombatStyle.SLASH, CombatStyle.CRUSH})
		{
			if (style.attackBonusOf(stats) > bonus)
			{
				bonus = style.attackBonusOf(stats);
				best = style;
			}
		}

		return best;
	}
}
