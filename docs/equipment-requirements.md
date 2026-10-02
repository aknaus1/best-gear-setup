# Bundled game data

Monsters, equipment, spells, prayers and potions all come from bundled OSRS Wiki snapshots, and the
plugin makes no network requests. Sources and licences are listed in
[the source notices](../THIRD_PARTY_NOTICES.md).

## What is bundled

| Resource | Generator | Contents |
| --- | --- | --- |
| `wiki-monsters.json.gz` | `tools/wiki_monsters.py` | 3,110 monster variants from `infobox_monster` and `Category:Bosses` |
| `wiki-gamedata.json.gz` | `tools/wiki_equipment.py` | 638 weapons and 1,305 other slot entries (darts appear as weapons and blowpipe ammunition), 48 combat spells, offensive prayers and potions |
| `status-immunities.json` | `tools/wiki_status_immunities.py` | Poison, venom, freeze and burn resistance by NPC id |
| `equipment-requirements.json` | `tools/equipment_requirements.py` | 123 reviewed, source-linked wear rules |

The equipment snapshot joins `infobox_item` with `infobox_bonuses` (stats, slot, category, speed,
members, tradeable). Rows are dropped when they are removed from the game, are Last Man Standing or
beta copies, are broken/inactive/unlit/empty states, or sit in the Wiki categories for Leagues, Grid
Master, beta items, shelved content, Emir's Arena (PvP Arena loadouts) and discontinued content.
Zero-stat cosmetics are dropped except items an encounter or effect rule needs (ring of recoil,
Efaritay's aid, slayer protection items and so on). Deadman gear gets a `(dmm)` marker from its Wiki
category, so the Deadman setting still controls it.

Stat-identical copies fold into one entry: charges, ornament kits (`(or)`, `(g)`, `(t)`, `(h1)`),
locked, poisoned, Barrows-degraded and NMZ/Soul Wars/Emir's imbues, plus recolours within a RuneLite
variation group and the "holy/sanguine/radiant/twisted" ornament prefixes. Items with the same stats
but their own effects (the blood ancient sceptre, eldritch nightmare staff, blessed quiver) stay
separate. Every entry lists its folded ids, and owning any of them counts as owning the item.

Facts with no structured Wiki field are tables in the generator, each following its Wiki page:
weapon styles per category (Weapons/Types), ammunition tiers (Arrows, Bolts, Darts, javelins, tars),
autocast lists (Autocast), spell max hits (Combat spells; level and spellbook come from
`infobox_spell`), prayer boosts and potion formulas.

## Wear requirements

Each item's levels combine three sources, keeping the strictest level per skill:

1. **Game cache parameters** 434/436, 435/437, 191/613, 579/614, 610/615, 611/616 and 612/617, read
   offline by `tools/spike/ReqDump.java` from a copy of the local cache.
2. **The item's Wiki text**: the first sentence that states a wield/wear/equip gate. Sentences about
   quests, history, polls, creation, enchanting or "no requirements" are ignored; for example the
   twisted bow gives Ranged 85, the master wand Magic 60 (its cache parameter says 55), and ranger gloves
   Ranged 40 with explicitly no Defence.
3. **Reviewed rules** for legacy families (metal tiers, d'hide, god armour, vestments, mystic,
   battlestaves, wands, skillcapes, black masks, salve amulets and so on). This pass added max capes
   (99 in all eight modelled combat skills, a necessary subset of the real gate) and Ava's accumulator
   (50 Ranged to obtain). An empty rule clears text-derived levels (salve amulets).

Ornament variants inherit their base item's levels. At runtime, once the client's cache is available,
the plugin re-reads the parameters for every catalogue item and can **only raise** bundled levels, so a
game update that adds or raises a requirement takes effect without a new snapshot. The pegasian boots'
cached Attack 75 pair stays disabled; their gate is Defence and Ranged 75. Quest, diary, minigame and
noncombat-skill gates are not modelled.

Provenance across the 1,932 catalogue ids: 480 have cache parameters, 1,018 have levels from Wiki text,
585 match a reviewed rule; 735 have no stated wear requirement from any source. The full per-item
provenance is written to the ignored `build/combat-reference/wiki-equipment-provenance.json`.

## Remaining limits

- Prices: the plugin uses live GE prices and RuneLite's component mappings. Untradeable items
  without tradeable components have no price estimate, so they cost nothing in Best in slot searches
  (budget searches use them only when owned).
- In-game verification has not been done: confirm the live cache overlay in a logged-in client
  (debug log "Live cache raised wear levels") and the pegasian boots' gate.
- Specials the Wiki DPS calculator leaves out now follow their Wiki pages; side effects such as binds,
  bleeds and drains are not scored. The Eclipse atlatl special is not modelled.
- Licensing of the bundled Wiki data (CC BY-NC-SA 3.0) is recorded in
  [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md); the formula-code review is resolved there.

## Regenerating

From the plugin directory, with Python 3 and `BGS_WIKI_CONTACT` set (see `tools/wiki_api.py`):

```sh
python tools/wiki_monsters.py --refresh
python tools/wiki_equipment.py --refresh
python tools/wiki_status_immunities.py --refresh
python tools/equipment_requirements.py
./gradlew build
```

On Windows, run `gradlew.bat build` instead of `./gradlew build`.

Downloads are cached in the ignored `build/combat-reference` folder; without `--refresh` the generators
reuse that cache. Wear levels from the game cache need `build/combat-reference/cache-reqs.json`, written
by `tools/spike/ReqDump.java` from a copy of the local game cache; without it, requirements come from Wiki
text and the reviewed rules only.
