# Combat formulas as implemented

Derived from the OSRS Wiki on 2026-08-06 by reading page source directly, not from recollection.
Wiki content is CC BY-NC-SA; these are the underlying game mechanics, implemented in our own code.

Sources:
- [Damage per second/Melee](https://oldschool.runescape.wiki/w/Damage_per_second/Melee)
- [Damage per second/Ranged](https://oldschool.runescape.wiki/w/Damage_per_second/Ranged)
- [Maximum ranged hit](https://oldschool.runescape.wiki/w/Maximum_ranged_hit)
- [Damage per second/Magic](https://oldschool.runescape.wiki/w/Damage_per_second/Magic)
- [Maximum magic hit](https://oldschool.runescape.wiki/w/Maximum_magic_hit)

## Corrections to the project spec (§5)

The spec's formulas are wrong in three places. Implemented per the wiki, not the spec:

1. **Style bonus is additive, not multiplicative.** The spec has
   `floor(floor(level * prayer) * styleBonus) + styleAdd + 8`. There is no style *multiplier* — it is
   `floor(level * prayer) + styleAdd + 8`, then a void multiplier.
2. **Average damage carries a correction term.** The spec has `hitChance * (maxHit / 2)`. The wiki
   uses `hitChance * (maxHit/2 + 1/(maxHit+1))`, because a damage roll of 0 on a successful hit is
   converted to 1. Consistent with a uniform roll over `0..maxHit` where 0 becomes 1.
3. **Magic defence roll uses the target's Magic level, not its Defence level** — `(9 + magicLevel) *
   (magicDefBonus + 64)`. Using defence level there is a common and significant error.

## Effective levels

Melee and ranged (void applied last):

    effective = floor( (floor((level + boost) * prayerMult) + styleAdd + 8) * voidMult )

Magic (void applied *before* the style add — deliberately different):

    effective = floor( floor((level + boost) * prayerMult) * voidMult + styleAdd + 8 )

## Style additions

| Style | Melee attack | Melee strength | Ranged (both) | Magic |
|---|---|---|---|---|
| Accurate | +3 | 0 | +3 | +3 (powered staves only) |
| Aggressive | 0 | +3 | — | — |
| Controlled | +1 | +1 | — | — |
| Defensive | 0 | 0 | — | — |
| Rapid | — | — | 0 (−1 tick speed) | — |
| Longrange | — | — | 0 | +1 (powered staves only) |

## Prayer multipliers

Attack / strength differ, and ranged attack differs from ranged strength for Rigour.

| Prayer | Attack | Strength |
|---|---|---|
| Burst of Strength | — | 1.05 |
| Superhuman Strength | — | 1.10 |
| Ultimate Strength | — | 1.15 |
| Clarity of Thought | 1.05 | — |
| Improved Reflexes | 1.10 | — |
| Incredible Reflexes | 1.15 | — |
| Chivalry | 1.15 | 1.18 |
| Piety | 1.20 | 1.23 |

| Prayer | Ranged attack | Ranged strength |
|---|---|---|
| Sharp Eye | 1.05 | 1.05 |
| Hawk Eye | 1.10 | 1.10 |
| Eagle Eye | 1.15 | 1.15 |
| Deadeye | 1.18 | 1.18 |
| Rigour | 1.20 | **1.23** |

Magic: Mystic Will 1.05, Mystic Lore 1.10, Mystic Might 1.15, Augury 1.25.

## Void multipliers

| Set | Accuracy | Strength |
|---|---|---|
| Melee void (regular or elite) | 1.1 | 1.1 |
| Ranged void | 1.1 | 1.1 |
| Elite ranged void | 1.1 | **1.125** |
| Magic void | 1.45 | (damage handled as a set effect) |

## Rolls

    attackRoll  = floor(effectiveAttack * (equipmentAttackBonus + 64) * gearBonus)

    defenceRoll = (targetDefenceLevel + 9) * (targetStyleDefenceBonus + 64)     // melee, ranged
    defenceRoll = (targetMagicLevel + 9)   * (targetMagicDefenceBonus + 64)     // magic

    hitChance = attackRoll > defenceRoll
              ? 1 - (defenceRoll + 2) / (2 * (attackRoll + 1))
              : attackRoll / (2 * (defenceRoll + 1))

## Max hits

Melee and ranged share a shape. Integer arithmetic avoids float rounding at the floor:

    maxHit = floor( (effectiveStrength * (strengthBonus + 64) + 320) / 640 )
    maxHit = floor( maxHit * gearBonus )

Magic, core case only (the full chain of modifiers in `Maximum magic hit` is set-effect work):

    maxHit = floor( baseSpellDamage * (1 + magicDamageBonus) )

where `magicDamageBonus` is the equipment magic damage percent / 100.

## Output

    averageDamage = hitChance * (maxHit / 2 + 1 / (maxHit + 1))
    dps           = averageDamage / (attackSpeedTicks * 0.6)

Standard and Ancient spells are 5 ticks. Powered staves use the weapon's own speed (4 ticks).
Rapid reduces a ranged weapon's speed by 1 tick.
