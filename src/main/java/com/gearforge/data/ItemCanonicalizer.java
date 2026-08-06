package com.gearforge.data;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;

/**
 * Resolves the several ids a single "thing you own" can appear under.
 * <p>
 * Two different notions of sameness are kept separate on purpose:
 * <ul>
 *   <li>{@link #canonicalize(int)} collapses notes and bank placeholders onto the real item. This is
 *       always correct and always applied.</li>
 *   <li>{@link #variantGroup(int)} collapses a family of related items (charged/uncharged, imbued,
 *       degraded) onto one representative. These genuinely differ in stats, so it is only used for
 *       display grouping and is user-toggleable.</li>
 * </ul>
 */
@Singleton
public class ItemCanonicalizer
{
	private final ItemManager itemManager;

	@Inject
	private ItemCanonicalizer(ItemManager itemManager)
	{
		this.itemManager = itemManager;
	}

	/**
	 * Maps a noted or placeholder id onto the real, unnoted item id.
	 */
	public int canonicalize(int itemId)
	{
		return itemManager.canonicalize(itemId);
	}

	/**
	 * Maps an item onto the base id of its variant family, e.g. every degradation state of a Barrows
	 * piece onto the undegraded one. Items with no known variants map to themselves.
	 */
	public int variantGroup(int itemId)
	{
		return ItemVariationMapping.map(itemId);
	}

	/**
	 * Every id in this item's variant family, including the item itself.
	 * <p>
	 * Resolved live rather than persisted, so saved setups pick up variants added after they were
	 * created instead of going stale.
	 */
	public Collection<Integer> variantsOf(int itemId)
	{
		Set<Integer> variants = new HashSet<>();
		variants.add(itemId);

		Collection<Integer> known = ItemVariationMapping.getVariations(variantGroup(itemId));
		if (known != null)
		{
			variants.addAll(known);
		}

		return variants;
	}
}
