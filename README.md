# GearForge

Your gear, solved. Rank everything you own by equipment stat, per slot.

GearForge reads your bank once and then answers the question you actually have: *of the items I own,
which is best for this slot?* It pairs well with **Auto Bank Sorter** and **Bank Tags** rather than
replacing them — GearForge is about combat gear, not bank organisation.

**[Join the Discord](https://discord.gg/btPFSMWMN6)** for support, suggestions and bug reports.

## Current state

All four surfaces work today:

- **Setups** — save a loadout from what you're wearing or straight from the BiS tab, then **Show in
  bank** to filter your bank down to just that setup's items, the way a Bank Tags tab works. Each
  setup shows how many of its items you actually own and which slots are missing.
- **Search** — pick an equipment slot and a bonus, get everything you own ranked best-first, with the
  item's icon, where it currently is (bank / inventory / worn), the value, and how it compares to
  what you are wearing right now.
- **BiS** — the best setup from your own bank for a combat style or a defensive profile, with the DPS
  breakdown, the reasoning, and close alternatives. Choose between **BiS for Level**, which respects
  your current levels, and **BiS Overall**, which ignores requirements.
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
node scripts/generate-item-categories.mjs
```

## Licence

BSD 2-Clause for the plugin code. See [LICENSE](LICENSE). Bundled wiki-derived data remains under
CC BY-NC-SA 3.0 as above.
