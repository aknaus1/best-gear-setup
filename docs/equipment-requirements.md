# Bundled game data

Monsters, equipment, spells, prayers and potions all come from OSRS Wiki snapshots bundled with the
plugin, so it makes no network requests. Sources and licences are listed in
[the source notices](../THIRD_PARTY_NOTICES.md).

## What's bundled

| Resource | Generator | Contents |
| --- | --- | --- |
| `wiki-monsters.json.gz` | `tools/wiki_monsters.py` | 3,130 monster variants from `infobox_monster` and `Category:Bosses` |
| `wiki-gamedata.json.gz` | `tools/wiki_equipment.py` | 638 weapons and 1,308 other slot entries (darts appear both as weapons and as blowpipe ammunition), 48 combat spells, offensive prayers and potions |
| `status-immunities.json` | `tools/wiki_status_immunities.py` | Poison, venom, freeze and burn resistance by NPC id |
| `equipment-requirements.json` | `tools/equipment_requirements.py` | 123 reviewed wear rules, each linked to its source |

The equipment snapshot joins `infobox_item` with `infobox_bonuses` (stats, slot, category, speed, members,
tradeable). A row is dropped if the item has been removed from the game, is a Last Man Standing or beta
copy, is in a broken, inactive, unlit or empty state, or sits in one of the Wiki categories for Leagues,
Grid Master, beta items, shelved content, Emir's Arena (PvP Arena loadouts) or discontinued content. A
Leagues Reward Shop cosmetic such as the soulreaper axe (o) is kept when RuneLite's item variations group it
with a kept main-game item, since the Wiki files it under the league although it is used on main-game worlds.
Cosmetics with no stats are dropped too, except for items an encounter or effect rule needs (the ring of
recoil, Efaritay's aid, slayer protection items and so on). Deadman gear gets a `(dmm)` marker from its
Wiki category, so the Deadman setting still controls it.

Copies with identical stats are merged into one entry: charges, ornament kits (`(or)`, `(g)`, `(t)`,
`(h1)`), locked, poisoned and Barrows-degraded versions, NMZ, Soul Wars and Emir's imbues, recolours within
a RuneLite variation group, and the "holy", "sanguine", "radiant" and "twisted" ornament prefixes. Items
with the same stats but their own effects (the blood ancient sceptre, eldritch nightmare staff, blessed
quiver) stay separate. Every entry lists the ids merged into it, and owning any of them counts as owning
the item.

Facts that don't have a structured Wiki field are kept as tables in the generator, each based on its Wiki
page: weapon styles per category (Weapons/Types), ammunition tiers (Arrows, Bolts, Darts, javelins, tars),
autocast lists (Autocast), spell max hits (Combat spells; the level and spellbook come from
`infobox_spell`), prayer boosts and potion formulas.

## Wear requirements

Each item's levels come from three sources, keeping the highest level for each skill:

1. **Game cache parameters** 434/436, 435/437, 191/613, 579/614, 610/615, 611/616 and 612/617, read
   offline by `tools/spike/ReqDump.java` from a copy of the local cache.
2. **The item's Wiki text**: the first sentence that states a requirement to wield, wear or equip it.
   Sentences about quests, history, polls, creating or enchanting the item, or saying it has "no
   requirements", are ignored. For example, this gives the twisted bow Ranged 85, the master wand Magic 60
   (its cache parameter says 55), and ranger gloves Ranged 40 with no Defence requirement, as the Wiki
   states.
3. **Reviewed rules** for older item families (metal tiers, d'hide, god armour, vestments, mystic,
   battlestaves, wands, skillcapes, black masks, salve amulets and so on). They also cover max capes (99 in
   all eight modelled combat skills, which is part of the real requirement but not all of it) and Ava's
   accumulator (50 Ranged to obtain). An empty rule clears levels taken from the Wiki text (salve amulets).

Ornament variants inherit their base item's levels. Once the client's cache is available at runtime, the
plugin re-reads the parameters for every item in the catalogue and can **only raise** the bundled levels,
so a game update that adds or raises a requirement takes effect without a new snapshot. The pegasian
boots' Attack 75 pair in the cache stays disabled, because their real requirement is Defence and Ranged
75. Quest, diary, minigame and non-combat skill requirements aren't modelled.

Where the levels come from, across the 1,932 catalogue ids: 480 have cache parameters, 1,018 have levels
from the Wiki text and 585 match a reviewed rule, while 735 have no wear requirement from any source. The
full per-item breakdown is written to `build/combat-reference/wiki-equipment-provenance.json`, which Git
ignores.

## Remaining limits

- Prices: the plugin uses live GE prices and RuneLite's component mappings. Untradeable items without
  tradeable components have no price estimate, so they cost nothing in Best in slot searches (budget
  searches only use them if you own them).
- This hasn't been checked in game yet: the live cache overlay still needs confirming in a logged-in
  client (debug log "Live cache raised wear levels"), as does the pegasian boots' requirement.
- Specials the Wiki DPS calculator leaves out follow their Wiki pages; side effects such as binds,
  bleeds and drains aren't scored. The Eclipse atlatl's special isn't modelled.
- The bundled Wiki data is under CC BY-NC-SA 3.0; see [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).

## Regenerating

From the plugin folder, with Python 3 and `BGS_WIKI_CONTACT` set (see `tools/wiki_api.py`):

```sh
python tools/wiki_monsters.py --refresh
python tools/wiki_equipment.py --refresh
python tools/wiki_status_immunities.py --refresh
python tools/equipment_requirements.py
./gradlew build
```

On Windows, run `gradlew.bat build` instead of `./gradlew build`.

Downloads are cached in the `build/combat-reference` folder, which Git ignores. Without `--refresh`, the
generators reuse that cache. Wear levels from the game cache need `build/combat-reference/cache-reqs.json`,
which `tools/spike/ReqDump.java` writes from a copy of the local game cache. Without it, requirements only
come from the Wiki text and the reviewed rules.
