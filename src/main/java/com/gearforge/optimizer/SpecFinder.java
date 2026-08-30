package com.gearforge.optimizer;

import com.gearforge.data.EquipmentSlot;
import com.gearforge.data.EquipmentStats;
import com.gearforge.data.GearItem;
import com.gearforge.data.ItemCategories;
import com.gearforge.data.Monster;
import com.gearforge.data.Reachability;
import com.gearforge.dps.CombatContext;
import com.gearforge.dps.CombatStyle;
import com.gearforge.dps.DamageDistribution;
import com.gearforge.dps.DpsEngine;
import com.gearforge.dps.MonsterAttribute;
import com.gearforge.dps.SpecDamage;
import com.gearforge.dps.SpecialAttack;
import com.gearforge.dps.SetupScore;
import com.gearforge.dps.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
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
	private final DpsEngine engine;
	private final ItemCategories itemCategories;

	@Inject
	public SpecFinder(DpsEngine engine, ItemCategories itemCategories)
	{
		this.engine = engine;
		this.itemCategories = itemCategories;
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
		return find(setup, owned, template, null);
	}

	/**
	 * @param monster the target, so a special that cannot physically reach it is not suggested — a
	 *                dragon warhammer is useless at Zulrah however good its numbers look
	 */
	public List<SpecSuggestion> find(
		Map<EquipmentSlot, GearItem> setup, List<GearItem> owned, CombatContext template,
		@Nullable Monster monster)
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

			// A special that cannot reach the target is not a weaker suggestion, it is an impossible one.
			if (!canReach(candidate, monster))
			{
				continue;
			}

			// Everything else owned is returned, including specials that are not worth using here.
			// Filtering those out made owning a weapon look identical to not owning one.
			suggestions.add(evaluate(special, candidate, setup, template, baseline, normalHit));
		}

		suggestions.sort(SpecSuggestion.BEST_FIRST);
		return suggestions;
	}

	/**
	 * Whether this special could physically be used on the target. Melee needs the reach; a bow does
	 * not care.
	 */
	private boolean canReach(GearItem weapon, @Nullable Monster monster)
	{
		if (monster == null || styleFor(weapon) == CombatStyle.RANGED
			|| styleFor(weapon) == CombatStyle.MAGIC)
		{
			return true;
		}

		return Reachability.meleeCanReach(monster, itemCategories.hasReach(weapon.getItemId()));
	}

	private SpecSuggestion evaluate(
		SpecialAttack special,
		GearItem weapon,
		Map<EquipmentSlot, GearItem> setup,
		CombatContext template,
		SetupScore baseline,
		double normalHit)
	{
		CombatStyle style = styleFor(weapon);
		CombatContext specContext = template.toBuilder()
			.style(style)
			.poweredStaff(style.isMagic())
			.equipment(withWeapon(setup, weapon))
			.weaponSpeedTicks(Math.max(1, weapon.getStats().getSpeed()))
			.accuracyMultiplier(template.getAccuracyMultiplier() * special.getAccuracyMultiplier())
			.damageMultiplier(template.getDamageMultiplier() * special.getDamageMultiplier())
			.build();

		SetupScore specScore = engine.score(specContext);
		int targetSize = template.getTarget() == null ? 1 : template.getTarget().getSize();

		// A Magic special ignores spell damage entirely: its ceiling comes from the Magic level.
		int maxHit = special.getShape() == SpecialAttack.Shape.MAGIC_LEVEL_MAX
			? magicLevelMaxHit(special, template.getMagicLevel() + template.getMagicBoost())
			: specScore.getMaxHit();

		DamageDistribution damage =
			SpecDamage.of(special, specScore.getHitChance(), maxHit, targetSize);

		// An instant special costs no attack turn, so it is a hit gained rather than a hit swapped.
		double added = special.isInstant() ? damage.mean() : damage.mean() - normalHit;
		String note = null;

		if (special.reducesDefence())
		{
			double uplift = special.getDefenceDrainPerDamage() > 0
				? drainByDamageValue(special, template, baseline, damage)
				: defenceReductionValue(special, template, baseline, specScore.getHitChance());
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

		boolean demon = target.getAttributes() != null
			&& target.getAttributes().contains(MonsterAttribute.DEMON);
		double fraction = special.defenceReductionAgainst(demon);
		int reducedDefence = (int) Math.floor(target.getDefenceLevel() * (1 - fraction));
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
	 * What a Bandos godsword's drain is worth.
	 * <p>
	 * It takes Defence equal to the damage it dealt, so its value cannot be read off an average hit:
	 * the relationship between Defence removed and time saved is not a straight line, and a spec that
	 * misses removes nothing at all. Every outcome is therefore weighed by how likely it is, which is
	 * the whole reason the damage is carried around as a distribution rather than a number.
	 */
	private double drainByDamageValue(
		SpecialAttack special, CombatContext template, SetupScore baseline, DamageDistribution damage)
	{
		Target target = template.getTarget();
		if (target == null || template.getTargetHitpoints() <= 0 || target.getDefenceLevel() <= 0)
		{
			return 0;
		}

		double hitpoints = template.getTargetHitpoints();
		double perDamage = special.getDefenceDrainPerDamage();
		double value = 0;

		// Nothing drained means nothing gained, so the miss and the scratches are skipped rather than
		// scored: a drain of zero leaves the defence exactly where it was.
		for (int dealt = 1; dealt <= damage.maximum(); dealt++)
		{
			double chance = damage.probabilityOf(dealt);
			if (chance <= 0)
			{
				continue;
			}

			int drained = (int) Math.floor(dealt * perDamage);
			if (drained <= 0)
			{
				continue;
			}

			// Defence cannot go below zero, whatever the godsword hits for.
			int reducedDefence = Math.max(0, target.getDefenceLevel() - drained);
			double after = engine.score(template.toBuilder()
				.target(target.toBuilder().defenceLevel(reducedDefence).build())
				.build()).getDps();

			if (after > baseline.getDps())
			{
				double timeSaved = hitpoints / baseline.getDps() - hitpoints / after;
				value += chance * timeSaved * baseline.getDps();
			}
		}

		return value;
	}

	/**
	 * The Magic specials scale their ceiling with Magic level and stop at a per-weapon cap.
	 */
	private static int magicLevelMaxHit(SpecialAttack special, int magicLevel)
	{
		int cap = special.getMagicLevelCap();
		return Math.max(1, Math.min(cap, (99 + cap * magicLevel) / 99));
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
	 * Which attack style the spec rolls with.
	 * <p>
	 * The weapon decides, not the style you were already using — you spec on the weapon's own strength.
	 * A bow rolls ranged, a staff rolls magic, and only then does the melee question of stab against
	 * slash against crush arise.
	 * <p>
	 * Getting this wrong is not a small error: a dark bow scored as a sword would read its strength
	 * bonus instead of its ranged strength and roll against the wrong defence, producing a number that
	 * looks perfectly reasonable and is nonsense.
	 */
	private CombatStyle styleFor(GearItem weapon)
	{
		if (itemCategories.suitsStyle(weapon.getItemId(), "RANGED"))
		{
			return CombatStyle.RANGED;
		}

		if (itemCategories.suitsStyle(weapon.getItemId(), "MAGIC"))
		{
			return CombatStyle.MAGIC;
		}

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
