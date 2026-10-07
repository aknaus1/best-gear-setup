# How Best Gear Setup works

This page covers what the plugin assumes and what it enforces. For installation and a quick tour, see the
[README](../README.md). For the calculation audit, sources and known gaps, see [COMBAT_AUDIT.md](../COMBAT_AUDIT.md).

## Items, ownership and prices

- **Owned items**: the plugin remembers your bank (once you've opened it), inventory and worn equipment,
  per account. Anything you own costs nothing in a setup.
- Decorated equipment also counts as its base item, including Twisted ancestral. Ornament kits that can be
  removed and traded are tracked separately and included in the price of getting the item. Owning only the
  base item or only the kit doesn't give you a paid upgrade for free.
- **Three modes**
  - *Owned items only*: only gear you already have.
  - *Owned + budget*: your gear plus anything you can buy within the budget (e.g. `50m`).
    Untradeable items are only used if you own them, unless they can be bought as tradeable components.
  - *Best in slot*: no limits, including untradeables you don't own and every diary tier.
- The **Equipment & access** settings switch Members, DMM, Beta and BH equipment on or off independently.
  Members is on by default; DMM, Beta and BH are off. These filters also apply to owned items and slot
  locks, and changing one reruns the search.
- Untradeable equipment built from tradeable parts counts toward the budget: crystal armour seeds, the
  inactive Bow of faerdhinen and Blade of saeldor, imbued rings and slayer helmets, charged weapons, and
  every Avernic treads upgrade. The Blood fury includes the fury and the blood shard, and the Avernic
  defender includes its hilt. Owning the base treads or an Elidinis' ward doesn't give you their paid
  upgrades. Current prices come from RuneLite's component mappings.
  Costs cover the equipment and any upgrade components it uses up. Crystal shards, corruption fees,
  weapon charges and ongoing upkeep aren't included. Because this gear can be bought, budget searches may
  buy it (including Tumeken's shadow, the Sanguinesti staff, tridents and the tormented-synapse weapons).
  Reward-only gear such as void and fire capes can't be bought on the GE, so budget searches only use it
  if you own it, while Best in slot always can.
  Culinaromancer's Chest gloves cost their full chest price in coins (barrows and dragon gloves 130,000),
  but like other quest rewards they're only used if you own them.
- Tradeable items (or tradeable components) without a current GE price are never treated as free. Budget
  searches only use them if you own them, and Best in slot marks them as unpriced instead of guessing a
  cost.
- Each search works from a snapshot of your items. If you gain or lose equipment afterwards (selling,
  dropping, buying), the results are thrown away and the search runs again. Consumables don't trigger this.
  Diary rewards need the exact tier to be tracked as owned or marked as owned by hand. Owning a lower tier
  never unlocks a higher one (Best in slot assumes every tier).

## Levels, prayers and potions

- The plugin uses your real combat levels and item requirements. Levels are remembered per account, so a
  logged-out search uses the levels it last saw in game (it only assumes 99s before the account's first
  login). By default it assumes the best prayer your levels and unlocks allow, plus potion boosts (you can
  change this). Piety/Chivalry (Knight Waves), Rigour, Augury, Deadeye and Mystic Vigour are read from your
  account while you're logged in and remembered the same way. Before the first login they're assumed
  unlocked, like the 99s.
- When the potion choice is *Best*, potion boosts follow the search mode. *Owned items only* uses only
  potions and hearts you own (any dose; a divine potion counts as the regular one). *Owned + budget* can buy
  potions (they don't count against the budget) but still needs you to own an imbued or saturated heart.
  *Best in slot* assumes everything. Against Chambers of Xeric and Tombs of Amascut targets, *Best* also
  considers that raid's own supplies (overloads, smelling salts) without needing you to own them, and
  leaves them out of the bank layout. Turn off **Include raid potions** under **Combat boosts** to plan for
  the start of a raid, before you have them. A potion you pick by name is always used.
- **Thralls** are on by default. Each setup adds the strongest thrall your base Magic level can cast:
  lesser (38, +0.208 DPS), superior (57, +0.417) or greater (76, +0.625). Thralls always hit, rolling 0 to
  their max hit of 1, 2 or 3 every 2.4 seconds. They need the Arceuus spellbook allowed under
  **Spellbooks** and A Kingdom Divided, which is read from your account while you're logged in and assumed
  complete before the first login. They aren't added to setups autocasting a non-Arceuus spell or against
  thrall-immune targets. A setup that includes one shows the thrall's icon next to its prayer and potions
  and an **Incl. thrall** line under its DPS.
- Demonbane spells assume Mark of Darkness is active (in the Combat boosts settings), and its icon appears
  next to the prayer and potions when a setup uses it. With it turned off, elemental weaknesses such as
  Yama's weakness to water can outrank demonbane spells.
- **Potions** are set per combat style with the dropdowns under **Combat boosts**: *Best available* (the
  strongest potion you can use anywhere, leaving out raid, NMZ and Deadman potions and brews), *None*, or
  any potion from the potion table (overloads, smelling salts, ...). A potion picked by name is always used.
  It's named above the results, with a warning if the target is outside its raid or minigame or (in owned
  and budget modes) you don't own it.
- Monster variants match all of their NPC IDs, and the search notes give the snapshot dates.
- Wear requirements combine the game cache's requirement parameters, levels stated in each item's Wiki
  text, and a reviewed rules table for older item families. The live client cache can only raise them.
  Quest, diary and creation levels aren't treated as wear requirements. Black masks need 20 Strength and
  40 combat as well as 10 Defence. Neither owned items nor armour locks can get around wear requirements.

## Bank layout and highlights

- **Bank highlights** outline the gear and ammunition in the selected setup, including equivalent item
  variants and darts loaded in a blowpipe. Switching result tabs or picking another setup updates them.
  Empty bank placeholders are skipped. You can change **Highlight bank gear** and **Bank color** under
  **Display & interaction** in the plugin settings.
- The **bank gear layout** shows just the selected setup's equipment and ammunition from your bank,
  arranged like the equipment slots in the sidebar. Any extra variants appear underneath.
  - If the setup casts a spell, its runes appear to the right of the head slot, along with runes for any
    Mark of Darkness, Charge or thrall the DPS assumes (plus the Book of the dead for a thrall).
    Elemental runes that the equipped staff or tome provides are left out, and combination, sunfire and
    aether runes count as substitutes.
  - The setup's potions and hearts sit below the runes. Any dose and the divine version count, and a
    (super) combat potion stands in for the matching attack and strength potions, and the other way round.
  - A special attack weapon switch (with its off-hand and ammunition) goes below the potions.
  - Each cell shows its own item if you have it (the basic rune, the 4-dose potion, the exact gear piece).
    Combination runes, other doses and other variants then go in the extra rows underneath: gear first,
    then runes, then potions, with each group starting a new row. Extra potions are grouped by type, one
    row per potion, highest dose first. These alternatives only fill the main cell when its own item is
    missing.
  - When a cell has nothing left to withdraw, its item's placeholder stays in the cell, so withdrawing
    gear doesn't empty the layout. A real item (such as another variant or dose) still takes the cell
    ahead of a placeholder, and spare placeholders aren't shown in the extra rows.
  - Bank and inventory highlights cover all of these too. Empty placeholders are skipped there, and
    picking another setup updates the layout.
  - Withdraw items as usual; dragging is turned off in this temporary view. Clicking the title-bar button
    again, opening another bank search or tab, or closing the bank takes you back to normal browsing
    without touching your saved bank order. The layout uses a temporary bank tab, so the chatbox stays
    free and no search box opens.
- The **gear button in the bank title bar** only appears while a setup is selected. Click it to switch
  between the gear layout and normal browsing. It closes any open Bank Tags tab, Quest Helper tab or potion
  store first. It looks like Quest Helper's button and sits in the same place beside Close, or just to the
  right of Quest Helper's button when that's showing. Its background shows as selected while the gear
  layout is open.
- **Clear search**, next to **Find best setup**, cancels the current search and clears its target,
  recommendations, bank and inventory highlights, and bank gear layout. Your saved settings stay.
- **Inventory highlights** outline the same setup's gear and ammunition in your inventory, including while
  the bank is open. **Highlight inventory gear** and **Inventory color** under **Display & interaction**
  control them separately from the bank highlights.

## Side panel

The main sidebar keeps the monster search, the relevant variant or phase, the ownership mode and
**Find best setup** above the results. The budget row only appears in budget mode, and the Wiki link sits
next to the target. **Fight** and **Gear (count)** open separate views with a **Back to results** button,
and your results and scroll position are still there when you come back. **Settings** opens the plugin's
page in RuneLite's configuration panel.

**Fight** has your HP, the Slayer task override, AoE, Wilderness, Forinthry Surge and Soulreaper
stacks. Raid settings only appear for raid targets. **Use active raid** reads the party, mode, invocation
and path values when the selected NPC belongs to the raid you're in; otherwise your saved planning values
apply. CoX still uses your account's HP and the Mining level you've chosen.

**In Wilderness** is always on for monsters that only appear in the Wilderness: revenants, mammoths,
lava dragons, elder chaos druids and the Wilderness bosses with their minions. For those targets the box
shows checked and can't be cleared. PvM Arena versions of the bosses aren't included.

Under **Fight → In Wilderness**, turn on **Limit expensive items** to set **Expensive items allowed**
(0–11) and **Expensive at (GP)** (e.g. `500k` or `1m`; the defaults are 3 items and `1m`). The limit counts
every equipped item at or above the threshold, including items you own, using the same full prices
(including components) as the equipment budget. Untradeables that break on an unprotected PvP death also
add the fee Perdu charges to repair them, from the OSRS Wiki's (broken) list: for example a fire cape counts
as 150,000, a dragon defender 240,000 (plus its kit when trimmed), an infernal cape 225,000 and an elite void
top 250,000. Items with unknown prices count as expensive. It applies
in every search mode and to slot locks, set bonuses and fill items, and does nothing outside Wilderness
searches. An invalid threshold counts every equipped item as expensive and shows a warning. This is a limit
on equipment, not a prediction of what you'd lose on death: protected items aren't subtracted, and
inventory supplies, loaded ammunition and charges aren't counted. Equipped ammunition is priced per item.

Results are always for a full kill from your saved assumptions. Right-clicking an NPC with **Best setup**
runs the same search; the live NPC is only used to confirm your Slayer task location and, with **Use active
raid**, to read raid scaling.

**Gear** lists your locks, exclusions and the items you've marked as owned, with the same right-click menus
as in the results and buttons to remove or reset each one. RuneLite's Best Gear Setup configuration
groups the remaining preferences into
**Search options**, **Attack styles**, **Equipment**, **Spellbooks**, **Combat boosts**, **Dragonfire**,
**Experience filters**, **Fill empty slots** and **Display & interaction**. Combat boosts includes the
potion dropdowns for each style, thralls, poison and rune assumptions. Each highlight toggle sits next to
its colour.

**Fight** also has a collapsed **Pre-fight preparation** section for Vulnerability (with the tome of water
option once it's on), landed special attacks and damage from defence-draining attacks. Its heading counts
how many are set.

Completion of the Kandarin hard diary is detected and remembered per RuneScape profile, with no manual
override. With the Slayer
task set to **Auto**, the selected monster is matched against your current or remembered assignment,
including boss tasks and alternative names. Tasks tied to a location also need your current location to be
confirmed, and the Fremennik Slayer Dungeon is supported. Locations the plugin can't map or read stay
unknown and don't give the task bonus automatically. **On** and **Off** are available as planning
overrides. Search details show the assumptions actually used and anything that couldn't be read.
By default, account details are read automatically and raid scaling
follows the raid you're in.

**Locks and owned items**:

- **Right-click any item in a result** to lock its slot to that item, keep the slot empty, always fill it
  for defence or prayer, exclude the item, or mark it as owned. When an item has variants listed
  separately (e.g. *Dragon hunter crossbow (b)*), the menu offers both **Exclude all ... variants** and
  **Exclude only** that one, and a variant of an excluded item says so in its tooltip. Locks and exclusions
  are listed with a remove button, and the search reruns automatically.
- **Lock any item**: right-click any slot in a result and pick **Lock (slot) to another item...**, or use
  **Lock any item** in the Gear view. Search for the item (for example a lightbearer for the ring slot) and
  click it. The slot filter limits the search to one slot. Locking an excluded item restores it. If the
  locked item can't be used (not owned in *Owned items only*, levels too low, members items off...), the
  slot is left empty, its padlock turns red and the reason is shown above the results.
- **Marked as owned**: search for any item and mark it as owned. This is for things the bank scan can't
  see (the POH costume room, items on another account...).

## Plugin settings

These preferences are in RuneLite's configuration for the plugin (**Settings** in the side panel opens it):

- **Search options**: one-handed or two-handed weapons only; optimise for DPS, accuracy, max hit or average
  hit; search depth (fast, normal or best).
- **Attack styles**: toggles for stab, slash, crush, ranged, magic and atlatl (the eclipse atlatl gets its
  own tab: ranged accuracy, Strength-based damage and Eclipse Moon burns).
- **Equipment** and **Spellbooks**: Members, DMM, Beta and BH equipment, and the spellbooks allowed for
  autocasting.
- **Combat boosts**: potions (including whether raid potions count), prayers, thralls and other reusable
  combat assumptions.
- **Dragonfire**: whether fire protection is required, and the antifire potion and Protect from Magic
  assumed.
- **Experience filters**: rule out styles that give Attack, Strength or Defence XP (for pures). Turn off the
  other two to train one melee skill only; controlled gives all three, so it needs all three on.
- **Fill empty slots**: fill slots that don't add damage with prayer or defence items (by defence type, or
  weighted by the target's own attacks), optionally giving up a little DPS to do so.
- **Display & interaction**: the **Right-click option** that adds **Best setup** to the menu of attackable
  NPCs (on by default), and the bank and inventory highlights with their colours.

## Encounter rules

### Ammunition and fighting distance

External ammunition goes in the ammo slot, including atlatl darts that have no bonuses. Blowpipe darts are
shown separately as loaded ammo. Consumable ammo is priced using the quantity you've set, and so are thrown
weapons that get used up (knives, darts, thrownaxes, javelins and chinchompas; not blowpipes, Tonalztics of
Ralos or the Hunter's spear). Whatever you hold in your bank, inventory and equipment covers part of that
quantity, and only the shortfall is bought (and counted against the budget). *Owned items only* only uses
them if you hold the full quantity. Ammunition you've marked as owned by hand has no known stack size, so
it's assumed to cover any quantity. When a stack you hold changes in a way that matters for the requested
quantity, the search runs again once the stack has stayed the same for five seconds, so it doesn't restart
on every shot.

Fighting distance comes from each encounter's reach rules:

- Zulrah needs at least two tiles, so melee setups need a halberd.
- Olm's head also needs halberd reach for melee; his hands are separate targets. The head takes a third of
  the damage from melee and Magic, the mage hand a third from melee and Ranged, and the melee hand a third
  from Magic and Ranged. Each damage roll is divided and rounded down on its own, including scythe hits and
  the fang's bounded roll. Thralls aren't affected. Head results assume the final phase; regeneration and
  clenched-hand phases aren't simulated.
- Kree'arra, his three bodyguards and Dawn have their own flying rules even when the monster tags are
  missing: melee setups need a halberd or salamander. Aviansies, including reanimated ones, allow
  salamanders for melee but not halberds. Dusk is a separate target. Salamander DPS isn't modelled, so
  salamanders are still left out.
- Kraken and cave krakens rule out all melee, halberds included. Their ranged damage rolls are divided by
  seven and rounded down, but a hit that does any damage still does at least one. Magic and thrall damage
  aren't affected. Sailing krakens and enormous tentacles don't get that reduction.
- TzKal-Zuk rules out all melee and assumes nine tiles to cover the shield's corner positions, which rules
  out attacks that only reach five.
- Zebak allows normal melee next to its footprint and halberds at two tiles; adjacent melee stays available.

Other targets assume you're standing next to them, so melee is always offered where the encounter allows
it. If you'd rather fight from range, use the ranged or magic results. The distance in use is shown above
the results, and each attack style's range is in its tooltip. Longrange's two extra tiles, its attack speed
and its Defence XP restrictions all apply. Weapon locks can't get around reach. Weapons with an unknown
range are left out when an encounter needs more than one tile. Reach checks don't pathfind or account for
line of sight, obstacles or changing boss phases.

Weapon reach follows the [OSRS Wiki combat tables](https://oldschool.runescape.wiki/w/Weapons/Types),
including [shortbows](https://oldschool.runescape.wiki/w/Shortbow_(weapon)),
[crossbows](https://oldschool.runescape.wiki/w/Crossbow_(weapon)) and
[Zulrah](https://oldschool.runescape.wiki/w/Zulrah). Encounter rules follow
[Kree'arra](https://oldschool.runescape.wiki/w/Kree%27arra),
[Grotesque Guardians](https://oldschool.runescape.wiki/w/Grotesque_Guardians/Strategies) and
[Inferno strategies](https://oldschool.runescape.wiki/w/Inferno/Strategies).
The Kraken's per-hit reduction follows the
[OSRS Wiki DPS calculator](https://github.com/weirdgloop/osrs-dps-calc/blob/main/src/lib/PlayerVsNPCCalc.ts),
and Olm's per-part reductions follow [Great Olm](https://oldschool.runescape.wiki/w/Great_Olm).
Encounter caps change expected damage as well as the max hit shown, after player bonuses and before
thralls. Multi-hit scythes are capped hit by hit, and the fang uses its bounded damage rolls. The rule in
effect is explained above the results and in the max-hit tooltip.

### Damage caps, reductions and immunities

- **Zulrah:** raw hits above 50 are rerolled evenly between 45 and 50, which isn't the same as simply
  capping the max hit.
- **Fragment of Seren:** each hit is capped at a random 22–24.
- **Verzik P1:** random caps of 0–10 for melee and 0–3 for Ranged and Magic, at every raid difficulty.
  The Dawnbringer's normal attacks get past these caps; its damaging special isn't part of sustained DPS.
  P2 and P3 don't have the P1 cap.
- **Tekton:** immune to Ranged and takes a fifth of Magic damage, with any hit that does damage still doing
  at least one.
- **Vasa's glowing crystal:** immune to Ranged and takes a third of Magic damage. This overrides the data's
  overly broad melee-only tag, so reduced-damage Magic is still an option.
- **Ice Demon:** takes a third of the damage, except from fire spells and demonbane attacks.
- **Tormented Demon:** its fire shield cuts damage by 20%, except from demonbane and abyssal melee attacks.
  Use **Boss phase** for the unshielded state or the shielded-but-defenceless state. These are snapshots:
  shield cycling and the unshielded speed and damage bonus aren't averaged over a whole fight.
- **Corporeal Beast:** takes half damage except from Magic and qualifying stab attacks (the fang,
  polearms, and spears other than the blue moon spear). Ruby bolt replacement hits bypass Corp's halving.
- **Slagilith:** takes a third of the damage unless you use a pickaxe. Zogres, skogres and Slash Bash take
  a quarter of the damage, except half from Crumble Undead and full damage from a comp ogre bow with brutal
  arrows.
- **Equipment immunities:** CoX guardians need melee with a pickaxe. Leafy monsters need leaf-bladed melee,
  broad ammunition or Magic Dart. Higher-tier vampyres need qualifying weapons; silver attacks on tier 2
  vampyres cap at 10, while Efaritay's aid allows normal attacks at half damage. Combat-style immunity tags
  are enforced too, including tags that contradict each other.

Caps and reductions follow the
[OSRS Wiki DPS calculator's encounter transforms](https://github.com/weirdgloop/osrs-dps-calc/blob/main/src/lib/PlayerVsNPCCalc.ts)
and its [equipment exceptions](https://github.com/weirdgloop/osrs-dps-calc/blob/main/src/lib/BaseCalc.ts).
These rules don't simulate changing protection prayers, movement, invulnerable periods, caps on damage over
time, or every quest- or raid-specific mechanic.

### AoE and grouped enemies

AoE is off by default. When it's on, the optimizer ranks setups by total damage across identical enemies
grouped together in multicombat, up to each attack's limit:

- Ancient burst and barrage spells hit up to nine; grey and red chinchompas up to eleven, and black
  chinchompas up to twelve; a charged venator bow hits up to three.
- The venator bow's two bounces roll accuracy separately and deal floor(two-thirds of the raw max hit),
  with encounter caps applied afterwards. With two enemies, the last bounce comes back to the main target.
- Scythes, including the holy and sanguine versions, hit one, two or three times, at full, half and
  quarter damage, against 1x1, 2x2 and 3x3-or-larger enemies. With AoE, any leftover hits can land on
  adjacent targets, but a large main target never gets another three hits on top.
- The Hallowfell hits the main enemy once and cleaves up to two other nearby enemies, each for
  floor(half the raw max hit) before encounter caps. Both extra hits use the same halved max, and neither a
  bigger enemy nor a bigger group adds more. The group has to fit in the Hallowfell's cleave arc (the
  tiles in front, to the side and the player's own tile).
- Normal weapons and Ancient rush and blitz spells only hit one target.

Chinchompa accuracy uses heavy ranged defence and the accuracy penalty of the fuse for the selected
distance. The group is assumed to share defences, encounter rules and task or gear bonuses, and to be
positioned so splashes and bounces work. A mixed group, such as Kree'arra and his minions, isn't modelled.

Results show total DPS, DPS on the main target, enemies hit and estimated kills per hour. Time to kill for
the main target uses the damage dealt to that enemy, including a returning venator bounce. Kills per hour
is based on damage output with no breaks in combat, so it ignores overkill, respawn times, movement,
eating, banking and phase changes. Thrall DPS is added once, to the main enemy, and is never multiplied by
the group size.

### Dragonfire protection

Fire protection is required by default, assuming super antifire and Protect from Magic are active. All
three are set in the **Dragonfire** settings.

- Regular chromatic and brutal dragons: super antifire on its own, or regular antifire plus a fire shield
  or Protect from Magic. Metal dragons and drakes ignore the prayer.
- Vorkath and the KBD: a potion plus a fire shield, or super antifire plus Protect from Magic. Some chip
  damage from the boss can still get through.
- Galvek and Elvarg: a potion and a fire shield.
- Wyverns: an icy breath shield. A normal anti-dragon shield and antifire potions don't work. The ancient
  wyvern shield also blocks freezing.
- Baby dragons have no fire requirement.

Protective shields stay in the running even when another shield has better offensive stats, and locks,
ownership and slot filling can't get around the requirement. This is a gear requirement, not a simulation
of incoming damage, food or downtime. Turn it off to model safespots or other strategies that avoid those
attacks.

### Slayer protection

Slayer monsters that need protective equipment always get it, on or off task, in every mode and with AoE.
If a slot lock leaves it out, the setup is reported as unusable. Superior variants follow their base
monster.

| Monsters | Required (any one) | Slot |
| --- | --- | --- |
| Dust, choke, smoke, nuclear and thermonuclear smoke devils | Facemask or slayer helmet | Head |
| Banshees, twisted and screaming banshees | Earmuffs or slayer helmet | Head |
| Aberrant, deviant, abhorrent and repugnant spectres | Nose peg or slayer helmet | Head |
| Sourhogs | Reinforced goggles or slayer helmet | Head |
| The sourhog fought during A Porcine of Interest | Reinforced goggles (slayer helmets only get this protection once the quest is complete) | Head |
| Basilisks, basilisk knights and sentinels, monstrous basilisks, cockatrices, cockathrices | Mirror shield or V's shield | Shield |
| Basilisk younglings | Mirror shield | Shield |
| Harpie bug swarms | Lit bug lantern | Shield |
| Hydras, Alchemical Hydra, drakes, sulphur lizards (Karuulm Slayer Dungeon) | Boots of stone, boots of brimstone or granite boots | Feet |

Shield requirements rule out two-handed weapons. You don't need the Karuulm boots once the Elite Kourend &
Kebos Diary is done. The plugin reads this from your account (or works it out from you owning Rada's
blessing 4), remembers it per profile, and assumes it isn't done before the first login. Wyrms also live
in Wyrmscraig, so their boots are a note rather than a requirement. Wall beasts only need a spiny helmet to
start the fight, and insulated boots (killerwatts), slayer gloves (fever spiders) and witchwood icons (cave
horrors) are recommended rather than required, so none of these limit the setup.

### Sources and prayers

The AoE and dragonfire rules follow [Scythe of vitur](https://oldschool.runescape.wiki/w/Scythe_of_vitur),
[Hallowfell](https://oldschool.runescape.wiki/w/Hallowfell),
[Venator bow](https://oldschool.runescape.wiki/w/Venator_bow),
[chinchompa fuses](https://oldschool.runescape.wiki/w/Chinchompa_(weapon)),
[black chinchompas](https://oldschool.runescape.wiki/w/Black_chinchompa),
[Ancient Magicks](https://oldschool.runescape.wiki/w/Ancient_Magicks) and
[dragonfire protection](https://oldschool.runescape.wiki/w/Dragonfire).
Offensive prayers are checked against RuneLite's actual prayer list, leaving out the Zeal entry that only
exists in the source. Piety keeps its real 20% accuracy and 23% strength boosts.

## Calculation

### Bundled data

The plugin has its own DPS calculator (using OSRS Wiki formulas) and optimiser. By default it reads two
bundled snapshots: 3,110 monster variants (`tools/wiki_monsters.py`) and about 1,900 pieces of equipment
along with the spell, prayer and potion tables (`tools/wiki_equipment.py`). Copies with identical stats
(charges, ornament kits, locked, poisoned, degraded and NMZ versions, recolours) are merged into one entry,
and owning any of them counts. League, Grid Master, beta, Last Man Standing and PvP Arena items are left
out, and Deadman and Bounty Hunter gear is hidden behind its setting.
See [the bundled data notes](equipment-requirements.md) for how the snapshots are built.

Weapon styles, ammunition, autocast spells, spell max hits, prayers and potions don't have structured Wiki
fields, so they're kept as small tables in the generator, each based on its Wiki page (Weapons/Types,
Arrows, Bolts, Darts, Autocast, Combat spells, and each prayer and potion). The Wiki pages set the rules.
For example, the skull sceptre (i) can only autocast Crumble Undead from the standard spellbook, and
slayer's staves and the void knight mace can only autocast waves and surges.

Modelled effects: void (including elite), slayer helmet and black mask (including imbued), salve amulet
variants, obsidian armour and the berserker necklace, inquisitor's, crystal armour with the bow of
faerdhinen or crystal bow, the dragon hunter lance and crossbow, arclight and emberlight, the keris, the
twisted bow, the scythe of vitur, osmumten's fang, Tumeken's shadow and the other powered staves, autocast
spells (standard, Ancient and Arceuus), light, standard and heavy ranged defence, and a charged Dizana's
quiver (+10 ranged accuracy and +1 ranged strength, but only when firing arrows or bolts).

The calculator also handles per-hit rounding, elemental weaknesses, damaging enchanted bolt procs, Barrows
and Moon set effects, conditional multi-hit weapons, health and buff snapshots, more weapon family
bonuses, flat armour and boss phase exceptions. The optimizer keeps the equipment those effects depend on
and seeds it into the search. Rewards from past Leagues are left out of normal searches.

### Fight inputs and raid scaling

**Fight** includes your HP, Wilderness, Forinthry Surge, Soulreaper stacks, Mining and pre-fight
preparation. Charge, Mark of Darkness and sunfire runes are in the Combat boosts settings. An HP of zero
means the full maximum, and a Mining level of zero uses your own level (99 when logged out). Your HP is a
snapshot (for Dharok's and ruby bolts). Attacks that change with the target's remaining HP (ruby bolts, the
Sun keris inside ToA, and Vardorvis's Defence and Strength) are integrated over a full kill, and the
optimizer ranks them by that whole-fight DPS. Gauntlet gear is only available inside the Gauntlet, and named Warden
variants take priority over conflicting source IDs.

**Raid scaling** follows the Wiki calculator's scaling modules. Raid monsters are listed with solo,
unscaled stats, so the plugin scales them for **Raid party size**, **ToA raid level**, **ToA path level**
and **CoX Challenge Mode**.

- **CoX** scales levels and HP by party size and the highest combat and HP levels in the party (taken as
  your own), with exceptions for Olm, the guardians (Mining), Tekton, the crystals and solo encounters.
- **ToB** normal and hard modes scale HP (fewer than three players counts as three); entry mode has its
  own table.
- **ToA** scales boss and path HP, and multiplies the boss's defence roll by (250 + raid level) / 250.
  Kephri's overlords keep an unscaled defence roll and puzzle-room NPCs keep their listed stats, as in the
  reference.

### Special attacks

**Use special attacks** (in Search options, on by default) works specials into every normal search.

Once a setup's equipment is chosen, the plugin tries that weapon's own special and every other usable
weapon of the same combat style with a damaging special, keeping the same armour (so a two-handed spec
weapon can't drop a required dragonfire shield). If a spec weapon that uses the ammo slot doesn't suit the
worn ammunition (say, a Zaryte crossbow with enchanted bolts after a bow), it brings its own: the
strongest usable ammunition, priced for the ammo quantity like the setup's own. When a one-handed spec
weapon follows a two-handed main weapon, the best off-hand (an avernic defender, for example) is switched
in with it.

A damage special is used whenever there's enough energy, as long as it beats normal attacks over the same
attack interval. A draining special (dragon warhammer, Statius's warhammer, elder maul, Bandos godsword,
arclight, emberlight, Tonalztics, seercull, Eye of Ayak) opens the kill instead. Each spec hits or misses
against the target's current stats, and the rest of the kill is fought at the drained stats, so both a
missed and a landed warhammer are taken into account. That makes the figure an average: kills where the
drain misses are slower, sometimes slower than not speccing at all.

**Drain specs** sets how drains are ranked:

- **Expected** (default): the average kill.
- **Ignore drain**: drains are ignored and the spec only counts for its damage.
- **Worst case**: every opening drain spec misses and just costs its
  attack. A drain spec is then only picked if it's worth it anyway, so a damage spec such as dragon claws
  usually wins instead.

DPS, time to kill and kills per hour include the specs, and setups are ranked with them, so a strong spec
switch can overtake a slightly stronger main weapon. **Spec energy** can be **Regenerating**
(the default: the steady state over a trip, 10% every 30 seconds, with the energy regenerated during one
kill opening the next) or **Full bar** (each kill opens with every whole spec in a full bar,
which suits single boss kills). The Lightbearer replaces your ring when doubled regeneration is worth more
than the ring it pushes out. In budget mode, the spec weapon, off-hand and ammunition are bought from
whatever budget is left.

The result shows the spec, its energy cost, the expected specs per kill, one spec's max hit and accuracy,
and, for draining specs, the DPS after one landed spec. The spec items are added to the cost and to the
bank and inventory highlights. Pre-fight preparation drains still apply first, so clear them if they
describe the same specs. Switch timing and overkill aren't modelled, and Tonalztics drains once per landed
special.

About 45 specials use the Wiki calculator's exact formulas. That covers accuracy and max hit multipliers,
the defence style each spec rolls against, multi-hit patterns (the dragon and burning claws cascades, the
dragon dagger, abyssal dagger, Crimson kisten, halberds on large targets, the Saradomin sword's magic hit,
the dark bow's minimums and 48 cap, the magic shortbow, Webweaver and Tonalztics), guaranteed hits
(Voidwaker, magic longbow and comp bow, Seercull, Dawnbringer), fixed-formula bows and staves, the Zaryte
crossbow's guaranteed bolt effect, and spec burns.

Specials the reference leaves out follow their Wiki pages: the Armadyl crossbow (double accuracy), the
abyssal tentacle (a normal hit), the ursine chainmace (double accuracy), rune claws (+10% damage, one tick
slower), Dinh's bulwark (+20% accuracy), and the damage ranges of the Bounty Hunter weapons. The Eclipse
atlatl's special isn't scored.

DPS treats the spec as repeated at the weapon's speed; choose **Average hit** or **Max hit** to rank by
damage per special instead. The energy cost is shown on each result. Energy regeneration, the Lightbearer
and switching between a spec weapon and a main weapon aren't simulated in this mode. The abyssal bludgeon
assumes full Prayer, and the Ancient godsword's delayed blood sacrifice isn't included.

### Boss phases

**Boss phase** picks a temporary state:

- Hueycoatl with the pillar buff: head and tail hits ×1.3 after the tail's caps.
- Royal Titans out of melee range: ranged attack roll ×6.
- Abyssal Sire mid-transition: hits halved.
- Mokhaiotl shielded (only demonbane works, and it always hits) or burrowing (always hits).
- Tormented Demon unshielded or defenceless.
- Yama with your partner tanking with Melee. Yama's Magic defence bonus is +60 while his target uses Magic
  and −30 while they use Melee. By default you're his target, so magic setups face +60; *Partner tanks
  with Melee* applies −30 instead.

Other targets ignore the phase. Named Wiki variants, such as Araxxor enraged or the Maggot King's ranges,
are picked with the **Version** picker instead. Royal Titans elementals use your Magic attack bonus for
accuracy, and every hit that lands is a max hit.

### Poison, venom and burns

**Poison, venom and burns** are added as damage over time, following the OSRS Wiki's Poison, Venom and
Burn pages.

- **Venom** comes from the toxic blowpipe, trident of the swamp and toxic staff of the dead (25%, or 100%
  with a serpentine helm), the noxious halberd (33%/50%) and the serpentine helm itself (1/6 with
  unpoisoned melee, 1/2 with poisoned weapons). It starts at 6 and goes up by 2 per hit to a maximum of
  20, hitting every 30 ticks, and never wears off.
- **Poison** comes from the abyssal tentacle, the swamp lizard, emerald bolts (e) (55% of hits, severity
  25, or 27 with a Zaryte crossbow), smoke spells (1/8; ancient sceptres add 10%) and the **Weapon poison**
  fight option. The item list only has unpoisoned versions, so that option treats poisonable daggers,
  spears, hastae and metal darts, knives, javelins, arrows and bolts as (p), (p+) or (p++). Melee poisons
  on 1/4 of successful hits, and ranged on 1/8 at 14 lower severity. Poison deals
  floor((severity + 4) / 5) every 30 ticks and drops 1 severity per hit; a new application resets it.
- **Burns** from the Eclipse Moon set (20% of successful atlatl hits) deal 1 damage per stack every 4 ticks
  for 10 hits, with up to five stacks, and are reduced like ranged damage.

Damage over time is integrated across the kill: the kill time is the point where direct damage plus
expected poison, venom and burn damage adds up to the target's HP. Because the target's poison timer
starts at a random point, short kills gain little from it and long boss fights get close to the steady
rate. Immunities come from a bundled snapshot of the OSRS Wiki's monster data (poison, venom, freeze and
burn resistance). Targets that are immune to venom but can be poisoned take severity-26 poison instead.

The **Ice ancient sceptre**'s +10% accuracy on ice spells applies to targets that can be frozen and aren't
frozen yet. Each cast that lands freezes the target and is followed by five ticks of immunity, so only the
casts trying to freeze it again get the bonus. Bosses without Wiki freeze data are treated as unfreezable.

### Known gaps

See [COMBAT_AUDIT.md](../COMBAT_AUDIT.md) for calculation adjustments, sources, validation and remaining
gaps. Weapon-switch timing, overkill, protection-prayer timing and attackable windows still aren't
supported, and the tests don't cover every mechanic.
