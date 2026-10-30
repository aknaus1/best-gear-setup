# Changelog

Notable changes to Best Gear Setup. Version numbers match `version=` in `runelite-plugin.properties`, which
is what the Plugin Hub shows.

## Unreleased

- Add Allowed and Excluded spell cards to Gear, with spell icons. They restrict autocast choices while leaving
  powered staves and salamanders available, show spells by unlock level, and explain when no permitted spell can
  be cast.
- Show expected attack and support runes per kill below estimated kills per hour. Counts include rune saving,
  staff and tome supply, special attack time, and the correct rune costs for each thrall tier. Powered staff
  charges, tome pages and the Master Combat Achievements thrall duration bonus are not counted.
- The bank layout shows your rune pouch or divine rune pouch beside the spell runes, and a rune's bank
  placeholder now keeps its cell ahead of combination runes, since runes in a pouch aren't visible to the bank.
- Searching a monster now picks its normal version instead of a Leagues (echo), Deadman, Nightmare Zone or PvP Arena one, so "black dragon" uses the level 227 dragon and its water weakness.

## 1.1.1 (2026-10-20)

- Add The Fractured Archive's NPCs from the Wiki: the Archive Guardian (flying and grounded), Ezekiel, Bork and
  the Smorks, Moia, Dreadnoth the Jellied and his jellies, Xarth and his corpses, the Dagon'hai Elder and Monks,
  the undead binders, and Ocellus's first and third phases with his void rifts, augmenters and siphons, with
  their poison, venom and freeze resistances. Ocellus's second phase is left out until the Wiki has its stats, as
  is any monster page without them.
- Refresh the other Wiki monster data (Blink's forms are now named *combo eating* and *special attacks*).
- Add the raid's equipment from the Wiki and the game cache: the Ascension crossbows with their bolts (plain,
  diamond and onyx, enchanted or not), the Obligator, Zorya's tome and the Rondache. Zorya's tome is a 3-tick
  powered staff with its base max hit of 18. The Obligator's Thrust (accurate or aggressive, 5 ticks) keeps the
  best of two damage rolls, three if crush is the target's second-weakest melee defence and four if its weakest,
  and deals 20% more to a 2x2 target and 40% more to 3x3 or larger; its Smash is a plain 3-tick crush attack.
  Thrust's hits on several small targets in a line and the Rondache's flat armour aren't modelled.

## 1.1.0 (2026-10-10)

- New *Inventory + equipped only* mode: searches use only what you carry and wear, ignoring the bank.
- New **Gear** panel lists your locks, excluded items and extra owned items, with a search to add more and
  separate buttons to clear locks and exclusions.
- New in-panel **Settings** view with the same options as the RuneLite configuration page.
- With *Use active defence* (on by default), defence reductions from specials that land on your target,
  yours and your party's, replace the pre-fight preparation values. Own specs are tracked without the
  Special Attack Counter plugin.
- Searches are about 40% faster, with the same results.
- Cosmetic copies with identical stats (recolours, ornament kits) are searched once. Tooltips list the
  alternatives and the bank layout accepts any of them. The blazing blowpipe and Dinh's blazing bulwark now
  count as their base items.
- Cosmetic copies use their original's ammo and spell compatibility.
- Results header shows raid party scaling and the target's Defence before and after drains.
- OSRS Wiki snapshots refreshed (retrieved 10 October 2026 UTC).

## 1.0.0 (2026-10-09)

First Plugin Hub submission.

- Finds the best melee, ranged and magic setup for any monster or boss, from the side panel or by
  right-clicking an NPC, in *Owned items only*, *Owned + budget* or *Best in slot* mode.
- Reads owned items from your bank, inventory and equipment and remembers them per account. You can also
  mark items as owned by hand, lock slots and exclude items.
- DPS calculator based on the OSRS Wiki formulas, covering encounter rules, raid scaling, boss phases,
  special attacks, AoE, damage over time and effects that change with HP over the fight. See
  [COMBAT_AUDIT.md](COMBAT_AUDIT.md).
- Mandatory slayer protection (facemasks, earmuffs, nose pegs, mirror shields, Karuulm boots and others)
  and dragonfire protection are enforced as gear requirements.
- Ships with OSRS Wiki snapshots (retrieved 1 October 2026 UTC), so the plugin makes no network requests.
