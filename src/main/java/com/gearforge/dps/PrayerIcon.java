package com.gearforge.dps;

import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.gameval.SpriteID;

/**
 * The prayer book sprite for each combat prayer, so the picker can show the icons players recognise
 * rather than a list of names.
 */
public final class PrayerIcon
{
	private static final Map<CombatPrayer, Integer> SPRITES = new EnumMap<>(CombatPrayer.class);

	static
	{
		SPRITES.put(CombatPrayer.BURST_OF_STRENGTH, SpriteID.Prayeron.BURST_OF_STRENGTH);
		SPRITES.put(CombatPrayer.CLARITY_OF_THOUGHT, SpriteID.Prayeron.CLARITY_OF_THOUGHT);
		SPRITES.put(CombatPrayer.SUPERHUMAN_STRENGTH, SpriteID.Prayeron.SUPERHUMAN_STRENGTH);
		SPRITES.put(CombatPrayer.IMPROVED_REFLEXES, SpriteID.Prayeron.IMPROVED_REFLEXES);
		SPRITES.put(CombatPrayer.ULTIMATE_STRENGTH, SpriteID.Prayeron.ULTIMATE_STRENGTH);
		SPRITES.put(CombatPrayer.INCREDIBLE_REFLEXES, SpriteID.Prayeron.INCREDIBLE_REFLEXES);
		SPRITES.put(CombatPrayer.CHIVALRY, SpriteID.Prayeron.CHIVALRY);
		SPRITES.put(CombatPrayer.PIETY, SpriteID.Prayeron.PIETY);

		SPRITES.put(CombatPrayer.SHARP_EYE, SpriteID.Prayeron.SHARP_EYE);
		SPRITES.put(CombatPrayer.HAWK_EYE, SpriteID.Prayeron.HAWK_EYE);
		SPRITES.put(CombatPrayer.EAGLE_EYE, SpriteID.Prayeron.EAGLE_EYE);
		SPRITES.put(CombatPrayer.DEADEYE, SpriteID.Prayeron.DEADEYE);
		SPRITES.put(CombatPrayer.RIGOUR, SpriteID.Prayeron.RIGOUR);

		SPRITES.put(CombatPrayer.MYSTIC_WILL, SpriteID.Prayeron.MYSTIC_WILL);
		SPRITES.put(CombatPrayer.MYSTIC_LORE, SpriteID.Prayeron.MYSTIC_LORE);
		SPRITES.put(CombatPrayer.MYSTIC_MIGHT, SpriteID.Prayeron.MYSTIC_MIGHT);
		SPRITES.put(CombatPrayer.AUGURY, SpriteID.Prayeron.AUGURY);
	}

	private PrayerIcon()
	{
	}

	/**
	 * @return the sprite id, or -1 for a prayer with no icon (NONE)
	 */
	public static int spriteFor(CombatPrayer prayer)
	{
		return SPRITES.getOrDefault(prayer, -1);
	}

	/**
	 * Readable name for a prayer, e.g. "Burst of Strength".
	 */
	public static String nameOf(CombatPrayer prayer)
	{
		String[] words = prayer.name().toLowerCase().split("_");
		StringBuilder name = new StringBuilder();

		for (String word : words)
		{
			if (name.length() > 0)
			{
				name.append(' ');
			}

			// "of" stays lowercase, as the game writes it.
			name.append("of".equals(word)
				? word
				: Character.toUpperCase(word.charAt(0)) + word.substring(1));
		}

		return name.toString();
	}
}
