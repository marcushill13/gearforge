package com.gearforge;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(GearForgeConfig.GROUP)
public interface GearForgeConfig extends Config
{
	String GROUP = "gearforge";

	@ConfigItem(
		keyName = "groupVariants",
		name = "Group item variants",
		description = "Show one entry per item instead of listing every charged, imbued and degraded version separately. The best version is the one shown.",
		position = 1
	)
	default boolean groupVariants()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideZeroBonus",
		name = "Hide items with no bonus",
		description = "Hide items that give zero or a negative bonus in the stat you are looking at.",
		position = 2
	)
	default boolean hideZeroBonus()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightBank",
		name = "Highlight setup items",
		description = "Outline the active setup's items in your bank and inventory.",
		position = 3
	)
	default boolean highlightBank()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "highlightColour",
		name = "Highlight colour",
		description = "Colour of the outline drawn around the active setup's items.",
		position = 4
	)
	default Color highlightColour()
	{
		return new Color(255, 152, 31, 200);
	}
}
