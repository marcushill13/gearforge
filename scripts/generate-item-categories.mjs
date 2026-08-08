/**
 * Regenerates src/main/resources/com/gearforge/item-categories.json
 *
 * RuneLite's item stats say whether something is a weapon, but not what *kind* — so nothing stops the
 * optimiser pairing a crossbow with arrows. This emits a weapon/ammo classification so those
 * pairings can be ruled out.
 *
 * Two traps the source data sets, both handled below:
 *   - Ballistas are category "Crossbow" but fire javelins, not bolts.
 *   - Ogre bows are category "Bow" but fire brutal arrows.
 * A rule written straight off the category field would reject both of those valid setups.
 *
 * Ammo carries no category at all in the source, so it is classified by name here, at build time,
 * where the names are available and the result can be eyeballed.
 *
 * Usage:  node scripts/generate-item-categories.mjs
 */

import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const SOURCE = 'https://raw.githubusercontent.com/weirdgloop/osrs-dps-calc/main/cdn/json/equipment.json';

const here = dirname(fileURLToPath(import.meta.url));
const OUTPUT = resolve(here, '../src/main/resources/com/gearforge/item-categories.json');

function classifyWeapon(item) {
  const name = item.name.toLowerCase();

  if (name.includes('ogre bow')) {
    return 'OGRE_BOW';
  }
  if (name.includes('ballista')) {
    return 'BALLISTA';
  }

  switch (item.category) {
    case 'Bow':
      return 'BOW';
    case 'Crossbow':
      return 'CROSSBOW';
    case 'Thrown':
      return 'THROWN';
    case 'Powered Staff':
      return 'POWERED_STAFF';
    // Staves are the magic weapons. Classifying them stops the optimizer offering a melee weapon as
    // a magic setup, which it will otherwise do because spell damage does not depend on the weapon.
    case 'Staff':
    case 'Bladed Staff':
    case 'Polestaff':
      return 'STAFF';
    default:
      // Melee weapons need no ammo and are left unclassified.
      return null;
  }
}

function classifyAmmo(item) {
  const name = item.name.toLowerCase();

  if (name.includes('brutal')) {
    return 'BRUTAL';
  }
  if (name.includes('arrow')) {
    return 'ARROW';
  }
  if (name.includes('bolt')) {
    return 'BOLT';
  }
  if (name.includes('javelin')) {
    return 'JAVELIN';
  }

  // Blessings, tars and other ammo-slot oddities that no weapon fires.
  return null;
}

async function main() {
  process.stdout.write(`Downloading ${SOURCE}\n`);
  const response = await fetch(SOURCE);
  if (!response.ok) {
    throw new Error(`Download failed: ${response.status} ${response.statusText}`);
  }

  const items = await response.json();
  const categories = {};
  const counts = {};

  for (const item of items) {
    let category = null;

    if (item.slot === 'weapon') {
      category = classifyWeapon(item);
    } else if (item.slot === 'ammo') {
      category = classifyAmmo(item);
    }

    if (category) {
      categories[item.id] = category;
      counts[category] = (counts[category] ?? 0) + 1;
    }
  }

  const output = {
    dataVersion: 1,
    source: SOURCE,
    generatedAt: new Date().toISOString().slice(0, 10),
    categories,
  };

  mkdirSync(dirname(OUTPUT), { recursive: true });
  writeFileSync(OUTPUT, JSON.stringify(output));

  process.stdout.write(`Wrote ${Object.keys(categories).length} classified items to ${OUTPUT}\n`);
  for (const [category, count] of Object.entries(counts).sort()) {
    process.stdout.write(`  ${category.padEnd(16)} ${count}\n`);
  }
}

main().catch((error) => {
  process.stderr.write(`${error.stack}\n`);
  process.exit(1);
});
