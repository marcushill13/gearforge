/**
 * Regenerates src/main/resources/com/gearforge/equipment-requirements.json
 *
 * RuneLite's item data carries no level requirements, and the OSRS Wiki only states them in prose,
 * so they are sourced from osrsbox-db, which exposes them as a structured field.
 *
 * Caveat worth knowing before trusting the output: osrsbox-db is no longer actively updated, so
 * items released after it stopped will be absent. Absent is handled as "requirement unknown" in the
 * plugin rather than "no requirement", so newer gear is never silently hidden from the player.
 *
 * Usage:  node scripts/generate-requirements.mjs
 */

import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const SOURCE = 'https://raw.githubusercontent.com/osrsbox/osrsbox-db/master/docs/items-complete.json';

const here = dirname(fileURLToPath(import.meta.url));
const OUTPUT = resolve(here, '../src/main/resources/com/gearforge/equipment-requirements.json');

/** Only combat-relevant skills; agility/herblore requirements on gear are not our concern. */
const SKILLS = ['attack', 'strength', 'defence', 'ranged', 'magic', 'prayer', 'hitpoints', 'slayer'];

async function main() {
  process.stdout.write(`Downloading ${SOURCE}\n`);
  const response = await fetch(SOURCE);
  if (!response.ok) {
    throw new Error(`Download failed: ${response.status} ${response.statusText}`);
  }

  const items = await response.json();

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

  const output = {
    dataVersion: 1,
    source: SOURCE,
    generatedAt: new Date().toISOString().slice(0, 10),
    equipableItemsSeen: equipable,
    requirements,
  };

  mkdirSync(dirname(OUTPUT), { recursive: true });
  writeFileSync(OUTPUT, JSON.stringify(output));

  process.stdout.write(
    `Wrote ${Object.keys(requirements).length} entries `
    + `(of ${equipable} equipable items) to ${OUTPUT}\n`,
  );
}

main().catch((error) => {
  process.stderr.write(`${error.stack}\n`);
  process.exit(1);
});
