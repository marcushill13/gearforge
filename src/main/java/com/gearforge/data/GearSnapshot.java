package com.gearforge.data;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;

/**
 * The persisted form of a player's bank. Serialised to JSON and stored per RS profile.
 * <p>
 * Only the bank is persisted. Inventory and worn equipment are repopulated from container events
 * within a tick of logging in, so storing them would only add staleness bugs.
 */
@Data
public class GearSnapshot
{
	/** Bumped when the shape of this class changes so old data can be discarded rather than misread. */
	public static final int CURRENT_VERSION = 1;

	private int version = CURRENT_VERSION;

	/** Canonical item id to quantity. */
	private Map<Integer, Integer> bank = new HashMap<>();

	/** Epoch millis of the last bank read, or 0 if never. */
	private long capturedAt;
}
