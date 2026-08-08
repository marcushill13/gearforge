package com.gearforge.ui;

import com.gearforge.bank.BankFilterService;
import com.gearforge.data.BankModel;
import com.gearforge.data.EquipmentSlot;
import com.gearforge.data.EquipmentStats;
import com.gearforge.data.GearItem;
import com.gearforge.data.GearStat;
import com.gearforge.data.ItemRequirements;
import com.gearforge.data.ItemStatEngine;
import com.gearforge.data.Monster;
import com.gearforge.data.MonsterRepository;
import com.gearforge.data.PlayerLevels;
import com.gearforge.data.PlayerModel;
import com.gearforge.dps.CombatContext;
import com.gearforge.dps.CombatPrayer;
import com.gearforge.dps.CombatStyle;
import com.gearforge.dps.Potion;
import com.gearforge.dps.PrayerIcon;
import com.gearforge.dps.Target;
import com.gearforge.optimizer.DpsOptimizer;
import com.gearforge.optimizer.GreedyOptimizer;
import com.gearforge.optimizer.OptimizerResult;
import com.gearforge.optimizer.ScoredSetup;
import com.gearforge.setups.Setup;
import com.gearforge.setups.SetupSource;
import com.gearforge.setups.SetupStore;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Best-in-slot from what you own.
 * <p>
 * Offensive profiles score whole setups through the DPS optimizer; defensive and prayer profiles are
 * genuinely slot-independent and use the greedy optimizer, which is exact and instant.
 */
@Singleton
class BisTab extends JPanel
{
	/** Ice Barrage. Magic best-in-slot is meaningless without fixing a spell, so one is assumed. */
	private static final int ASSUMED_SPELL_DAMAGE = 30;
	private static final int SPELL_SPEED_TICKS = 5;

	private final ClientThread clientThread;
	private final ScheduledExecutorService executor;
	private final BankModel bankModel;
	private final ItemStatEngine statEngine;
	private final PlayerModel playerModel;
	private final ItemRequirements itemRequirements;
	private final DpsOptimizer dpsOptimizer;
	private final GreedyOptimizer greedyOptimizer;
	private final SetupStore setupStore;
	private final BankFilterService bankFilterService;
	private final MonsterRepository monsters;
	private final ItemManager itemManager;
	private final SpriteManager spriteManager;

	/** Matches the wiki calculator's habit of scoring against a low-defence crab by default. */
	private static final String DEFAULT_TARGET = "Ammonite Crab";

	/** How many rows the dropdown shows before scrolling. Not a cap on its contents. */
	private static final int TARGET_POPUP_ROWS = 12;

	/** Raced against each other for the overview. */
	private static final List<CombatStyle> OFFENSIVE_STYLES = Arrays.asList(
		CombatStyle.STAB, CombatStyle.SLASH, CombatStyle.CRUSH, CombatStyle.RANGED, CombatStyle.MAGIC);

	private final JComboBox<Profile> profilePicker = Cards.comboBox(Profile.values());
	private final JTextField targetSearch = new JTextField();
	private final JComboBox<Monster> targetPicker = Cards.comboBox(new Monster[0]);
	private final JPanel results = new JPanel();

	private GearPool pool = GearPool.USABLE;

	/**
	 * Whether to count slayer helmet bonuses. Detected from the player's task, with a manual override
	 * for planning a task you have not been assigned yet.
	 */
	private SlayerChoice slayerChoice = SlayerChoice.AUTO;

	/** Selected prayer and potion. Neither is on by default; both are chosen explicitly. */
	private CombatPrayer prayer = CombatPrayer.NONE;
	private Potion potion = Potion.NONE;

	/**
	 * Plain buttons rather than toggles: RuneLite's sprite and item image helpers only accept
	 * {@link JButton}, and selection is drawn here anyway so the two pickers match.
	 */
	private final Map<CombatPrayer, JButton> prayerButtons = new EnumMap<>(CombatPrayer.class);
	private final Map<Potion, JButton> potionButtons = new EnumMap<>(Potion.class);

	/** The setup currently on screen, so it can be saved without recomputing. */
	private Map<EquipmentSlot, GearItem> shownSetup = Collections.emptyMap();
	private String shownName = "Setup";
	private Runnable onSetupSaved = () ->
	{
	};

	@Inject
	private BisTab(
		ClientThread clientThread,
		ScheduledExecutorService executor,
		BankModel bankModel,
		ItemStatEngine statEngine,
		PlayerModel playerModel,
		ItemRequirements itemRequirements,
		DpsOptimizer dpsOptimizer,
		GreedyOptimizer greedyOptimizer,
		SetupStore setupStore,
		BankFilterService bankFilterService,
		MonsterRepository monsters,
		ItemManager itemManager,
		SpriteManager spriteManager)
	{
		this.clientThread = clientThread;
		this.executor = executor;
		this.bankModel = bankModel;
		this.statEngine = statEngine;
		this.playerModel = playerModel;
		this.itemRequirements = itemRequirements;
		this.dpsOptimizer = dpsOptimizer;
		this.greedyOptimizer = greedyOptimizer;
		this.setupStore = setupStore;
		this.bankFilterService = bankFilterService;
		this.monsters = monsters;
		this.itemManager = itemManager;
		this.spriteManager = spriteManager;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(buildControls(), BorderLayout.NORTH);

		results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
		results.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel holder = new JPanel(new BorderLayout());
		holder.setBackground(ColorScheme.DARK_GRAY_COLOR);
		holder.add(results, BorderLayout.NORTH);

		JScrollPane scroll = new JScrollPane(
			holder, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(scroll, BorderLayout.CENTER);

		profilePicker.addActionListener(event -> rebuild());
	}

	/**
	 * The prayers as a grid of their in-game icons, four across, matching the prayer book.
	 * <p>
	 * Single-select: clicking the active prayer turns it off. GearForge scores one prayer at a time,
	 * and the strong prayers each already cover their whole style.
	 */
	private JPanel buildPrayerGrid()
	{
		JPanel grid = new JPanel(new GridLayout(0, 4, 2, 2));
		grid.setBackground(ColorScheme.DARK_GRAY_COLOR);
		grid.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

		for (CombatPrayer candidate : CombatPrayer.values())
		{
			int sprite = PrayerIcon.spriteFor(candidate);
			if (sprite < 0)
			{
				// NONE has no icon; it is expressed by deselecting instead.
				continue;
			}

			JButton button = new JButton();
			button.setToolTipText(PrayerIcon.nameOf(candidate));
			button.setPreferredSize(new Dimension(34, 34));
			button.setFocusPainted(false);
			button.setBorderPainted(false);
			button.setOpaque(true);
			spriteManager.addSpriteTo(button, sprite, 0);

			button.addActionListener(event ->
			{
				prayer = prayer == candidate ? CombatPrayer.NONE : candidate;
				paintPrayerButtons();
				rebuild();
			});

			prayerButtons.put(candidate, button);
			grid.add(button);
		}

		paintPrayerButtons();
		return grid;
	}

	private void paintPrayerButtons()
	{
		prayerButtons.forEach((candidate, button) ->
		{
			boolean on = candidate == prayer;
			button.setBackground(on ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR);
		});
	}

	/**
	 * The potions as a scrollable list of item icons and names.
	 */
	private JPanel buildBoostList()
	{
		JPanel list = new JPanel();
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setBackground(ColorScheme.DARK_GRAY_COLOR);

		for (Potion candidate : Potion.values())
		{
			if (candidate == Potion.NONE)
			{
				continue;
			}

			JButton button = new JButton(candidate.toString());
			button.setHorizontalAlignment(SwingConstants.LEFT);
			button.setFont(FontManager.getRunescapeSmallFont());
			button.setFocusPainted(false);
			button.setBorderPainted(false);
			button.setOpaque(true);
			button.setIconTextGap(6);
			button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
			button.setAlignmentX(Component.LEFT_ALIGNMENT);
			itemManager.getImage(candidate.getItemId()).addTo(button);

			button.addActionListener(event ->
			{
				potion = potion == candidate ? Potion.NONE : candidate;
				paintPotionButtons();
				rebuild();
			});

			potionButtons.put(candidate, button);
			list.add(button);
		}

		paintPotionButtons();

		JPanel holder = new JPanel(new BorderLayout());
		holder.setBackground(ColorScheme.DARK_GRAY_COLOR);
		holder.add(list, BorderLayout.NORTH);

		JScrollPane scroll = new JScrollPane(
			holder, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		// Capped so the list scrolls rather than pushing the results off screen.
		scroll.setPreferredSize(new Dimension(0, 150));
		scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
		wrapper.add(scroll, BorderLayout.CENTER);
		return wrapper;
	}

	private void paintPotionButtons()
	{
		potionButtons.forEach((candidate, button) ->
		{
			boolean on = candidate == potion;
			button.setBackground(on ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR);
			button.setForeground(on ? ColorScheme.DARKER_GRAY_COLOR : ColorScheme.LIGHT_GRAY_COLOR);
		});
	}

	private JPanel buildControls()
	{
		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
		controls.setBackground(ColorScheme.DARK_GRAY_COLOR);
		controls.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

		controls.add(Cards.field("Optimise for", profilePicker));
		controls.add(Cards.gap(8));

		GearPool[] pools = GearPool.values();
		String[] labels = new String[pools.length];
		for (int i = 0; i < pools.length; i++)
		{
			labels[i] = pools[i].toString();
		}

		controls.add(Cards.segmented(labels, pool.ordinal(), index ->
		{
			pool = pools[index];
			rebuild();
		}));
		controls.add(Cards.gap(8));

		SlayerChoice[] choices = SlayerChoice.values();
		String[] slayerLabels = new String[choices.length];
		for (int i = 0; i < choices.length; i++)
		{
			slayerLabels[i] = choices[i].toString();
		}

		controls.add(Cards.field("Slayer helmet", Cards.segmented(
			slayerLabels, slayerChoice.ordinal(), index ->
			{
				slayerChoice = choices[index];
				rebuild();
			})));
		controls.add(Cards.gap(8));

		controls.add(Cards.expandable("Prayers", buildPrayerGrid(),
			header -> spriteManager.addSpriteTo(header, SpriteID.Staticons.PRAYER, 0)));
		controls.add(Cards.gap(6));
		controls.add(Cards.expandable("Boosts", buildBoostList(),
			header -> spriteManager.addSpriteTo(header, SpriteID.Staticons.HERBLORE, 0)));
		controls.add(Cards.gap(8));

		targetSearch.setFont(FontManager.getRunescapeSmallFont());
		targetSearch.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		targetSearch.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		targetSearch.setCaretColor(ColorScheme.LIGHT_GRAY_COLOR);
		targetSearch.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		targetSearch.setToolTipText("Type to narrow the list of targets");
		targetSearch.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent event)
			{
				repopulateTargets();
			}

			@Override
			public void removeUpdate(DocumentEvent event)
			{
				repopulateTargets();
			}

			@Override
			public void changedUpdate(DocumentEvent event)
			{
				repopulateTargets();
			}
		});

		controls.add(Cards.field("Score against", targetSearch));
		controls.add(Cards.gap(4));
		targetPicker.setMaximumRowCount(TARGET_POPUP_ROWS);
		controls.add(targetPicker);

		repopulateTargets();
		targetPicker.addActionListener(event -> rebuild());

		return controls;
	}

	/**
	 * Refills the target dropdown from the search box, keeping the current pick if it still matches so
	 * typing does not silently change what you are scoring against.
	 */
	private void repopulateTargets()
	{
		Monster previous = (Monster) targetPicker.getSelectedItem();

		// Uncapped on purpose. Trimming the list made it stop partway through the alphabet with
		// nothing on screen to say why, which reads as a broken scrollbar rather than a limit.
		List<Monster> matches = monsters.search(targetSearch.getText());

		DefaultComboBoxModel<Monster> model = new DefaultComboBoxModel<>();
		for (Monster monster : matches)
		{
			model.addElement(monster);
		}

		targetPicker.setModel(model);

		if (previous != null && matches.contains(previous))
		{
			targetPicker.setSelectedItem(previous);
		}
		else if (model.getSize() > 0)
		{
			Monster fallback = monsters.byName(DEFAULT_TARGET);
			targetPicker.setSelectedItem(
				fallback != null && matches.contains(fallback) ? fallback : model.getElementAt(0));
		}
	}

	void rebuild()
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(this::rebuild);
			return;
		}

		if (!bankModel.hasBankData())
		{
			showMessage("Open your bank once to get started.");
			return;
		}

		Profile profile = (Profile) profilePicker.getSelectedItem();
		if (profile == null)
		{
			return;
		}

		GearPool pool = this.pool;
		SlayerChoice slayer = this.slayerChoice;
		Monster target = (Monster) targetPicker.getSelectedItem();

		// Read on the EDT and carried through, because the search runs on another thread and must not
		// touch Swing state.
		CombatPrayer chosenPrayer = this.prayer;
		Potion chosenPotion = this.potion;

		clientThread.invoke(() ->
		{
			if (pool == GearPool.USABLE && !playerModel.isLoggedIn())
			{
				SwingUtilities.invokeLater(() ->
					showMessage("Log in so GearForge can read your levels, or switch to "
						+ GearPool.EVERYTHING + "."));
				return;
			}

			PlayerLevels levels = playerModel.snapshot();
			List<GearItem> owned = statEngine.resolveOwnedGear(bankModel);

			// Only the two lookups above need the client: item names come from the item cache and
			// levels from the client. The search itself is pure computation and moves off the game
			// thread — running a beam search there froze the client on every gear swap.
			executor.execute(() ->
				optimise(owned, levels, profile, pool, slayer, target, chosenPrayer, chosenPotion));
		});
	}

	/**
	 * The expensive half. Runs on a background thread, never on the client thread or the EDT.
	 */
	private void optimise(
		List<GearItem> owned,
		PlayerLevels levels,
		Profile profile,
		GearPool pool,
		SlayerChoice slayer,
		@Nullable Monster target,
		CombatPrayer prayer,
		Potion potion)
	{
		boolean slayerTask = slayer.resolve(levels.isOnSlayerTask());
		List<GearItem> pooled = pool == GearPool.USABLE ? onlyEquippable(owned, levels) : owned;

		if (profile == Profile.ALL_STYLES)
		{
			Map<CombatStyle, ScoredSetup> byStyle = new LinkedHashMap<>();
			for (CombatStyle style : OFFENSIVE_STYLES)
			{
				List<ScoredSetup> best = dpsOptimizer.best(
					pooled, contextFor(Profile.forStyle(style), levels, target, prayer, potion), slayerTask, 1);

				if (!best.isEmpty())
				{
					byStyle.put(style, best.get(0));
				}
			}

			SwingUtilities.invokeLater(() -> renderAllStyles(byStyle, target));
		}
		else if (profile.isOffensive())
		{
			List<ScoredSetup> best = dpsOptimizer.best(
				pooled, contextFor(profile, levels, target, prayer, potion), slayerTask, 3);
			SwingUtilities.invokeLater(() -> renderOffensive(best, profile, pool, target));
		}
		else
		{
			OptimizerResult best = greedyOptimizer.best(pooled, profile.getStat());
			SwingUtilities.invokeLater(() -> renderDefensive(best, profile, pool));
		}
	}

	private List<GearItem> onlyEquippable(List<GearItem> owned, PlayerLevels levels)
	{
		List<GearItem> usable = new ArrayList<>();
		for (GearItem item : owned)
		{
			if (itemRequirements.canEquip(item.getItemId(), levels))
			{
				usable.add(item);
			}
		}

		return usable;
	}

	/**
	 * Must run on the client thread — player levels come from the client.
	 */
	private CombatContext contextFor(
		Profile profile,
		PlayerLevels levels,
		@Nullable Monster target,
		CombatPrayer prayer,
		Potion potion)
	{
		CombatStyle style = profile.getStyle();

		// Prayers and potions do not scale every item evenly, so leaving them out can flip which item
		// wins. That was the first thing players reported after release.
		return CombatContext.builder()
			.attackLevel(levels.getAttack())
			.strengthLevel(levels.getStrength())
			.rangedLevel(levels.getRanged())
			.magicLevel(levels.getMagic())
			.attackBoost(potion.attackBoost(levels))
			.strengthBoost(potion.strengthBoost(levels))
			.rangedBoost(potion.rangedBoost(levels))
			.magicBoost(potion.magicBoost(levels))
			.prayer(prayer)
			.style(style)
			.equipment(EquipmentStats.builder().build())
			.target(target == null ? Target.dummy() : target.toTarget())
			.poweredStaff(false)
			.baseSpellDamage(style.isMagic() ? ASSUMED_SPELL_DAMAGE : 0)
			.weaponSpeedTicks(SPELL_SPEED_TICKS)
			.build();
	}

	/**
	 * The overview: one row per attack style, best first, each opening the full setup.
	 * <p>
	 * Deliberately shows the gear and lets DPS be the ranking, rather than presenting a single number
	 * the player has to configure their way towards.
	 */
	private void renderAllStyles(Map<CombatStyle, ScoredSetup> byStyle, @Nullable Monster target)
	{
		results.removeAll();

		if (byStyle.isEmpty())
		{
			showMessage("Nothing in this gear pool can attack yet. Open your bank with a weapon in it.");
			return;
		}

		List<Map.Entry<CombatStyle, ScoredSetup>> ranked = new ArrayList<>(byStyle.entrySet());
		ranked.sort((a, b) -> Double.compare(b.getValue().getScore().getDps(), a.getValue().getScore().getDps()));

		Map.Entry<CombatStyle, ScoredSetup> winner = ranked.get(0);
		shownSetup = winner.getValue().getSetup();
		shownName = Profile.forStyle(winner.getKey()).toString();

		results.add(Cards.sectionLabel("Best of every style"));
		results.add(Cards.gap(4));

		for (Map.Entry<CombatStyle, ScoredSetup> entry : ranked)
		{
			results.add(styleRow(entry.getKey(), entry.getValue()));
			results.add(Cards.gap(3));
		}

		results.add(Cards.gap(8));
		results.add(Cards.muted("Scored against "
			+ (target == null ? "a dummy target" : target.displayName())
			+ ". Pick a style above to see its full setup."));

		finish();
	}

	/**
	 * One clickable style result. Selecting it switches the picker to that style, which shows the setup.
	 */
	private JPanel styleRow(CombatStyle style, ScoredSetup setup)
	{
		Profile profile = Profile.forStyle(style);

		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(row.getBackground());

		JLabel dps = new JLabel(String.format("%.2f DPS", setup.getScore().getDps()));
		dps.setFont(FontManager.getRunescapeBoldFont());
		dps.setForeground(ColorScheme.BRAND_ORANGE);
		dps.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(dps);

		GearItem weapon = setup.getSetup().get(EquipmentSlot.WEAPON);
		JLabel detail = new JLabel(profile + (weapon == null ? "" : " · " + weapon.getName()));
		detail.setFont(FontManager.getRunescapeSmallFont());
		detail.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		detail.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(detail);

		row.add(text, BorderLayout.CENTER);

		JButton open = Cards.button("Open");
		open.setToolTipText("Show the full setup for " + profile);
		open.addActionListener(event -> profilePicker.setSelectedItem(profile));
		row.add(open, BorderLayout.EAST);

		return row;
	}

	private void renderOffensive(
		List<ScoredSetup> best, Profile profile, GearPool pool, @Nullable Monster target)
	{
		results.removeAll();

		if (best.isEmpty())
		{
			showMessage("Nothing in this gear pool can attack with " + profile.toString().toLowerCase()
				+ ".");
			return;
		}

		ScoredSetup top = best.get(0);
		remember(top.getSetup(), profile.toString());

		results.add(summaryCard(
			String.format("%.2f DPS", top.getScore().getDps()),
			String.format("%.1f%% accuracy  ·  max hit %d",
				top.getScore().accuracyPercent(), top.getScore().getMaxHit())));

		List<String> reasons = new ArrayList<>(top.getNotes());
		String against = target == null ? "a dummy target" : target.displayName();
		reasons.add(profile.getStyle().isMagic()
			? "Assumes Ice Barrage, scored against " + against + "."
			: "Scored against " + against + ".");
		addUncheckedWarning(top.getSetup(), pool, reasons);
		addSection("Why", reasons);

		addSetup(top.getSetup());

		if (best.size() > 1)
		{
			List<String> alternatives = new ArrayList<>();
			for (int i = 1; i < best.size(); i++)
			{
				ScoredSetup alternative = best.get(i);
				double delta = alternative.getScore().getDps() - top.getScore().getDps();
				alternatives.add(String.format("%.2f DPS (%.2f) — %s",
					alternative.getScore().getDps(), delta, describeDifference(top, alternative)));
			}

			addSection("Close alternatives", alternatives);
		}

		finish();
	}

	private void renderDefensive(OptimizerResult best, Profile profile, GearPool pool)
	{
		results.removeAll();

		if (best.isEmpty())
		{
			showMessage("Nothing in this gear pool gives "
				+ profile.getStat().getDisplayName().toLowerCase() + ".");
			return;
		}

		remember(best.getSetup(), profile.toString());

		results.add(summaryCard(
			profile.getStat().format(best.getTotal()),
			profile.getStat().getDisplayName() + " total"));

		List<String> reasons = new ArrayList<>(best.getReasons());
		addUncheckedWarning(best.getSetup(), pool, reasons);
		addSection("Why", reasons);

		addSetup(best.getSetup());
		finish();
	}

	/**
	 * The requirements dataset is incomplete, so say plainly when a recommended item could not be
	 * checked rather than implying it is definitely wearable.
	 */
	private void addUncheckedWarning(Map<EquipmentSlot, GearItem> setup, GearPool pool, List<String> reasons)
	{
		if (pool != GearPool.USABLE)
		{
			return;
		}

		List<String> unchecked = new ArrayList<>();
		for (GearItem item : setup.values())
		{
			if (!itemRequirements.isKnown(item.getItemId()))
			{
				unchecked.add(item.getName());
			}
		}

		if (!unchecked.isEmpty())
		{
			reasons.add("Could not check requirements for " + String.join(", ", unchecked)
				+ " — check you can wear " + (unchecked.size() == 1 ? "it" : "them") + ".");
		}
	}

	private JPanel summaryCard(String headline, String detail)
	{
		JPanel inner = Cards.card();
		inner.add(Cards.headline(headline));
		inner.add(Cards.gap(2));
		inner.add(Cards.body(detail));

		JPanel card = Cards.accentCard(ColorScheme.BRAND_ORANGE);
		card.add(inner, BorderLayout.CENTER);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	private void addSection(String title, List<String> lines)
	{
		if (lines.isEmpty())
		{
			return;
		}

		results.add(Cards.gap(10));
		results.add(Cards.sectionLabel(title));

		for (String line : lines)
		{
			results.add(Cards.muted(line));
			results.add(Cards.gap(2));
		}
	}

	private void addSetup(Map<EquipmentSlot, GearItem> setup)
	{
		results.add(Cards.gap(10));
		results.add(Cards.sectionLabel("Setup"));

		for (EquipmentSlot slot : EquipmentSlot.values())
		{
			GearItem item = setup.get(slot);
			if (item != null)
			{
				results.add(setupRow(slot, item));
				results.add(Cards.gap(2));
			}
		}

		results.add(Cards.gap(8));

		JButton save = Cards.button("Save as setup");
		save.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		save.setToolTipText("Save this as a setup you can come back to");
		save.addActionListener(event -> saveShownSetup());
		results.add(save);

		results.add(Cards.gap(4));

		// Filtering without saving: most of the time you just want to go and grab the gear.
		JButton show = Cards.button("Show in bank");
		show.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		show.setToolTipText("Filter your bank to these items without saving a setup");
		show.addActionListener(event -> showShownSetupInBank());
		results.add(show);
	}

	/**
	 * Filters the bank to the displayed result directly, with no saved setup involved.
	 */
	private void showShownSetupInBank()
	{
		if (shownSetup.isEmpty())
		{
			return;
		}

		bankFilterService.applySetup(asSetup(shownName));
		onSetupSaved.run();
	}

	private Setup asSetup(String name)
	{
		Setup setup = Setup.named(name, SetupSource.BIS);
		shownSetup.forEach((slot, item) -> setup.put(slot, item.getItemId()));
		return setup;
	}

	private void remember(Map<EquipmentSlot, GearItem> setup, String name)
	{
		this.shownSetup = setup;
		this.shownName = name;
	}

	/**
	 * Turns the displayed result into a saved setup — the design's "BiS output is a setup" step.
	 */
	private void saveShownSetup()
	{
		if (shownSetup.isEmpty())
		{
			return;
		}

		String name = JOptionPane.showInputDialog(this, "Name this setup", shownName);
		if (name == null || name.trim().isEmpty())
		{
			return;
		}

		setupStore.add(asSetup(name.trim()));
		onSetupSaved.run();
	}

	/**
	 * Lets the panel refresh the Setups tab when a setup is saved from here.
	 */
	void setOnSetupSaved(Runnable callback)
	{
		this.onSetupSaved = callback;
	}

	/**
	 * Names the slots where the alternative differs, so a near-tie is explained rather than just listed.
	 */
	private static String describeDifference(ScoredSetup top, ScoredSetup alternative)
	{
		List<String> changes = new ArrayList<>();

		for (EquipmentSlot slot : EquipmentSlot.values())
		{
			GearItem chosen = top.getSetup().get(slot);
			GearItem other = alternative.getSetup().get(slot);

			if (other != null && (chosen == null || chosen.getItemId() != other.getItemId()))
			{
				changes.add(other.getName());
			}
		}

		return changes.isEmpty() ? "fewer items" : String.join(", ", changes);
	}

	private JPanel setupRow(EquipmentSlot slot, GearItem item)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(30, 30));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		itemManager.getImage(item.getItemId()).addTo(icon);
		row.add(icon, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(row.getBackground());

		JLabel name = new JLabel(item.getName());
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		name.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(name);

		JLabel slotLabel = new JLabel(slot.getDisplayName());
		slotLabel.setFont(FontManager.getRunescapeSmallFont());
		slotLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		slotLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(slotLabel);

		row.add(text, BorderLayout.CENTER);
		row.setToolTipText(item.getName());
		return row;
	}

	private void showMessage(String message)
	{
		results.removeAll();
		results.add(Cards.muted(message));
		finish();
	}

	private void finish()
	{
		results.revalidate();
		results.repaint();
	}

	/**
	 * Whether slayer helmet bonuses count. Defaults to reading the player's actual task, with manual
	 * overrides for planning ahead.
	 */
	private enum SlayerChoice
	{
		AUTO("Auto"),
		ON("On task"),
		OFF("Off task");

		private final String displayName;

		SlayerChoice(String displayName)
		{
			this.displayName = displayName;
		}

		boolean resolve(boolean detected)
		{
			switch (this)
			{
				case ON:
					return true;
				case OFF:
					return false;
				default:
					return detected;
			}
		}

		@Override
		public String toString()
		{
			return displayName;
		}
	}

	/**
	 * Which items the optimizer is allowed to choose from.
	 */
	private enum GearPool
	{
		USABLE("BiS for Level"),
		EVERYTHING("BiS Overall");

		private final String displayName;

		GearPool(String displayName)
		{
			this.displayName = displayName;
		}

		@Override
		public String toString()
		{
			return displayName;
		}
	}

	/**
	 * What the user is optimising for. Offensive profiles carry a combat style, defensive ones carry a
	 * single stat.
	 */
	private enum Profile
	{
		/**
		 * The default. Leads with the gear rather than making the player pick a style first — this is a
		 * best-in-slot tool that happens to rank by DPS, not a DPS calculator.
		 */
		ALL_STYLES("Best of every style", null, null),

		STAB("Melee — stab", CombatStyle.STAB, null),
		SLASH("Melee — slash", CombatStyle.SLASH, null),
		CRUSH("Melee — crush", CombatStyle.CRUSH, null),
		RANGED("Ranged", CombatStyle.RANGED, null),
		MAGIC("Magic", CombatStyle.MAGIC, null),

		DEF_STAB("Defence — stab", null, GearStat.STAB_DEFENCE),
		DEF_SLASH("Defence — slash", null, GearStat.SLASH_DEFENCE),
		DEF_CRUSH("Defence — crush", null, GearStat.CRUSH_DEFENCE),
		DEF_MAGIC("Defence — magic", null, GearStat.MAGIC_DEFENCE),
		DEF_RANGED("Defence — ranged", null, GearStat.RANGED_DEFENCE),
		PRAYER("Max prayer", null, GearStat.PRAYER);

		private final String displayName;
		@Nullable
		private final CombatStyle style;
		@Nullable
		private final GearStat stat;

		Profile(String displayName, @Nullable CombatStyle style, @Nullable GearStat stat)
		{
			this.displayName = displayName;
			this.style = style;
			this.stat = stat;
		}

		boolean isOffensive()
		{
			return style != null;
		}

		/**
		 * @return the single-style profile for this combat style, so a row in the overview can jump to it
		 */
		static Profile forStyle(CombatStyle style)
		{
			for (Profile profile : values())
			{
				if (profile.style == style)
				{
					return profile;
				}
			}

			return ALL_STYLES;
		}

		CombatStyle getStyle()
		{
			return style;
		}

		GearStat getStat()
		{
			return stat;
		}

		@Override
		public String toString()
		{
			return displayName;
		}
	}
}
