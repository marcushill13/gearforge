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
	TRIDENT_OF_THE_SEAS(ItemID.TOTS_CHARGED, ItemID.TOTS),

	/** The toxic form, less two rather than less five. */
	TRIDENT_OF_THE_SWAMP(ItemID.TOXIC_TOTS_CHARGED),

	/** A clean third of the Magic level. */
	SANGUINESTI_STAFF(ItemID.SANGUINESTI_STAFF, ItemID.SANGUINESTI_STAFF_UNCHARGED),

	/** A third of the level plus one, the strongest of them. */
	TUMEKENS_SHADOW(ItemID.TUMEKENS_SHADOW),

	/** Scales differently from the rest, on a curve of its own. */
	WARPED_SCEPTRE(ItemID.WARPED_SCEPTRE);

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
			default:
				return 1;
		}
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
