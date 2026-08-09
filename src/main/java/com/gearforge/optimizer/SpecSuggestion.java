package com.gearforge.optimizer;

import com.gearforge.data.GearItem;
import com.gearforge.dps.SpecialAttack;
import java.util.Comparator;
import javax.annotation.Nullable;

/**
 * One spec weapon worth bringing, and what it is worth.
 */
public final class SpecSuggestion
{
	static final Comparator<SpecSuggestion> BEST_FIRST =
		Comparator.comparingDouble(SpecSuggestion::getDamageAdded).reversed();

	private final SpecialAttack special;
	private final GearItem weapon;
	private final double damageAdded;
	private final double specDamage;
	private final String note;

	SpecSuggestion(
		SpecialAttack special, GearItem weapon, double damageAdded, double specDamage,
		@Nullable String note)
	{
		this.special = special;
		this.weapon = weapon;
		this.damageAdded = damageAdded;
		this.specDamage = specDamage;
		this.note = note;
	}

	/**
	 * Whether the weapon is actually in the player's bank. Unowned weapons are scored too, so the
	 * panel can answer "would this be worth buying" — but it has to say which is which.
	 */
	public boolean isOwned()
	{
		return weapon.getLocations() != null && !weapon.getLocations().isEmpty();
	}

	public SpecialAttack getSpecial()
	{
		return special;
	}

	public GearItem getWeapon()
	{
		return weapon;
	}

	/**
	 * Damage the spec adds to the kill over just attacking normally. The one number the ranking uses,
	 * and the only one that lets a warhammer be compared with a set of claws.
	 */
	public double getDamageAdded()
	{
		return damageAdded;
	}

	/** What the special attack itself hits for, on average. */
	public double getSpecDamage()
	{
		return specDamage;
	}

	/**
	 * Why it scores what it does, for specs whose value is not the damage you can see.
	 */
	@Nullable
	public String getNote()
	{
		return note;
	}
}
