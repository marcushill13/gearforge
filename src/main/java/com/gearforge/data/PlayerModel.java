package com.gearforge.data;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;

/**
 * The player's levels.
 * <p>
 * Real levels rather than boosted ones: best-in-slot should answer "what should I wear", not "what
 * looks good while a potion happens to be active". Reads must happen on the client thread, which is
 * why {@link #snapshot()} exists — take one there, use it anywhere.
 */
@Singleton
public class PlayerModel
{
	private final Client client;

	@Inject
	private PlayerModel(Client client)
	{
		this.client = client;
	}

	public boolean isLoggedIn()
	{
		return client.getGameState() == GameState.LOGGED_IN;
	}

	/**
	 * Whether the player currently has a slayer task, read from the amount remaining.
	 * <p>
	 * This answers "do I have a task at all", not "is this specific monster my task" — matching a task
	 * creature to a boss needs a mapping we do not ship. Callers combine it with whether the target is
	 * a slayer monster, which is a much better guess than assuming either way.
	 * <p>
	 * Must be called on the client thread.
	 */
	public boolean hasSlayerTask()
	{
		return isLoggedIn() && client.getVarpValue(VarPlayerID.SLAYER_COUNT) > 0;
	}

	/**
	 * Must be called on the client thread.
	 */
	public PlayerLevels snapshot()
	{
		return PlayerLevels.builder()
			.attack(level(Skill.ATTACK))
			.strength(level(Skill.STRENGTH))
			.defence(level(Skill.DEFENCE))
			.ranged(level(Skill.RANGED))
			.magic(level(Skill.MAGIC))
			.prayer(level(Skill.PRAYER))
			.hitpoints(level(Skill.HITPOINTS))
			.slayer(level(Skill.SLAYER))
			.onSlayerTask(hasSlayerTask())
			.build();
	}

	private int level(Skill skill)
	{
		return isLoggedIn() ? client.getRealSkillLevel(skill) : 1;
	}
}
