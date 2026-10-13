# Changelog

Notable changes to Best Gear Setup. Version numbers match `version=` in `runelite-plugin.properties`, which
is what the Plugin Hub shows.

## Unreleased

- Recommend an overhead protection prayer with every setup, for single monsters and each raid encounter:
  curated raid advice with a reason where the fight switches styles, otherwise the Wiki attack styles.
  Ranged and magic setups fight from distance, so melee-only attacks need no prayer; overheads above your
  Prayer level are left out.
- Raid encounters follow the order the rooms appear: ToB from Maiden to Verzik P3, the common CM CoX rotation
  with Olm last, and a new **ToA path order** (Ba-Ba first or Zebak first) with the Wardens last.
- Raid boosts follow the raid: CoX overloads after Tekton (including enraged Tekton), ToA smelling salts after
  the first two paths, and none in ToB.
- Choose each raid encounter's **Attack style**; Auto plans Skeletal Mystics, the large Muttadile and Zebak
  from distance. The choice is saved and restricts that encounter's search.
- **ToB Maiden role** (DPS, freezer or both) and **ToB Nylocas role** (the wave colour you kill). Wave
  nylocas and Vasilias forms are planned with their own style only.
- Move fight settings (active encounter, thralls, special attacks, raid equipment slots and raid
  spellbook) from Settings into the Fight editor.
- Keep raid ammunition worn in otherwise empty ammo slots, including the starting setup, and reflect it
  in inventory capacity and the bank layout. Loaded blowpipe darts appear in the pipe's tooltip instead.
- Add loaded-dart exclusions to worn, special and inventory blowpipe menus. Inventory gear uses the
  same tooltips and lock, exclusion and ownership actions as equipped gear; supplies retain pouch contents
  and gain exclusion and ownership menus.
- Derive Olm hand repetitions from raid party scale and use every required hand phase when comparing
  shared kits and equipment reductions; keep the head as one fight and displayed HP per phase.
- Raid consolidation compares the fight time lost by removing switches instead of discarding less-used
  armour; prevent unnecessary downgrades of owned Blue Moon armour and imbued god capes in CM CoX.
- Full raids are searchable by name or CM CoX / ToA / ToB; remove the Encounter plan selector.
- Sidebar order is search, current boss/monster, Version, gear source, buttons, equipment and collapsible
  inventory. The boss selector stays synchronized with the raid equipment currently shown.
- Search progress covers the entire raid, including kit selection and equipment consolidation; it never
  resets between encounters or reaches 100% before results are ready.
- Pack raid runes into owned regular/divine pouches, count the pouch as one inventory slot and list
  its planned contents on hover. Move the sidebar inventory below worn equipment.
- Full CoX Challenge Mode, ToA and normal ToB plans with encounter and boss-phase setups, starting gear
  for Tekton / Ba-Ba / Maiden, a shared inventory and worn-equipment / inventory bank layout.
- Raid equipment-slot limit and one spellbook for the raid; the shared kit must fit the entire gear budget
  and 28 inventory slots, including after equipment switches.
- Optional active encounter targeting of the NPC you are in combat with (not one merely clicked or nearby),
  with tracked defence drains matched to the actual NPC instance.
  Combat updates stay inside an existing raid kit instead of re-planning every room.
- When the setup needs runes, the bank layout and highlights include your rune pouch or divine rune pouch.

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
