package com.gearforge.dps;

import com.gearforge.data.EquipmentStats;
import java.util.EnumSet;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

/**
 * What is being hit.
 * <p>
 * Magic uses the target's <em>Magic</em> level in its defence roll rather than its Defence level,
 * which is why both are tracked separately.
 */
@Value
@Builder
public class Target
{
	@Builder.Default
	String name = "Dummy";

	int defenceLevel;
	int magicLevel;

	/**
	 * The target's magic <em>attack</em> bonus. Only the twisted bow uses it, and it scales off
	 * whichever of this and the magic level is higher.
	 */
	int magicAttack;

	/** Defensive bonuses. Only the defence fields and slot are meaningful here. */
	EquipmentStats defensiveBonuses;

	/** Number of tiles across. Drives scythe and colossal blade multi-hit behaviour later. */
	@Builder.Default
	int size = 1;

	@Builder.Default
	Set<MonsterAttribute> attributes = EnumSet.noneOf(MonsterAttribute.class);

	/**
	 * A target that rolls zero defence — the neutral baseline the BiS tab uses when no boss is
	 * selected, matching a combat dummy.
	 */
	public static Target dummy()
	{
		return Target.builder()
			.name("Dummy")
			.defenceLevel(0)
			.magicLevel(0)
			.defensiveBonuses(EquipmentStats.builder().build())
			.build();
	}

	public boolean hasAttribute(MonsterAttribute attribute)
	{
		return attributes.contains(attribute);
	}
}
