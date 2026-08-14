/**
 * Regenerates src/main/resources/com/gearforge/monsters.json
 *
 * Source: the OSRS Wiki's own DPS calculator dataset, which is derived from wiki monster data.
 * Wiki content is CC BY-NC-SA — attribution is required and the licence is non-commercial. GearForge
 * is free and open source and credits the wiki in its README and in the Bosses tab.
 *
 * Every attackable monster is kept, trimmed to the handful of fields the DPS engine needs, so players
 * can score gear against whatever they actually fight. The `boss` flag marks the curated set the
 * Bosses tab lists.
 *
 * Usage:  node scripts/generate-monsters.mjs
 */

import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const SOURCE = 'https://raw.githubusercontent.com/weirdgloop/osrs-dps-calc/main/cdn/json/monsters.json';
const ATTRIBUTION = 'Monster data from the OSRS Wiki (CC BY-NC-SA 3.0), via weirdgloop/osrs-dps-calc';

const here = dirname(fileURLToPath(import.meta.url));
const OUTPUT = resolve(here, '../src/main/resources/com/gearforge/monsters.json');

/**
 * Bosses worth shipping. Matched on exact name; every version of a matched name is kept so phase
 * and variant selection works (Zulrah's forms, Vorkath, Olm's hands).
 */
/**
 * Below this, an ordinary monster is not worth listing. Bosses and slayer creatures are exempt.
 */
const MINIMUM_LEVEL = 50;

const BOSSES = [
  // God Wars Dungeon
  'General Graardor', "K'ril Tsutsaroth", 'Commander Zilyana', "Kree'arra", 'Nex',
  // Wilderness
  'Callisto', 'Artio', 'Venenatis', 'Spindel', "Vet'ion", "Calvar'ion",
  'Chaos Elemental', 'Chaos Fanatic', 'Crazy archaeologist', 'Scorpia', 'King Black Dragon',
  // Slayer
  'Abyssal Sire', 'Cerberus', 'Alchemical Hydra', 'Kraken', 'Thermonuclear smoke devil',
  'Dusk', 'Dawn', 'Skotizo', 'Demonic gorilla',
  // Solo / quest
  'Vorkath', 'Zulrah', 'Corporeal Beast', 'Giant Mole', 'Deranged archaeologist', 'Sarachnis',
  'Kalphite Queen', 'Dagannoth Rex', 'Dagannoth Prime', 'Dagannoth Supreme', 'Obor', 'Bryophyta',
  'Hespori', 'Zalcano', 'Scurrius', 'Araxxor', 'Amoxliatl', 'The Hueycoatl',
  // Nightmare
  'The Nightmare', "Phosani's Nightmare",
  // Desert Treasure II
  'Phantom Muspah', 'Duke Sucellus', 'The Leviathan', 'The Whisperer', 'Vardorvis',
  // Gauntlet
  'Crystalline Hunllef', 'Corrupted Hunllef',
  // Chambers of Xeric
  'Tekton', 'Vasa Nistirio', 'Vespula', 'Muttadile', 'Vanguard', 'Great Olm', 'Ice demon',
  'Abyssal portal', 'Skeletal Mystic', 'Lizardman shaman',
  // Theatre of Blood
  'The Maiden of Sugadinti', 'Pestilent Bloat', 'Nylocas Vasilias', 'Sotetseg', 'Xarpus',
  'Verzik Vitur',
  // Tombs of Amascut
  'Akkha', 'Ba-Ba', 'Kephri', 'Zebak', "Tumeken's Warden", "Elidinis' Warden",
  // Other notable
  'Sol Heredit', 'TzTok-Jad', 'TzKal-Zuk', 'Yama',
];

const WANTED = new Set(BOSSES);

async function main() {
  process.stdout.write(`Downloading ${SOURCE}\n`);
  const response = await fetch(SOURCE);
  if (!response.ok) {
    throw new Error(`Download failed: ${response.status} ${response.statusText}`);
  }

  const all = await response.json();
  const matched = new Set();
  const monsters = [];

  for (const monster of all) {
    const skills = monster.skills ?? {};

    // Everything attackable is kept so DPS can be checked against the thing you actually fight, not
    // just bosses. The boss flag is only what the Bosses tab lists.
    if (!(skills.hp > 0)) {
      continue;
    }

    const isBoss = WANTED.has(monster.name);
    if (isBoss) {
      matched.add(monster.name);
    }

    // Nobody looks up their best-in-slot for a level 13 zombie — they use a DPS calculator for that,
    // and every one of these makes the picker longer for everyone else. Bosses and slayer creatures
    // are kept at any level, because a low-level slayer task is still a task you grind.
    if (!isBoss && !monster.is_slayer_monster && (monster.level ?? 0) < MINIMUM_LEVEL) {
      continue;
    }

    const defensive = monster.defensive ?? {};

    monsters.push({
      id: monster.id,
      name: monster.name,
      version: monster.version ?? '',
      combatLevel: monster.level ?? 0,
      size: monster.size ?? 1,
      defenceLevel: skills.def ?? 0,
      magicLevel: skills.magic ?? 0,
      // The twisted bow scales off whichever of magic level and magic attack is higher.
      magicAttack: monster.offensive?.magic ?? 0,
      hitpoints: skills.hp ?? 0,
      // Ranged defence is split by ammo weight in the source; "standard" is the sane default.
      defensive: {
        stab: defensive.stab ?? 0,
        slash: defensive.slash ?? 0,
        crush: defensive.crush ?? 0,
        magic: defensive.magic ?? 0,
        ranged: defensive.standard ?? 0,
      },
      attributes: monster.attributes ?? [],
      // Elemental weakness. A matching spell gains this percentage of both accuracy and max hit,
      // which is far too large to leave out — over a thousand monsters carry one.
      weaknessElement: monster.weakness?.element ?? null,
      weaknessSeverity: monster.weakness?.severity ?? 0,
      slayerMonster: Boolean(monster.is_slayer_monster),
      boss: isBoss,
    });
  }

  monsters.sort((a, b) => a.name.localeCompare(b.name) || a.version.localeCompare(b.version));

  const missing = BOSSES.filter((name) => !matched.has(name));
  if (missing.length > 0) {
    process.stdout.write(`\nWARNING - these names matched nothing and were dropped:\n`);
    for (const name of missing) {
      process.stdout.write(`  - ${name}\n`);
    }
    process.stdout.write('\n');
  }

  const output = {
    dataVersion: 1,
    attribution: ATTRIBUTION,
    source: SOURCE,
    generatedAt: new Date().toISOString().slice(0, 10),
    monsters,
  };

  mkdirSync(dirname(OUTPUT), { recursive: true });
  writeFileSync(OUTPUT, JSON.stringify(output));

  const bossEntries = monsters.filter((m) => m.boss).length;
  process.stdout.write(
    `Wrote ${monsters.length} attackable monsters (${bossEntries} boss entries covering `
    + `${matched.size}/${BOSSES.length} bosses) to ${OUTPUT}\n`,
  );
}

main().catch((error) => {
  process.stderr.write(`${error.stack}\n`);
  process.exit(1);
});
