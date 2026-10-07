# Third-party notices

Best Gear Setup's own code is released under the BSD 2-Clause licence (see `LICENSE`). That licence doesn't
cover the OSRS Wiki data bundled with the plugin, or the code adapted from the other projects listed below,
which keep their own terms.

The Plugin Hub only ships the plugin JAR, so `LICENSE` and this file are included in it too, as
`META-INF/LICENSE` and `META-INF/THIRD_PARTY_NOTICES.md`.

## OSRS Wiki data

The bundled game data comes from the [Old School RuneScape Wiki](https://oldschool.runescape.wiki/) and its
contributors, and is published under **CC BY-NC-SA 3.0**
([licence](https://creativecommons.org/licenses/by-nc-sa/3.0/deed.en),
[legal code](https://creativecommons.org/licenses/by-nc-sa/3.0/legalcode.en),
[Wiki copyright page](https://oldschool.runescape.wiki/w/RuneScape:Copyrights)). Its attribution,
non-commercial and share-alike terms apply to this data, not the plugin's BSD licence.

| File | Where it comes from |
| --- | --- |
| `wiki-monsters.json.gz` | Built by `tools/wiki_monsters.py` from the Wiki's `infobox_monster` bucket and `Category:Bosses`, retrieved `2026-10-01T05:06:58Z`. Variant names are normalised, and encounter tags, multi-NPC mappings, Nylocas colour variants and Challenge Mode exclusions are added. 3,110 variants. |
| `wiki-gamedata.json.gz` | Built by `tools/wiki_equipment.py` from the Wiki's `infobox_item`, `infobox_bonuses` and `infobox_spell` buckets, item page text (for wear requirements) and page categories, retrieved `2026-10-01` (UTC). Items with identical stats are merged, names are normalised, and League, beta and PvP Arena items are left out. It also has hand-written tables (weapon styles, ammunition tiers, autocast lists, spell max hits, prayer and potion boosts), each based on a named Wiki page. Wear levels also use requirement parameters read from a local copy of the game cache. |
| `status-immunities.json` | Built by `tools/wiki_status_immunities.py` from the Wiki's monster resistance fields, retrieved `2026-10-01T04:09:01Z`. Resistance values are normalised and keyed by NPC ID. |
| `equipment-requirements.json` | Built by `tools/equipment_requirements.py`. Hand-reviewed wear rules, each with its item selectors, slot checks, review notes and a link to the Wiki page it comes from. It's based on the Wiki, so the same licence applies. |

Retrieval times are in UTC; in Pacific time, the monster and status snapshots were both taken on
30 September 2026. The generator scripts record every change they make to the source data. If you
redistribute the data, keep its metadata and the per-rule source links with it.

## OSRS Wiki DPS calculator

The combat formulas are checked against the
[OSRS Wiki DPS calculator](https://github.com/weirdgloop/osrs-dps-calc), which is licensed under
[GPL-3.0](https://github.com/weirdgloop/osrs-dps-calc/blob/main/LICENSE), at revision
`89c3e25b344aea90d0189746e4b5f73dde0f0383`. [COMBAT_AUDIT.md](COMBAT_AUDIT.md) lists where each formula
comes from.

Game formulas and values are facts, so the plugin can use the same ones as the calculator, but its code is
written from the game's rules and the Wiki pages rather than translated from the calculator's source. The
one exception is the Chambers of Xeric scaling values in `RaidScaling.cox`: the Wiki doesn't publish them,
so they come from the calculator and are credited to it in the source.

`tools/reference_similarity.py` compares the plugin's calculator code (`src/main/java/com/bestgearsetup/calc`)
with the calculator's library files, matching code structure after normalising names and syntax and also
comparing comments, to catch anything that reads like a translation. The plugin follows the same game
mechanics, constants and modifier order as the calculator, but it's built differently: it works with
expected values rather than hit distributions, combines the accuracy and damage steps, and uses rule
tables.

## RuneLite Slayer target aliases

`slayer-targets.json` is adapted from the assignment names and target aliases in RuneLite's
`net.runelite.client.plugins.slayer.Task`, with extra target names added for the Dagannoth Kings and
Barrows. RuneLite's BSD 3-Clause notice for that file:

```text
Copyright (c) 2017, Tyler <https://github.com/tylerthardy>
Copyright (c) 2018, Shaun Dreclin <shaundreclin@gmail.com>
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

## RuneLite Fremennik Slayer Dungeon regions

The three Fremennik Slayer Dungeon region IDs in `AccountFacts` come from RuneLite's
`net.runelite.client.plugins.discord.DiscordGameEventType`. Its BSD 3-Clause notice:

```text
Copyright (c) 2018, Tomas Slusny <slusnucky@gmail.com>
Copyright (c) 2018, PandahRS <https://github.com/PandahRS>
Copyright (c) 2020, Brooklyn <https://github.com/Broooklyn>
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```
