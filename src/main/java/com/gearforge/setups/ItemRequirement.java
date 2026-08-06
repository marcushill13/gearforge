package com.gearforge.setups;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One item a setup asks for.
 * <p>
 * The accepted variant ids are deliberately <em>not</em> stored: they are resolved at match time from
 * the item variation mapping. Persisting them would freeze today's answer into saved data and go
 * stale the moment a new variant is released.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemRequirement
{
	private int itemId;
	private int quantity;

	/**
	 * Whether a different member of the same variant family satisfies this — a degraded Barrows piece
	 * standing in for the undegraded one, say.
	 */
	private boolean fuzzy;

	public static ItemRequirement of(int itemId)
	{
		return new ItemRequirement(itemId, 1, true);
	}
}
