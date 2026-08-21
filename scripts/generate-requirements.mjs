/**
 * Regenerates src/main/resources/com/gearforge/equipment-requirements.json
 *
 * RuneLite's item data carries no level requirements, so they come from two sources, neither of
 * which is enough on its own:
 *
 * - osrsbox-db exposes them as a structured field, but stopped being updated in 2021. Everything
 *   released since — Torva, Masori, the Moons sets, oathplate, the whole modern bank — is absent,
 *   and absent reads to the player as "you can wear this". That is how blood moon armour came to be
 *   recommended to an account without the Strength for it.
 * - The OSRS Wiki is current, but states requirements in prose, so it has to be read rather than
 *   queried. It is also, where the two disagree, the correct one: osrsbox has the kodai wand at 75
 *   Magic and the bow of faerdhinen at 75 Ranged, and both are wrong.
 *
 * So the wiki is read for every item the DPS calculator knows about — which is the set of gear that
 * can ever be recommended — and the two are merged by taking the higher level per skill. Merging
 * upward means a stale entry can no longer hide a real requirement, and the cost of the rare
 * over-strict entry is one item the player has to notice for themselves.
 *
 * Usage:  node scripts/generate-requirements.mjs
 */

import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { fetchWikitext, idsFromWikitext, requirementsFromWikitext, SKILLS } from './lib-wiki-requirements.mjs';

const OSRSBOX = 'https://raw.githubusercontent.com/osrsbox/osrsbox-db/master/docs/items-complete.json';

/** The gear the DPS calculator knows about: everything GearForge could ever put on a player. */
const EQUIPMENT = 'https://raw.githubusercontent.com/weirdgloop/osrs-dps-calc/main/cdn/json/equipment.json';

const WIKI = 'https://oldschool.runescape.wiki';

/** The wiki's API takes fifty titles at a time. */
const BATCH = 50;

/** Courtesy pause between batches. There is no hurry; this runs by hand. */
const PAUSE_MS = 250;

const here = dirname(fileURLToPath(import.meta.url));
const OUTPUT = resolve(here, '../src/main/resources/com/gearforge/equipment-requirements.json');

async function download(url) {
  process.stdout.write(`Downloading ${url}\n`);
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Download failed: ${response.status} ${response.statusText}`);
  }

  return response.json();
}

/**
 * osrsbox's structured requirements, keyed by item id.
 */
function fromOsrsbox(items) {
  const requirements = {};
  let equipable = 0;

  for (const item of Object.values(items)) {
    if (!item?.equipable_by_player || !item.equipment) {
      continue;
    }

    equipable += 1;

    const raw = item.equipment.requirements;
    if (!raw) {
      continue;
    }

    const entry = {};
    for (const skill of SKILLS) {
      const level = raw[skill];
      if (typeof level === 'number' && level > 1) {
        entry[skill] = level;
      }
    }

    if (Object.keys(entry).length > 0) {
      requirements[item.id] = entry;
    }
  }

  return { requirements, equipable };
}

/**
 * Reads the wiki for every distinct item name in the calculator's equipment list.
 */
async function fromWiki(equipment) {
  const idsByName = new Map();
  for (const item of equipment) {
    if (!item?.name || typeof item.id !== 'number') {
      continue;
    }

    if (!idsByName.has(item.name)) {
      idsByName.set(item.name, []);
    }

    idsByName.get(item.name).push(item.id);
  }

  const names = [...idsByName.keys()];
  const requirements = {};
  let read = 0;

  for (let start = 0; start < names.length; start += BATCH) {
    const batch = names.slice(start, start + BATCH);
    const pages = await fetchWikitext(batch);

    for (const name of batch) {
      const wikitext = pages.get(name);
      if (!wikitext) {
        continue;
      }

      const found = requirementsFromWikitext(wikitext);
      if (!found) {
        continue;
      }

      read += 1;

      // The ids the calculator has under this name, plus every version the page itself lists — a
      // degraded or ornamented copy needs the same levels as the one the page is named for.
      const ids = new Set([...idsByName.get(name), ...idsFromWikitext(wikitext)]);
      for (const id of ids) {
        requirements[id] = { ...found };
      }
    }

    process.stdout.write(`  read ${Math.min(start + BATCH, names.length)}/${names.length} pages\r`);
    await new Promise((wake) => { setTimeout(wake, PAUSE_MS); });
  }

  process.stdout.write('\n');
  return { requirements, read };
}

/**
 * Takes the higher level per skill, so neither source can quietly drop a requirement the other has.
 */
function merge(into, from) {
  let conflicts = 0;

  for (const [id, entry] of Object.entries(from)) {
    const existing = into[id];
    if (!existing) {
      into[id] = entry;
      continue;
    }

    for (const [skill, level] of Object.entries(entry)) {
      if (existing[skill] === undefined) {
        existing[skill] = level;
      } else if (existing[skill] !== level) {
        conflicts += 1;
        existing[skill] = Math.max(existing[skill], level);
      }
    }
  }

  return conflicts;
}

async function main() {
  const [items, equipment] = await Promise.all([download(OSRSBOX), download(EQUIPMENT)]);

  const osrsbox = fromOsrsbox(items);
  process.stdout.write(`osrsbox: ${Object.keys(osrsbox.requirements).length} entries\n`);

  process.stdout.write(`Reading ${WIKI} for ${equipment.length} equipment entries\n`);
  const wiki = await fromWiki(equipment);
  process.stdout.write(`wiki: ${Object.keys(wiki.requirements).length} entries `
    + `from ${wiki.read} pages\n`);

  // The wiki goes in first so a disagreement is resolved in the direction of the current source.
  const requirements = { ...wiki.requirements };
  const conflicts = merge(requirements, osrsbox.requirements);

  const output = {
    dataVersion: 2,
    source: `${OSRSBOX} + ${WIKI}`,
    generatedAt: new Date().toISOString().slice(0, 10),
    equipableItemsSeen: osrsbox.equipable,
    requirements,
  };

  mkdirSync(dirname(OUTPUT), { recursive: true });
  writeFileSync(OUTPUT, JSON.stringify(output));

  process.stdout.write(
    `Wrote ${Object.keys(requirements).length} entries to ${OUTPUT} `
    + `(${conflicts} levels where the two sources disagreed, higher kept)\n`,
  );
}

main().catch((error) => {
  process.stderr.write(`${error.stack}\n`);
  process.exit(1);
});
