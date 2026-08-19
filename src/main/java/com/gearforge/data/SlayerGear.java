package com.gearforge.data;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Gear a slayer monster obliges you to bring.
 * <p>
 * These are not preferences. An aberrant spectre cannot be fought without a nose peg — the game will
 * not let you — so a best-in-slot answer that puts a helm of neitiznot in that slot is not merely
 * suboptimal, it is unusable. The optimizer had no idea these existed and confidently recommended
 * setups nobody could equip.
 * <p>
 * Two kinds of requirement, and they are handled differently:
 * <ul>
 *     <li><b>Worn</b> — a nose peg, earmuffs, a mirror shield. The slot is forced to one of the
 *     accepted items, best first, so a slayer helmet is preferred over a bare nose peg when both are
 *     owned: it satisfies the requirement and carries stats.</li>
 *     <li><b>Carried</b> — a rock hammer, a bag of salt, an ice cooler. These finish the kill rather
 *     than occupying a slot, so they are said in the reasoning instead of being equipped.</li>
 * </ul>
 * Hand-kept, because no dataset carries it. Deliberately confined to monsters where the item is
 * genuinely required rather than merely useful.
 */
public final class SlayerGear
{
	/** Any slayer helmet satisfies the nose peg, earmuffs, facemask and spiny helmet requirements. */
	private static final int[] SLAYER_HELMETS = {11864, 11865, 26674, 19639, 19641, 19643, 21264, 21266,
		21888, 21890, 23073, 23075, 24370, 24372, 25177, 25179, 25898, 25900, 27615, 27617};

	private static final Map<String, Requirement> REQUIREMENTS = build();

	/**
	 * What a monster demands, or null if it demands nothing.
	 */
	public static final class Requirement
	{
		private final EquipmentSlot slot;
		private final List<Integer> accepted;
		private final String note;

		Requirement(@Nullable EquipmentSlot slot, List<Integer> accepted, String note)
		{
			this.slot = slot;
			this.accepted = Collections.unmodifiableList(accepted);
			this.note = note;
		}

		/** The slot that must hold one of the accepted items, or null for a carried item. */
		@Nullable
		public EquipmentSlot getSlot()
		{
			return slot;
		}

		/** Acceptable items, best first. Empty for a carried requirement. */
		public List<Integer> getAccepted()
		{
			return accepted;
		}

		/** Said in the reasoning either way, so the player knows why the slot is fixed. */
		public String getNote()
		{
			return note;
		}

		public boolean isWorn()
		{
			return slot != null;
		}
	}

	private SlayerGear()
	{
	}

	@Nullable
	public static Requirement forMonster(@Nullable Monster monster)
	{
		return monster == null
			? null
			: REQUIREMENTS.get(monster.getName().toLowerCase(Locale.ROOT));
	}

	private static Map<String, Requirement> build()
	{
		Map<String, Requirement> requirements = new LinkedHashMap<>();

		// A slayer helmet covers all four of the head-slot protections, and is worth wearing over the
		// bare item because it carries stats the bare item does not.
		worn(requirements, EquipmentSlot.HEAD, withHelmets(4168),
			"Needs a nose peg or a slayer helmet — without one you cannot fight it at all.",
			"aberrant spectre", "deviant spectre", "abhorrent spectre", "repugnant spectre");

		worn(requirements, EquipmentSlot.HEAD, withHelmets(4166),
			"Needs earmuffs or a slayer helmet — without one you cannot fight it at all.",
			"banshee", "twisted banshee");

		worn(requirements, EquipmentSlot.HEAD, withHelmets(4164),
			"Needs a facemask or a slayer helmet — without one you cannot fight it at all.",
			"dust devil", "choke devil");

		worn(requirements, EquipmentSlot.HEAD, withHelmets(4551),
			"Needs a spiny helmet or a slayer helmet.",
			"wall beast");

		// A slayer helmet does not help here: the shield is what stops the gaze.
		worn(requirements, EquipmentSlot.SHIELD, Arrays.asList(24266, 4156),
			"Needs a mirror shield or V's shield to block its gaze.",
			"basilisk", "basilisk knight", "monstrous basilisk", "basilisk sentinel");

		worn(requirements, EquipmentSlot.AMULET, Collections.singletonList(8923),
			"Needs a witchwood icon against its screech.",
			"cave horror");

		worn(requirements, EquipmentSlot.BOOTS, Collections.singletonList(7159),
			"Needs insulated boots.",
			"killerwatt");

		worn(requirements, EquipmentSlot.GLOVES, Collections.singletonList(6720),
			"Needs slayer gloves.",
			"fever spider");

		// Carried rather than worn: these finish the kill and take no slot.
		carried(requirements, "Bring a rock hammer — it will not die without one.",
			"gargoyle", "marble gargoyle");

		carried(requirements, "Bring a bag of salt — it will not die without one.",
			"rockslug");

		carried(requirements, "Bring fungicide spray — it will not die without it.",
			"zygomite");

		carried(requirements, "Bring an ice cooler to finish it.",
			"desert lizard", "lizard", "small lizard");

		carried(requirements, "Only a leaf-bladed weapon or Magic Dart will hurt it.",
			"kurask", "turoth");

		return requirements;
	}

	/**
	 * The bare protective item, then every slayer helmet. Helmets come first once sorted by the
	 * optimizer, but the bare item is kept so someone without a helmet still gets an answer.
	 */
	private static List<Integer> withHelmets(int bareItem)
	{
		java.util.List<Integer> accepted = new java.util.ArrayList<>();
		for (int helmet : SLAYER_HELMETS)
		{
			accepted.add(helmet);
		}

		accepted.add(bareItem);
		return accepted;
	}

	private static void worn(
		Map<String, Requirement> into, EquipmentSlot slot, List<Integer> accepted, String note,
		String... monsters)
	{
		for (String monster : monsters)
		{
			into.put(monster, new Requirement(slot, accepted, note));
		}
	}

	private static void carried(Map<String, Requirement> into, String note, String... monsters)
	{
		for (String monster : monsters)
		{
			into.put(monster, new Requirement(null, Collections.emptyList(), note));
		}
	}
}
