package com.gearforge.dps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The combined result of every set effect that applied to a candidate setup.
 */
public final class SetEffects
{
	private final double accuracyMultiplier;
	private final double damageMultiplier;

	/**
	 * Damage added to the maximum rather than multiplied into it. The colossal blade and the ratbane
	 * weapons work this way, and folding them into a multiplier would scale them with the rest of your
	 * gear when the game does not.
	 */
	private final int flatMaxHit;
	private final VoidSet voidSet;
	private final List<String> notes;

	SetEffects(double accuracyMultiplier, double damageMultiplier, VoidSet voidSet, List<String> notes)
	{
		this(accuracyMultiplier, damageMultiplier, 0, voidSet, notes);
	}

	SetEffects(
		double accuracyMultiplier, double damageMultiplier, int flatMaxHit, VoidSet voidSet,
		List<String> notes)
	{
		this.accuracyMultiplier = accuracyMultiplier;
		this.damageMultiplier = damageMultiplier;
		this.flatMaxHit = flatMaxHit;
		this.voidSet = voidSet;
		this.notes = new ArrayList<>(notes);
	}

	public static SetEffects none()
	{
		return new SetEffects(1.0, 1.0, VoidSet.NONE, Collections.emptyList());
	}

	public double getAccuracyMultiplier()
	{
		return accuracyMultiplier;
	}

	public double getDamageMultiplier()
	{
		return damageMultiplier;
	}

	public int getFlatMaxHit()
	{
		return flatMaxHit;
	}

	public VoidSet getVoidSet()
	{
		return voidSet;
	}

	/**
	 * Plain-language reasons, e.g. "Salve (ei): target is undead". Rendered in the BiS tab.
	 */
	public List<String> getNotes()
	{
		return Collections.unmodifiableList(notes);
	}
}
