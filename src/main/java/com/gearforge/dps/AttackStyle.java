package com.gearforge.dps;

import lombok.Getter;

/**
 * The style selected on the weapon interface. These add flat levels before the +8, they do not
 * multiply — a detail the project spec got wrong.
 * <p>
 * Ranged applies its bonus to both effective ranged attack and effective ranged strength. Magic only
 * applies a style bonus on powered staves; a spell cast from a spellbook gets nothing.
 */
@Getter
public enum AttackStyle
{
	ACCURATE(3, 0, 3, 3, 0),
	AGGRESSIVE(0, 3, 0, 0, 0),
	CONTROLLED(1, 1, 0, 0, 0),
	DEFENSIVE(0, 0, 0, 0, 0),
	RAPID(0, 0, 0, 0, -1),
	LONGRANGE(0, 0, 0, 1, 0);

	private final int meleeAttackAdd;
	private final int meleeStrengthAdd;
	private final int rangedAdd;
	private final int magicAdd;
	/** Change to the weapon's attack speed in ticks. Rapid is one tick faster. */
	private final int speedModifier;

	AttackStyle(int meleeAttackAdd, int meleeStrengthAdd, int rangedAdd, int magicAdd, int speedModifier)
	{
		this.meleeAttackAdd = meleeAttackAdd;
		this.meleeStrengthAdd = meleeStrengthAdd;
		this.rangedAdd = rangedAdd;
		this.magicAdd = magicAdd;
		this.speedModifier = speedModifier;
	}
}
