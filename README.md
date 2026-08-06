# GearForge

Your gear, solved. Rank everything you own by equipment stat, per slot.

GearForge reads your bank once and then answers the question you actually have: *of the items I own,
which is best for this slot?* It pairs well with **Auto Bank Sorter** and **Bank Tags** rather than
replacing them — GearForge is about combat gear, not bank organisation.

## Current state

Three of the four planned surfaces work today:

- **Setups** — save a loadout from what you're wearing or straight from the BiS tab, then **Show in
  bank** to filter your bank down to just that setup's items, the way a Bank Tags tab works. Each
  setup shows how many of its items you actually own and which slots are missing.
- **Slots** — pick an equipment slot and a bonus, get everything you own ranked best-first, with the
  item's icon, where it currently is (bank / inventory / worn), the value, and how it compares to
  what you are wearing right now.
- **BiS** — the best setup from your own bank for a combat style or a defensive profile, with the DPS
  breakdown, the reasoning, and close alternatives. Choose between **BiS for Level**, which respects
  your current levels, and **BiS Overall**, which ignores requirements.
- **Bosses** — search a boss and get the best setup you own for it, scored against its real defensive
  stats, magic level and attributes. Every combat style is raced against each other, so it answers
  *which* style to bring rather than assuming, and shows the DPS of the ones it rejected.

Bank contents are snapshotted when you open your bank, stored **per RuneScape account**, and shown
with an honest age ("Bank data from 2 hours ago"). Variant families (charged, imbued, degraded)
collapse to a single entry showing the best version.

### Known limitations

- **Requirement data is incomplete.** RuneLite's item data has no level requirements and the OSRS
  Wiki only states them in prose, so they ship as a generated resource sourced from osrsbox-db.
  That dataset is no longer updated, so items released after it stopped are absent — around 1,800 of
  3,900 equipable items are covered. Missing items are treated as *unknown* rather than
  *unrestricted*: they still appear in **Gear I can use**, with a note saying they could not be
  checked. Regenerate with `node scripts/generate-requirements.mjs`.
- **Quest requirements are not checked at all** (e.g. Barrows gloves).
- **Set effect coverage is partial.** Void, salve amulet, and slayer helmet / black mask are
  implemented from verified multipliers. Twisted bow, Dharok's, dragon hunter weapons, Inquisitor's,
  scythe, keris and the obsidian sets are not modelled yet, and setups relying on them will score low.
- **Magic BiS assumes Ice Barrage** against a dummy target. Spell selection is not built.
- Ammo is not yet co-optimised with the weapon, so bow/arrow pairings are not enforced.
- **Setups cover equipment only.** No inventory grid, rune pouch or quiver yet, so no import from
  Inventory Setups and no share codes.
- **Charges and doses are not checked.** Unknown charge levels read as neutral rather than as a
  warning, so a setup will not tell you a weapon is out of charges.

Planned: the setup editor, Inventory Setups import, upgrade nudges, and the per-boss tab.

Bank filtering goes through RuneLite's own **Bank Tags** plugin, which must be enabled (it is by
default). GearForge registers a private virtual tag rather than writing to your own tags.

**Known conflict:** the third-party **Bank Tag Layouts** plugin generates its own layout for every
tag, including GearForge's, which overrides the equipment-doll arrangement and packs items into a
flat row instead. If your filtered bank is not laid out as an equipment screen, that is why — disable
Bank Tag Layouts, or delete its `banktaglayouts.layout_gearforge` entry.

Note for anyone extending this: every RuneLite plugin gets its own Guice child injector, so
`BankTagsService` and `LayoutManager` are **not** injectable from another plugin — attempting it makes
Guice fail to construct the plugin, which removes it from the plugin list with no visible error. Both
are reached instead through the running plugin's own injector, found via `PluginManager` and
`Plugin.getInjector()`.

## No automation

GearForge never withdraws, deposits, equips or clicks anything. It reads game state, computes, and
displays. Every action it enables is one you perform yourself.

## Getting started

Install, then open your bank once. That's the whole setup.

## Building

```
./gradlew build
```

To run a development client with the plugin loaded:

```
./gradlew run
```

See [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts) for logging
into a development client.

## Combat maths

Formulas are transcribed from the OSRS Wiki with sources recorded in
[docs/combat-formulas.md](docs/combat-formulas.md), including three places where common references
are wrong and one place where two wiki pages contradict each other.

## Notes on data

Equipment bonuses come from RuneLite's own item stat service via `ItemManager` — GearForge performs no
network requests of its own. That table loads asynchronously shortly after the client starts, so the
panel may briefly report that stats are still loading.

## Attribution

Boss data is derived from the [OSRS Wiki](https://oldschool.runescape.wiki/), via
[weirdgloop/osrs-dps-calc](https://github.com/weirdgloop/osrs-dps-calc). Wiki content is licensed
**CC BY-NC-SA 3.0** — attribution is required and the licence is non-commercial. GearForge is free and
open source and must stay that way; do not paywall or monetise it. The credit line is also shown in
the Bosses tab, and must not be removed.

Combat formulas are likewise derived from the OSRS Wiki — see
[docs/combat-formulas.md](docs/combat-formulas.md).

The bundled datasets are generated from two projects, both licensed GPL-3.0:

- `equipment-requirements.json` — [osrsbox-db](https://github.com/osrsbox/osrsbox-db)
- `monsters.json`, `item-categories.json` — [weirdgloop/osrs-dps-calc](https://github.com/weirdgloop/osrs-dps-calc)

What is extracted is raw factual game data — defence levels, attack speeds, level requirements — and
is bundled with attribution. The plugin code itself is BSD 2-Clause; see
[docs/plugin-hub-submission.md](docs/plugin-hub-submission.md) for the reasoning. If either project
would prefer different terms, please open an issue.

Regenerate either dataset with:

```
node scripts/generate-requirements.mjs
node scripts/generate-monsters.mjs
```

## Licence

BSD 2-Clause for the plugin code. See [LICENSE](LICENSE). Bundled wiki-derived data remains under
CC BY-NC-SA 3.0 as above.
