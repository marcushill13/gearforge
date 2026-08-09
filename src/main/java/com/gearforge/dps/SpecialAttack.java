package com.gearforge.dps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.runelite.api.gameval.ItemID;

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
 * wrong is worse than one that is missing, because it would be recommended.
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
	ELDER_MAUL("Elder maul", 50, Shape.DEFENCE_REDUCTION, ItemID.ELDER_MAUL, 1.25, 1.0, 0.35);

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
		DEFENCE_REDUCTION
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
			case DEFENCE_REDUCTION:
				return energyCost + "% energy · -"
					+ Math.round(defenceReduction * 100) + "% defence";
			default:
				return energyCost + "% energy";
		}
	}

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
