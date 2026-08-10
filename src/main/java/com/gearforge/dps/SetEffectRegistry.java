package com.gearforge.dps;

import com.gearforge.data.GearItem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Singleton;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemVariationMapping;

/**
 * Item effects that depend on the whole setup and the target, not on one slot.
 * <p>
 * These are what make setup scoring differ from per-slot ranking: a slayer helm is worth nothing off
 * task, void is worth nothing until all four pieces are present, and salve does not stack with a
 * slayer helm so wearing both wastes one.
 * <p>
 * <b>Coverage is deliberately partial.</b> Only effects whose multipliers were read from the OSRS
 * Wiki or its DPS calculator's source are implemented: void, salve, slayer helm / black mask, and the
 * bane weapons below. Still missing — twisted bow (scales off the target's Magic level rather than a
 * constant), Inquisitor's, scythe, obsidian, the vampyre weapons, Scorching bow, and demonbane
 * spells. Guessing their multipliers would produce confidently wrong numbers.
 * <p>
 * Two known simplifications, both of which <em>under</em>-state rather than over-state DPS: the
 * keris family's 1-in-51 triple-damage proc is not modelled, and per-monster demonbane effectiveness
 * (Duke Sucellus resists part of it) is ignored.
 * <p>
 * Dharok's is deliberately absent: its bonus scales with missing hitpoints, so at full health it is
 * just a slow greataxe and recommending it as best-in-slot would mislead.
 */
@Singleton
public class SetEffectRegistry
{
	private static final int VOID_MELEE_HELM = ItemID.GAME_PEST_MELEE_HELM;
	private static final int VOID_RANGED_HELM = ItemID.GAME_PEST_ARCHER_HELM;
	private static final int VOID_MAGE_HELM = ItemID.GAME_PEST_MAGE_HELM;
	private static final int VOID_TOP = ItemID.PEST_VOID_KNIGHT_TOP;
	private static final int VOID_ROBES = ItemID.PEST_VOID_KNIGHT_ROBES;
	private static final int VOID_GLOVES = ItemID.PEST_VOID_KNIGHT_GLOVES;
	private static final int ELITE_VOID_TOP = ItemID.ELITE_VOID_KNIGHT_TOP;
	private static final int ELITE_VOID_ROBES = ItemID.ELITE_VOID_KNIGHT_ROBES;

	private static final int SALVE_AMULET_I = ItemID.NZONE_SALVE_AMULET;
	private static final int SALVE_AMULET_EI = ItemID.NZONE_SALVE_AMULET_E;

	private static final int BLACK_MASK_I = ItemID.NZONE_BLACK_MASK;
	private static final int SLAYER_HELM_I = ItemID.SLAYER_HELM_I;

	private static final int DRAGON_HUNTER_LANCE = ItemID.DRAGONHUNTER_LANCE;
	private static final int DRAGON_HUNTER_CROSSBOW = ItemID.DRAGONHUNTER_XBOW;
	private static final int DRAGON_HUNTER_WAND = ItemID.DRAGONHUNTER_WAND;

	private static final int ARCLIGHT = ItemID.ARCLIGHT;
	private static final int EMBERLIGHT = ItemID.EMBERLIGHT;
	private static final int DARKLIGHT = ItemID.DARKLIGHT;
	private static final int SILVERLIGHT = ItemID.SILVERLIGHT;

	private static final int BARRONITE_MACE = ItemID.BARRONITE_MACE;
	private static final int LEAF_BLADED_BATTLEAXE = ItemID.LEAFBLADED_BATTLEAXE;

	private static final int TWISTED_BOW = ItemID.TWISTED_BOW;

	private static final int BONE_CLAWS = ItemID.BONE_CLAWS;

	private static final int BLISTERWOOD_FLAIL = ItemID.BLISTERWOOD_FLAIL;
	private static final int BLISTERWOOD_SICKLE = ItemID.BLISTERWOOD_SICKLE;
	private static final int BLISTERWOOD_STAKE = ItemID.BLISTERWOOD_STAKE;
	private static final int HALLOWED_FLAIL = ItemID.HALLOWED_FLAIL;
	private static final int IVANDIS_FLAIL = ItemID.IVANDIS_FLAIL;
	private static final int ROD_OF_IVANDIS = ItemID.BURGH_ROD_COMMAND_FINAL_10;
	private static final int SILVER_SICKLE = ItemID.SILVER_SICKLE;
	private static final int GADDERHAMMER = ItemID.GADDERANKS_WARHAMMER;
	private static final int SCORCHING_BOW = ItemID.SCORCHING_BOW;

	private static final int CRYSTAL_HELM = ItemID.CRYSTAL_HELMET;
	private static final int CRYSTAL_BODY = ItemID.CRYSTAL_CHESTPLATE;
	private static final int CRYSTAL_LEGS = ItemID.CRYSTAL_PLATELEGS;
	private static final int CRYSTAL_BOW = ItemID.CRYSTAL_BOW;
	private static final int BOW_OF_FAERDHINEN = ItemID.BOW_OF_FAERDHINEN;

	private static final int INQUISITORS_HELM = ItemID.INQUISITORS_HELM;
	private static final int INQUISITORS_BODY = ItemID.INQUISITORS_BODY;
	private static final int INQUISITORS_SKIRT = ItemID.INQUISITORS_SKIRT;

	/** The twisted bow stops scaling here, or higher inside the Chambers of Xeric. */
	private static final int TBOW_CAP = 250;
	private static final int TBOW_XERICIAN_CAP = 350;

	private static final Set<Integer> KERIS_FAMILY = new HashSet<>(Arrays.asList(
		ItemID.KERIS_PARTISAN,
		ItemID.KERIS_PARTISAN_BREACH,
		ItemID.KERIS_PARTISAN_CORRUPTION,
		ItemID.KERIS_PARTISAN_SUN,
		ItemID.KERIS_PARTISAN_AMASCUT));

	private static final double SEVEN_SIXTHS = 7.0 / 6.0;

	/**
	 * Every item this registry cares about. The optimizer feeds these into its candidate pool so a
	 * set effect is never missed just because a piece scored poorly on raw stats.
	 */
	public Set<Integer> relevantItemIds()
	{
		Set<Integer> ids = new HashSet<>();
		ids.add(VOID_MELEE_HELM);
		ids.add(VOID_RANGED_HELM);
		ids.add(VOID_MAGE_HELM);
		ids.add(VOID_TOP);
		ids.add(VOID_ROBES);
		ids.add(VOID_GLOVES);
		ids.add(ELITE_VOID_TOP);
		ids.add(ELITE_VOID_ROBES);
		ids.add(SALVE_AMULET_I);
		ids.add(SALVE_AMULET_EI);
		ids.add(BLACK_MASK_I);
		ids.add(SLAYER_HELM_I);
		ids.add(DRAGON_HUNTER_LANCE);
		ids.add(DRAGON_HUNTER_CROSSBOW);
		ids.add(DRAGON_HUNTER_WAND);
		ids.add(ARCLIGHT);
		ids.add(EMBERLIGHT);
		ids.add(DARKLIGHT);
		ids.add(SILVERLIGHT);
		ids.add(BARRONITE_MACE);
		ids.add(LEAF_BLADED_BATTLEAXE);
		ids.addAll(KERIS_FAMILY);
		ids.add(TWISTED_BOW);
		ids.add(INQUISITORS_HELM);
		ids.add(INQUISITORS_BODY);
		ids.add(INQUISITORS_SKIRT);
		return ids;
	}

	public SetEffects evaluate(Collection<GearItem> equipped, CombatStyle style, Target target, boolean onSlayerTask)
	{
		Set<Integer> ids = new HashSet<>();
		for (GearItem item : equipped)
		{
			ids.add(item.getItemId());
		}

		List<String> notes = new ArrayList<>();
		VoidSet voidSet = detectVoid(ids, style, notes);

		double accuracy = 1.0;
		double damage = 1.0;

		// Salve and slayer both key off the target and explicitly do not stack, so take the better one
		// rather than multiplying — wearing both is a waste, and modelling it as a stack inflates DPS.
		double salve = salveMultiplier(ids, style, target, notes);
		double slayer = slayerMultiplier(ids, style, onSlayerTask, notes);

		double targetBonus = Math.max(salve, slayer);
		if (targetBonus > 1.0)
		{
			accuracy *= targetBonus;
			damage *= targetBonus;

			if (salve > 1.0 && slayer > 1.0)
			{
				notes.add(salve >= slayer
					? "Slayer helm ignored — salve does not stack with it"
					: "Salve ignored — slayer helm does not stack with it");
			}
		}

		// Bane weapons stack multiplicatively on top of slayer and salve rather than replacing them.
		accuracy *= baneAccuracy(ids, style, target, notes);
		damage *= baneDamage(ids, style, target, notes);

		double inquisitor = inquisitorMultiplier(ids, style, notes);
		accuracy *= inquisitor;
		damage *= inquisitor;

		double[] crystal = crystalArmour(ids, style, notes);
		accuracy *= crystal[0];
		damage *= crystal[1];

		// The twisted bow is a bow: its scaling applies to its own shots, not to spells.
		if (style.isRanged() && ids.contains(TWISTED_BOW))
		{
			int magic = twistedBowMagic(target);
			accuracy *= twistedBowScaling(magic, true);
			damage *= twistedBowScaling(magic, false);
			notes.add("Twisted bow: scaled to " + magic + " magic");
		}

		return new SetEffects(accuracy, damage, voidSet, notes);
	}

	/**
	 * Weapons with a bane passive against a monster type. Only one weapon can be wielded, so at most
	 * one applies.
	 * <p>
	 * Accuracy and damage differ for the dragon hunter crossbow (30% and 25%), which is why they are
	 * computed separately rather than sharing one multiplier.
	 */
	private double baneAccuracy(Set<Integer> ids, CombatStyle style, Target target, List<String> notes)
	{
		if (target.hasAttribute(MonsterAttribute.DRAGON))
		{
			if (style.isMagic() && ids.contains(DRAGON_HUNTER_WAND))
			{
				notes.add("Dragon hunter wand: target is draconic");
				return 7.0 / 4.0;
			}

			if (style.isRanged() && ids.contains(DRAGON_HUNTER_CROSSBOW))
			{
				notes.add("Dragon hunter crossbow: target is draconic");
				return 1.30;
			}

			if (style.isMelee() && ids.contains(DRAGON_HUNTER_LANCE))
			{
				notes.add("Dragon hunter lance: target is draconic");
				return 1.20;
			}
		}

		// The demonbane weapons are all melee. Their passive belongs to their own attacks, so it must
		// not inflate a spell cast while merely holding one.
		if (style.isMelee() && target.hasAttribute(MonsterAttribute.DEMON))
		{
			if (ids.contains(ARCLIGHT) || ids.contains(EMBERLIGHT))
			{
				notes.add(ids.contains(ARCLIGHT)
					? "Arclight: target is demonic"
					: "Emberlight: target is demonic");
				return 1.70;
			}

			if (ids.contains(DARKLIGHT) || ids.contains(SILVERLIGHT))
			{
				notes.add(ids.contains(DARKLIGHT)
					? "Darklight: target is demonic"
					: "Silverlight: target is demonic");
				return 1.60;
			}

			if (ids.contains(BONE_CLAWS))
			{
				notes.add("Burning claws: target is demonic");
				return 1.05;
			}
		}

		// The ranged demonbane. Demonbane is not a melee-only idea, and treating it as one left the
		// scorching bow scoring as an ordinary bow against the things it is built for.
		if (style.isRanged()
			&& target.hasAttribute(MonsterAttribute.DEMON)
			&& ids.contains(SCORCHING_BOW))
		{
			notes.add("Scorching bow: target is demonic");
			return 1.30;
		}

		if (style.isMelee() && isVampyre(target))
		{
			if (ids.contains(HALLOWED_FLAIL) || ids.contains(ItemID.SUNSPEAR))
			{
				notes.add("Vampyre bane: target is a vampyre");
				return 1.25;
			}

			if (ids.contains(BLISTERWOOD_FLAIL) || ids.contains(BLISTERWOOD_SICKLE))
			{
				notes.add("Blisterwood: target is a vampyre");
				return 1.05;
			}
		}

		// Only the breaching partisan improves accuracy; the rest of the family is damage only.
		if (style.isMelee()
			&& target.hasAttribute(MonsterAttribute.KALPHITE)
			&& ids.contains(ItemID.KERIS_PARTISAN_BREACH))
		{
			notes.add("Keris partisan of breaching: target is a kalphite");
			return 133.0 / 100.0;
		}

		return 1.0;
	}

	private double baneDamage(Set<Integer> ids, CombatStyle style, Target target, List<String> notes)
	{
		if (target.hasAttribute(MonsterAttribute.DRAGON))
		{
			if (style.isMelee() && ids.contains(DRAGON_HUNTER_LANCE))
			{
				return 1.20;
			}

			if (style.isRanged() && ids.contains(DRAGON_HUNTER_CROSSBOW))
			{
				return 1.25;
			}

			if (style.isMagic() && ids.contains(DRAGON_HUNTER_WAND))
			{
				return 7.0 / 5.0;
			}
		}

		// Everything below is a melee weapon's own passive and does nothing for spells or arrows.
		if (!style.isMelee())
		{
			return 1.0;
		}

		if (target.hasAttribute(MonsterAttribute.DEMON))
		{
			if (ids.contains(ARCLIGHT) || ids.contains(EMBERLIGHT))
			{
				return 1.70;
			}

			if (ids.contains(BONE_CLAWS))
			{
				return 1.05;
			}

			// Darklight and silverlight raise the attack roll only. They had a damage bonus here that
			// the game does not give them, which overstated them by 60% against every demon.
		}

		// The vampyre banes, strongest first. Each supersedes the next rather than stacking.
		if (isVampyre(target))
		{
			if (ids.contains(ItemID.SUNSPEAR))
			{
				notes.add("Sunspear: target is a vampyre");
				return 1.50;
			}

			if (ids.contains(BLISTERWOOD_FLAIL) || ids.contains(HALLOWED_FLAIL)
				|| ids.contains(BLISTERWOOD_STAKE))
			{
				notes.add("Blisterwood: target is a vampyre");
				return 1.25;
			}

			if (ids.contains(BLISTERWOOD_SICKLE))
			{
				notes.add("Blisterwood sickle: target is a vampyre");
				return 23.0 / 20.0;
			}

			if (ids.contains(IVANDIS_FLAIL))
			{
				notes.add("Ivandis flail: target is a vampyre");
				return 6.0 / 5.0;
			}

			// The rod does nothing to the strongest tier.
			if (wearingFamily(ids, ROD_OF_IVANDIS) && !target.hasAttribute(MonsterAttribute.VAMPYRE3))
			{
				notes.add("Rod of ivandis: target is a vampyre");
				return 1.10;
			}

			if (target.hasAttribute(MonsterAttribute.VAMPYRE1) && ids.contains(SILVER_SICKLE))
			{
				notes.add("Silver: target is a lesser vampyre");
				return 1.10;
			}
		}

		if (target.hasAttribute(MonsterAttribute.SHADE) && ids.contains(GADDERHAMMER))
		{
			// A twentieth of its hits land for double rather than a quarter more; averaged, that is a
			// little under 29% overall.
			notes.add("Gadderhammer: target is a shade");
			return 0.95 * 1.25 + 0.05 * 2.0;
		}

		if (target.hasAttribute(MonsterAttribute.KALPHITE) && wearingKeris(ids))
		{
			// The amascut partisan trades the family's damage bonus down for effects we do not model.
			double multiplier = ids.contains(ItemID.KERIS_PARTISAN_AMASCUT) ? 1.15 : 133.0 / 100.0;
			notes.add("Keris: target is a kalphite");
			return multiplier;
		}

		if (target.hasAttribute(MonsterAttribute.GOLEM) && ids.contains(BARRONITE_MACE))
		{
			notes.add("Barronite mace: target is a golem");
			return 23.0 / 20.0;
		}

		if (target.hasAttribute(MonsterAttribute.LEAFY) && ids.contains(LEAF_BLADED_BATTLEAXE))
		{
			notes.add("Leaf-bladed battleaxe: target is leafy");
			return 47.0 / 40.0;
		}

		return 1.0;
	}

	/**
	 * Inquisitor's armour, which only does anything on the crush attack style.
	 * <p>
	 * Not a full-set-only bonus: each piece counts on its own, worth 1 for the helm and 2 each for the
	 * hauberk and plateskirt, applied as {@code (200 + n) / 200} to both accuracy and damage. The full
	 * set therefore gives 2.5%, but two pieces are still worth wearing.
	 */
	/**
	 * Crystal armour, which does nothing at all unless a crystal bow or a bow of faerdhinen is drawn.
	 * <p>
	 * Each piece carries its own bonus and they add rather than multiply: the helmet is worth 5%
	 * accuracy and 2.5% damage, the body 15% and 7.5%, the legs 10% and 5%. The full set is +30%
	 * accuracy and +15% damage, which is most of the reason the bow is worth using at all.
	 * <p>
	 * Leaving this out made the optimizer pick rune knives with a shield over a bow of faerdhinen,
	 * because without the set the bow is just a two-hander that costs you the shield slot.
	 *
	 * @return accuracy and damage multipliers, both 1.0 when the set does not apply
	 */
	/**
	 * Any of the three vampyre tiers. They are separate attributes because the banes treat them
	 * differently — the rod is useless against the strongest, and silver only troubles the weakest.
	 */
	private static boolean isVampyre(Target target)
	{
		return target.hasAttribute(MonsterAttribute.VAMPYRE1)
			|| target.hasAttribute(MonsterAttribute.VAMPYRE2)
			|| target.hasAttribute(MonsterAttribute.VAMPYRE3);
	}

	private static double[] crystalArmour(Set<Integer> ids, CombatStyle style, List<String> notes)
	{
		if (!style.isRanged() || !(wearingFamily(ids, CRYSTAL_BOW) || wearingFamily(ids, BOW_OF_FAERDHINEN)))
		{
			return new double[]{1.0, 1.0};
		}

		double accuracy = 0;
		double damage = 0;
		int pieces = 0;

		if (wearingFamily(ids, CRYSTAL_HELM))
		{
			accuracy += 0.05;
			damage += 0.025;
			pieces++;
		}

		if (wearingFamily(ids, CRYSTAL_BODY))
		{
			accuracy += 0.15;
			damage += 0.075;
			pieces++;
		}

		if (wearingFamily(ids, CRYSTAL_LEGS))
		{
			accuracy += 0.10;
			damage += 0.05;
			pieces++;
		}

		if (pieces == 0)
		{
			return new double[]{1.0, 1.0};
		}

		notes.add(pieces == 3
			? "Crystal armour: full set with a crystal bow"
			: "Crystal armour: " + pieces + " of 3 pieces with a crystal bow");

		return new double[]{1 + accuracy, 1 + damage};
	}

	/**
	 * Whether any worn item belongs to the same variant family as this one. Crystal gear arrives in a
	 * charge state per piece and a colour per clan, and every one of them behaves identically.
	 */
	private static boolean wearingFamily(Set<Integer> ids, int exemplar)
	{
		int family = ItemVariationMapping.map(exemplar);
		for (int id : ids)
		{
			if (id == exemplar || ItemVariationMapping.map(id) == family)
			{
				return true;
			}
		}

		return false;
	}

	private double inquisitorMultiplier(Set<Integer> ids, CombatStyle style, List<String> notes)
	{
		if (style != CombatStyle.CRUSH)
		{
			return 1.0;
		}

		int bonus = 0;
		if (ids.contains(INQUISITORS_HELM))
		{
			bonus += 1;
		}

		if (ids.contains(INQUISITORS_BODY))
		{
			bonus += 2;
		}

		if (ids.contains(INQUISITORS_SKIRT))
		{
			bonus += 2;
		}

		if (bonus == 0)
		{
			return 1.0;
		}

		notes.add(bonus == 5
			? "Full Inquisitor's on crush"
			: "Inquisitor's pieces on crush");

		return (200.0 + bonus) / 200.0;
	}

	/**
	 * The magic value the twisted bow scales against: the higher of the target's Magic level and its
	 * magic attack bonus, capped.
	 */
	public static int twistedBowMagic(Target target)
	{
		int cap = target.hasAttribute(MonsterAttribute.XERICIAN) ? TBOW_XERICIAN_CAP : TBOW_CAP;
		return Math.min(cap, Math.max(target.getMagicLevel(), target.getMagicAttack()));
	}

	/**
	 * The twisted bow's scaling curve, returned as a multiplier.
	 * <p>
	 * Unlike every other bane weapon this is a formula rather than a constant, and it truncates at
	 * each step — the intermediate {@code /100} divisions are integer division, not real division, so
	 * the result differs from the algebraically equivalent form.
	 *
	 * @param accuracyMode true for the accuracy curve, false for damage
	 */
	public static double twistedBowScaling(int magic, boolean accuracyMode)
	{
		int factor = accuracyMode ? 10 : 14;
		int base = accuracyMode ? 140 : 250;
		int clamp = accuracyMode ? 140 : 250;

		int t2 = (3 * magic - factor) / 100;
		int inner = (3 * magic / 10) - (10 * factor);
		int t3 = (inner * inner) / 100;

		int bonus = Math.max(0, Math.min(clamp, base + t2 - t3));
		return bonus / 100.0;
	}

	private static boolean wearingKeris(Set<Integer> ids)
	{
		for (int keris : KERIS_FAMILY)
		{
			if (ids.contains(keris))
			{
				return true;
			}
		}

		return false;
	}

	private VoidSet detectVoid(Set<Integer> ids, CombatStyle style, List<String> notes)
	{
		boolean hasGloves = ids.contains(VOID_GLOVES);
		boolean regularTop = ids.contains(VOID_TOP);
		boolean eliteTop = ids.contains(ELITE_VOID_TOP);
		boolean regularRobes = ids.contains(VOID_ROBES);
		boolean eliteRobes = ids.contains(ELITE_VOID_ROBES);

		boolean hasBody = regularTop || eliteTop;
		boolean hasLegs = regularRobes || eliteRobes;
		boolean elite = eliteTop && eliteRobes;

		if (!hasGloves || !hasBody || !hasLegs)
		{
			return VoidSet.NONE;
		}

		if (style.isMelee() && ids.contains(VOID_MELEE_HELM))
		{
			notes.add("Void melee set complete");
			return VoidSet.MELEE;
		}

		if (style.isRanged() && ids.contains(VOID_RANGED_HELM))
		{
			notes.add(elite ? "Elite void ranged set complete" : "Void ranged set complete");
			return elite ? VoidSet.ELITE_RANGED : VoidSet.RANGED;
		}

		if (style.isMagic() && ids.contains(VOID_MAGE_HELM))
		{
			notes.add("Void magic set complete");
			return VoidSet.MAGIC;
		}

		return VoidSet.NONE;
	}

	private double salveMultiplier(Set<Integer> ids, CombatStyle style, Target target, List<String> notes)
	{
		if (!target.hasAttribute(MonsterAttribute.UNDEAD))
		{
			return 1.0;
		}

		if (ids.contains(SALVE_AMULET_EI))
		{
			notes.add("Salve (ei): target is undead");
			return 1.2;
		}

		if (ids.contains(SALVE_AMULET_I))
		{
			double multiplier = style.isMagic() ? 1.15 : SEVEN_SIXTHS;
			notes.add("Salve (i): target is undead");
			return multiplier;
		}

		return 1.0;
	}

	private double slayerMultiplier(Set<Integer> ids, CombatStyle style, boolean onSlayerTask, List<String> notes)
	{
		if (!onSlayerTask)
		{
			return 1.0;
		}

		if (!ids.contains(SLAYER_HELM_I) && !ids.contains(BLACK_MASK_I))
		{
			return 1.0;
		}

		// Melee gets 7/6; ranged and magic get 1.15 from the imbued versions.
		double multiplier = style.isMelee() ? SEVEN_SIXTHS : 1.15;
		notes.add("Slayer helm: target is on task");
		return multiplier;
	}
}
