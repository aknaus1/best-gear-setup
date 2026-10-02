# Best Gear Setup

A RuneLite plugin that finds the best gear setup for any monster or boss, right inside the
client. Monsters, equipment, spells, prayers and potions come from bundled
[OSRS Wiki](https://oldschool.runescape.wiki/) snapshots, so the plugin makes no network requests.
The plugin uses OSRS Wiki combat formulas and RuneLite item prices; see
[source attribution](THIRD_PARTY_NOTICES.md).

## Launch locally

Requires JDK 11.

1. Open this folder in IntelliJ IDEA (or another IDE with Gradle support) as a Gradle project.
2. Run the `main` method of `src/test/java/com/bestgearsetup/BestGearSetupPluginTest.java`, adding `-ea`
   to the VM options. This starts a RuneLite client with the plugin loaded.

`./gradlew build` compiles the plugin and runs the tests.

## Features

- **Right-click any attackable NPC → "Best setup"** to open the panel and search setups for it.
- **Search** any monster or boss by name in the side panel.
- **Owned items**: the plugin remembers your bank (after you open it once), inventory and worn
  equipment, per account. Owned items cost nothing in a setup.
- Decorated equipment also counts as its underlying base item, including Twisted ancestral.
  Removable tradable ornament kits are linked separately and included in acquisition prices.
  Owning only the base or kit does not grant an unowned paid upgrade.
- **Three modes**
  - *Owned items only*: only gear you already have.
  - *Owned + budget*: your gear plus anything you can buy within the budget (e.g. `50m`).
  - *Best in slot*: no limit.
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
  weapon charges and ongoing upkeep are excluded. Reward-only gear such as void and fire capes
  has no GE acquisition cost. Unowned untradeables still require the setting to be enabled.
- Tradable items (or tradable components) without a current GE price are never treated as free:
  budget searches only use them if you own them, and Best in slot results mark them as unpriced
  rather than adding a guessed cost.
- Each search uses a snapshot of your items. Gaining or losing equipment afterwards (selling,
  dropping, buying) discards the results and searches again; consumables do not.
  Diary rewards require the exact tier to be tracked as owned or manually marked as owned;
  owning a lower tier never unlocks a higher one, even with unowned untradeables enabled.
- Uses your real combat levels and item requirements. The best prayer your levels allow and
  potion boosts are assumed by default (configurable).
- Monster variants match all their NPC IDs, and search notes give the snapshot dates.
- Wear requirements combine the game cache's requirement parameters, levels stated in each item's
  Wiki text, and a reviewed rules table for legacy families; the live client cache can only raise them.
  Quest, diary and creation levels are not wear gates. Black masks require 20 Strength and 40 combat
  as well as 10 Defence; owned items and armour locks cannot bypass wear requirements.

## Advanced options

Mode, budget and slayer-task status are controlled in the main side panel. **Fight options**
contains fighting distance, ammo quantity and pre-fight specials (vulnerability, dragon warhammer, elder maul,
arclight, emberlight, Tonalztics, bandos godsword, seercull and Eye of Ayak).
The lookup controls do not repeat in RuneLite's settings; saved values are preserved.
Raid scaling, path levels, dragonfire protection and boss phases appear only for matching
encounters. Attack effects and pre-fight specials have their own collapsed sections.
Snapshot information and calculation assumptions are under **Search details**, below the gear.
External ammunition is included in the ammo slot, including zero-bonus atlatl darts;
blowpipe darts are shown separately as loaded ammo. Consumable ammo uses the configured quantity
when pricing a setup.

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

In the side panel (**Potions, locks & items**):

**Fight options** also contains **Enable AoE**, **Grouped enemies**, **Require fire protection**,
**Antifire potion** and **Protect from Magic**. These lookup controls are hidden in the configuration page.

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

Mechanics follow [Scythe of vitur](https://oldschool.runescape.wiki/w/Scythe_of_vitur),
[Hallowfell](https://oldschool.runescape.wiki/w/Hallowfell),
[Venator bow](https://oldschool.runescape.wiki/w/Venator_bow),
[chinchompa fuses](https://oldschool.runescape.wiki/w/Chinchompa_(weapon)),
[black chinchompas](https://oldschool.runescape.wiki/w/Black_chinchompa),
[Ancient Magicks](https://oldschool.runescape.wiki/w/Ancient_Magicks) and
[dragonfire protection](https://oldschool.runescape.wiki/w/Dragonfire).
Offensive prayers are checked against RuneLite's actual prayer list, excluding the source-only Zeal
entry. Piety retains its real 20% accuracy / 23% strength boosts.

- **Potions** per combat style: *Best available* (strongest potion usable anywhere; raid, NMZ and
  Deadman potions and brews excluded), *None*, or any potion from the potion table (overloads,
  smelling salts, ...).
- **Right-click any item in a result** to lock its slot to that item, keep the slot empty, always fill
  it for defence / prayer, exclude the item, or mark it as owned. Locks and exclusions are listed with
  a remove button; the search re-runs automatically.
- **Marked as owned**: search any item and mark it owned, for things the bank scan can't see (POH
  costume room, items on another account...).

In RuneLite's settings for the plugin:
- **Search options**: stab / slash / crush / ranged / magic / atlatl toggles (the eclipse atlatl gets its own
  tab: ranged accuracy, Strength-based damage, Eclipse Moon burns), one- or two-handed weapons only,
  optimise for DPS / accuracy / max hit / average hit, search depth (fast / normal / best).
- **Experience**: forbid styles that give Attack, Strength or Defence XP (pures), or require a melee
  style that gives a chosen XP type.
- **Fill empty slots**: fill slots that add no damage with prayer or defence items (by defence type,
  or weighted by the target's own attacks), optionally giving up a DPS margin.
- **Equipment & access**: Members, DMM, Beta and BH equipment, unowned untradeables and allowed
  spellbooks.
- **Boosts**: prayers, unlocked prayers, thrall DPS.

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
excluded. About 45 specials use the Wiki calculator's exact formulas: accuracy and max-hit multipliers,
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
(always accurate), Tormented Demon unshielded/defenceless, and Yama's tank using Magic (+60 Magic
defence, otherwise −30). Other targets ignore the phase. Named Wiki variants, such as Araxxor
enraged or the Maggot King's ranges, are selected in the lookup. Royal Titans elementals use the Magic attack bonus for accuracy with guaranteed
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
Your owned-item lists stay in RuneLite's profile configuration.

The bundled `wiki-monsters.json.gz`, `wiki-gamedata.json.gz` and `status-immunities.json` derive from
the [Old School RuneScape Wiki](https://oldschool.runescape.wiki/) contributors under
[CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/); the equipment snapshot also
includes requirement parameters read from the game cache. Regenerate monsters with
`python tools/wiki_monsters.py`, equipment and support tables with `python tools/wiki_equipment.py`,
and status immunities with `python tools/wiki_status_immunities.py`. The curated requirements table retains a
source link per rule and is generated with `python tools/equipment_requirements.py`.
Downloads (`--refresh`) require `BGS_WIKI_CONTACT` (an email address or repository URL for the
User-Agent) and follow the MediaWiki API etiquette via `tools/wiki_api.py`: serial requests, `maxlag`,
server-directed retries, and API errors that fail the run instead of overwriting a cache or snapshot.
Its offline tests run with `python -m unittest discover -s tools -p "test_*.py"`.
See [data attribution and licensing](THIRD_PARTY_NOTICES.md).
