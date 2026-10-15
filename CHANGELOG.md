# Changelog

Notable changes to Best Gear Setup. Version numbers match `version=` in `runelite-plugin.properties`, which
is what the Plugin Hub shows.

## Unreleased

- When the Nylocas Vasilias changes form, the raid view now switches to the new form's setup straight away. Before, the
  form change dropped your attack and the view stayed on the old form until you clicked the boss again.
- Ranged and magic kits no longer leech the party's defence reducers. If you can use an elder maul, dragon
  warhammer or Bandos godsword, every kit at Maiden, Xarpus, Sotetseg, Tekton and Olm's melee hand switches to it
  for your fair share of the reducer specs, as a melee kit already did. Before, a ranged or magic kit took full
  credit for the teammates' hammers and left the maul in the bank.
- Raid boost dose estimates follow each potion's real duration instead of a flat five-minute refresh. The new
  **Divine potions** setting (on by default) plans divine versions where they exist, each dose holding the
  boost for five minutes, and matches divine bottles first in the bank, prepots included. Ordinary boosts drain a level a minute and are drunk
  again below the new **Repot below** threshold (default 50% of the boost at your level), so a +19 super combat
  at 99 covers about 9.5 minutes per dose. Combined potions are drunk again when their first skill needs it.
- Raid inventories are laid out the way players arrange them, packed with no gaps, in the sidebar and the
  bank layout. The main switch (the style with the most carried gear) comes first, then the other styles'
  switches in prayer-book order (magic, ranged, melee), in blocks two rows tall filled down each column. Pieces
  worn in the same encounters form a set, and each set starts its own column: a four-piece set forms a square
  (slots 1, 2, 5 and 6) and a two-piece switch a vertical pair, so each switch is a quick 1, 5, 2, 6 rather than
  a stretch along a row. The book of the dead and the rune pouch fill the cells those switches leave free. The
  special weapons start a fresh row below, in the same style order, and spell supplies the switch rows couldn't
  hold complete it, the rune pouch at the end. Then the pickaxe, then one-off pieces, then potions. A one-off is a
  situational piece never worn alongside the rest of its style's carried gear, such as a salve amulet swapped in
  only at Bloat, or a style's only carried item other than a weapon several encounters switch to.
- Prepot-only boosts now appear in the raid bank layout, below the worn equipment, so they can be withdrawn
  and drunk before entering. Raid boosts in the bank are tagged Pre, Bring or Both like the sidebar, with the
  same explanation on hover in the layout, and prepots are outlined with the rest of the kit.
- Raid gear packing preserves the freeze accuracy found before removing equipment. It cannot replace a guaranteed
  Maiden freeze with a splashable fallback to free slots for specs or other gear. Freeze notes now name the
  offensive prayer and Magic boost assumed by the selected switch. Freeze switches equip replacements while
  retaining all other worn gear; they cannot gain accuracy by silently removing melee armour.
- ToB freezers now plan Maiden's freeze to never splash rather than for damage. Binds on Maiden's nylocas
  always land once the magic attack roll reaches (base Magic + 9) × 204 (Wiki), and the freeze is the switch
  from the Maiden setup with the fewest items reaching it, autocasting rather than casting by hand whenever an
  autocasting weapon reaches it too, always from the Maiden setup actually worn (a mage Maiden setup may need no
  switch); the Maiden setup is planned with such a freeze when
  any exists, otherwise with the most accurate. The Ice ancient sceptre's unfrozen-target bonus counts on every
  freeze. South freeze assumes a saturated or imbued heart for the freeze when one can be used. The freeze no
  longer counts as combat time.
- Saved searches now hit far more often. The cache key keeps only owned items a search can read (gear and
  its variants, boosts and rune pouches), so loot, coins and food no longer force a fresh search. Owned-only
  and Inventory-only searches no longer key on GE prices, which never change their result, so they survive
  price refreshes and client restarts; Budget, Best in slot and Wilderness-capped searches still do. A
  regression test records every ownership query a real search makes and fails if the key ignores one.
- Saved searches now split large results into 16-KiB profile settings, publishing a checksummed manifest
  after all chunks. RuneLite rejected the previous roughly half-megabyte raid values during configuration
  sync, leaving their index entries but losing the actual plans across restart. Missing/corrupt chunks
  safely miss, and eviction removes every chunk. Regression tests cover sync size rejection and large
  records restored in a separate JVM.
- Raid searches take about half as long, with the same results (a measured ToB search: 67s to 35s). A raid
  search runs thousands of encounter searches; each was being compressed and queued as a profile write, and
  keyed by serialising its whole excluded-item set. They now stay in their own memory-only cache, so they
  also no longer push your single-target results out of the profile, and their keys reuse digests of shared
  price, ownership and exclusion snapshots. Damage-over-time kill times, loadout hashing and equipment-rule
  name checks no longer repeat work for every candidate.
- Saved raid plans are about 85% smaller: a plan keeps only each encounter's chosen setup, the only one a
  published plan reads, and records list field names once per type. The profile now keeps 12 raids and 64
  single-target results in the same 1.75 MiB.
- Completed raid and encounter caches now persist in each RuneScape profile and survive client restarts.
  Deterministic SHA-256 input keys replace session object identities, while saved code/data fingerprints
  reject incompatible results. Compressed bounded records preserve raid switches, shared loadouts and
  identity-keyed potion assumptions. Missing, corrupt or outdated records fall back to a normal search.
- Owned gear is now published as one immutable category across bank, inventory, equipment and connected
  storage. Totals must agree across two consecutive ticks, preventing partial withdrawals or equips from
  appearing as item losses or duplicate stacks. Search inputs capture availability and quantities together;
  unchanged owned gear and relevant ammo totals retain the same cache key and hash regardless of location.
- Inventory switches now use a fixed equipment-slot order within each combat group, with weapons and
  offhands first, followed by armour and accessories. Same-slot items sort by name rather than encounter
  traversal; sidebar, bank and refreshed raid inventories share the order. Specials, tools, potions and
  runes follow combat gear, with repeated potion bottles kept together.
  Switch groups pack consecutively without blank rows between small switches. Gear worn only to fill an
  empty slot, or inherited unchanged by a quick weapon switch, keeps its original combat group.
- Optional Dude, Where's My Stuff? integration includes its recorded STASH, POH costume storage,
  active deathbanks/deathpiles, group storage and other item storage in owned gear and ammunition totals.
  Gear tooltips show recorded locations. Full snapshots replace removed contents; account changes and
  a disconnected provider clear imported data. Bank/inventory/equipment are counted once, and stored
  items stay excluded from Inventory + equipped only. Storage transfers wait for the provider's reply
  before checking ownership, preserving searches while withdrawing or depositing the same gear.
- Selected raid plans now track live progress: show the starting kit when the raid begins, select saved
  setups for live targets and Akkha phases, and advance to the next room after completion. ToB and ToA
  use game completion messages; CM CoX waits for its room's required NPC deaths. Adds, disappearing NPCs,
  puzzle completions and phase changes do not count as defeated bosses. ToA room entry follows the actual
  chosen path. The sidebar shows completion progress and resets after leaving or changing accounts.
  Progress navigation uses the packed kit without searches; ammunition consumption, loot and live drains
  do not re-plan an active raid. Explicit setup searches and settings changes can still re-plan it.
- Right-click **Best setup** on a monster in the selected raid now opens its existing encounter setup,
  including Akkha's live protection phase, without restarting the search or leaving raid mode. During
  planning, the requested encounter opens when the current raid search finishes.
- Owned kitted and recoloured equivalents take priority in the bank layout and highlights. Masori
  assembler and its max-cape version match their respective base assemblers for bank presentation;
  base copies remain fallbacks when the decorated item is absent or only a placeholder. Ownership
  continues to require the exact version.
- Inventory highlights now mark only the gear worn for the displayed setup, following raid encounter
  selections. Carried switches, special weapons, loaded darts, runes and boosts are not outlined unless
  they occupy a worn equipment slot. Bank highlights still cover the complete kit.
- Repeated searches reuse recent completed raid plans and encounter results when their calculation inputs
  match. Changing a raid's equipment slot limit can reuse the encounter calculations while re-planning the
  kit. Memory caches switch with the account and profile caches survive restarts; changed prices, levels,
  ownership, relevant ammo quantities, fight settings or live raid inputs require matching fresh results.
- Withdrawing, depositing, equipping or unequipping owned gear no longer restarts the loadout search.
  Ownership and ammunition totals are checked after the tick's container updates finish, so raid results
  stay available while preparing the kit. Actual ownership changes still refresh the search.
- Raid boost supplies now estimate doses and pack each four-dose bottle in its own inventory slot,
  excluding only the initial prepot dose and boosts obtained inside the raid. Counts follow combat time and
  known rest gaps with a five-minute refresh policy; ordinary boosts still decay. Hearts stay reusable.
  Inventory capacity checks and live refresh preserve bottle counts; bank hover guidance gives honest
  withdrawal counts over unchanged real stacks. Owned dose quantities remain unvalidated and supplies
  remain outside the gear budget. Healing, restores and stamina demand is not estimated.
- The raid kit's **estimated combat time** now weighs each room by the time it actually takes:
  - Phases of one HP pool share it instead of each counting it in full (Akkha's phases, the Vasilias forms,
    Zebak and Tekton before and after their enrage).
  - Repeated targets are counted: Maiden's nylocas (the chinned clump apart from the loose ones), the
    Nylocas waves (now with a **big** nylocas encounter), the Apmeken baboon waves, Kephri's scarabs, Akkha's
    shadows, CoX's Guardians, Mystics and Shamans by party size, and the tightrope's rangers and mages.
  - Vasa's crystals follow how fast the party damages Vasa between them.
  - New **More Overlords** toggle (ToA, under **Fight → Raid**) for Kephri's extra scarabs.
  - **Bloat** takes as many downs as the party needs (32 ticks each, with walks between), not HP over DPS.
- Raid spec energy follows the raid from a full bar at the start: ToB refills it as each room starts, while
  ToA and CoX carry it from room to room, regenerating over estimated downtime (puzzles, preparation,
  walking). The kit planner chooses affordable entry-energy variants. Olm hands have chronological phase entries.
- The whole raid kit shares at most one defence-reducing special weapon and one damaging special weapon
  per combat style (melee, ranged and magic), so a mage spec no longer blocks a melee or ranged spec.
  Specials pay their full attack interval; alternate spec and ordinary-only choices keep owned kits feasible.
  Maiden's Matomenos and low-HP raid support adds conserve special energy for bosses.
- Shared boss drains account for teammates continuing ordinary attacks and sharing the drain benefit.
  Sotetseg reopens after maze resets within one finite reserved opening budget; each Olm hand phase separately
  gates its drain opener against energy already available. Unused fight regeneration carries forward.
  Bloat credits a reducer during its first ~18-second down and conservatively excludes uncertain claw burns.
- Teammates also spec: in a party, each other player is assumed to open Olm's melee hand phases, Tekton,
  Maiden and Xarpus with one dragon warhammer special (a fixed, modest reference teammate) before yours. Each
  Sotetseg third gets two reducer attempts, at most one per player. With an elder maul, dragon warhammer or
  Bandos godsword you take your fair share at every one of these bosses, as far as your energy allows: one
  opener at Olm's melee hand, Tekton, Maiden and Xarpus, and two of Sotetseg's six in a party of three to five
  (three in a party of two). A kit no longer leeches the teammates' reducers by spending its bar on damage
  specs. Every class and kit gets the teammates' drains.
  - Each Warden counts in half of raids (one is fought in P2, the other in P3). The core counts the fifth of
    its HP its five-times damage needs, and P2's shield is broken as often as the core's damage requires.
  - Kills take whole attacks, so overkill on small targets counts and can favour a faster weapon there.

- **Quick switch swaps** (under **Fight → Raid**, default 2) sets the most equipment swaps a raid quick switch
  may make, weapon included. It's a limit, not a target: a piece is only swapped when it beats every smaller
  switch, so raising it to 6 still plans a two-piece switch when that's as fast. Quick switches share their
  extra slots, so switching between any two forms stays within the limit; results name the shared slots.
- ToA's **Apmeken baboons** (brawlers, throwers, mages and shamans) are now planned as quick switches like
  the Vasilias forms: one baboon wears a full setup and the others switch from it. Baboons always take a
  max hit from the style they're weak to, so their switches only swap pieces that raise the max hit.
- **Raid equipment slots** is a maximum, not a target, and now also counts the rune pouch, runes outside it
  and a carried Book of the dead; boosts and food still only need to fit in 28 slots. The results summary
  shows the slots used against the limit.
- Raising **Raid equipment slots** could find no kit, or a slower one, than a lower limit (e.g. ToB at 18
  or 20): the planner now keeps the Vasilias forms' and baboons' switches consistent as each joins the kit,
  and keeps comparing shared-gear alternatives when the fastest setups fit but no kit was found from them.

- Raid plans tag each boost with when you drink it: **Pre** (prepot before entering, not carried),
  **Bring** (carried in the inventory) or **Both** (prepot for the first room and brought for a later one).
  The tag shows on the encounter's potion icons and on carried potions in the inventory; hover for details.
  Potions handed out inside the raid (overloads, smelling salts) say so in their tooltip.
- CM CoX's Tekton and ToA's first path are now **prepotted** like ToB's Maiden: their boosts are drunk before
  entering, so the kit no longer carries them unless a later room (e.g. ToA's second path) needs them too.
- Kephri's scarabs are planned **without boosts**; only Kephri herself is boosted, so a potion is carried for
  the Scabaras path only when her setup uses it.

- Raid plans show an **estimated combat time** for the whole raid in the summary, the same estimate kits
  and spellbooks are compared by. Hover it for each boss's share. Teammates are assumed to match your DPS; puzzles, walking and downtime
  aren't counted.

- Audited overhead recommendations across every bundled monster variant, with an exhaustive gear,
  distance and Prayer-level check. Added encounter rules for delayed, counted and phase-based attacks,
  including Jad, Hydra, Hunllef, Muspah, Nightmare, Leviathan, Whisperer, Doom, Yama, Nex and Sailing bosses.
  Manticore advice explicitly requires prayer before launch, while impact-checked attacks are distinguished.
  Corrected Vanguard protection, P1 obelisk advice, Akkha's continuing attacks during shadows, prayer-disabled
  encounters, and mixed accuracy / damage types. KBD, distant Araxxor and Sarachnis also inspect equipment.
  Special modes with unverified mechanics stay explicit instead of borrowing normal-game encounter rules.

- Overhead advice now reads the recommended equipment and estimates ordinary incoming damage from
  attack accuracy, max hits, weights and armour. Mixed attack lists no longer imply reactive prayer switching.
  Vorkath uses Missiles with ranged and a fire shield, and Magic with melee or shieldless ranged; advice
  explains antifire and prayer-disabling breath. Fire requirements and unlocked prayers constrain the choice.
  Ice Demon, distant large Muttadile and Verzik P2 use fixed Missiles; Vasa's switching note distinguishes
  its teleport special from its ordinary boulders. Hover overhead advice for estimate assumptions.

- **Require Ava's / quiver** now applies to every ranged attack whose ammunition an Ava's device recovers
  (arrows, bolts, darts including blowpipe darts, knives, thrownaxes, javelins and atlatl darts), including
  raid setups, rather than only the Eclipse atlatl. Chinchompas, weapons without ammunition, such as
  crystal bows, and special-attack switches are unaffected. Existing saved preferences are preserved.

- ToB Range DPS uses ranged attacks for Maiden's unclumped nylocas. Every ToB role compares melee,
  ranged and magic full setups for the Nylocas boss, keeping the faster compatible kit. Both quick switches use the weapon
  and the same extra gear slot, such as neck or cape; weapon-only switches are also allowed. Results
  name the shared slot, and live encounter updates preserve it.

- The **Fight** button turns orange with a dot when the selected target or raid has options you haven't
  seen yet, such as raid party size, ToA raid level or ToB role. Its tooltip names them. The flag clears once
  you open Fight, and each option is flagged only the first time it appears.

- **ToA path order** has a new **Auto (fastest)** option, now the default. It plans the raid in both
  orders (Ba-Ba first and Zebak first) and keeps the one with the lower estimated combat time; the result
  notes which order was chosen and each order's time. Auto searches take longer because both orders are planned.

- Raid kits no longer drop a stronger switch to keep a weaker one, such as keeping a beacon ring instead of
  an imbued god cape in ToA. The starting setup now wears gear other setups carry anyway in its empty slots
  (for example the melee ring while maging), which frees an equipment slot for the better item. When the kit
  is over the equipment limit, the starting setup may also wear another setup's item that slightly lowers its
  damage (such as melee boots while maging) if that costs less time than dropping a different switch. These
  pieces are highlighted like filled slots.

- Raid kits are worn in for whichever first-room setup carries the fewest switches, not always the first
  fight: any ToA first-path fight (the Apmeken baboons or Ba-Ba, or Zebak when Crondis is first), either
  Tekton phase or a Maiden-room setup. The bank and results open on that starting setup. ToA baboon brawlers
  no longer drift to melee to save switches.

- ToB plans follow common role duties more closely. Freezers freeze Maiden's nylocas and everyone else
  kills them: North freezes and then helps with the unclumped nylocas, Range DPS helps with those until the
  clump forms and then chins it, and Melee DPS now helps with them too. The Vasilias changes form at random
  after a couple of hits, so the plan wears a full setup for one form and quick-switches for the other
  two (the weapon and at most one other piece) instead of bringing full setups for them. Maiden's room is
  prepotted, so its boosts are no longer carried; the kit carries a boost for every later setup that uses
  it, and a quick switch is only boosted when that potion is brought anyway. A potion chosen by name is
  still used.

- CM CoX Scavengers and Vespula no longer shape the raid kit. Scavengers are killed until the potion
  secondaries drop, so how many varies, and players pray Redemption and kill the portal rather than attack
  Vespula. Both are still listed, with the fastest setup built from the kit the other rooms chose, and they
  no longer count toward the Auto spellbook comparison. The Vespula portal still shapes the kit.

- Full raid plans are found faster with the same results: about 2x for CoX CM with owned gear and 2-3.5x in
  Best in slot (ToA around 44s instead of 2.5 minutes). Kit planning stops as soon as no remaining kit can fit
  the equipment limit or beat the best time found, checks inventory space without building display details,
  and Auto spellbook reuses each room's melee and ranged searches across spellbooks when thralls don't
  differ.

- Search progress now has an animated spinner, elapsed time and stage descriptions (comparing equipment,
  checking gear rules, packing raid kits and comparing shared gear), so long steps still show activity
  while the completed-work percentage pauses. Animation stops when results arrive, a search is cleared
  or fails, or the plugin shuts down.

- ToA full plans now use a starting path level (0-3) and Walk the Path toggle. Estimate HP from random
  level increases in the selected path order; live levels override estimates for bosses and supporting
  targets. Individual encounters retain their manual current path level (0-6).

- Raid spellbook defaults to Auto: compare Standard, Ancient and Arceuus complete kits by estimated combat
  time, including modeled spell damage and enabled thralls, under the same shared budget/inventory limits.
  ToB freeze roles lock Ancient even with a saved manual choice; active encounter refreshes keep the chosen
  plan's spellbook. Unavailable kits are skipped, and Vengeance is excluded for now.

- Active encounter keeps the shown results when you engage another NPC of the same monster (same variant and,
  for Akkha, the same prayer), such as the next abyssal demon or wave nylocas; in a raid it shows the plan's
  encounter again instead of searching. Results that used specs landed on the last NPC, or that were planned
  without a live NPC (e.g. a raid's first room picked in the panel), are still refreshed.
- The inventory under worn gear starts minimized, and expanding or minimizing it now carries over to new
  searches as well as raid encounter switches and re-plans.
- The Dawnbringer is never planned as a worn weapon. At Verzik P1 it is a special-attack-only weapon every
  player can use (it is passed around): P1 uses your own setup under her damage caps, plus Dawnbringer specs
  when special attacks are on. It is never banked, bought or carried in, and its special now costs 35% energy.
- Recommend an overhead protection prayer with every setup, for single monsters and each raid encounter:
  curated raid advice with a reason where the fight switches styles, otherwise the Wiki attack styles.
  Ranged and magic setups fight from distance, where melee attacks are dropped unless the monster only melees
  (then Protect from Melee, or none if you safespot it). Unreadable attack data shows Unknown, and overheads
  above your Prayer level are left out, or named with the level needed when none is unlocked.
- Raid encounters follow the order the rooms appear: ToB from Maiden to Verzik P3, the fixed CM CoX layout
  with Olm last, and a new **ToA path order** (Ba-Ba first or Zebak first) with the Wardens last.
- Raid boosts follow the raid: CoX overloads after Tekton (including enraged Tekton), ToA smelling salts after
  the first two paths, and none in ToB.
- Choose each raid encounter's **Attack style**; Auto plans Skeletal Mystics, the large Muttadile and Zebak
  from distance. Only styles the phase can be damaged by are offered, and phases of one fight (Tekton,
  Zebak, Kephri, Akkha, each Warden's P3 and core) share the choice. A phase the style can't damage keeps
  Auto; a style turned off under Attack styles falls back to Auto with a warning, and one with no usable
  setup searches every style with a warning. Saved styles can be reset from a failed raid's error.
- **ToB role**: South freeze, North freeze, Range DPS or Melee DPS replaces the separate Maiden/Nylocas
  selectors. South freezes and kills Maiden's clump and mages Nylo; North freezes and can help or return
  to Maiden, then melees Nylo in 3/4-player teams and mages in 5. Range DPS chins the clump and ranges Nylo;
  Melee DPS focuses Maiden and melees Nylo. Freeze setups require Ice Barrage and ranged clump setups require
  chinchompas. Owned magic weapons without Ancient autocast support can manually cast the required Ice Barrage;
  the setup labels manual casting and uses the spell's five-tick speed. Every role still plans each Vasilias form
  with its own style. The new selector defaults to Melee DPS.
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
