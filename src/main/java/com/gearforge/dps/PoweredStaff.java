package com.gearforge.dps;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemVariationMapping;

/**
 * Staves that carry their own attack rather than casting a spell.
 * <p>
 * Their maximum comes from the Magic level and the staff, and no spell is involved at all. GearForge
 * assumed Ice Barrage for every magic setup, which scored every one of these wrong at the root — a
 * trident was being judged on a spell it cannot cast.
 * <p>
 * Every formula is transcribed from the reference calculator. Magic damage bonuses still apply on top,
 * which the engine handles; this is only the base.
 */
public enum PoweredStaff
{
	/** A third of the Magic level, less five. */
	TRIDENT_OF_THE_SEAS(ItemID.TOTS_CHARGED, ItemID.TOTS, 22288, 22290),

	/** The toxic form, less two rather than less five. */
	TRIDENT_OF_THE_SWAMP(ItemID.TOXIC_TOTS_CHARGED, 22292, 22294),

	/** A clean third of the Magic level. */
	SANGUINESTI_STAFF(ItemID.SANGUINESTI_STAFF, ItemID.SANGUINESTI_STAFF_UNCHARGED, 25731, 25733),

	/** A third of the level plus one, the strongest of them. */
	TUMEKENS_SHADOW(ItemID.TUMEKENS_SHADOW),

	/** Scales differently from the rest, on a curve of its own. */
	WARPED_SCEPTRE(ItemID.WARPED_SCEPTRE),

	/** A third of the level less eight. */
	THAMMARONS_SCEPTRE(22555, 22552),

	/** A third less six, and it lowers the target's Defence on its special. */
	ACCURSED_SCEPTRE(27665, 27662, 27679, 27676),

	/** A third less six, like the accursed. */
	EYE_OF_AYAK(31113, 31115),

	/** A third less two. */
	DAWNBRINGER(22516),

	/** A third of the level less five, plus the ratbane ten it can only ever be used with. */
	BONE_STAFF(28796),

	/** A flat eight, whatever your level. */
	STARTER_STAFF(22335),

	// The crystal and corrupted staves hit for a fixed amount that ignores the Magic level entirely.

	CRYSTAL_STAFF_BASIC(23898, 23852),
	CRYSTAL_STAFF_ATTUNED(23899, 23853),
	CRYSTAL_STAFF_PERFECTED(23900, 23854),

	// The salamanders, which scale like a strength bonus rather than like a staff.

	SWAMP_LIZARD(10149),
	ORANGE_SALAMANDER(10146),
	RED_SALAMANDER(10147),
	BLACK_SALAMANDER(10148),
	TECU_SALAMANDER(28834);

	private final Set<Integer> itemIds;

	PoweredStaff(int... itemIds)
	{
		Set<Integer> ids = new HashSet<>();
		for (int id : itemIds)
		{
			ids.add(id);
		}

		this.itemIds = Collections.unmodifiableSet(ids);
	}

	/**
	 * The staff's own maximum before magic damage bonuses.
	 *
	 * @param magicLevel the Magic level including any boost
	 */
	public int maxHit(int magicLevel)
	{
		switch (this)
		{
			case TRIDENT_OF_THE_SEAS:
				return Math.max(1, magicLevel / 3 - 5);
			case TRIDENT_OF_THE_SWAMP:
				return Math.max(1, magicLevel / 3 - 2);
			case SANGUINESTI_STAFF:
				return Math.max(1, magicLevel / 3);
			case TUMEKENS_SHADOW:
				return Math.max(1, magicLevel / 3 + 1);
			case WARPED_SCEPTRE:
				return Math.max(1, (8 * magicLevel + 96) / 37);
			case THAMMARONS_SCEPTRE:
				return Math.max(1, magicLevel / 3 - 8);
			case ACCURSED_SCEPTRE:
			case EYE_OF_AYAK:
				return Math.max(1, magicLevel / 3 - 6);
			case DAWNBRINGER:
				return Math.max(1, magicLevel / 3 - 2);

			// The +10 is a ratbane bonus, but the staff cannot be used on anything else.
			case BONE_STAFF:
				return Math.max(1, magicLevel / 3 - 5) + 10;

			case STARTER_STAFF:
				return 8;

			// Fixed damage, entirely independent of the Magic level.
			case CRYSTAL_STAFF_BASIC:
				return 23;
			case CRYSTAL_STAFF_ATTUNED:
				return 31;
			case CRYSTAL_STAFF_PERFECTED:
				return 39;

			// The salamanders use the strength formula, with the breath's own bonus in place of gear.
			case SWAMP_LIZARD:
				return salamander(magicLevel, 56);
			case ORANGE_SALAMANDER:
				return salamander(magicLevel, 59);
			case RED_SALAMANDER:
				return salamander(magicLevel, 77);
			case BLACK_SALAMANDER:
				return salamander(magicLevel, 92);
			case TECU_SALAMANDER:
				return salamander(magicLevel, 104);

			default:
				return 1;
		}
	}

	/**
	 * A salamander's breath scales like a melee maximum, with the weapon's own bonus standing in for
	 * strength gear.
	 */
	private static int salamander(int magicLevel, int bonus)
	{
		return (magicLevel * (bonus + 64) + 320) / 640;
	}

	/**
	 * Tumeken's shadow triples every magic bonus you are wearing, which is most of what it is for.
	 */
	public double magicDamageMultiplier()
	{
		return this == TUMEKENS_SHADOW ? 3.0 : 1.0;
	}

	/**
	 * Matched on the variant family, so charged and uncharged forms both resolve.
	 */
	@Nullable
	public static PoweredStaff forItem(int itemId)
	{
		for (PoweredStaff staff : values())
		{
			if (staff.itemIds.contains(itemId))
			{
				return staff;
			}
		}

		int family = ItemVariationMapping.map(itemId);
		for (PoweredStaff staff : values())
		{
			for (int known : staff.itemIds)
			{
				if (ItemVariationMapping.map(known) == family)
				{
					return staff;
				}
			}
		}

		return null;
	}

	@Override
	public String toString()
	{
		String[] words = name().toLowerCase().split("_");
		StringBuilder name = new StringBuilder();

		for (String word : words)
		{
			if (name.length() > 0)
			{
				name.append(' ');
			}

			name.append("of".equals(word) || "the".equals(word)
				? word
				: Character.toUpperCase(word.charAt(0)) + word.substring(1));
		}

		return name.toString();
	}
}
