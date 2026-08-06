package com.gearforge.setups;

import com.gearforge.data.BankModel;
import com.gearforge.data.EquipmentSlot;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Turns a setup into a shareable code and back.
 * <p>
 * Self-contained by design — no server, no lookup, nothing to go offline. The code carries everything
 * needed to rebuild the setup.
 * <p>
 * Format: {@code GF1|<base64 name>|<slot:itemId,...>|<inventory ids,...>}. Deliberately not raw JSON
 * in base64: this stays short enough to paste into a chat message, and stays readable enough that a
 * malformed code can be diagnosed by eye. The name is base64'd so a pipe or comma in it cannot break
 * parsing.
 */
public final class SetupCodec
{
	private static final String PREFIX = "GF1";
	private static final String SECTION = "\\|";
	private static final int SECTIONS = 4;

	private SetupCodec()
	{
	}

	public static String encode(Setup setup)
	{
		StringBuilder code = new StringBuilder(PREFIX).append('|');

		code.append(Base64.getUrlEncoder().withoutPadding()
			.encodeToString(setup.getName().getBytes(StandardCharsets.UTF_8)));
		code.append('|');

		boolean first = true;
		for (Map.Entry<EquipmentSlot, ItemRequirement> entry : setup.getEquipment().entrySet())
		{
			if (!first)
			{
				code.append(',');
			}

			code.append(entry.getKey().getSlotIndex()).append(':').append(entry.getValue().getItemId());
			first = false;
		}

		code.append('|');

		for (int slot = 0; slot < setup.getInventory().size(); slot++)
		{
			if (slot > 0)
			{
				code.append(',');
			}

			ItemRequirement requirement = setup.getInventory().get(slot);
			code.append(requirement == null ? BankModel.EMPTY_SLOT : requirement.getItemId());
		}

		return code.toString();
	}

	/**
	 * @return the decoded setup, or null if the code is not a GearForge code or is malformed. Never
	 *     throws — a mistyped code is a normal thing for a user to do, not an error condition.
	 */
	@Nullable
	public static Setup decode(String code)
	{
		if (code == null)
		{
			return null;
		}

		String[] sections = code.trim().split(SECTION, -1);
		if (sections.length != SECTIONS || !PREFIX.equals(sections[0]))
		{
			return null;
		}

		try
		{
			String name = new String(
				Base64.getUrlDecoder().decode(sections[1]), StandardCharsets.UTF_8);

			Setup setup = Setup.named(name.isEmpty() ? "Shared setup" : name, SetupSource.SHARED);

			if (!sections[2].isEmpty())
			{
				for (String pair : sections[2].split(","))
				{
					String[] parts = pair.split(":");
					if (parts.length != 2)
					{
						return null;
					}

					EquipmentSlot slot = EquipmentSlot.fromSlotIndex(Integer.parseInt(parts[0]));
					if (slot != null)
					{
						setup.put(slot, Integer.parseInt(parts[1]));
					}
				}
			}

			if (!sections[3].isEmpty())
			{
				String[] ids = sections[3].split(",");
				int[] inventory = new int[Math.min(ids.length, BankModel.INVENTORY_SIZE)];
				for (int slot = 0; slot < inventory.length; slot++)
				{
					inventory[slot] = Integer.parseInt(ids[slot]);
				}

				setup.setInventoryFrom(inventory);
			}

			return setup.size() == 0 ? null : setup;
		}
		catch (IllegalArgumentException e)
		{
			// Covers both malformed base64 and unparseable numbers.
			return null;
		}
	}
}
