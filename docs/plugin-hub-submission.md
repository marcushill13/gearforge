# Plugin Hub submission checklist

Status of GearForge against RuneLite's Plugin Hub requirements, as of 2026-08-07.

## Code requirements

| Requirement | Status |
|---|---|
| Java 11 compatible (`options.release = 11`) | ✅ |
| No reflection | ✅ audited — only `Class#getSimpleName` avoided too |
| No JNI/JNA, Unsafe, LWJGL | ✅ |
| No external processes | ✅ |
| No dynamic classloading or code generation | ✅ |
| No Java serialization (Gson only) | ✅ |
| No `META-INF/services/...Plugin` file | ✅ |
| Renamed from the template (no `com.example`) | ✅ |
| No build artifacts committed | ✅ `.gitignore` covers `build/`, `.gradle/` |
| Permissive licence | ⚠️ see **Licensing** below |
| `build.gradle` matches the template structure | ✅ |

## Behaviour requirements

| Requirement | Status |
|---|---|
| No automation — never withdraws, deposits, equips or clicks | ✅ read/compute/display only |
| No input injection | ✅ |
| No menu entries that send actions to the server | ✅ |
| No boss mechanic prediction, prayer/attack indicators, tick helpers | ✅ |
| No PvP opponent information | ✅ |
| No network I/O of its own | ✅ all data is bundled; `ItemManager` fetches stats itself |
| No player data exposed over HTTP | ✅ |
| Per-account storage (`setRSProfileConfiguration`) | ✅ setups and bank snapshots are per RS profile |
| Config kept minimal | ✅ 4 options (design budget is 8) |
| No adult content, no player-provided-ID-driven features | ✅ |

## Housekeeping

- `runelite-plugin.properties` — display name, author, description, tags, main class all set.
- Icon: 24×24 PNG, 658 bytes, genuinely a PNG.
- `version` is intentionally left blank; the Hub sets it from the release tag.
- No unused config items, classes, or imports.

## Licensing — needs a decision before release

The plugin **code** is BSD 2-Clause. Three bundled resources are generated from third-party datasets:

| Resource | Source | Source licence |
|---|---|---|
| `equipment-requirements.json` | [osrsbox-db](https://github.com/osrsbox/osrsbox-db) | **GPL-3.0** |
| `monsters.json` | [weirdgloop/osrs-dps-calc](https://github.com/weirdgloop/osrs-dps-calc) | **GPL-3.0** (data itself derives from the OSRS Wiki, CC BY-NC-SA) |
| `item-categories.json` | same as above | **GPL-3.0** / CC BY-NC-SA |

The tension: distributing GPL-3.0-derived material inside a BSD-2 project is at best unclear, and
CC BY-NC-SA additionally requires attribution, forbids commercial use, and is share-alike.

Arguments that this is fine: the extracted content is raw factual game data (defence levels, item
requirements, attack speeds), and facts are not themselves copyrightable — only a creative selection
or arrangement of them is, and these are exhaustive dumps rather than curated compilations.

Arguments to be cautious: the upstream projects licence their repositories GPL-3.0, and a court has
not been asked what their dataset compilations are.

**Decision (2026-08-07): keep BSD-2 for the plugin code and document the bundled data as separately
licensed.** This matches RuneLite's preference for permissive licences and is ordinary practice for
wiki-derived plugin data. It rests on the reading that the extracted content is raw factual game data
rather than a creative compilation.

Alternatives considered and rejected: relicensing the whole plugin GPL-3.0 (unambiguously safe, but
runs against RuneLite's packaging guidance), and dropping `equipment-requirements.json` (loses the
"BiS for Level" filter and does not resolve the question for the other two files anyway).

Conditions that must hold for this to stay defensible:

- Attribution stays in the README and in the Bosses tab.
- The plugin stays **free and unmonetised** — CC BY-NC-SA is non-commercial.
- If either upstream project objects, relicensing to GPL-3.0 is the fallback and costs nothing but a
  version bump.

This is a considered judgement, not legal advice.

## Submitting

The Hub takes a PR against [runelite/plugin-hub](https://github.com/runelite/plugin-hub) adding a
single file, `plugins/gearforge`, containing:

```
repository=https://github.com/<user>/gearforge.git
commit=<full 40-character commit hash>
```

The commit must be reachable and the repository public. Reviewers build it themselves.
