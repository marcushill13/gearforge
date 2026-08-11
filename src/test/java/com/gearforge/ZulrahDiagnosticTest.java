package com.gearforge;

import com.gearforge.data.EquipmentStats;
import com.gearforge.dps.CombatContext;
import com.gearforge.dps.CombatPrayer;
import com.gearforge.dps.CombatStyle;
import com.gearforge.dps.DpsEngine;
import com.gearforge.dps.Potion;
import com.gearforge.data.ItemCategories;
import com.gearforge.data.Monster;
import com.gearforge.data.MonsterRepository;
import com.gearforge.data.PlayerLevels;
import com.gearforge.data.Reachability;
import com.google.gson.Gson;
import net.runelite.api.gameval.ItemID;
import com.gearforge.dps.SetupScore;
import com.gearforge.dps.Target;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A reported max hit of 52 with a blade of saeldor at Zulrah. Reproduced here from the numbers the
 * panel says it assumed, so the figure can be checked against what the weapon can actually do rather
 * than argued about.
 */
public class ZulrahDiagnosticTest
{
	@Test
	public void reportTheMeleeMaximum()
	{
		PlayerLevels maxed = PlayerLevels.builder()
			.attack(99).strength(99).defence(99).ranged(99).magic(99)
			.prayer(99).hitpoints(99).slayer(99).herblore(99)
			.build();

		// Roughly the setup the panel showed: saeldor, torva helm, blood moon body and legs, torture,
		// infernal cape, barrows gloves, dragon boots, berserker ring.
		int strengthBonus = 89 + 4 + 10 + 8 + 12 + 4 + 8 + 6 + 4;

		CombatContext context = CombatContext.builder()
			.attackLevel(99)
			.strengthLevel(99)
			.strengthBoost(Potion.SUPER_COMBAT.strengthBoost(maxed))
			.attackBoost(Potion.SUPER_COMBAT.attackBoost(maxed))
			.prayer(CombatPrayer.PIETY)
			.style(CombatStyle.SLASH)
			.equipment(EquipmentStats.builder().aslash(150).strength(strengthBonus).build())
			.target(Target.builder()
				.name("Zulrah")
				.defenceLevel(300)
				.defensiveBonuses(EquipmentStats.builder().dslash(0).build())
				.build())
			.weaponSpeedTicks(4)
			.build();

		SetupScore score = new DpsEngine().score(context);

		System.out.printf(
			"Saeldor at Zulrah: strength bonus %d, effective strength %d, max hit %d, accuracy %.1f%%%n",
			strengthBonus, score.getEffectiveStrengthLevel(), score.getMaxHit(), score.accuracyPercent());

		// A blade of saeldor with a super combat and piety tops out around 50. Anything far above that
		// means something is being counted twice.
		assertTrue("Max hit " + score.getMaxHit() + " is higher than the weapon can produce",
			score.getMaxHit() <= 52);
	}

	/**
	 * The reported bug: a blade of saeldor recommended for Zulrah on the Bosses tab. It cannot reach.
	 * The BiS tab had the filter; this tab was a separate path that never received it.
	 */
	@Test
	public void aSaeldorCannotBeOfferedForZulrah()
	{
		Monster zulrah = new MonsterRepository(new Gson()).all().stream()
			.filter(monster -> monster.getName().equalsIgnoreCase("Zulrah"))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Zulrah missing"));

		ItemCategories categories = new ItemCategories(new Gson());

		assertTrue("Zulrah needs reach", Reachability.requiresReach(zulrah));
		assertFalse("A blade of saeldor is not a polearm",
			categories.hasReach(ItemID.BLADE_OF_SAELDOR));
		assertFalse("So it cannot be offered",
			Reachability.meleeCanReach(zulrah, categories.hasReach(ItemID.BLADE_OF_SAELDOR)));

		// A dragon warhammer cannot reach it either, which is why it must not be suggested as a spec.
		assertFalse(Reachability.meleeCanReach(zulrah, categories.hasReach(ItemID.DRAGON_WARHAMMER)));

		// A halberd can, and that is the answer people actually use.
		assertTrue(Reachability.meleeCanReach(zulrah, categories.hasReach(ItemID.DRAGON_HALBERD)));
	}
}
