# Changelog

Notable changes to Best Gear Setup. Version numbers match `version=` in `runelite-plugin.properties`, which
is what the Plugin Hub shows.

## Unreleased

- Show the inventory layout for every search, not only setups with equipment switches: the panel's
  inventory and the bank layout now place boosts, runes and pouches in their inventory slots.

## 1.1.1 (2026-10-17)

- Add Beta raid planning for CM CoX, ToA and ToB: shared kits, encounter setups, spellbook
  comparisons, role duties, path order, gear budgets and 28-slot inventory limits.
- Follow live raid progress and boss phases within the packed kit, using current scaling,
  boosts, landed defence reducers and remaining special energy.
- Add CM CoX chest guidance, protection-prayer advice and corrected encounter mechanics.
- Preserve Maiden freeze accuracy and worn equipment; require Ava's / quiver recovery by default.
- Group inventory switches by combat style and show spell supplies, prepot guidance and boost bottles.
  Dose estimates exclude healing, restores and stamina; supply quantities and cost remain unvalidated.
- Persist bounded, checksummed search results per profile; reuse unchanged ownership across transfers
  and refresh incompatible results. Raid encounter trials stay in memory to reduce search overhead.
- Include recorded storage through optional Dude, Where's My Stuff? integration; clear it on account changes.
- Improve bank highlights, decorated-item matching, item menus and search progress.
- Simplify the sidebar and collapse search details. Default ToA to 150 and Ba-Ba first.
- Say when a raid search finds nothing because *Inventory + equipped only* mode sees no carried gear.

See [mechanics](docs/mechanics.md) for assumptions and limitations.

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
