# Overhead catalogue audit

Evaluated on 2026-10-13 against the bundled snapshot retrieved 2026-10-10.

**3,130 monster variants, including 295 boss variants, were exercised across 563,400 recommendation cases.** This checks every variant against three combat classes, four representative armour/shield profiles, Prayer levels 30/37/40/43/99, and automatic/adjacent/two-tile distance.

The per-variant decisions, notes, attack labels and rule sources are in the [generated catalogue](../build/reports/overheads/catalogue.json). Rerun `OverheadCatalogueTest` and then `python tools/write_overhead_audit.py` to refresh this report. The generated catalogue lives in the build directory.

## Coverage

Counts below count each variant once. If its combat-class recommendations have different bases, the least certain basis wins. Encounter rules describe verified mechanics or a documented strategy, including conditional instructions; they are not a claim of mathematically optimal damage for every kit.

| Basis | All variants | Boss variants |
| --- | ---: | ---: |
| Unknown attack data | 76 | 0 |
| Unverified special mode | 39 | 0 |
| Insufficient fire protection | 8 | 0 |
| Style / max-hit fallback | 138 | 0 |
| Ordinary damage estimate | 2,515 | 110 |
| No blockable attack in snapshot | 102 | 9 |
| Encounter rule | 252 | 176 |

## Corrections made

- Added delayed-cue, projectile-impact, counted-cycle and phase rules for Jad, Nightmare/Phosani, Scurrius, Hydra, Hunllef, Muspah, Leviathan, Whisperer, Doom, Yama, Nex, Hueycoatl, Cerberus, Galvek, Maggot King, Manticores, Inferno blobs, demonic gorillas and tormented demons.
- Distinguished timing: Manticore prayers must precede launch; Leviathan/Doom projectiles are checked at impact; Muspah's ordinary ranged attacks require the overhead at attack start and its magic wind-up requires the switch before launch. Counted cycles and boss forms have their own cues.
- Corrected ranged/magic Vanguard prayers, the unprotectable P1 obelisk attacks, continuing Akkha attacks while damaging shadows, P3 Warden supporting attacks, and approach-to-melee assumptions.
- Added equipment checks for KBD fire shields/two-handed weapons, Araxxor's choice of distant attack against weaker defence, and Sarachnis's aranea boots. Vorkath's equipment rule remains in place.
- Added prayer-disabled Barbarian Assault and Earth Warrior Champion rules, Sol's timed parry, and warnings about special modes whose mechanics have not been verified.
- Separated ranged-magic/ranged-melee accuracy from Magic/Melee protection, and magical-melee/magical-ranged accuracy from Melee/Missiles protection. Explicit partial penetration scales blocked damage.

## What the automated pass establishes

Every matrix case returns a coherent recommendation or an explicit unknown: available prayers respect level boundaries, the named overhead belongs to its prayer list, and a mixed infobox does not create a switching recommendation without an encounter rule. Regression tests exercise timing instructions, equipment dependencies, different damage/accuracy types, fire compatibility, and preservation of search assumptions when result views are copied.

This is an evaluation of the recommendation engine, not 563,400 live game fights or a complete DPS/kill-time optimisation search for every monster. The representative table uses one-handed weapons, plate armour plus an anti-dragon shield, 99 base combat levels, 99 Prayer and automatic distance. It does not represent the player's bank or actual owned setup.

## Limits of generic advice

The current snapshot omits melee/ranged attack bonuses and supplies synthetic equal attack weights. Generic recommendations therefore estimate missing bonuses as zero and do not establish actual attack frequencies. Infobox maximum hits may combine specials or multiple hits. Base defensive levels are used; potion/defensive-prayer boosts and equipment damage-reduction effects are not simulated. Attack timing, guaranteed hits, adaptive style selection, room companions, minigame restrictions and boss specials need encounter rules rather than an ordinary damage formula.

Explicit distance is an assumption about maintained position. Mobile monsters can approach, and team tanking, kiting and invocation/mode choices can change the right overhead. Known approach, projectile-melee and phase exceptions are documented in their advice. Source links below identify rule references; they do not independently verify every variant or special mode.

## Detailed results

Per-variant boss recommendations and explicit gaps are in the [generated details](../build/reports/overheads/details.md) and [catalogue](../build/reports/overheads/catalogue.json). Both are regenerated locally rather than committed.

## Explicit gaps

An unknown attack record is not interpreted as *None needed*. An unverified special mode is not allowed to borrow the standard boss's timing. Insufficient fire protection is a gear problem in this representative profile (for example, an anti-dragon shield is not a wyvern shield), not a finding that the monster's mechanics are unknown.

## Encounter references

The runtime rules attach these references to advice; the catalogue retains them for review.

- [Alchemical Hydra/Strategies](https://oldschool.runescape.wiki/w/Alchemical_Hydra/Strategies)
- [Amoxliatl](https://oldschool.runescape.wiki/w/Amoxliatl)
- [Araxxor](https://oldschool.runescape.wiki/w/Araxxor)
- [Araxxor/Strategies](https://oldschool.runescape.wiki/w/Araxxor/Strategies)
- [Barbarian Assault](https://oldschool.runescape.wiki/w/Barbarian_Assault)
- [Basilisk Knight/Strategies](https://oldschool.runescape.wiki/w/Basilisk_Knight/Strategies)
- [Callisto/Strategies](https://oldschool.runescape.wiki/w/Callisto/Strategies)
- [Cerberus/Strategies](https://oldschool.runescape.wiki/w/Cerberus/Strategies)
- [Chambers of Xeric/Strategies](https://oldschool.runescape.wiki/w/Chambers_of_Xeric/Strategies)
- [Champions%27 Challenge](https://oldschool.runescape.wiki/w/Champions%27_Challenge)
- [Chaos Elemental/Strategies](https://oldschool.runescape.wiki/w/Chaos_Elemental/Strategies)
- [Commander Zilyana/Strategies](https://oldschool.runescape.wiki/w/Commander_Zilyana/Strategies)
- [Corporeal Beast](https://oldschool.runescape.wiki/w/Corporeal_Beast)
- [Demonic gorilla/Strategies](https://oldschool.runescape.wiki/w/Demonic_gorilla/Strategies)
- [Doom of Mokhaiotl/Strategies](https://oldschool.runescape.wiki/w/Doom_of_Mokhaiotl/Strategies)
- [Dragon Slayer II](https://oldschool.runescape.wiki/w/Dragon_Slayer_II)
- [Duke Sucellus/Strategies](https://oldschool.runescape.wiki/w/Duke_Sucellus/Strategies)
- [Fortis Colosseum/Strategies](https://oldschool.runescape.wiki/w/Fortis_Colosseum/Strategies)
- [General Graardor/Strategies](https://oldschool.runescape.wiki/w/General_Graardor/Strategies)
- [Grotesque Guardians/Strategies](https://oldschool.runescape.wiki/w/Grotesque_Guardians/Strategies)
- [Jal-Ak](https://oldschool.runescape.wiki/w/Jal-Ak)
- [K%27ril Tsutsaroth/Strategies](https://oldschool.runescape.wiki/w/K%27ril_Tsutsaroth/Strategies)
- [Kalphite Queen/Strategies](https://oldschool.runescape.wiki/w/Kalphite_Queen/Strategies)
- [Kephri](https://oldschool.runescape.wiki/w/Kephri)
- [King Black Dragon/Strategies](https://oldschool.runescape.wiki/w/King_Black_Dragon/Strategies)
- [Kree%27arra/Strategies](https://oldschool.runescape.wiki/w/Kree%27arra/Strategies)
- [Lizardman shaman/Strategies](https://oldschool.runescape.wiki/w/Lizardman_shaman/Strategies)
- [Maggot King/Strategies](https://oldschool.runescape.wiki/w/Maggot_King/Strategies)
- [Monkey Madness II](https://oldschool.runescape.wiki/w/Monkey_Madness_II)
- [Nex/Strategies](https://oldschool.runescape.wiki/w/Nex/Strategies)
- [Obor](https://oldschool.runescape.wiki/w/Obor)
- [Phantom Muspah/Strategies](https://oldschool.runescape.wiki/w/Phantom_Muspah/Strategies)
- [Phosani%27s Nightmare/Strategies](https://oldschool.runescape.wiki/w/Phosani%27s_Nightmare/Strategies)
- [Royal Titans](https://oldschool.runescape.wiki/w/Royal_Titans)
- [Sarachnis/Strategies](https://oldschool.runescape.wiki/w/Sarachnis/Strategies)
- [Scurrius](https://oldschool.runescape.wiki/w/Scurrius)
- [Shellbane Gryphon/Strategies](https://oldschool.runescape.wiki/w/Shellbane_Gryphon/Strategies)
- [Sol Heredit](https://oldschool.runescape.wiki/w/Sol_Heredit)
- [The Gauntlet/Strategies](https://oldschool.runescape.wiki/w/The_Gauntlet/Strategies)
- [The Hueycoatl/Strategies](https://oldschool.runescape.wiki/w/The_Hueycoatl/Strategies)
- [The Leviathan/Strategies](https://oldschool.runescape.wiki/w/The_Leviathan/Strategies)
- [The Mimic](https://oldschool.runescape.wiki/w/The_Mimic)
- [The Nightmare/Strategies](https://oldschool.runescape.wiki/w/The_Nightmare/Strategies)
- [The Whisperer/Strategies](https://oldschool.runescape.wiki/w/The_Whisperer/Strategies)
- [Theatre of Blood/Strategies](https://oldschool.runescape.wiki/w/Theatre_of_Blood/Strategies)
- [Tombs of Amascut/Strategies](https://oldschool.runescape.wiki/w/Tombs_of_Amascut/Strategies)
- [Tormented Demon/Strategies](https://oldschool.runescape.wiki/w/Tormented_Demon/Strategies)
- [TzHaar Fight Cave/Strategies](https://oldschool.runescape.wiki/w/TzHaar_Fight_Cave/Strategies)
- [Vardorvis/Strategies](https://oldschool.runescape.wiki/w/Vardorvis/Strategies)
- [Venenatis/Strategies](https://oldschool.runescape.wiki/w/Venenatis/Strategies)
- [Vet%27ion/Strategies](https://oldschool.runescape.wiki/w/Vet%27ion/Strategies)
- [Vorkath/Strategies](https://oldschool.runescape.wiki/w/Vorkath/Strategies)
- [While Guthix Sleeps](https://oldschool.runescape.wiki/w/While_Guthix_Sleeps)
- [Yama/Strategies](https://oldschool.runescape.wiki/w/Yama/Strategies)
- [Zalcano](https://oldschool.runescape.wiki/w/Zalcano)
- [Zulrah/Strategies](https://oldschool.runescape.wiki/w/Zulrah/Strategies)
