package com.gearforge.dps;

import com.gearforge.data.EquipmentStats;
import lombok.Builder;
import lombok.Value;

/**
 * Everything needed to score one attack: who is attacking, with what, in which style, against what.
 */
@Value
@Builder(toBuilder = true)
public class CombatContext
{
	int attackLevel;
	int strengthLevel;
	int rangedLevel;
	int magicLevel;

	/** Potion boosts, already resolved to flat levels. */
	int attackBoost;
	int strengthBoost;
	int rangedBoost;
	int magicBoost;

	@Builder.Default
	CombatPrayer prayer = CombatPrayer.NONE;

	@Builder.Default
	VoidSet voidSet = VoidSet.NONE;

	CombatStyle style;

	@Builder.Default
	AttackStyle attackStyle = AttackStyle.ACCURATE;

	/** The summed bonuses of every equipped item. */
	EquipmentStats equipment;

	/** The weapon's attack interval in ticks, before any style modifier. */
	int weaponSpeedTicks;

	@Builder.Default
	Target target = Target.dummy();

	/**
	 * The target's hitpoints, used only to value a defence reduction: how much a shorter kill is worth
	 * depends entirely on how long the kill was. Zero when unknown, which scores such specs at nothing
	 * rather than guessing.
	 */
	int targetHitpoints;

	/**
	 * The enchanted bolt loaded, if any. Its effect cannot be folded into a multiplier — it fires a
	 * fraction of the time and often ignores defence — so the engine applies it to the damage
	 * distribution instead.
	 */
	BoltEffect boltEffect;

	/**
	 * The powered staff held, if any. These carry their own attack rather than casting a spell, so
	 * their maximum comes from the Magic level and not from {@code baseSpellDamage}.
	 */
	PoweredStaff poweredStaffType;

	/** Damage added to the maximum rather than multiplied into it — the colossal blade and ratbane. */
	int flatMaxHit;

	/** A brimstone ring lowers the target's magic defence a quarter of the time. */
	boolean brimstoneRing;

	/**
	 * Whether a miss is rolled a second time — Osmumten's fang, and the confliction gauntlets with a
	 * one-handed magic weapon. Not an accuracy multiplier: the two rolls share a defence roll, so the
	 * result has to be worked out from both.
	 */
	boolean rerollsMisses;

	/** A zaryte crossbow strengthens every bolt effect, so the engine has to know it is held. */
	boolean zaryteCrossbow;

	/**
	 * Whether the magic attack comes from a powered staff. Only powered staves get a style bonus to
	 * effective magic level; a spellbook cast gets none.
	 */
	boolean poweredStaff;

	/** Base damage of the spell or powered staff, before magic damage bonus. Magic only. */
	int baseSpellDamage;

	/**
	 * The spell being cast, when one is. Carries the element, which decides whether the target's
	 * elemental weakness and any matching tome apply.
	 */
	Spell spell;

	/**
	 * Whether the ranged weapon draws on Strength rather than Ranged. The eclipse atlatl throws with
	 * your melee strength, and scoring it off the Ranged level makes it look far worse than it is.
	 */
	boolean rangedScalesWithStrength;

	/**
	 * Target-specific accuracy multiplier — slayer helm on task, salve, twisted bow. Set effects
	 * populate this; 1.0 means none.
	 */
	@Builder.Default
	double accuracyMultiplier = 1.0;

	/** Target-specific damage multiplier, same idea as {@link #accuracyMultiplier}. */
	@Builder.Default
	double damageMultiplier = 1.0;
}
