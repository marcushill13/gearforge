package com.gearforge.setups;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Saved setups, persisted per RuneScape account.
 * <p>
 * Remembers which setup was last shown in the bank so it can be restored after a relog. What is
 * <em>currently</em> shown is owned by {@code BankFilterService}, because that can also be an unsaved
 * preview from the BiS tab.
 */
@Slf4j
@Singleton
public class SetupStore
{
	private static final String CONFIG_GROUP = "gearforge";
	private static final String SETUPS_KEY = "setups";
	private static final String ACTIVE_KEY = "activeSetup";

	private final ConfigManager configManager;
	private final Gson gson;

	private final List<Setup> setups = new ArrayList<>();
	private String activeSetupId;

	@Inject
	private SetupStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	public List<Setup> all()
	{
		return Collections.unmodifiableList(setups);
	}

	public boolean isEmpty()
	{
		return setups.isEmpty();
	}

	public void add(Setup setup)
	{
		setups.add(setup);
		save();
	}

	/**
	 * Persists an in-place edit to a setup, such as dismissing an upgrade suggestion.
	 */
	public void persist()
	{
		save();
	}

	public void remove(String id)
	{
		setups.removeIf(setup -> id.equals(setup.getId()));

		if (id.equals(activeSetupId))
		{
			activeSetupId = null;
		}

		save();
	}

	@Nullable
	public Setup active()
	{
		if (activeSetupId == null)
		{
			return null;
		}

		for (Setup setup : setups)
		{
			if (activeSetupId.equals(setup.getId()))
			{
				return setup;
			}
		}

		return null;
	}

	public boolean isActive(Setup setup)
	{
		return setup.getId().equals(activeSetupId);
	}

	/**
	 * Makes a setup the one the bank highlights. Activating never withdraws or moves anything.
	 */
	public void activate(Setup setup)
	{
		activeSetupId = setup.getId();
		setup.setLastUsedMillis(System.currentTimeMillis());
		save();
	}

	public void deactivate()
	{
		activeSetupId = null;
		save();
	}

	/**
	 * Loads setups for the active RS profile. Clears first so one account's setups cannot leak into
	 * another's after a hop.
	 */
	public void load()
	{
		setups.clear();
		activeSetupId = null;

		if (configManager.getRSProfileKey() == null)
		{
			return;
		}

		String json = configManager.getRSProfileConfiguration(CONFIG_GROUP, SETUPS_KEY);
		if (json != null && !json.isEmpty())
		{
			try
			{
				SetupsFile file = gson.fromJson(json, SetupsFile.class);
				if (file != null && file.setups != null)
				{
					setups.addAll(file.setups);
				}
			}
			catch (JsonSyntaxException e)
			{
				log.warn("Could not read saved setups, starting fresh", e);
			}
		}

		activeSetupId = configManager.getRSProfileConfiguration(CONFIG_GROUP, ACTIVE_KEY);
		log.debug("Loaded {} setups", setups.size());
	}

	private void save()
	{
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}

		SetupsFile file = new SetupsFile();
		file.setups = new ArrayList<>(setups);
		configManager.setRSProfileConfiguration(CONFIG_GROUP, SETUPS_KEY, gson.toJson(file));

		if (activeSetupId == null)
		{
			configManager.unsetRSProfileConfiguration(CONFIG_GROUP, ACTIVE_KEY);
		}
		else
		{
			configManager.setRSProfileConfiguration(CONFIG_GROUP, ACTIVE_KEY, activeSetupId);
		}
	}

	/**
	 * A concrete wrapper so Gson can resolve the list's element type without a TypeToken.
	 */
	private static final class SetupsFile
	{
		List<Setup> setups = new ArrayList<>();
	}
}
