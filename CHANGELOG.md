# Changelog

Notable changes to Best Gear Setup. Version numbers match `version=` in `runelite-plugin.properties`, which
is what the Plugin Hub shows.

## 1.0.0 (unreleased)

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
