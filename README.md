# Best Gear Setup

A [RuneLite](https://runelite.net/) plugin that finds the best gear setup for any monster or boss, right
inside the client: from the items you own, within a budget, or best in slot. Monsters, equipment, spells,
prayers and potions come from bundled [OSRS Wiki](https://oldschool.runescape.wiki/) snapshots, so the plugin
makes no network requests. The plugin uses OSRS Wiki combat formulas and RuneLite item prices; see
[source attribution](THIRD_PARTY_NOTICES.md).

## Getting started

1. In RuneLite, open **Configuration** (the wrench icon), select **Plugin Hub**, search for *Best Gear Setup*
   and install it.
2. Log in and open your bank once, so the plugin knows which items you own.
3. Open the **Best Gear Setup** panel in the sidebar, or right-click an attackable NPC and choose
   **Best setup**.
4. Search for a monster, pick its version, choose a mode (and budget), then **Find best setup**.

Results show the best melee, ranged and magic setups with their DPS and what you would need to buy.
Right-click an item in a result to lock, exclude or mark it as owned.

## Features

- **Right-click any attackable NPC → "Best setup"** to open the panel and search setups for it.
- **Search** any monster or boss by name in the side panel. Toggle between *Bosses* and *All
  monsters*. Phases and variants of one monster (Abyssal Sire phases, Vorkath post-quest / Dragon
  Slayer II, Verzik modes...) show as a single result; pick the version, and any temporary boss phase, below the search.
- **Owned items**: the plugin remembers your bank (after you open it once), inventory and worn
  equipment, per account. Owned items cost nothing in a setup.
- Decorated equipment also counts as its underlying base item, including Twisted ancestral.
  Removable tradable ornament kits are linked separately and included in acquisition prices.
  Owning only the base or kit does not grant an unowned paid upgrade.
- **Three modes**
  - *Owned items only*: only gear you already have.
  - *Owned + budget*: your gear plus anything you can buy within the budget (e.g. `50m`).
    Untradeables are used only if you own them, unless they are bought through tradable components.
  - *Best in slot*: no limit, including unowned untradeables and every diary tier.
- **Equipment & access** settings independently enable Members, DMM, Beta and
  BH equipment. Members is enabled by default; DMM, Beta and BH are disabled. These filters
  also apply to owned items and slot locks, and changing a setting reruns the search.
- Best setup per combat style (melee, ranged, magic), with DPS, max hit, accuracy, attack speed,
  what to buy and the total cost at live GE prices.
- Untradeable equipment with tradable bases counts toward the budget: crystal armour seeds,
  inactive Bow of faerdhinen / Blade of saeldor, imbued rings and slayer helmets, charged weapons,
  and every Avernic treads upgrade. Blood fury includes the fury and blood shard; the Avernic
  defender includes its hilt. Owning base treads or an Elidinis' ward does not grant paid upgrades.
  RuneLite's component mappings supply current prices.
  Costs cover equipment and consumed upgrade components; crystal shards, corruption fees,
  weapon charges and ongoing upkeep are excluded. Because they can be bought, budget searches may
  buy this gear (including Tumeken's shadow, the Sanguinesti staff, tridents and the
  tormented-synapse weapons). Reward-only gear such as void and fire capes has no GE acquisition
  cost; budget searches use it only when owned, Best in slot always.
- Tradable items (or tradable components) without a current GE price are never treated as free:
  budget searches only use them if you own them, and Best in slot results mark them as unpriced
  rather than adding a guessed cost.
- Each search uses a snapshot of your items. Gaining or losing equipment afterwards (selling,
  dropping, buying) discards the results and searches again; consumables do not.
  Diary rewards require the exact tier to be tracked as owned or manually marked as owned;
  owning a lower tier never unlocks a higher one (Best in slot assumes every tier).
- Uses your real combat levels and item requirements. Levels are remembered per account, so a
  logged-out search uses the levels last seen in game (99s only before the account's first login).
  The best prayer your levels and unlocks allow and potion boosts are assumed by default
  (configurable). Piety/Chivalry (Knight Waves), Rigour, Augury, Deadeye and Mystic Vigour are read
  from your account while logged in and remembered the same way; before the first login they are
  assumed unlocked, like the 99s.
- Potion boosts follow the search mode when the potion choice is *Best*: *Owned items only* uses only
  potions and hearts you own (any dose; a divine potion counts as the ordinary one), *Owned + budget*
  may buy potions (not counted in the budget) but needs an owned imbued/saturated heart, and *Best in
  slot* assumes everything. A potion picked by name is always used.
- Demonbane spells assume Mark of Darkness is active (Fight options), and its icon is shown beside the
  prayer and potions when a setup uses it; with it off, elemental weaknesses such as Yama's water
  weakness can outrank demonbane spells.
- Monster variants match all their NPC IDs, and search notes give the snapshot dates.
- Wear requirements combine the game cache's requirement parameters, levels stated in each item's
  Wiki text, and a reviewed rules table for legacy families; the live client cache can only raise them.
  Quest, diary and creation levels are not wear gates. Black masks require 20 Strength and 40 combat
  as well as 10 Defence; owned items and armour locks cannot bypass wear requirements.

## Side panel

Mode, budget and slayer-task status are controlled in the main side panel. **Fight options**
contains fighting distance, ammo quantity and pre-fight specials (vulnerability, dragon warhammer, elder maul,
arclight, emberlight, Tonalztics, bandos godsword, seercull and Eye of Ayak). It also contains
**Enable AoE**, **Grouped enemies**, **Require fire protection**, **Antifire potion** and **Protect from Magic**.
The lookup controls do not repeat in RuneLite's settings; saved values are preserved.
Raid scaling, path levels and dragonfire protection appear only for matching encounters; the
**Boss phase** picker appears below the monster search, under the version, when the target has one. Attack effects and pre-fight specials have their own collapsed sections.
Snapshot information and calculation assumptions are under **Search details**, below the gear.

**Potions, locks & items**:

- **Potions** per combat style: *Best available* (strongest potion usable anywhere; raid, NMZ and
  Deadman potions and brews excluded), *None*, or any potion from the potion table (overloads,
  smelling salts, ...). A potion picked by name is always used; it is named above the results, with a
  warning when the target is outside its raid or minigame or (in owned / budget modes) you don't own it.
- **Right-click any item in a result** to lock its slot to that item, keep the slot empty, always fill
  it for defence / prayer, exclude the item, or mark it as owned. When an item has separately listed
  variants (e.g. *Dragon hunter crossbow (b)*), the menu offers **Exclude all ... variants** as well as
  **Exclude only** that entry, and a variant of an excluded item says so in its tooltip. Locks and
  exclusions are listed with a remove button; the search re-runs automatically.
- **Marked as owned**: search any item and mark it owned, for things the bank scan can't see (POH
  costume room, items on another account...).

## Plugin settings

In RuneLite's settings for the plugin:
- **Right-click option**: adds **Best setup** to attackable NPCs' right-click menu (on by default).
- **Search options**: stab / slash / crush / ranged / magic / atlatl toggles (the eclipse atlatl gets its own
  tab: ranged accuracy, Strength-based damage, Eclipse Moon burns), one- or two-handed weapons only,
  optimise for DPS / accuracy / max hit / average hit, search depth (fast / normal / best).
- **Experience**: forbid styles that give Attack, Strength or Defence XP (pures), or require a melee
  style that gives a chosen XP type.
- **Fill empty slots**: fill slots that add no damage with prayer or defence items (by defence type,
  or weighted by the target's own attacks), optionally giving up a DPS margin.
- **Equipment & access**: Members, DMM, Beta and BH equipment and allowed spellbooks.
- **Boosts**: prayers, thrall DPS.

## Encounter rules

### Ammunition and fighting distance

External ammunition is included in the ammo slot, including zero-bonus atlatl darts;
blowpipe darts are shown separately as loaded ammo. Consumable ammo uses the configured quantity
when pricing a setup, and so do thrown weapons used up as they are thrown (knives, darts, thrownaxes,
javelins, chinchompas; not blowpipes, Tonalztics of Ralos or the Hunter's spear): stacks you hold in the
bank, inventory and equipment cover part of it and only the shortfall is bought (and counted against the
budget). *Owned items only* uses them only when you hold the whole quantity. Ammunition marked owned by hand
has no known stack size and is assumed to cover any quantity. When a held stack changes in a way that
matters for the requested quantity, the search runs again once the stack has stayed unchanged for five
seconds, so firing doesn't restart it on every attack.

**Distance (0 = Auto)** applies encounter reach restrictions: Zulrah requires at least two tiles,
so melee setups need a halberd. Olm's head also requires halberd reach for melee; its hands are separate targets.
Olm's head takes one-third damage from melee and Magic, the mage hand takes one-third from melee
and Ranged, and the melee hand takes one-third from Magic and Ranged. Each damage roll is divided
and rounded down individually, including scythe hits and fang's bounded roll. Thralls are unaffected.
Head results assume the final phase; regeneration and clenched-hand phases are not simulated.
Kree'arra, his three bodyguards and Dawn have explicit flying rules even when monster tags are
missing: supported melee setups require a halberd or salamander. Aviansies, including reanimated
variants, permit salamanders for melee and reject halberds.
Dusk is a separate target. Salamander DPS is not modelled, so salamanders remain excluded.
Kraken and cave krakens exclude all melee, including halberds. Their ranged damage rolls are
divided by seven and rounded down, preserving a minimum of one for a positive hit; Magic and
thrall damage are unaffected. Sailing krakens and enormous tentacles do not inherit that reduction.
TzKal-Zuk excludes all melee. Auto assumes nine tiles to cover shield corner positions;
an explicit distance models a closer position, with a six-tile lower bound excluding five-tile attacks.
Zebak allows ordinary melee adjacent to its footprint and halberds at two tiles. Set the distance
for your chosen position; Auto keeps adjacent melee available.
Enter a larger tile distance to model a fighting position or safespot. **Use current distance**
measures from your player to the nearest tile of the live NPC selected through Best setup (or
your current combat target matching the lookup). This is a snapshot; movement does not continually
change the search. The effective distance is shown above results and attack range is on the style tooltip.
Longrange's extra two tiles, its attack speed and Defence XP restrictions all apply. Weapon locks
cannot bypass reach. Unknown weapon ranges are excluded when fighting farther than one tile away.
Auto assumes adjacent combat for targets without a known encounter restriction. Distance checks
do not pathfind or check line of sight, obstacles, or changing boss phases.
Weapon reach follows the [OSRS Wiki combat tables](https://oldschool.runescape.wiki/w/Weapons/Types),
including [shortbows](https://oldschool.runescape.wiki/w/Shortbow_(weapon)),
[crossbows](https://oldschool.runescape.wiki/w/Crossbow_(weapon)) and
[Zulrah](https://oldschool.runescape.wiki/w/Zulrah). Encounter rules follow
[Kree'arra](https://oldschool.runescape.wiki/w/Kree%27arra),
[Grotesque Guardians](https://oldschool.runescape.wiki/w/Grotesque_Guardians/Strategies), and
[Inferno strategies](https://oldschool.runescape.wiki/w/Inferno/Strategies).
Kraken's per-hit reduction follows the
[OSRS Wiki DPS calculator](https://github.com/weirdgloop/osrs-dps-calc/blob/main/src/lib/PlayerVsNPCCalc.ts).
Olm's part-specific reductions follow [Great Olm](https://oldschool.runescape.wiki/w/Great_Olm).
Encounter caps modify expected damage as well as displayed max hits, after player bonuses and
before thralls. Multi-hit scythes are capped separately; fang uses its bounded damage rolls.
The active rule is explained above the results and in the max-hit tooltip.

### Damage caps, reductions and immunities

- **Zulrah:** raw hits above 50 reroll uniformly to 45-50; this is not a simple max-hit clamp.
- **Fragment of Seren:** each hit is limited by a random 22-24 cap.
- **Verzik P1:** random 0-10 melee and 0-3 Ranged/Magic caps, for all raid difficulties.
  Dawnbringer's normal attacks bypass these caps; its damaging special is not part of sustained DPS.
  P2/P3 do not inherit the P1 cap.
- **Tekton:** Ranged immunity and one-fifth Magic damage, retaining positive hits at one.
- **Vasa's glowing crystal:** Ranged immunity and one-third Magic damage. This corrects the
  overly broad melee-only data tag; reduced Magic remains a valid option.
- **Ice Demon:** one-third damage except fire spells and demonbane attacks.
- **Tormented Demon:** 20% fire-shield reduction except demonbane and abyssal melee attacks.
  Use **Boss phase** for unshielded or shielded-but-defenceless states. These are snapshots;
  shield cycling and the unshielded speed/damage bonus are not averaged over a whole fight.
- **Corporeal Beast:** half damage except Magic and qualifying stab attacks (fang, polearms,
  and spears other than the blue moon spear). Ruby bolt replacement bypasses Corp's halving.
- **Slagilith:** one-third damage without a pickaxe. Zogres, skogres and Slash Bash take quarter
  damage except half damage from Crumble Undead or full damage from comp ogre bow with brutal arrows.
- **Equipment immunities:** CoX guardians require melee with a pickaxe; leafy monsters require
  leaf-bladed melee, broad ammunition or Magic Dart. Higher-tier vampyres require qualifying weapons;
  tier 2 silver attacks cap at 10, while Efaritay's aid permits ordinary attacks at half damage.
  Combat-class immunity tags are also enforced, including conflicting only/immune tags.

Cap and reduction mechanics follow the [OSRS Wiki DPS calculator's encounter transforms](https://github.com/weirdgloop/osrs-dps-calc/blob/main/src/lib/PlayerVsNPCCalc.ts)
and its [equipment exceptions](https://github.com/weirdgloop/osrs-dps-calc/blob/main/src/lib/BaseCalc.ts).
These rules do not simulate changing protection prayers, movement, invulnerability periods,
damage-over-time caps, or every quest/raid-specific mechanic.

### AoE and grouped enemies

AoE defaults off. When enabled, the optimizer ranks total damage across identical enemies grouped in
multicombat, subject to each attack's limit: Ancient burst/barrage up to nine; grey/red chinchompas up
to eleven and black chinchompas up to twelve; charged venator bow up to three hits. Venator's two
bounces have independently rolled accuracy and floor(two-thirds of the raw max hit) damage, with
encounter caps applied afterward. With two enemies the last bounce returns to the primary target.
Scythes, including holy and sanguine variants, use one/two/three hits at full/half/quarter damage for
1x1/2x2/3x3-or-larger enemies. AoE can use any remaining hits against adjacent targets; it never adds
another three hits to a large primary target. Ordinary weapons and Ancient rush/blitz remain single target.
Hallowfell hits the primary enemy once and cleaves up to two distinct nearby enemies, each with
floor(half the raw maximum hit) damage before encounter caps. Both secondary hits use the same
half-strength maximum; neither a larger enemy nor a larger group grants further hits. The group
assumption requires enemies to fit Hallowfell's cleave arc (front, side and player tiles).
Chinchompa accuracy uses heavy ranged defence and the selected distance's fuse accuracy penalty.
The group assumes identical defences, encounter rules and task/gear bonuses, and valid splash/bounce
positions. It does not model a mixed pack such as Kree'arra and his minions.

Results show total DPS, primary DPS, enemies hit and estimated KPH. Primary TTK uses damage to that
enemy, including a returning venator bounce. KPH is damage-based throughput assuming continuous combat;
overkill, respawn times, movement, eating, banking and phase transitions are excluded. Thrall DPS is
added once to the primary enemy, never multiplied by group size.

### Dragonfire protection

Fire protection defaults required, with super antifire and Protect from Magic assumed active. Regular
chromatic/brutal dragons accept super antifire alone or regular antifire plus a fire shield/Protect
from Magic. Metal dragons and drakes ignore that prayer. Vorkath and KBD require potion plus fire
shield, or super antifire plus Protect from Magic; boss chip damage can remain. Galvek/Elvarg require
a potion and fire shield. Wyverns require an icy breath shield: an ordinary anti-dragon shield and
antifire potions do not work. The ancient wyvern shield also blocks freezing. Baby dragons have no
fire requirement. Protective shields are retained as candidates even when another shield has better
offensive stats; locks, ownership and slot filling cannot bypass the requirement. This is a gear
constraint, not an incoming damage or food/downtime simulation. Disable it to model safespots or
other strategies that avoid those attacks.

### Slayer protection

Slayer monsters with mandatory protective equipment always get it, on or off task, in every mode and in
AoE; a slot lock that leaves it out is reported as unusable. Superior variants follow their base monster.

| Monsters | Required (any one) | Slot |
| --- | --- | --- |
| Dust, choke, smoke, nuclear and thermonuclear smoke devils | Facemask or slayer helmet | Head |
| Banshees, twisted and screaming banshees | Earmuffs or slayer helmet | Head |
| Aberrant, deviant, abhorrent and repugnant spectres | Nose peg or slayer helmet | Head |
| Sourhogs | Reinforced goggles or slayer helmet | Head |
| The sourhog fought during A Porcine of Interest | Reinforced goggles (slayer helmets gain this protection only once the quest is complete) | Head |
| Basilisks, basilisk knights and sentinels, monstrous basilisks, cockatrices, cockathrices | Mirror shield or V's shield | Shield |
| Basilisk younglings | Mirror shield | Shield |
| Harpie bug swarms | Lit bug lantern | Shield |
| Hydras, Alchemical Hydra, drakes, sulphur lizards (Karuulm Slayer Dungeon) | Boots of stone, boots of brimstone or granite boots | Feet |

Shield requirements exclude two-handed weapons. The Karuulm boots are waived once the Elite Kourend & Kebos
Diary is complete; this is read from your account (or inferred from owning Rada's blessing 4) and remembered
per profile, and is assumed incomplete before the first login. Wyrms also live in Wyrmscraig, so their boots
are a note rather than a requirement. Wall beasts need a spiny helmet only to start the fight, and insulated
boots (killerwatts), slayer gloves (fever spiders) and witchwood icons (cave horrors) are recommended rather
than required, so none of these restrict the setup.

### Sources and prayers

AoE and dragonfire mechanics follow [Scythe of vitur](https://oldschool.runescape.wiki/w/Scythe_of_vitur),
[Hallowfell](https://oldschool.runescape.wiki/w/Hallowfell),
[Venator bow](https://oldschool.runescape.wiki/w/Venator_bow),
[chinchompa fuses](https://oldschool.runescape.wiki/w/Chinchompa_(weapon)),
[black chinchompas](https://oldschool.runescape.wiki/w/Black_chinchompa),
[Ancient Magicks](https://oldschool.runescape.wiki/w/Ancient_Magicks) and
[dragonfire protection](https://oldschool.runescape.wiki/w/Dragonfire).
Offensive prayers are checked against RuneLite's actual prayer list, excluding the source-only Zeal
entry. Piety retains its real 20% accuracy / 23% strength boosts.

## How it works

The plugin includes its own DPS calculator (OSRS Wiki formulas) and optimiser. By default it reads
two bundled snapshots: 3,110 monster variants (`tools/wiki_monsters.py`) and about 1,900 pieces of
equipment with the spell, prayer and potion tables (`tools/wiki_equipment.py`). Stat-identical copies
(charges, ornament kits, locked, poisoned, degraded and NMZ versions, recolours) fold into one entry,
and owning any of them counts. League, Grid Master, beta, Last Man Standing and PvP Arena items are
left out; Deadman and Bounty Hunter gear stays behind its setting.
See [the bundled data notes](docs/equipment-requirements.md) for how the snapshots are built.

Weapon styles, ammunition, autocast spells, spell max hits, prayers and potions have no structured
Wiki field, so they are small tables in the generator, each following its Wiki page (Weapons/Types,
Arrows, Bolts, Darts, Autocast, Combat spells, each prayer and potion). The Wiki pages define the rules: for example the skull sceptre (i) autocasts only Crumble Undead from the
standard book, and slayer's staves and the void knight mace only waves and surges.

Modelled effects: void (incl. elite), slayer helmet / black mask (imbued), salve amulet variants,
obsidian armour and berserker necklace, inquisitor's, crystal armour with bow of faerdhinen / crystal
bow, dragon hunter lance / crossbow, arclight / emberlight, keris, twisted bow, scythe of vitur,
osmumten's fang, Tumeken's shadow and the other powered staves, autocast spells (standard, Ancient,
Arceuus), light / standard / heavy ranged defence, and a charged Dizana's quiver (+10 ranged accuracy and
+1 ranged strength only when firing arrows or bolts).

The calculation audit added per-hit rounding, elemental weaknesses, damaging enchanted-bolt procs,
Barrows/Moon set effects, conditional multi-hit weapons, health/buff snapshots, additional weapon
family bonuses, flat armour and boss-phase exceptions. The optimizer retains and seeds the associated
effect-bearing equipment. Historical Leagues rewards are excluded from ordinary searches.

**Fight options** includes your HP, target HP, Wilderness, Forinthry Surge, Charge, Mark of Darkness,
sunfire runes, Kandarin hard diary, Soulreaper stacks and Mining. Zero HP means full supplied maximum;
zero Mining uses your level (99 when logged out). Your HP is a snapshot (Dharok, ruby bolts). Target
HP is where the fight, or a gear switch, starts: attacks that change with the target's remaining HP
(ruby bolts, the Sun keris inside ToA, and Vardorvis's Defence/Strength) are integrated from that HP
to the kill, and the optimizer ranks them by that whole-fight DPS. Gauntlet gear is available only in
its activity; named Warden variants take priority over conflicting source IDs.

**Raid scaling** follows the Wiki calculator's scaling modules. Raid monsters are listed with solo,
unscaled stats, so the plugin scales them for **Raid party size**, **ToA raid level**, **ToA path level** and
**CoX Challenge Mode**. CoX scales levels and HP by party size and the highest combat/HP levels (your
own), with the Olm, guardian (Mining), Tekton, crystal and solo-encounter exceptions. ToB normal/hard
scales HP (below three players counts as three); entry mode uses its own table. ToA scales boss and
path HP, and multiplies the boss's defence roll by (250 + raid level) / 250; Kephri's overlords keep
an unscaled defence roll and puzzle-room NPCs keep their listed stats, as in the reference.

**Only special attacks** (first item in Fight options) scores each weapon by its special attack
instead of its ordinary attack, and weapons without a damaging special are
excluded. While it is on, a note above the results reminds you that DPS, TTK and kills/hour assume
back-to-back specials without an energy limit. About 45 specials use the Wiki calculator's exact formulas: accuracy and max-hit multipliers,
the defence style each spec rolls against, multi-hit structures (dragon/burning claws cascades, dragon
dagger, abyssal dagger, Crimson kisten, halberds on large targets, Saradomin sword's magic hit, dark bow
minimums and 48 cap, magic shortbow, Webweaver, Tonalztics), guaranteed hits (Voidwaker, magic
longbow/comp bow, Seercull, Dawnbringer), fixed-formula bows and staves, the Zaryte crossbow's guaranteed
bolt effect, and spec burns. Specials the reference leaves out follow their Wiki pages: Armadyl crossbow
(double accuracy), abyssal tentacle (an ordinary hit), ursine chainmace (double accuracy), rune claws
(+10% damage, one tick slower), Dinh's bulwark (+20% accuracy), and the Bounty Hunter weapons' damage
ranges. The Eclipse atlatl's special is not scored. DPS treats the spec as repeated at the weapon's speed; choose
**Average hit** or **Max hit** to rank damage per special. Energy cost is shown per result; energy
regeneration, Lightbearer and switching between a spec weapon and a main weapon are not simulated.
The abyssal bludgeon assumes full Prayer, and Ancient godsword's delayed blood sacrifice is excluded.

**Boss phase** selects a temporary state: Hueycoatl pillar buff (head and
tail hits x1.3 after the tail caps), Royal Titans out of melee range (ranged attack roll x6), Abyssal
Sire transition (hits halved), and Mokhaiotl shielded (demonbane only, always accurate) or burrowing
(always accurate), Tormented Demon unshielded/defenceless, and Yama's partner tanking with Melee.
Yama's Magic defence bonus is +60 while his target uses Magic and −30 while they use Melee; by default
you are his target, so magic setups face +60, and *Partner tanks with Melee* applies −30. Other targets ignore the phase. Named Wiki variants, such as Araxxor
enraged or the Maggot King's ranges, are selected with the **Version** picker. Royal Titans elementals use the Magic attack bonus for accuracy with guaranteed
maximum hits.

**Poison, venom and burns** are added as damage over time, following the OSRS Wiki's Poison, Venom
and Burn pages. Venom comes from the toxic blowpipe, trident of the swamp and toxic staff of the dead
(25%, or 100% with a serpentine helm), the noxious halberd (33%/50%) and the serpentine helm itself
(1/6 with unpoisoned melee, 1/2 with poisoned weapons). It starts at 6 and rises by 2 per hit to 20,
every 30 ticks, and never wears off. Poison comes from the abyssal tentacle, swamp lizard, emerald
bolts (e) (55% of hits, severity 25 or 27 with a Zaryte crossbow), smoke spells (1/8; ancient sceptres
+10%) and the **Weapon poison** fight option. The catalogue lists unpoisoned items, so that option
treats poisonable daggers, spears, hastae and metal darts, knives, javelins, arrows and bolts as
(p)/(p+)/(p++): melee poisons 1/4 of successful hits, ranged 1/8 at 14 lower severity. Poison deals
floor((severity + 4) / 5) every 30 ticks and loses 1 severity per hit; a fresh application resets it.
The Eclipse Moon set's burn (20% of successful atlatl hits) deals 1 per stack every 4 ticks for 10
hits, up to five stacks, and is mitigated as ranged damage.

Damage over time is integrated over the kill: the kill time solves direct damage plus expected poison,
venom and burn damage by that time = target HP. The target's own poison timer has a random phase, so
short kills gain little and long boss fights approach the steady rate. Immunities come from a bundled
snapshot of the OSRS Wiki's monster data (poison, venom, freeze and burn resistance). Venom-immune targets that can be poisoned take severity-26
poison instead. The **Ice ancient sceptre**'s +10% ice-spell accuracy applies to freezable targets that
are not frozen: each landed cast freezes the target, then gives five ticks of immunity, and only the
casts that try to re-freeze get the bonus. Bosses without Wiki freeze data are treated as unfreezable.

See [COMBAT_AUDIT.md](COMBAT_AUDIT.md) for calculation adjustments, sources, validation and
remaining gaps. Special-attack rotations (energy over a fight, weapon switches),
overkill, protection-prayer and attackable-window timing remain unsupported. Test coverage does not
certify every mechanic.

## Data and privacy

The plugin makes no network requests: everything it needs is bundled, and the search runs locally.
Item prices come from RuneLite's own price data. Owned-item lists, remembered levels and unlocks are saved
in RuneLite's configuration for each RuneScape profile; like other plugin settings, RuneLite syncs them only
if you are signed in to a RuneLite account.

The bundled `wiki-monsters.json.gz`, `wiki-gamedata.json.gz` and `status-immunities.json` derive from
the [Old School RuneScape Wiki](https://oldschool.runescape.wiki/) contributors under
[CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/); the equipment snapshot also
includes requirement parameters read from the game cache. The curated requirements table
(`equipment-requirements.json`) retains a source link per rule. The generators are in `tools/`; see
[CONTRIBUTING.md](CONTRIBUTING.md#bundled-data) to regenerate the data.
See [data attribution and licensing](THIRD_PARTY_NOTICES.md).

## Support

Report bugs and request features through the
[issue tracker](https://github.com/aknaus1/best-gear-setup/issues). For a wrong result, include the monster,
version, mode, budget and the text under **Search details**, which records the snapshot dates and assumptions
behind the search. Please don't post your account name or full bank contents.

## Development

To run the plugin from source, run the tests, regenerate the bundled data or release a version, see
[CONTRIBUTING.md](CONTRIBUTING.md).

## Documentation

- [COMBAT_AUDIT.md](COMBAT_AUDIT.md): calculation order, adjustments, sources, validation and known gaps.
- [docs/equipment-requirements.md](docs/equipment-requirements.md): how the bundled snapshots and wear
  requirements are built.
- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md): data sources, licences and the reference-code review.
- [CHANGELOG.md](CHANGELOG.md): changes by version.
- Dated test reports from 2 October 2026, kept as history:
  [evaluation](docs/evaluation-2026-10-02.md), [fix verification](docs/fix-verification-2026-10-02.md) and
  [retest](docs/fix-retest-2026-10-02.md). Each tested an earlier working tree; the findings listed as open
  in the first two are resolved in the retest, and those fixes shipped in commit `a0df924`.

## License

The plugin code is licensed under the [BSD 2-Clause licence](LICENSE). The bundled OSRS Wiki data remains
under [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/); see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Best Gear Setup is not affiliated with Jagex or the
OSRS Wiki.
