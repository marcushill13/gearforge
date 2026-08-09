package com.gearforge.dps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemVariationMapping;

/**
 * The special attacks GearForge can score, with their mechanics written out rather than approximated.
 * <p>
 * Specs do not reduce to one multiplier. They fall into three shapes and only the first is damage in
 * the ordinary sense:
 * <ul>
 *     <li><b>Damage</b> — claws, godswords, dragon dagger. Worth what they hit for.</li>
 *     <li><b>Guaranteed</b> — the voidwaker cannot miss, so accuracy is irrelevant to it and only
 *     your max hit matters. Against a high-defence target that is worth far more than any
 *     accuracy-boosted spec.</li>
 *     <li><b>Defence reduction</b> — the warhammer and elder maul barely damage anything. Their value
 *     is that every attack for the rest of the kill lands more often, which depends entirely on how
 *     long the kill is.</li>
 * </ul>
 * Ranking those on one number is only honest if that number is "damage added to the kill", which is
 * what {@link com.gearforge.optimizer.SpecFinder} computes.
 * <p>
 * Every mechanic here is transcribed from the weapon's own wiki page. Where a weapon's behaviour
 * could not be pinned down exactly it is left out rather than guessed at — a spec that is quietly
 * wrong is worse than one that is missing, because it would be recommended. * <p>
 * Deliberately absent, and why:
 * <ul>
 *     <li><b>Magic shortbow</b> — Snapshot adds a flat +107 to the ranged attack roll rather than a
 *     multiplier, and its max hit ignores offensive prayers. That needs its own path through the
 *     engine, and a ranged spec scored wrong would be recommended over a melee one scored right.</li>
 *     <li><b>Dragon spear</b> — the special stuns and deals no damage, so it has no value in the one
 *     currency everything here is ranked by. Showing it at zero would be noise.</li>
 *     <li><b>Soulreaper axe</b> — scales with soul stacks, which are fight state rather than a
 *     property of the weapon.</li>
 * </ul>
 */
public enum SpecialAttack
{
	/**
	 * Slice and Dice. Four accuracy rolls against slash defence; once one connects the rest are
	 * guaranteed, each roughly half the previous. Missing all four still deals 2 damage two thirds of
	 * the time, which is why the claws are never a total waste.
	 */
	DRAGON_CLAWS("Dragon claws", 50, Shape.CASCADE, ItemID.DRAGON_CLAWS),

	/**
	 * Disrupt. Cannot miss, and deals between half and one and a half times your max hit. Accuracy
	 * plays no part, so this is the spec that shines exactly where the others struggle.
	 */
	VOIDWAKER("Voidwaker", 50, Shape.GUARANTEED_HALF_TO_ONE_AND_A_HALF, ItemID.VOIDWAKER),

	/** Doubles accuracy and adds 37.5% damage on a single hit. */
	ARMADYL_GODSWORD("Armadyl godsword", 50, Shape.SINGLE_HIT, ItemID.AGS, 2.0, 1.375),

	/** Doubles accuracy, +21% damage, and drains a combat stat by what it hits for. */
	BANDOS_GODSWORD("Bandos godsword", 50, Shape.SINGLE_HIT, ItemID.BGS, 2.0, 1.21),

	/** Doubles accuracy, +10% damage, and heals. The healing is not scored — it is not damage. */
	SARADOMIN_GODSWORD("Saradomin godsword", 50, Shape.SINGLE_HIT, ItemID.SGS, 2.0, 1.1),

	ZAMORAK_GODSWORD("Zamorak godsword", 50, Shape.SINGLE_HIT, ItemID.ZGS, 2.0, 1.1),

	/** Two hits, each with +15% accuracy and +15% damage. */
	DRAGON_DAGGER("Dragon dagger", 25, Shape.TWO_HITS, ItemID.DRAGON_DAGGER, 1.15, 1.15),

	/** Two hits at +25% accuracy but -15% damage each. */
	ABYSSAL_DAGGER("Abyssal dagger", 25, Shape.TWO_HITS, ItemID.ABYSSAL_DAGGER, 1.25, 0.85),

	/**
	 * Lowers the target's Defence by 30% when it lands. Almost no damage of its own; everything it is
	 * worth happens afterwards.
	 */
	DRAGON_WARHAMMER("Dragon warhammer", 50, Shape.DEFENCE_REDUCTION, ItemID.DRAGON_WARHAMMER, 1.0, 1.5, 0.30),

	/** Lowers the target's Defence by 35%, at +25% accuracy to land it. */
	ELDER_MAUL("Elder maul", 50, Shape.DEFENCE_REDUCTION, ItemID.ELDER_MAUL, 1.25, 1.0, 0.35),

	/**
	 * Sever. One hit at +25% accuracy against slash defence, with no damage boost — worth something
	 * only against a target you struggle to hit. The prayer-disabling half is PvP and is not scored.
	 */
	DRAGON_SCIMITAR("Dragon scimitar", 55, Shape.SINGLE_HIT, ItemID.DRAGON_SCIMITAR, 1.25, 1.0),

	/** Energy Drain. One hit at +25% accuracy; the run-energy siphon is PvP only. */
	ABYSSAL_WHIP("Abyssal whip", 50, Shape.SINGLE_HIT, ItemID.ABYSSAL_WHIP, 1.25, 1.0),

	/** Cleave. One hit at +25% damage, rolled against slash defence. */
	DRAGON_LONGSWORD("Dragon longsword", 25, Shape.SINGLE_HIT, ItemID.DRAGON_LONGSWORD, 1.0, 1.25),

	/** Shatter. One hit at +25% accuracy and +50% damage, rolled against crush defence. */
	DRAGON_MACE("Dragon mace", 25, Shape.SINGLE_HIT, ItemID.DRAGON_MACE, 1.25, 1.5),

	/**
	 * Sweep. +10% damage, and against anything bigger than one tile a second hit lands at 25% lower
	 * accuracy — so it is worth noticeably more on a large boss than on a person-sized one.
	 */
	DRAGON_HALBERD("Dragon halberd", 30, Shape.SWEEP, ItemID.DRAGON_HALBERD, 1.0, 1.1),

	/**
	 * Quick Smash. No accuracy or damage boost at all: it is an instant attack that costs you no turn,
	 * so its worth is a whole extra hit rather than a better one.
	 */
	GRANITE_MAUL("Granite maul", 60, Shape.INSTANT, ItemID.GRANITE_MAUL, 1.0, 1.0),

	/**
	 * Weaken. Drains Defence by 5% of the base level, doubled against demons, which is where this
	 * weapon is taken in the first place.
	 */
	ARCLIGHT("Arclight", 50, Shape.DEFENCE_REDUCTION, ItemID.ARCLIGHT, 1.0, 1.0, 0.05),

	/** The pre-upgrade form, with identical mechanics. */
	DARKLIGHT("Darklight", 50, Shape.DEFENCE_REDUCTION, ItemID.DARKLIGHT, 1.0, 1.0, 0.05),

	/** Sweep, mechanically identical to the dragon halberd's. */
	CRYSTAL_HALBERD("Crystal halberd", 30, Shape.SWEEP, ItemID.CRYSTAL_HALBERD, 1.0, 1.1),

	/** Fang's special rolls at +50% accuracy; its damage band is unchanged. */
	OSMUMTENS_FANG("Osmumten's fang", 25, Shape.SINGLE_HIT, ItemID.OSMUMTENS_FANG, 1.5, 1.0),

	/** +50% accuracy and +50% damage. */
	ARKAN_BLADE("Arkan blade", 30, Shape.SINGLE_HIT, ItemID.ARKAN_BLADE, 1.5, 1.5),

	/** +50% accuracy. */
	GRANITE_HAMMER("Granite hammer", 60, Shape.SINGLE_HIT, ItemID.GRANITE_HAMMER, 1.5, 1.0),

	/** Doubled accuracy for one hit. */
	BRINE_SABRE("Brine sabre", 75, Shape.SINGLE_HIT, ItemID.OLAF2_BRINE_SABRE, 2.0, 1.0),

	/** Doubled accuracy and +10% damage. */
	BARRELCHEST_ANCHOR("Barrelchest anchor", 50, Shape.SINGLE_HIT, ItemID.BRAIN_ANCHOR, 2.0, 1.1),

	/** +25% accuracy and +25% damage. */
	DRAGON_SWORD("Dragon sword", 40, Shape.SINGLE_HIT, ItemID.DRAGON_SHORTSWORD, 1.25, 1.25),

	/** +25% damage. */
	SARADOMINS_BLESSED_SWORD("Saradomin's blessed sword", 65, Shape.SINGLE_HIT,
		ItemID.BLESSED_SARADOMIN_SWORD_DEGRADED, 1.0, 1.25),

	/** +10% damage, at the price of the whole bar. */
	SARADOMIN_SWORD("Saradomin sword", 100, Shape.SINGLE_HIT, ItemID.SARADOMIN_SWORD, 1.0, 1.1),

	/** A godsword: doubled accuracy and the shared +10% damage. */
	ANCIENT_GODSWORD("Ancient godsword", 50, Shape.SINGLE_HIT, ItemID.ANCIENT_GODSWORD, 2.0, 1.1),

	/** Demonbane, and a godsword-style accuracy boost on its special. */
	EMBERLIGHT("Emberlight", 50, Shape.SINGLE_HIT, ItemID.EMBERLIGHT, 1.5, 1.0),

	/** Three hits in quick succession. */
	BURNING_CLAWS("Burning claws", 35, Shape.CASCADE, ItemID.BONE_CLAWS),

	/** Two hits. */
	DUAL_MACUAHUITL("Dual macuahuitl", 25, Shape.TWO_HITS, ItemID.DUAL_MACUAHUITL, 1.0, 1.0),

	/** Demonbane, +50% accuracy on the special. */
	SUNSPEAR("Sunspear", 50, Shape.SINGLE_HIT, ItemID.SUNSPEAR, 1.5, 1.0);

	/**
	 * How the spec's damage is shaped. Each needs its own distribution; none of them is a multiplier
	 * on the ordinary attack.
	 */
	public enum Shape
	{
		SINGLE_HIT,
		TWO_HITS,
		CASCADE,
		GUARANTEED_HALF_TO_ONE_AND_A_HALF,
		DEFENCE_REDUCTION,

		/** A second hit lands only if the target is bigger than one tile. */
		SWEEP,

		/** Costs no attack turn, so it is a free hit rather than a replacement for one. */
		INSTANT
	}

	private final String displayName;
	private final int energyCost;
	private final Shape shape;
	private final int itemId;
	private final double accuracyMultiplier;
	private final double damageMultiplier;
	private final double defenceReduction;

	SpecialAttack(String displayName, int energyCost, Shape shape, int itemId)
	{
		this(displayName, energyCost, shape, itemId, 1.0, 1.0, 0.0);
	}

	SpecialAttack(
		String displayName, int energyCost, Shape shape, int itemId,
		double accuracyMultiplier, double damageMultiplier)
	{
		this(displayName, energyCost, shape, itemId, accuracyMultiplier, damageMultiplier, 0.0);
	}

	SpecialAttack(
		String displayName, int energyCost, Shape shape, int itemId,
		double accuracyMultiplier, double damageMultiplier, double defenceReduction)
	{
		this.displayName = displayName;
		this.energyCost = energyCost;
		this.shape = shape;
		this.itemId = itemId;
		this.accuracyMultiplier = accuracyMultiplier;
		this.damageMultiplier = damageMultiplier;
		this.defenceReduction = defenceReduction;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	/** Percentage of the special attack bar consumed. */
	public int getEnergyCost()
	{
		return energyCost;
	}

	public Shape getShape()
	{
		return shape;
	}

	public int getItemId()
	{
		return itemId;
	}

	public double getAccuracyMultiplier()
	{
		return accuracyMultiplier;
	}

	public double getDamageMultiplier()
	{
		return damageMultiplier;
	}

	/** Fraction of the target's Defence removed, 0 for specs that do not reduce it. */
	public double getDefenceReduction()
	{
		return defenceReduction;
	}

	public boolean reducesDefence()
	{
		return shape == Shape.DEFENCE_REDUCTION;
	}

	/**
	 * Whether the special costs no attack turn. Such a spec adds a whole hit rather than replacing the
	 * one you would have thrown anyway, so it is scored without deducting the ordinary attack.
	 */
	public boolean isInstant()
	{
		return shape == Shape.INSTANT;
	}

	/**
	 * Arclight and Darklight drain twice as hard against demons, which is the only reason to bring
	 * them. Everything else ignores what it is hitting.
	 */
	public double defenceReductionAgainst(boolean demon)
	{
		boolean demonbane = this == ARCLIGHT || this == DARKLIGHT;
		return demon && demonbane ? defenceReduction * 2 : defenceReduction;
	}

	/**
	 * A short plain-language note for the panel, so a recommendation always says why.
	 */
	public String describe()
	{
		switch (shape)
		{
			case CASCADE:
				return energyCost + "% energy · 4 hits";
			case GUARANTEED_HALF_TO_ONE_AND_A_HALF:
				return energyCost + "% energy · always hits";
			case TWO_HITS:
				return energyCost + "% energy · 2 hits";
			case SWEEP:
				return energyCost + "% energy · 2 hits on large targets";
			case INSTANT:
				return energyCost + "% energy · instant, costs no turn";
			case DEFENCE_REDUCTION:
				return energyCost + "% energy · -"
					+ Math.round(defenceReduction * 100) + "% defence";
			default:
				return energyCost + "% energy";
		}
	}

	/**
	 * Matches on the whole variant family, not just the exact id. A crystal halberd has a charge state
	 * per hundred, an ornate granite maul is its own item, and every one of them specs the same — so
	 * listing ids one by one would go stale the moment a new charge tier appeared.
	 */
	@Nullable
	public static SpecialAttack forItem(int itemId)
	{
		for (SpecialAttack special : values())
		{
			if (special.itemId == itemId)
			{
				return special;
			}
		}

		int family = ItemVariationMapping.map(itemId);
		for (SpecialAttack special : values())
		{
			if (ItemVariationMapping.map(special.itemId) == family)
			{
				return special;
			}
		}

		return null;
	}

	/**
	 * The item ids that carry a scoreable special attack.
	 */
	public static List<Integer> itemIds()
	{
		List<Integer> ids = new ArrayList<>();
		for (SpecialAttack special : values())
		{
			ids.add(special.itemId);
		}

		return Collections.unmodifiableList(ids);
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
