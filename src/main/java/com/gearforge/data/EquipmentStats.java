package com.gearforge.data;

import lombok.Builder;
import lombok.Value;

/**
 * A plain, immutable copy of an item's equipment bonuses.
 * <p>
 * This deliberately mirrors {@link net.runelite.client.game.ItemEquipmentStats} rather than using it
 * directly: that class has a package-private constructor, so it cannot be built in unit tests. Copying
 * into our own type keeps the ranking and (later) DPS code testable without a running client.
 * <p>
 * Note {@code magicDamage} is a percentage in whole units — an Occult necklace is {@code 5.0f}, meaning
 * +5%. Fractional values exist, which is why it is a float.
 */
@Value
@Builder
public class EquipmentStats
{
	int astab;
	int aslash;
	int acrush;
	int amagic;
	int arange;

	int dstab;
	int dslash;
	int dcrush;
	int dmagic;
	int drange;

	int strength;
	int rangedStrength;
	float magicDamage;
	int prayer;

	/** Matches {@link EquipmentSlot#getSlotIndex()}. */
	int slot;
	boolean twoHanded;
	/** Attack speed in game ticks. 0 for non-weapons. */
	int speed;

	/**
	 * Adds up a whole setup's bonuses, which is what the DPS engine scores against.
	 * <p>
	 * Slot, two-handedness and speed are not summable, so they are taken from the weapon: the sum's
	 * {@code speed} is the fastest non-zero speed present, which for a valid setup is the weapon's.
	 */
	public static EquipmentStats sum(Iterable<EquipmentStats> pieces)
	{
		EquipmentStatsBuilder total = EquipmentStats.builder();

		int astab = 0, aslash = 0, acrush = 0, amagic = 0, arange = 0;
		int dstab = 0, dslash = 0, dcrush = 0, dmagic = 0, drange = 0;
		int strength = 0, rangedStrength = 0, prayer = 0, speed = 0;
		float magicDamage = 0f;

		for (EquipmentStats piece : pieces)
		{
			if (piece == null)
			{
				continue;
			}

			astab += piece.astab;
			aslash += piece.aslash;
			acrush += piece.acrush;
			amagic += piece.amagic;
			arange += piece.arange;
			dstab += piece.dstab;
			dslash += piece.dslash;
			dcrush += piece.dcrush;
			dmagic += piece.dmagic;
			drange += piece.drange;
			strength += piece.strength;
			rangedStrength += piece.rangedStrength;
			magicDamage += piece.magicDamage;
			prayer += piece.prayer;

			if (piece.speed > 0 && (speed == 0 || piece.speed < speed))
			{
				speed = piece.speed;
			}
		}

		return total
			.astab(astab).aslash(aslash).acrush(acrush).amagic(amagic).arange(arange)
			.dstab(dstab).dslash(dslash).dcrush(dcrush).dmagic(dmagic).drange(drange)
			.strength(strength).rangedStrength(rangedStrength)
			.magicDamage(magicDamage).prayer(prayer)
			.speed(speed)
			.build();
	}
}
