package com.gearforge.ui;

import com.gearforge.bank.BankFilterService;
import com.gearforge.data.BankModel;
import com.gearforge.data.EquipmentSlot;
import com.gearforge.data.EquipmentStats;
import com.gearforge.data.GearItem;
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
import com.gearforge.dps.Target;
import com.gearforge.optimizer.DpsOptimizer;
import com.gearforge.optimizer.ScoredSetup;
import com.gearforge.setups.Setup;
import com.gearforge.setups.SetupSource;
import com.gearforge.setups.SetupStore;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Search a boss, get the best setup you own for it.
 * <p>
 * Unlike the BiS tab this scores against the boss's real defensive stats, magic level and attributes,
 * and it races every combat style rather than making you guess — a target with high magic defence and
 * low stab defence answers "what should I bring" by itself.
 */
@Singleton
class BossesTab extends JPanel
{
	/**
	 * High enough to show the whole shipped dataset at once; the list scrolls. Only a guard against
	 * building thousands of buttons if the dataset ever grows.
	 */
	private static final int MAX_RESULTS = 250;

	/** Styles raced against each other for the chosen boss. */
	private static final List<CombatStyle> STYLES = Arrays.asList(
		CombatStyle.STAB, CombatStyle.SLASH, CombatStyle.CRUSH, CombatStyle.RANGED, CombatStyle.MAGIC);

	/**
	 * Bosses are fought buffed, so one is assumed. Super combat covers melee and the overloads are
	 * raid-specific, so this is the safe general choice; ranged and magic get their own boosts from it
	 * only where the potion actually provides them.
	 */
	private static final Potion BOSS_POTION = Potion.SUPER_COMBAT;

	/** Ice Barrage, as on the BiS tab — magic is meaningless without fixing a spell. */
	private static final int ASSUMED_SPELL_DAMAGE = 30;
	private static final int SPELL_SPEED_TICKS = 5;

	private final ClientThread clientThread;
	private final ScheduledExecutorService executor;
	private final BankModel bankModel;
	private final ItemStatEngine statEngine;
	private final PlayerModel playerModel;
	private final ItemRequirements itemRequirements;
	private final DpsOptimizer dpsOptimizer;
	private final MonsterRepository monsters;
	private final SetupStore setupStore;
	private final BankFilterService bankFilterService;
	private final ItemManager itemManager;

	private final JTextField search = new JTextField();
	private final JPanel content = new JPanel();

	@Nullable
	private Monster selected;
	private Map<EquipmentSlot, GearItem> shownSetup = new LinkedHashMap<>();
	private String shownName = "Setup";
	private Runnable onSetupSaved = () ->
	{
	};

	@Inject
	private BossesTab(
		ClientThread clientThread,
		ScheduledExecutorService executor,
		BankModel bankModel,
		ItemStatEngine statEngine,
		PlayerModel playerModel,
		ItemRequirements itemRequirements,
		DpsOptimizer dpsOptimizer,
		MonsterRepository monsters,
		SetupStore setupStore,
		BankFilterService bankFilterService,
		ItemManager itemManager)
	{
		this.clientThread = clientThread;
		this.executor = executor;
		this.bankModel = bankModel;
		this.statEngine = statEngine;
		this.playerModel = playerModel;
		this.itemRequirements = itemRequirements;
		this.dpsOptimizer = dpsOptimizer;
		this.monsters = monsters;
		this.setupStore = setupStore;
		this.bankFilterService = bankFilterService;
		this.itemManager = itemManager;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(buildSearch(), BorderLayout.NORTH);

		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel holder = new JPanel(new BorderLayout());
		holder.setBackground(ColorScheme.DARK_GRAY_COLOR);
		holder.add(content, BorderLayout.NORTH);

		JScrollPane scroll = new JScrollPane(
			holder, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(scroll, BorderLayout.CENTER);
	}

	private JPanel buildSearch()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setBorder(BorderFactory.createEmptyBorder(4, 0, 10, 0));

		search.setFont(FontManager.getRunescapeSmallFont());
		search.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		search.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		search.setCaretColor(ColorScheme.LIGHT_GRAY_COLOR);
		search.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent event)
			{
				onSearchChanged();
			}

			@Override
			public void removeUpdate(DocumentEvent event)
			{
				onSearchChanged();
			}

			@Override
			public void changedUpdate(DocumentEvent event)
			{
				onSearchChanged();
			}
		});

		panel.add(Cards.field("Find a boss", search));
		return panel;
	}

	private void onSearchChanged()
	{
		// Typing means picking a different boss, so drop the previous result.
		selected = null;
		rebuild();
	}

	void rebuild()
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(this::rebuild);
			return;
		}

		if (monsters.bosses().isEmpty())
		{
			showMessage("Boss data is missing from this build.");
			return;
		}

		if (selected == null)
		{
			renderList();
			return;
		}

		computeFor(selected);
	}

	private void renderList()
	{
		content.removeAll();

		List<Monster> matches = monsters.search(search.getText(), monsters.bosses());
		if (matches.isEmpty())
		{
			content.add(Cards.muted("No boss matches that."));
			finish();
			return;
		}

		boolean truncated = matches.size() > MAX_RESULTS;
		List<Monster> shown = truncated ? matches.subList(0, MAX_RESULTS) : matches;

		content.add(Cards.sectionLabel(truncated
			? "First " + MAX_RESULTS + " of " + matches.size() + " — type to narrow"
			: matches.size() + (matches.size() == 1 ? " boss" : " bosses")));
		content.add(Cards.gap(4));

		for (Monster monster : shown)
		{
			JButton button = Cards.button(monster.displayName());
			button.setHorizontalAlignment(SwingConstants.LEFT);
			button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
			button.addActionListener(event ->
			{
				selected = monster;
				rebuild();
			});

			content.add(button);
			content.add(Cards.gap(2));
		}

		content.add(Cards.gap(10));
		content.add(Cards.muted(monsters.getAttribution()));
		finish();
	}

	/**
	 * Races every combat style against this boss and shows the winner.
	 */
	private void computeFor(Monster monster)
	{
		if (!bankModel.hasBankData())
		{
			showMessage("Open your bank once to get started.");
			return;
		}

		content.removeAll();
		content.add(Cards.muted("Working out the best setup for " + monster.displayName() + "…"));
		finish();

		clientThread.invoke(() ->
		{
			PlayerLevels levels = playerModel.snapshot();
			List<GearItem> resolved = statEngine.resolveOwnedGear(bankModel);

			// Five beam searches follow. They must not run on the game thread.
			executor.execute(() -> race(monster, resolved, levels));
		});
	}

	/**
	 * Races every combat style against the boss. Pure computation, deliberately off the game thread.
	 */
	private void race(Monster monster, List<GearItem> resolved, PlayerLevels levels)
	{
		List<GearItem> owned = usableGear(resolved, levels);
		Target target = monster.toTarget();

		// Only count slayer bonuses when the player actually has a task and this is something a
		// slayer master assigns — assuming either way skews the numbers.
		boolean onTask = levels.isOnSlayerTask() && monster.isSlayerMonster();

		List<ScoredSetup> byStyle = new ArrayList<>();
		List<CombatStyle> styleOf = new ArrayList<>();

		for (CombatStyle style : STYLES)
		{
			List<ScoredSetup> best = dpsOptimizer.best(
				owned, contextFor(style, levels, target), onTask, 1);

			if (!best.isEmpty())
			{
				byStyle.add(best.get(0));
				styleOf.add(style);
			}
		}

		SwingUtilities.invokeLater(() -> renderResult(monster, byStyle, styleOf, onTask));
	}

	private List<GearItem> usableGear(List<GearItem> owned, PlayerLevels levels)
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

	private CombatContext contextFor(CombatStyle style, PlayerLevels levels, Target target)
	{
		// Bosses are fought with prayers and potions up, so score that way. Leaving them out can flip
		// which item wins, because they do not scale every item evenly.
		return CombatContext.builder()
			.attackLevel(levels.getAttack())
			.strengthLevel(levels.getStrength())
			.rangedLevel(levels.getRanged())
			.magicLevel(levels.getMagic())
			.attackBoost(BOSS_POTION.attackBoost(levels))
			.strengthBoost(BOSS_POTION.strengthBoost(levels))
			.rangedBoost(BOSS_POTION.rangedBoost(levels))
			.magicBoost(BOSS_POTION.magicBoost(levels))
			.prayer(CombatPrayer.bestFor(style))
			.style(style)
			.equipment(EquipmentStats.builder().build())
			.target(target)
			.poweredStaff(false)
			.baseSpellDamage(style.isMagic() ? ASSUMED_SPELL_DAMAGE : 0)
			.weaponSpeedTicks(SPELL_SPEED_TICKS)
			.build();
	}

	private void renderResult(
		Monster monster, List<ScoredSetup> byStyle, List<CombatStyle> styleOf, boolean onTask)
	{
		content.removeAll();

		JButton back = Cards.button("Back to list");
		back.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		back.addActionListener(event ->
		{
			selected = null;
			rebuild();
		});
		content.add(back);
		content.add(Cards.gap(8));

		if (byStyle.isEmpty())
		{
			content.add(Cards.muted("Nothing you own and can equip works against "
				+ monster.displayName() + "."));
			finish();
			return;
		}

		int bestIndex = 0;
		for (int i = 1; i < byStyle.size(); i++)
		{
			if (byStyle.get(i).getScore().getDps() > byStyle.get(bestIndex).getScore().getDps())
			{
				bestIndex = i;
			}
		}

		ScoredSetup best = byStyle.get(bestIndex);
		CombatStyle bestStyle = styleOf.get(bestIndex);

		shownSetup = new LinkedHashMap<>(best.getSetup());
		shownName = monster.getName();

		JPanel inner = Cards.card();
		inner.add(Cards.headline(String.format("%.2f DPS", best.getScore().getDps())));
		inner.add(Cards.gap(2));
		inner.add(Cards.body(styleName(bestStyle)));
		inner.add(Cards.gap(2));
		inner.add(Cards.muted(monster.displayName()));

		JPanel card = Cards.accentCard(ColorScheme.BRAND_ORANGE);
		card.add(inner, BorderLayout.CENTER);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		content.add(card);

		List<String> why = new ArrayList<>();
		why.add(String.format("%.1f%% accuracy, max hit %d",
			best.getScore().accuracyPercent(), best.getScore().getMaxHit()));
		why.add("Assumes a potion and the best prayer for the style.");
		why.add("Its defences — stab " + monster.getDefensive().getStab()
			+ ", slash " + monster.getDefensive().getSlash()
			+ ", crush " + monster.getDefensive().getCrush()
			+ ", magic " + monster.getDefensive().getMagic()
			+ ", ranged " + monster.getDefensive().getRanged());
		if (onTask)
		{
			why.add("You have a slayer task, so slayer helmet bonuses are counted.");
		}
		else if (monster.isSlayerMonster())
		{
			why.add("Slayer helmet bonuses not counted — you have no task assigned.");
		}
		why.addAll(best.getNotes());
		addSection("Why", why);

		List<String> others = new ArrayList<>();
		for (int i = 0; i < byStyle.size(); i++)
		{
			if (i != bestIndex)
			{
				others.add(String.format("%s: %.2f DPS", styleName(styleOf.get(i)),
					byStyle.get(i).getScore().getDps()));
			}
		}
		addSection("Other styles", others);

		addSetup(best.getSetup());

		content.add(Cards.gap(10));
		content.add(Cards.muted(monsters.getAttribution()));
		finish();
	}

	private static String styleName(CombatStyle style)
	{
		switch (style)
		{
			case STAB:
				return "Melee, stab";
			case SLASH:
				return "Melee, slash";
			case CRUSH:
				return "Melee, crush";
			case RANGED:
				return "Ranged";
			default:
				return "Magic";
		}
	}

	private void addSection(String title, List<String> lines)
	{
		if (lines.isEmpty())
		{
			return;
		}

		content.add(Cards.gap(10));
		content.add(Cards.sectionLabel(title));
		for (String line : lines)
		{
			content.add(Cards.muted(line));
			content.add(Cards.gap(2));
		}
	}

	private void addSetup(Map<EquipmentSlot, GearItem> setup)
	{
		content.add(Cards.gap(10));
		content.add(Cards.sectionLabel("Setup"));

		for (EquipmentSlot slot : EquipmentSlot.values())
		{
			GearItem item = setup.get(slot);
			if (item != null)
			{
				content.add(setupRow(slot, item));
				content.add(Cards.gap(2));
			}
		}

		content.add(Cards.gap(8));

		JButton show = Cards.button("Show in bank");
		show.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		show.setToolTipText("Filter your bank to these items without saving a setup");
		show.addActionListener(event ->
		{
			bankFilterService.applySetup(asSetup(shownName));
			onSetupSaved.run();
		});
		content.add(show);

		content.add(Cards.gap(4));

		JButton save = Cards.button("Save as setup");
		save.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		save.addActionListener(event ->
		{
			String name = JOptionPane.showInputDialog(this, "Name this setup", shownName);
			if (name != null && !name.trim().isEmpty())
			{
				setupStore.add(asSetup(name.trim()));
				onSetupSaved.run();
			}
		});
		content.add(save);
	}

	private Setup asSetup(String name)
	{
		Setup setup = Setup.named(name, SetupSource.BIS);
		shownSetup.forEach((slot, item) -> setup.put(slot, item.getItemId()));
		return setup;
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
		return row;
	}

	private void showMessage(String message)
	{
		content.removeAll();
		content.add(Cards.muted(message));
		finish();
	}

	private void finish()
	{
		content.revalidate();
		content.repaint();
	}

	void setOnSetupSaved(Runnable callback)
	{
		this.onSetupSaved = callback;
	}
}
