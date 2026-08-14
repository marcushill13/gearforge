package com.gearforge.data;

import com.gearforge.dps.MonsterAttribute;
import com.gearforge.dps.Spell;
import com.gearforge.dps.Target;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One boss, as shipped in the generated dataset.
 * <p>
 * A plain class with a no-arg constructor because this is read with Gson.
 */
@Data
@NoArgsConstructor
public class Monster
{
	private int id;
	private String name = "";

	/** Distinguishes forms of the same boss, e.g. Zulrah's colours. Often empty. */
	private String version = "";

	private int combatLevel;
	private int size = 1;
	private int defenceLevel;
	private int magicLevel;

	/** Magic attack bonus, used by the twisted bow's scaling. */
	private int magicAttack;

	private int hitpoints;
	private Defensive defensive = new Defensive();
	private List<String> attributes = new ArrayList<>();
	private boolean slayerMonster;

	/** Elemental weakness, if any. A matching spell gains the severity in accuracy and damage. */
	private String weaknessElement;
	private int weaknessSeverity;

	/** Whether this is a curated boss. Bosses keep their forms when the list is collapsed. */
	private boolean boss;

	/**
	 * Name plus form, e.g. "Zulrah (Tanzanite)". Used everywhere the boss is shown.
	 */
	public String displayName()
	{
		return version == null || version.isEmpty() ? name : name + " (" + version + ")";
	}

	/**
	 * Used directly by combo boxes, so it must read as the boss name rather than a field dump.
	 */
	@Override
	public String toString()
	{
		return displayName();
	}

	/**
	 * Converts to what the DPS engine scores against.
	 */
	public Target toTarget()
	{
		EquipmentStats bonuses = EquipmentStats.builder()
			.dstab(defensive.stab)
			.dslash(defensive.slash)
			.dcrush(defensive.crush)
			.dmagic(defensive.magic)
			.drange(defensive.ranged)
			.build();

		return Target.builder()
			.name(displayName())
			.defenceLevel(defenceLevel)
			.magicLevel(magicLevel)
			.magicAttack(magicAttack)
			.defensiveBonuses(bonuses)
			.size(size)
			.attributes(parseAttributes())
			.weaknessElement(Spell.Element.parse(weaknessElement))
			.weaknessSeverity(weaknessSeverity)
			.build();
	}

	/**
	 * Maps the dataset's attribute strings onto the ones item effects key off. Unknown attributes are
	 * ignored rather than failing — the source carries more of them than we model.
	 */
	private Set<MonsterAttribute> parseAttributes()
	{
		Set<MonsterAttribute> parsed = EnumSet.noneOf(MonsterAttribute.class);

		for (String attribute : attributes)
		{
			for (MonsterAttribute known : MonsterAttribute.values())
			{
				if (known.name().equalsIgnoreCase(attribute))
				{
					parsed.add(known);
					break;
				}
			}
		}

		return parsed;
	}

	@Data
	@NoArgsConstructor
	public static class Defensive
	{
		private int stab;
		private int slash;
		private int crush;
		private int magic;
		private int ranged;
	}
}
