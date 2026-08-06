package com.gearforge;

import com.gearforge.bank.BankFilterService;
import com.gearforge.bank.BankHighlighter;
import com.gearforge.data.BankModel;
import com.gearforge.setups.Setup;
import com.gearforge.setups.SetupStore;
import com.gearforge.ui.GearForgePanel;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "GearForge",
	description = "Rank everything you own by equipment stat, per slot. Pairs well with Auto Bank Sorter and Bank Tags.",
	tags = {"gear", "equipment", "bank", "stats", "bis", "loadout", "combat"}
)
public class GearForgePlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private BankModel bankModel;

	@Inject
	private SetupStore setupStore;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BankHighlighter bankHighlighter;

	@Inject
	private BankFilterService bankFilterService;

	@Inject
	private GearForgePanel panel;

	private NavigationButton navButton;

	@Override
	protected void startUp()
	{
		bankModel.load();
		setupStore.load();
		overlayManager.add(bankHighlighter);
		bankFilterService.register();
		readLiveContainers();

		BufferedImage icon = ImageUtil.loadImageResource(GearForgePlugin.class, "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("GearForge")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		panel.refresh();

		log.debug("GearForge started");
	}

	@Override
	protected void shutDown()
	{
		bankFilterService.unregister();
		overlayManager.remove(bankHighlighter);
		clientToolbar.removeNavigation(navButton);
		navButton = null;

		log.debug("GearForge stopped");
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		switch (event.getContainerId())
		{
			case InventoryID.BANK:
				bankModel.updateBank(event.getItemContainer());
				break;
			case InventoryID.INV:
				bankModel.updateInventory(event.getItemContainer());
				break;
			case InventoryID.WORN:
				bankModel.updateEquipment(event.getItemContainer());
				break;
			default:
				return;
		}

		panel.refresh();
	}

	/**
	 * Reads the inventory and worn equipment straight from the client.
	 * <p>
	 * Container events only fire when a container <em>changes</em>, and the ones at login happen before
	 * this plugin starts if it is enabled mid-session or started late. Relying on them alone left worn
	 * gear empty until the player changed something, which made "Save what I'm wearing" claim nothing
	 * was equipped.
	 */
	private void readLiveContainers()
	{
		clientThread.invoke(() ->
		{
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				return;
			}

			bankModel.updateInventory(client.getItemContainer(InventoryID.INV));
			bankModel.updateEquipment(client.getItemContainer(InventoryID.WORN));
			panel.refresh();
		});
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		// Bank Tags applies a filter to the open bank, so re-apply whenever the bank is opened rather
		// than only at the moment the user activates a setup.
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			bankFilterService.reapply();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (GearForgeConfig.GROUP.equals(event.getGroup()))
		{
			panel.refresh();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// The RS profile is only known once logged in, and it changes when hopping accounts, so
		// reload rather than letting one account's gear leak into another's panel.
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			bankModel.load();
			setupStore.load();

			// Restore whatever was last shown in the bank for this account.
			Setup active = setupStore.active();
			if (active != null)
			{
				bankFilterService.applySetup(active);
			}

			readLiveContainers();
			panel.refresh();
		}
	}

	@Provides
	GearForgeConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GearForgeConfig.class);
	}
}
