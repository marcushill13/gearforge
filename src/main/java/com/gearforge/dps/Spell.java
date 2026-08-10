package com.gearforge.dps;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * The combat spells worth casting, with the level they need and the element they carry.
 * <p>
 * GearForge assumed Ice Barrage for every magic setup. That is the strongest spell in the game and
 * the right guess against something with no elemental weakness — but over a thousand monsters have
 * one, and a matching spell gains that weakness as a percentage of <em>both</em> accuracy and max
 * hit. Against a fire-weak target a Fire Surge beats Ice Barrage outright despite hitting for six
 * less on paper.
 * <p>
 * Assuming a single spell also made the elemental tomes useless, since a tome only raises a spell of
 * its own element.
 * <p>
 * Max hits come from the reference calculator's spell data. Level requirements are the game's own.
 */
public enum Spell
{
	// Standard spellbook, four elements through five tiers.

	WIND_STRIKE("Wind strike", 1, 2, Element.AIR),
	WATER_STRIKE("Water strike", 5, 4, Element.WATER),
	EARTH_STRIKE("Earth strike", 9, 6, Element.EARTH),
	FIRE_STRIKE("Fire strike", 13, 8, Element.FIRE),

	WIND_BOLT("Wind bolt", 17, 9, Element.AIR),
	WATER_BOLT("Water bolt", 23, 10, Element.WATER),
	EARTH_BOLT("Earth bolt", 29, 11, Element.EARTH),
	FIRE_BOLT("Fire bolt", 35, 12, Element.FIRE),

	WIND_BLAST("Wind blast", 41, 13, Element.AIR),
	WATER_BLAST("Water blast", 47, 14, Element.WATER),
	EARTH_BLAST("Earth blast", 53, 15, Element.EARTH),
	FIRE_BLAST("Fire blast", 59, 16, Element.FIRE),

	WIND_WAVE("Wind wave", 62, 17, Element.AIR),
	WATER_WAVE("Water wave", 65, 18, Element.WATER),
	EARTH_WAVE("Earth wave", 70, 19, Element.EARTH),
	FIRE_WAVE("Fire wave", 75, 20, Element.FIRE),

	WIND_SURGE("Wind surge", 81, 21, Element.AIR),
	WATER_SURGE("Water surge", 85, 22, Element.WATER),
	EARTH_SURGE("Earth surge", 90, 23, Element.EARTH),
	FIRE_SURGE("Fire surge", 95, 24, Element.FIRE),

	// Ancient spellbook. No element, but the strongest damage in the game.

	SMOKE_RUSH("Smoke rush", 50, 13, null),
	SHADOW_RUSH("Shadow rush", 52, 14, null),
	BLOOD_RUSH("Blood rush", 56, 15, null),
	ICE_RUSH("Ice rush", 58, 16, null),

	SMOKE_BURST("Smoke burst", 62, 17, null),
	SHADOW_BURST("Shadow burst", 64, 18, null),
	BLOOD_BURST("Blood burst", 68, 21, null),
	ICE_BURST("Ice burst", 70, 22, null),

	SMOKE_BLITZ("Smoke blitz", 74, 23, null),
	SHADOW_BLITZ("Shadow blitz", 76, 24, null),
	BLOOD_BLITZ("Blood blitz", 80, 25, null),
	ICE_BLITZ("Ice blitz", 82, 26, null),

	SMOKE_BARRAGE("Smoke barrage", 86, 27, null),
	SHADOW_BARRAGE("Shadow barrage", 88, 28, null),
	BLOOD_BARRAGE("Blood barrage", 92, 29, null),
	ICE_BARRAGE("Ice barrage", 94, 30, null);

	/** The four elements a monster can be weak to. */
	public enum Element
	{
		AIR,
		WATER,
		EARTH,
		FIRE;

		@Nullable
		public static Element parse(@Nullable String name)
		{
			if (name == null)
			{
				return null;
			}

			for (Element element : values())
			{
				if (element.name().equalsIgnoreCase(name))
				{
					return element;
				}
			}

			return null;
		}
	}

	private final String displayName;
	private final int magicLevel;
	private final int maxHit;
	private final Element element;

	Spell(String displayName, int magicLevel, int maxHit, @Nullable Element element)
	{
		this.displayName = displayName;
		this.magicLevel = magicLevel;
		this.maxHit = maxHit;
		this.element = element;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public int getMagicLevel()
	{
		return magicLevel;
	}

	public int getMaxHit()
	{
		return maxHit;
	}

	@Nullable
	public Element getElement()
	{
		return element;
	}

	/**
	 * What this spell would hit for against a given target, before gear.
	 * <p>
	 * A matching element adds the target's weakness severity to the base maximum, which is what lets a
	 * Fire Surge beat an Ice Barrage against something fire-weak.
	 */
	public int maxHitAgainst(Target target)
	{
		if (element == null || target == null || element != target.getWeaknessElement())
		{
			return maxHit;
		}

		return maxHit + maxHit * target.getWeaknessSeverity() / 100;
	}

	/**
	 * The best spell for this target at this level.
	 * <p>
	 * Ranked by what each would actually hit for rather than by raw max hit, so an elemental spell wins
	 * where the weakness makes it win and Ice Barrage wins everywhere else.
	 *
	 * @param magicLevel the caster's Magic level
	 * @param ancients   whether the ancient spellbook is available
	 */
	public static Spell bestFor(Target target, int magicLevel, boolean ancients)
	{
		Spell best = null;
		int bestDamage = -1;

		for (Spell spell : values())
		{
			if (spell.magicLevel > magicLevel || (!ancients && spell.element == null))
			{
				continue;
			}

			int damage = spell.maxHitAgainst(target);
			if (damage > bestDamage)
			{
				bestDamage = damage;
				best = spell;
			}
		}

		return best;
	}

	/**
	 * Every spell castable at this level, best first, for showing the alternatives.
	 */
	public static List<Spell> castableAt(int magicLevel, boolean ancients)
	{
		List<Spell> castable = new ArrayList<>();
		for (Spell spell : values())
		{
			if (spell.magicLevel <= magicLevel && (ancients || spell.element != null))
			{
				castable.add(spell);
			}
		}

		return castable;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
