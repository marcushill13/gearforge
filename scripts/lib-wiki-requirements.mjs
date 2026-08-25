/**
 * Reads equipment level requirements out of the OSRS Wiki.
 *
 * osrsbox-db exposes requirements as a structured field, which is why it is the primary source — but
 * it stopped being updated in 2021, so nothing released since is in it. That is not a small gap: it
 * is every piece of gear a modern account would actually be recommended, and the plugin treats an
 * absent entry as "unknown", which reads to the player as "you can wear this".
 *
 * The wiki has no structured field for requirements. It states them in the lead sentence of each
 * item's page, phrased consistently enough to read: "requires level 75 Strength and 50 Defence to
 * equip". Only confident matches are kept; anything else stays unknown, exactly as before.
 */

const API = 'https://oldschool.runescape.wiki/api.php';
const USER_AGENT = 'GearForge/1.0 (RuneLite plugin; equipment requirement generation)';

/** Only combat-relevant skills; a Smithing level to repair an item is not a requirement to wear it. */
export const SKILLS = ['attack', 'strength', 'defence', 'ranged', 'magic', 'prayer', 'hitpoints', 'slayer'];
const SKILL_PATTERN = SKILLS.map((skill) => skill[0].toUpperCase() + skill.slice(1)).join('|');

/** The verbs that mean "to put it on", as opposed to making, repairing or charging it. */
const EQUIP_VERBS = /\b(?:equip|wear|wield|use)\b/i;

/**
 * A sentence that also talks about building the item is thrown away rather than picked apart. The
 * DT2 rings state both in one breath — "requires having killed Vardorvis to wear ... the ring
 * requires level 90 Magic and 80 Crafting to create" — and reading it gave a 90 Magic requirement to
 * a ring that has none. Losing the odd real one costs nothing: unknown is how it was before.
 */
const MAKING_VERBS = /\bto\s+(?:create|make|craft|forge|build|repair|imbue|enchant|attach|unlock)\b/i;

const NUMBER_FIRST = new RegExp(
  String.raw`(\d{1,3})\s*(?:in\s+)?(?:\[\[)?(` + SKILL_PATTERN
    + String.raw`)(?:\|[^\]]*)?(?:\]\])?(\s+and\s+(?:\[\[)?(` + SKILL_PATTERN
    + String.raw`)(?:\]\])?)?`,
  'g');

// "[[Defence]] level 80" and "a [[Ranged]] level of 80", which is how a fair few pages put it.
const SKILL_FIRST = new RegExp(
  String.raw`\[\[(` + SKILL_PATTERN + String.raw`)(?:\|[^\]]*)?\]\]\s*level\s*(?:of\s*)?(\d{1,3})`,
  'gi');

/**
 * Pulls "75 [[Strength]]" and "[[Defence]] level 80" out of one sentence, in either order.
 *
 * Also handles the two phrasings that would otherwise read as no requirement at all: "level 80 in
 * [[Ranged]]", and one level shared across two skills, as in "80 [[Attack]] and [[Strength]]".
 */
function levelsIn(sentence) {
  const found = {};

  for (const match of sentence.matchAll(NUMBER_FIRST)) {
    const level = Number(match[1]);
    if (level < 2 || level > 99) {
      continue;
    }

    found[match[2].toLowerCase()] = level;

    // "80 Attack and Strength" states one level for both, and reading only the first understates the
    // requirement — the direction that puts unwearable gear back on the recommendation.
    if (match[4]) {
      found[match[4].toLowerCase()] = level;
    }
  }

  for (const match of sentence.matchAll(SKILL_FIRST)) {
    const level = Number(match[2]);
    if (level >= 2 && level <= 99) {
      found[match[1].toLowerCase()] = level;
    }
  }

  return found;
}

/**
 * The requirements stated on one page, or null if the page never states any.
 *
 * Only the lead section is read. Later sections talk about killing things, repairing things and
 * unlocking things, all with levels of their own — reading the whole article turned "the ring
 * requires you to have killed Vardorvis" into a 90 Magic requirement on a ring that has none.
 */
export function requirementsFromWikitext(wikitext) {
  const lead = wikitext.split(/\n==/)[0];

  // The infobox carries levels of its own — a Slayer level to kill something, a Smithing level to
  // repair — that have nothing to do with equipping.
  const prose = lead.replace(/\{\{[^{}]*\}\}/gs, ' ').replace(/\[\[File:[^\]]*\]\]/g, ' ');

  for (const sentence of prose.split(/(?<=\.)\s+/)) {
    if (!/requir/i.test(sentence) || !EQUIP_VERBS.test(sentence)
      || MAKING_VERBS.test(sentence)) {
      continue;
    }

    const found = levelsIn(sentence);
    if (Object.keys(found).length > 0) {
      return found;
    }
  }

  return null;
}

/**
 * Whether the page states, one way or another, that the item needs no levels to wear.
 *
 * Absent data and no requirement are not the same thing, and the plugin was treating them alike: it
 * warned "could not check requirements for Amulet of glory" about an amulet anyone can wear. A page
 * that never names a combat skill in the same breath as putting the item on is stating that there is
 * nothing to check. A page that does name one but whose wording could not be read stays unknown,
 * since that is the case where guessing "none" would put unwearable gear back on the panel.
 */
export function statesNoRequirement(wikitext) {
  const lead = wikitext.split(/\n==/)[0];
  const prose = lead.replace(/\{\{[^{}]*\}\}/gs, ' ').replace(/\[\[File:[^\]]*\]\]/g, ' ');
  const anySkill = new RegExp(SKILL_PATTERN, 'i');

  for (const sentence of prose.split(/(?<=\.)\s+/)) {
    if (!/requir/i.test(sentence) || !EQUIP_VERBS.test(sentence) || MAKING_VERBS.test(sentence)) {
      continue;
    }

    if (anySkill.test(sentence)) {
      return false;
    }
  }

  return true;
}

/**
 * Every item id the page describes, including each version of a multi-version item.
 */
export function idsFromWikitext(wikitext) {
  const ids = [];
  for (const match of wikitext.matchAll(/^\|\s*id\d*\s*=\s*(\d+)\s*$/gm)) {
    ids.push(Number(match[1]));
  }

  return ids;
}

/**
 * Fetches the wikitext of up to 50 pages in one request.
 *
 * @returns {Promise<Map<string, string>>} title to wikitext, missing pages absent
 */
export async function fetchWikitext(titles) {
  const url = new URL(API);
  url.searchParams.set('action', 'query');
  url.searchParams.set('prop', 'revisions');
  url.searchParams.set('rvprop', 'content');
  url.searchParams.set('rvslots', 'main');
  url.searchParams.set('redirects', '1');
  url.searchParams.set('format', 'json');
  url.searchParams.set('formatversion', '2');
  url.searchParams.set('titles', titles.join('|'));

  const response = await fetch(url, { headers: { 'User-Agent': USER_AGENT } });
  if (!response.ok) {
    throw new Error(`Wiki request failed: ${response.status} ${response.statusText}`);
  }

  const body = await response.json();
  const wikitext = new Map();

  for (const page of body?.query?.pages ?? []) {
    const content = page?.revisions?.[0]?.slots?.main?.content;
    if (content) {
      wikitext.set(page.title, content);
    }
  }

  // The API reports the title it resolved to; map the name we asked for back onto the content.
  for (const list of [body?.query?.normalized ?? [], body?.query?.redirects ?? []]) {
    for (const entry of list) {
      const content = wikitext.get(entry.to);
      if (content) {
        wikitext.set(entry.from, content);
      }
    }
  }

  return wikitext;
}
