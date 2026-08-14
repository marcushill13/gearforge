package com.gearforge.optimizer;

import com.gearforge.data.EquipmentSlot;
import com.gearforge.data.EquipmentStats;
import com.gearforge.data.GearItem;
import com.gearforge.data.ItemCategories;
import com.gearforge.dps.CombatContext;
import com.gearforge.dps.BoltEffect;
import com.gearforge.dps.CombatStyle;
import com.gearforge.dps.DpsEngine;
import com.gearforge.dps.PoweredStaff;
import com.gearforge.dps.SetEffectRegistry;
import com.gearforge.dps.SetEffects;
import com.gearforge.dps.SetupScore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.gameval.ItemID;

/**
 * Finds the highest-DPS setup from what the player owns.
 * <p>
 * DPS is not slot-independent — a two-handed weapon costs you the shield, void is worthless until the
 * whole set is on, and a salve amulet changes what the helmet slot is worth. So this scores whole
 * setups rather than picking per slot, using a beam search ordered weapon-first because the weapon
 * dominates everything downstream.
 * <p>
 * The candidate pool per slot is the top few items by raw offensive bonus <em>plus</em> every item the
 * set effect registry cares about. Without that second part a void piece would be pruned for having
 * poor stats, and the set bonus would never be discovered.
 */
@Singleton
public class DpsOptimizer
{
	/**
	 * How many items per slot survive the raw-stat pre-filter.
	 * <p>
	 * Measured, not guessed. At five, the search fell short of an exhaustive one in every one of
	 * fifteen deep banks, by up to 4.16%. At ten it was still short in thirteen; at fifteen, six. At
	 * twenty it matched exhaustive every time, and stayed there at thirty and forty.
	 * <p>
	 * The failure the low number caused is invisible from outside: an item that is second best at both
	 * accuracy and strength beats items that are first at one, but never enters the search, and the
	 * wrong answer is presented with the same confidence as the right one.
	 * <p>
	 * {@code OptimizerBreadthTest} holds the measurement and fails if the shipped breadth stops
	 * matching an exhaustive search, or stops being fast enough to run on a gear change.
	 */
	private static final int CANDIDATES_PER_SLOT = 20;

	/** How many partial setups survive each beam step. */
	private static final int BEAM_WIDTH = 50;

	/** Rerolls a magic miss, but only alongside a one-handed weapon. */
	private static final int CONFLICTION_GAUNTLETS = 31106;

	/**
	 * The blowpipes, which are loaded with darts rather than fed from the ammo slot.
	 * <p>
	 * Both the pipe and the dart are weapon-slot items, so nothing ever put them together: a toxic
	 * blowpipe was scored on its own 20 ranged strength while rune knives scored 26, and the knives
	 * won. In game the dart's strength adds to the pipe's, which is the whole point of the weapon.
	 */
	private static final Set<Integer> BLOWPIPES = new HashSet<>(Arrays.asList(
		12926, 12924, 28688, 28687, 30374, 30373, 31575, 31577, 31579, 31581, 31583, 31585));

	/** Thrown with the arm rather than drawn, so it reads Strength instead of Ranged. */
	private static final int ECLIPSE_ATLATL = 29000;

	/**
	 * Adjustable so the breadth can be measured rather than assumed. The pre-filter keeps the best few
	 * items per slot by raw offensive stat, which is a guess at what might win — an item that is second
	 * best at both accuracy and strength can beat items that are first at one, and would never enter
	 * the search.
	 */
	private final int candidatesPerSlot;

	/**
	 * Weapon first because it dominates; shield straight after so the two-handed decision resolves
	 * early rather than polluting the beam.
	 */
	private static final List<EquipmentSlot> SLOT_ORDER = Arrays.asList(
		EquipmentSlot.WEAPON,
		EquipmentSlot.SHIELD,
		EquipmentSlot.HEAD,
		EquipmentSlot.BODY,
		EquipmentSlot.LEGS,
		EquipmentSlot.GLOVES,
		EquipmentSlot.BOOTS,
		EquipmentSlot.AMULET,
		EquipmentSlot.CAPE,
		EquipmentSlot.RING,
		EquipmentSlot.AMMO);

	private final DpsEngine engine;
	private final SetEffectRegistry setEffects;
	private final ItemCategories itemCategories;

	@Inject
	public DpsOptimizer(DpsEngine engine, SetEffectRegistry setEffects, ItemCategories itemCategories)
	{
		this(engine, setEffects, itemCategories, CANDIDATES_PER_SLOT);
	}

	static int candidatesPerSlotDefault()
	{
		return CANDIDATES_PER_SLOT;
	}

	/** For measuring how much the pre-filter costs in accuracy. */
	DpsOptimizer(
		DpsEngine engine, SetEffectRegistry setEffects, ItemCategories itemCategories,
		int candidatesPerSlot)
	{
		this.candidatesPerSlot = candidatesPerSlot;
		this.engine = engine;
		this.setEffects = setEffects;
		this.itemCategories = itemCategories;
	}

	/**
	 * @param owned        every equippable item the player has
	 * @param template     levels, prayer, style and target; equipment and speed are filled in per candidate
	 * @param onSlayerTask whether slayer-task effects should count
	 * @param topN         how many results to return
	 * @return the best setups, highest DPS first, or empty if the player owns no usable weapon
	 */
	public List<ScoredSetup> best(
		Collection<GearItem> owned,
		CombatContext template,
		boolean onSlayerTask,
		int topN)
	{
		Map<EquipmentSlot, List<GearItem>> candidates = buildCandidates(owned, template.getStyle());

		// Worked out once: a blowpipe is only as good as the best dart in the bank.
		int dartStrength = bestDartStrength(owned);

		List<GearItem> weapons = candidates.get(EquipmentSlot.WEAPON);
		if (weapons == null || weapons.isEmpty())
		{
			return new ArrayList<>();
		}

		List<Map<EquipmentSlot, GearItem>> beam = new ArrayList<>();
		beam.add(new EnumMap<>(EquipmentSlot.class));

		for (EquipmentSlot slot : SLOT_ORDER)
		{
			beam = extend(beam, slot, candidates.get(slot), template, onSlayerTask, dartStrength);
		}

		return rank(beam, template, onSlayerTask, topN, dartStrength);
	}

	/**
	 * Grows every partial setup by each candidate for this slot (plus leaving it empty), then keeps
	 * only the best {@value #BEAM_WIDTH}.
	 */
	private List<Map<EquipmentSlot, GearItem>> extend(
		List<Map<EquipmentSlot, GearItem>> beam,
		EquipmentSlot slot,
		@Nullable List<GearItem> slotCandidates,
		CombatContext template,
		boolean onSlayerTask,
		int dartStrength)
	{
		List<Map<EquipmentSlot, GearItem>> grown = new ArrayList<>();

		for (Map<EquipmentSlot, GearItem> partial : beam)
		{
			// Leaving a slot empty is always allowed, and for the shield it is mandatory behind a
			// two-hander.
			grown.add(partial);

			if (slotCandidates == null)
			{
				continue;
			}

			boolean shieldBlocked = slot == EquipmentSlot.SHIELD
				&& Constraints.isTwoHanded(partial.get(EquipmentSlot.WEAPON));

			if (shieldBlocked)
			{
				continue;
			}

			GearItem weapon = partial.get(EquipmentSlot.WEAPON);

			for (GearItem candidate : slotCandidates)
			{
				// Ammo has to actually fit the weapon — otherwise the search happily pairs a crossbow
				// with arrows, which reads as a confident recommendation and is simply wrong.
				//
				// A weapon that fires nothing is worse than that. Knives and darts are thrown from the
				// hand, so a quiver of bolts worn beside them adds nothing in game — but its ranged
				// strength was being summed into the setup all the same, which is how rune knives came
				// to out-score a bow of faerdhinen.
				if (slot == EquipmentSlot.AMMO
					&& weapon != null
					&& (!itemCategories.requiresAmmo(weapon.getItemId())
						|| !itemCategories.ammoFits(weapon.getItemId(), candidate.getItemId())))
				{
					continue;
				}

				Map<EquipmentSlot, GearItem> extended = new EnumMap<>(partial);
				extended.put(slot, candidate);
				grown.add(extended);
			}
		}

		// A setup with no weapon cannot be scored, so keep those alive unscored until one is chosen.
		if (slot == EquipmentSlot.WEAPON)
		{
			grown.removeIf(setup -> setup.get(EquipmentSlot.WEAPON) == null);
		}

		grown.sort(Comparator.comparingDouble(
			(Map<EquipmentSlot, GearItem> setup) -> score(setup, template, onSlayerTask, dartStrength).getDps()).reversed());

		return grown.subList(0, Math.min(BEAM_WIDTH, grown.size()));
	}

	private List<ScoredSetup> rank(
		List<Map<EquipmentSlot, GearItem>> beam,
		CombatContext template,
		boolean onSlayerTask,
		int topN,
		int dartStrength)
	{
		List<ScoredSetup> scored = new ArrayList<>();
		Set<String> seen = new HashSet<>();

		for (Map<EquipmentSlot, GearItem> setup : beam)
		{
			SetEffects effects = setEffects.evaluate(
				setup.values(), template.getStyle(), template.getTarget(), onSlayerTask);

			ScoredSetup candidate = new ScoredSetup(setup, score(setup, template, onSlayerTask, dartStrength), template.getStyle(), effects.getNotes());
			if (seen.add(candidate.signature()))
			{
				scored.add(candidate);
			}
		}

		scored.sort(ScoredSetup.BY_DPS_DESC);
		return scored.subList(0, Math.min(topN, scored.size()));
	}

	/**
	 * The strongest dart in the bank, which is what a blowpipe would be loaded with.
	 */
	private static int bestDartStrength(Collection<GearItem> owned)
	{
		int best = 0;
		for (GearItem item : owned)
		{
			if (item.getName().toLowerCase().endsWith("dart")
				&& item.getStats().getRangedStrength() > best)
			{
				best = item.getStats().getRangedStrength();
			}
		}

		return best;
	}

	/**
	 * Scores a complete or partial setup. Empty slots simply contribute nothing.
	 */
	private SetupScore score(
		Map<EquipmentSlot, GearItem> setup, CombatContext template, boolean onSlayerTask,
		int dartStrength)
	{
		GearItem weapon = setup.get(EquipmentSlot.WEAPON);
		if (weapon == null)
		{
			return SetupScore.builder().dps(0).build();
		}

		// A bow with no arrows cannot attack, so it must not be scored as though it can.
		if (itemCategories.requiresAmmo(weapon.getItemId()))
		{
			GearItem ammo = setup.get(EquipmentSlot.AMMO);
			if (ammo == null || !itemCategories.ammoFits(weapon.getItemId(), ammo.getItemId()))
			{
				return SetupScore.builder().dps(0).build();
			}
		}

		List<EquipmentStats> pieces = new ArrayList<>();
		for (GearItem item : setup.values())
		{
			pieces.add(item.getStats());
		}

		// A blowpipe fires the darts loaded into it, and both are weapon-slot items, so the two never
		// met in the search. Without the dart the pipe is scored on a fraction of its real strength.
		if (BLOWPIPES.contains(weapon.getItemId()) && dartStrength > 0)
		{
			pieces.add(EquipmentStats.builder().rangedStrength(dartStrength).build());
		}

		SetEffects effects = setEffects.evaluate(
			setup.values(), template.getStyle(), template.getTarget(), onSlayerTask);

		// A spellbook cast keeps its own 5-tick speed regardless of what is held.
		boolean spellbookCast = template.getStyle().isMagic() && !template.isPoweredStaff();
		int speed = spellbookCast ? template.getWeaponSpeedTicks() : weapon.getStats().getSpeed();

		CombatContext context = template.toBuilder()
			.equipment(EquipmentStats.sum(pieces))
			.weaponSpeedTicks(speed)
			.voidSet(effects.getVoidSet())
			.accuracyMultiplier(effects.getAccuracyMultiplier())
			.damageMultiplier(effects.getDamageMultiplier())
			.flatMaxHit(effects.getFlatMaxHit())
			.brimstoneRing(effects.isBrimstoneRing())
			// Confliction gauntlets reroll a magic miss, but only with a one-handed weapon.
			.rerollsMisses(effects.isRerollsMisses()
				|| (template.getStyle().isMagic()
					&& setup.containsKey(EquipmentSlot.GLOVES)
					&& setup.get(EquipmentSlot.GLOVES).getItemId() == CONFLICTION_GAUNTLETS
					&& !weapon.getStats().isTwoHanded()))
			.build();

		return engine.score(context);
	}

	/**
	 * Top few per slot by offensive relevance, unioned with everything the set effect registry needs.
	 */
	private Map<EquipmentSlot, List<GearItem>> buildCandidates(Collection<GearItem> owned, CombatStyle style)
	{
		Map<EquipmentSlot, List<GearItem>> bySlot = new EnumMap<>(EquipmentSlot.class);
		for (GearItem item : owned)
		{
			EquipmentSlot slot = EquipmentSlot.fromSlotIndex(item.getStats().getSlot());

			// A weapon that cannot swing this way is not a candidate. A whip's three attack options are
			// all slash, so offering one for a crush setup was recommending something impossible.
			if (slot == EquipmentSlot.WEAPON && !itemCategories.canUseStyle(item.getItemId(), style))
			{
				continue;
			}
			if (slot != null)
			{
				bySlot.computeIfAbsent(slot, key -> new ArrayList<>()).add(item);
			}
		}

		Set<Integer> effectItems = setEffects.relevantItemIds();
		Map<EquipmentSlot, List<GearItem>> candidates = new EnumMap<>(EquipmentSlot.class);

		for (Map.Entry<EquipmentSlot, List<GearItem>> entry : bySlot.entrySet())
		{
			List<GearItem> items = entry.getValue();

			if (entry.getKey() == EquipmentSlot.WEAPON)
			{
				// A weapon has to match the style. Otherwise, with no bow owned, a longsword wins the
				// ranged slot simply by being the least bad of the wrong options.
				List<GearItem> suitable = new ArrayList<>();
				for (GearItem item : items)
				{
					if (Constraints.isUsableWeapon(item)
						&& itemCategories.suitsStyle(item.getItemId(), style.name()))
					{
						suitable.add(item);
					}
				}

				items = suitable;
			}

			if (entry.getKey() == EquipmentSlot.WEAPON)
			{
				items = new ArrayList<>(items);
				items.removeIf(item -> !Constraints.isUsableWeapon(item));
			}

			// LinkedHashSet keeps the pool deterministic and free of duplicates across the two passes.
			Set<GearItem> pool = new LinkedHashSet<>();
			pool.addAll(topBy(items, accuracyRelevance(style)));
			pool.addAll(topBy(items, damageRelevance(style)));

			for (GearItem item : items)
			{
				if (effectItems.contains(item.getItemId()))
				{
					pool.add(item);
				}
			}

			candidates.put(entry.getKey(), new ArrayList<>(pool));
		}

		return candidates;
	}

	private List<GearItem> topBy(List<GearItem> items, Comparator<GearItem> order)
	{
		List<GearItem> sorted = new ArrayList<>(items);
		sorted.sort(order);
		return sorted.subList(0, Math.min(candidatesPerSlot, sorted.size()));
	}

	private static Comparator<GearItem> accuracyRelevance(CombatStyle style)
	{
		return Comparator
			.comparingInt((GearItem item) -> style.attackBonusOf(item.getStats())).reversed()
			.thenComparing(tieBreak(style));
	}

	private static Comparator<GearItem> damageRelevance(CombatStyle style)
	{
		if (style.isMagic())
		{
			return Comparator
				.comparingDouble((GearItem item) -> item.getStats().getMagicDamage()).reversed()
				.thenComparing(tieBreak(style));
		}

		if (style.isRanged())
		{
			return Comparator
				.comparingInt((GearItem item) -> item.getStats().getRangedStrength()).reversed()
				.thenComparing(tieBreak(style));
		}

		return Comparator
			.comparingInt((GearItem item) -> item.getStats().getStrength()).reversed()
			.thenComparing(tieBreak(style));
	}

	/**
	 * The tie-break, once offence is equal.
	 * <p>
	 * Offensive ties are common and exact. The oathplate chest is +16 slash and nothing else; the
	 * fighter torso is +0 across the board. Both give +4 strength, so for stab and crush the two are
	 * identical to the DPS engine and nothing is left to separate them. This used to fall through to
	 * the item name, which meant the alphabet decided — and "Fighter torso" beat "Oathplate chest"
	 * every time.
	 * <p>
	 * Defence is the right decider: at equal damage a player wants the piece that survives better.
	 * Prayer bonus follows, then the name, so a run is still reproducible.
	 */
	static final Comparator<GearItem> BY_DEFENSIVE_VALUE = Comparator
		.comparingInt((GearItem item) -> defensiveValue(item.getStats())).reversed()
		.thenComparing(Comparator.comparingInt((GearItem item) -> item.getStats().getPrayer()).reversed())
		.thenComparing(GearItem::getName);

	/**
	 * Ranks the pieces that are level on the stat being sorted by. Strength before defence: Dharok's
	 * platelegs are tankier than oathplate legs and worse in every way that matters, and defence alone
	 * put them first.
	 */
	private static Comparator<GearItem> tieBreak(CombatStyle style)
	{
		return Comparator
			.comparingInt((GearItem item) -> strengthFor(style, item)).reversed()
			.thenComparing(BY_DEFENSIVE_VALUE);
	}

	private static int strengthFor(CombatStyle style, GearItem item)
	{
		EquipmentStats stats = item.getStats();

		if (style.isRanged())
		{
			return stats.getRangedStrength();
		}

		return style.isMagic() ? (int) (stats.getMagicDamage() * 10) : stats.getStrength();
	}

	static int defensiveValue(EquipmentStats stats)
	{
		return stats.getDstab() + stats.getDslash() + stats.getDcrush()
			+ stats.getDmagic() + stats.getDrange();
	}
}
