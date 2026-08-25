package com.gearforge.data;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import com.gearforge.dps.CombatStyle;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Set;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemVariationMapping;

/**
 * What kind of weapon or ammunition an item is.
 * <p>
 * RuneLite's item stats say an item is a weapon but not what type, so nothing there prevents pairing
 * a crossbow with arrows. Ships as a generated resource — see
 * {@code scripts/generate-item-categories.mjs}.
 */
@Slf4j
@Singleton
public class ItemCategories
{
	private static final String RESOURCE = "/com/gearforge/item-categories.json";

	public static final String BOW = "BOW";
	public static final String OGRE_BOW = "OGRE_BOW";
	public static final String CROSSBOW = "CROSSBOW";
	public static final String BALLISTA = "BALLISTA";
	public static final String THROWN = "THROWN";
	public static final String POWERED_STAFF = "POWERED_STAFF";
	public static final String STAFF = "STAFF";

	/** Reaches two tiles, unlike every other melee weapon. */
	public static final String POLEARM = "POLEARM";

	public static final String ARROW = "ARROW";
	public static final String BRUTAL = "BRUTAL";
	public static final String BOLT = "BOLT";
	public static final String JAVELIN = "JAVELIN";

	/**
	 * Ranged weapons that make their own ammunition, listed by the family every variant maps to.
	 * <p>
	 * A bow of faerdhinen is category "Bow", and every bow was taken to need arrows in the ammo slot —
	 * so a setup holding one scored zero and was thrown away before it could be compared with
	 * anything. It was not that the bow lost to a blowpipe; it was never in the running. The same went
	 * for every crystal bow, the corrupted bows from the Gauntlet, Craw's bow and the webweaver.
	 * <p>
	 * Families rather than ids: this one entry covers the seven Prifddinas colours of the corrupted
	 * bow, both charge states, and the deadman copy.
	 */
	private static final Set<Integer> SUPPLY_OWN_AMMO = Collections.unmodifiableSet(
		new java.util.HashSet<>(java.util.Arrays.asList(
			25862,  // Bow of faerdhinen, every colour and charge state
			4212,   // Crystal bow, including the Gauntlet's basic, attuned and perfected
			23855,  // Corrupted bow
			22547,  // Craw's bow
			27652,  // Webweaver bow
			12924,  // Toxic blowpipe
			28687,  // Blazing blowpipe
			30373   // Rosewood blowpipe
		)));

	private final Map<Integer, String> categories;

	@Inject
	public ItemCategories(Gson gson)
	{
		this.categories = load(gson);
	}

	private static Map<Integer, String> load(Gson gson)
	{
		try (InputStream stream = ItemCategories.class.getResourceAsStream(RESOURCE))
		{
			if (stream == null)
			{
				log.warn("Item category resource missing; ammo pairing will not be checked");
				return Collections.emptyMap();
			}

			CategoryFile file = gson.fromJson(
				new InputStreamReader(stream, StandardCharsets.UTF_8), CategoryFile.class);

			if (file == null || file.categories == null)
			{
				return Collections.emptyMap();
			}

			log.debug("Loaded {} item categories", file.categories.size());
			return file.categories;
		}
		catch (IOException | JsonSyntaxException e)
		{
			log.warn("Could not read item categories; ammo pairing will not be checked", e);
			return Collections.emptyMap();
		}
	}

	/**
	 * @return the item's category, or null if it is unclassified — melee weapons, staves, and every
	 *     non-weapon fall here, and none of them constrain ammo.
	 */
	@Nullable
	public String categoryOf(int itemId)
	{
		return categories.get(itemId);
	}

	/**
	 * Whether this item is a ranged weapon of any kind.
	 */
	public boolean isRangedWeapon(int itemId)
	{
		String category = categoryOf(itemId);
		return BOW.equals(category)
			|| OGRE_BOW.equals(category)
			|| CROSSBOW.equals(category)
			|| BALLISTA.equals(category)
			|| THROWN.equals(category);
	}

	/**
	 * Whether a weapon can sensibly be used with a combat style.
	 * <p>
	 * Without this the optimizer will happily recommend a longsword as a ranged setup when the player
	 * owns no bow: a melee weapon contributes zero ranged attack and zero ranged strength, so it
	 * scores badly rather than being rejected, and "badly" still wins if nothing else is available.
	 * A wrong-but-confident answer is worse than saying you own nothing suitable.
	 *
	 * @param style the combat style being optimised, as {@code CombatStyle#name()}
	 */
	/**
	 * The attack styles a weapon category actually offers.
	 * <p>
	 * A whip has three options and every one of them is slash — it cannot crush at all. Recommending
	 * "melee — crush, abyssal tentacle" is not a slightly wrong answer, it is an impossible one, and the
	 * optimizer was producing exactly that because it read the crush bonus off the item and never asked
	 * whether the weapon could swing that way.
	 * <p>
	 * Transcribed from the reference calculator's style table.
	 */
	private static final Map<String, Set<CombatStyle>> STYLES_BY_CATEGORY = buildStyles();

	private static Map<String, Set<CombatStyle>> buildStyles()
	{
		Map<String, Set<CombatStyle>> styles = new HashMap<>();

		styles.put("WHIP", EnumSet.of(CombatStyle.SLASH));
		styles.put("FLAIL", EnumSet.of(CombatStyle.SLASH));
		styles.put("SLASH_SWORD", EnumSet.of(CombatStyle.SLASH, CombatStyle.STAB));
		styles.put("STAB_SWORD", EnumSet.of(CombatStyle.SLASH, CombatStyle.STAB));
		styles.put("CLAW", EnumSet.of(CombatStyle.SLASH, CombatStyle.STAB));
		styles.put("POLEARM", EnumSet.of(CombatStyle.SLASH, CombatStyle.STAB));
		styles.put("TWO_HANDED_SWORD", EnumSet.of(CombatStyle.SLASH, CombatStyle.CRUSH));
		styles.put("2H_SWORD", EnumSet.of(CombatStyle.SLASH, CombatStyle.CRUSH));
		styles.put("AXE", EnumSet.of(CombatStyle.SLASH, CombatStyle.CRUSH));
		styles.put("SCYTHE", EnumSet.of(CombatStyle.SLASH, CombatStyle.CRUSH));
		styles.put("BLUNT", EnumSet.of(CombatStyle.CRUSH));
		styles.put("BLUDGEON", EnumSet.of(CombatStyle.CRUSH));
		styles.put("POLESTAFF", EnumSet.of(CombatStyle.CRUSH));
		styles.put("BULWARK", EnumSet.of(CombatStyle.CRUSH));
		styles.put("GUN", EnumSet.of(CombatStyle.CRUSH));
		styles.put("UNARMED", EnumSet.of(CombatStyle.CRUSH));
		styles.put("PICKAXE", EnumSet.of(CombatStyle.CRUSH, CombatStyle.STAB));
		styles.put("PARTISAN", EnumSet.of(CombatStyle.CRUSH, CombatStyle.STAB));
		styles.put("SPIKED", EnumSet.of(CombatStyle.CRUSH, CombatStyle.STAB));
		styles.put("SPEAR", EnumSet.of(CombatStyle.CRUSH, CombatStyle.SLASH, CombatStyle.STAB));
		styles.put("BANNER", EnumSet.of(CombatStyle.CRUSH, CombatStyle.SLASH, CombatStyle.STAB));
		styles.put("STAFF", EnumSet.of(CombatStyle.CRUSH, CombatStyle.MAGIC));
		styles.put("BLADED_STAFF",
			EnumSet.of(CombatStyle.CRUSH, CombatStyle.SLASH, CombatStyle.STAB, CombatStyle.MAGIC));
		styles.put("POWERED_STAFF", EnumSet.of(CombatStyle.MAGIC));
		styles.put("SALAMANDER", EnumSet.of(CombatStyle.MAGIC, CombatStyle.RANGED, CombatStyle.SLASH));

		for (String ranged : new String[]{"BOW", "CROSSBOW", "THROWN", "CHINCHOMPA", "BALLISTA", "OGRE_BOW"})
		{
			styles.put(ranged, EnumSet.of(CombatStyle.RANGED));
		}

		return styles;
	}

	/**
	 * Whether this weapon can actually attack with this style.
	 * <p>
	 * An unknown category is allowed everything: refusing what we cannot classify would silently delete
	 * valid answers, which is the worse failure.
	 */
	public boolean canUseStyle(int weaponId, CombatStyle style)
	{
		Set<CombatStyle> allowed = STYLES_BY_CATEGORY.get(categoryOf(weaponId));
		return allowed == null || allowed.contains(style);
	}

	/**
	 * Whether this weapon reaches two tiles. Polearms do; everything else stops at one, which is the
	 * difference between being able to melee Zulrah and not.
	 */
	public boolean hasReach(int weaponId)
	{
		return POLEARM.equals(categoryOf(weaponId));
	}

	public boolean suitsStyle(int weaponId, String style)
	{
		String category = categoryOf(weaponId);
		boolean ranged = isRangedWeapon(weaponId);
		boolean magic = STAFF.equals(category) || POWERED_STAFF.equals(category);

		if ("RANGED".equals(style))
		{
			return ranged;
		}

		// A spell can technically be cast holding anything, but "best in slot for magic" holding a
		// melee weapon is nonsense to a player — and because spell damage does not depend on the
		// weapon, the search would otherwise pick whatever had the best incidental bonuses.
		if ("MAGIC".equals(style))
		{
			return magic;
		}

		// Melee: anything that is not a bow, crossbow or staff.
		return !ranged && !magic;
	}

	/**
	 * Whether this weapon needs ammunition to attack at all. A bow with no arrows does nothing.
	 */
	public boolean requiresAmmo(int weaponId)
	{
		if (suppliesOwnAmmo(weaponId))
		{
			return false;
		}

		String category = categoryOf(weaponId);
		return BOW.equals(category)
			|| OGRE_BOW.equals(category)
			|| CROSSBOW.equals(category)
			|| BALLISTA.equals(category);
	}

	/**
	 * Whether this weapon brings its own ammunition, so nothing in the ammo slot is needed — or
	 * counted, since a quiver of arrows worn beside a crystal bow does nothing in game.
	 */
	public boolean suppliesOwnAmmo(int weaponId)
	{
		return SUPPLY_OWN_AMMO.contains(weaponId)
			|| SUPPLY_OWN_AMMO.contains(ItemVariationMapping.map(weaponId));
	}

	/**
	 * Whether this ammunition can be fired from this weapon.
	 * <p>
	 * Unclassified weapons accept anything: melee weapons, staves and thrown weapons do not draw from
	 * the ammo slot, so constraining them would reject valid setups for no gain.
	 */
	public boolean ammoFits(int weaponId, int ammoId)
	{
		String weapon = categoryOf(weaponId);
		if (weapon == null)
		{
			return true;
		}

		String ammo = categoryOf(ammoId);

		switch (weapon)
		{
			case BOW:
				return ARROW.equals(ammo);
			// Ogre bows fire brutal arrows, and the composite also takes ordinary ones.
			case OGRE_BOW:
				return BRUTAL.equals(ammo) || ARROW.equals(ammo);
			case CROSSBOW:
				return BOLT.equals(ammo);
			// Ballistas are categorised as crossbows upstream but fire javelins.
			case BALLISTA:
				return JAVELIN.equals(ammo);
			default:
				return true;
		}
	}

	private static final class CategoryFile
	{
		int dataVersion;
		String source;
		String generatedAt;
		Map<Integer, String> categories = new HashMap<>();
	}
}
