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

## Boss variants

These are the snapshot's `boss=true` variants. Some other bosses/modes/minions are not tagged as bosses by the snapshot; all still appear in the complete catalogue and were included in the matrix.

| Variant | Melee setup | Ranged setup | Magic setup | Basis |
| --- | --- | --- | --- | --- |
| abyssal sire (phase 1) | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| abyssal sire (phase 2) | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| abyssal sire (phase 3 (stage 1)) | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| abyssal sire (phase 3 (stage 2)) | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| agrith naar | Protect from Magic | Protect from Magic | Protect from Magic | ordinary damage estimate |
| agrith-na-na | Protect from Magic | Protect from Magic | Protect from Magic | ordinary damage estimate |
| ahrim the blighted | Protect from Magic | Protect from Magic | Protect from Magic | ordinary damage estimate |
| akkha | Switch: Melee / Missiles / Magic | Switch: Melee / Missiles / Magic | Switch: Melee / Missiles / Magic | encounter rule |
| alchemical hydra (electric) | Switch: Magic / Missiles | Switch: Magic / Missiles | Switch: Magic / Missiles | encounter rule |
| alchemical hydra (extinguished) | Switch: Magic / Missiles | Switch: Magic / Missiles | Switch: Magic / Missiles | encounter rule |
| alchemical hydra (fire) | Switch: Magic / Missiles | Switch: Magic / Missiles | Switch: Magic / Missiles | encounter rule |
| alchemical hydra (serpentine) | Switch: Magic / Missiles | Switch: Magic / Missiles | Switch: Magic / Missiles | encounter rule |
| amoxliatl (post-quest) | Protect from Magic | Protect from Magic | Protect from Magic | encounter rule |
| amoxliatl (quest) | Protect from Magic | Protect from Magic | Protect from Magic | encounter rule |
| araxxor (enraged) | Protect from Melee | Switch: Magic / Melee | Switch: Magic / Melee | encounter rule |
| araxxor (in combat) | Protect from Melee | Switch: Magic / Melee | Switch: Magic / Melee | encounter rule |
| arrg | Protect from Melee | Protect from Missiles | Protect from Missiles | ordinary damage estimate |
| artio | Switch: Melee / Magic / Missiles | Switch: Missiles / Magic / Melee | Switch: Missiles / Magic / Melee | encounter rule |
| arzinian avatar of magic (invincible) | Protect from Magic | Protect from Magic | Protect from Magic | ordinary damage estimate |
| arzinian avatar of magic (level 125) | Protect from Magic | Protect from Magic | Protect from Magic | ordinary damage estimate |
| arzinian avatar of magic (level 75) | Protect from Magic | Protect from Magic | Protect from Magic | ordinary damage estimate |
| arzinian avatar of ranging (invincible) | Protect from Missiles | Protect from Missiles | Protect from Missiles | ordinary damage estimate |
| arzinian avatar of ranging (level 125) | Protect from Missiles | Protect from Missiles | Protect from Missiles | ordinary damage estimate |
| arzinian avatar of ranging (level 75) | Protect from Missiles | Protect from Missiles | Protect from Missiles | ordinary damage estimate |
| arzinian avatar of strength (invincible) | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| arzinian avatar of strength (level 125) | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| arzinian avatar of strength (level 75) | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| ba-ba | Protect from Melee | Protect from Melee | Protect from Melee | encounter rule |
| barrelchest | Protect from Melee | Protect from Missiles | Protect from Missiles | ordinary damage estimate |
| black demon (level 172) | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| black demon (level 178) | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| black demon (level 184) | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| black demon (level 188) | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| black golem | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| black knight titan | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| blood moon | None needed | None needed | None needed | no blockable attack in snapshot |
| blue moon | None needed | None needed | None needed | no blockable attack in snapshot |
| bouncer | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| bouncer (ghost) | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| branda the fire queen | Protect from Melee | Protect from Melee | Protect from Melee | encounter rule |
| brutus | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| bryophyta | Protect from Magic | Protect from Magic | Protect from Magic | ordinary damage estimate |
| callisto | Switch: Melee / Magic / Missiles | Switch: Missiles / Magic / Melee | Switch: Missiles / Magic / Melee | encounter rule |
| calvar'ion (enraged) | Protect from Melee | Protect from Melee | Protect from Melee | encounter rule |
| calvar'ion (normal) | Protect from Melee | Protect from Melee | Protect from Melee | encounter rule |
| cerberus | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| chaos elemental | Protect from Magic | Protect from Magic | Protect from Magic | encounter rule |
| chaos fanatic | Protect from Magic | Protect from Magic | Protect from Magic | ordinary damage estimate |
| chronozon | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| commander zilyana | Protect from Melee | Protect from Magic | Protect from Missiles | encounter rule |
| corporeal beast | Protect from Magic | Protect from Magic | Protect from Magic | encounter rule |
| corrupted hunllef | Switch: Missiles / Magic | Switch: Missiles / Magic | Switch: Missiles / Magic | encounter rule |
| count draynor | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| crazy archaeologist | Protect from Melee | Protect from Missiles | Protect from Missiles | ordinary damage estimate |
| crystalline hunllef | Switch: Missiles / Magic | Switch: Missiles / Magic | Switch: Missiles / Magic | encounter rule |
| culinaromancer | Protect from Magic | Protect from Magic | Protect from Magic | ordinary damage estimate |
| dad | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| dagannoth mother | Protect from Melee | Protect from Missiles | Protect from Missiles | ordinary damage estimate |
| dagannoth prime | Protect from Magic | Protect from Magic | Protect from Magic | ordinary damage estimate |
| dagannoth rex | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| dagannoth supreme | Protect from Missiles | Protect from Missiles | Protect from Missiles | ordinary damage estimate |
| damis (first form) | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| damis (second form) | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| dawn | Protect from Missiles | Protect from Missiles | Protect from Missiles | encounter rule |
| delrith | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| demonic brutus (brutus) | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| demonic brutus (ghost) | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| deranged archaeologist | Protect from Melee | Protect from Missiles | Protect from Missiles | ordinary damage estimate |
| dessourt | Protect from Melee | Protect from Magic | Protect from Magic | ordinary damage estimate |
| dessous | Protect from Melee | Protect from Magic | Protect from Magic | ordinary damage estimate |
| dharok the wretched | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| doom of mokhaiotl (deep delve) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| doom of mokhaiotl (deep delve) (npc 14708) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| doom of mokhaiotl (deep delve) (npc 14709) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| doom of mokhaiotl (delve 1) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles | Switch: Magic / Missiles | encounter rule |
| doom of mokhaiotl (delve 1) (npc 14708) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles | Switch: Magic / Missiles | encounter rule |
| doom of mokhaiotl (delve 2) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| doom of mokhaiotl (delve 2) (npc 14708) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| doom of mokhaiotl (delve 3) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| doom of mokhaiotl (delve 3) (npc 14708) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| doom of mokhaiotl (delve 4) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| doom of mokhaiotl (delve 4) (npc 14708) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| doom of mokhaiotl (delve 5) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| doom of mokhaiotl (delve 5) (npc 14707) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| doom of mokhaiotl (delve 5) (npc 14708) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| doom of mokhaiotl (delve 6) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| doom of mokhaiotl (delve 6) (npc 14708) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| doom of mokhaiotl (delve 6) (npc 14709) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| doom of mokhaiotl (delve 7) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| doom of mokhaiotl (delve 7) (npc 14708) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| doom of mokhaiotl (delve 7) (npc 14709) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| doom of mokhaiotl (delve 8) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| doom of mokhaiotl (delve 8) (npc 14708) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| doom of mokhaiotl (delve 8) (npc 14709) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| duke sucellus (asleep) | Protect from Melee | Protect from Magic | Protect from Magic | encounter rule |
| duke sucellus (asleep) (npc 12166) | Protect from Melee | Protect from Magic | Protect from Magic | encounter rule |
| duke sucellus (asleep) (npc 12193) | Protect from Melee | Protect from Magic | Protect from Magic | encounter rule |
| duke sucellus (awake) | Protect from Melee | Protect from Magic | Protect from Magic | encounter rule |
| duke sucellus (awake) (npc 12191) | Protect from Melee | Protect from Magic | Protect from Magic | encounter rule |
| duke sucellus (awake) (npc 12195) | Protect from Melee | Protect from Magic | Protect from Magic | encounter rule |
| duke sucellus (defeated) | Protect from Melee | Protect from Magic | Protect from Magic | encounter rule |
| duke sucellus (defeated) (npc 12192) | Protect from Melee | Protect from Magic | Protect from Magic | encounter rule |
| duke sucellus (defeated) (npc 12196) | Protect from Melee | Protect from Magic | Protect from Magic | encounter rule |
| dusk (first form) | Protect from Melee | Protect from Melee | Protect from Melee | encounter rule |
| dusk (second form) | Protect from Melee | Protect from Missiles | Protect from Missiles | encounter rule |
| eclipse moon (clone) | None needed | None needed | None needed | no blockable attack in snapshot |
| eclipse moon (regular) | None needed | None needed | None needed | no blockable attack in snapshot |
| eldric the ice king | Protect from Melee | Protect from Melee | Protect from Melee | encounter rule |
| elidinis' warden (core-ejected) | None needed | None needed | None needed | encounter rule |
| elidinis' warden (invulnerable) | Switch: Magic / Missiles | Switch: Magic / Missiles | Switch: Magic / Missiles | encounter rule |
| elidinis' warden (phase 2) | Switch: Magic / Missiles | Switch: Magic / Missiles | Switch: Magic / Missiles | encounter rule |
| elidinis' warden (phase 3 enraged) | Switch: Magic / Missiles | Switch: Magic / Missiles | Switch: Magic / Missiles | encounter rule |
| elidinis' warden (phase 3) | Switch: Magic / Missiles | Switch: Magic / Missiles | Switch: Magic / Missiles | encounter rule |
| elvarg | Protect from Magic | Protect from Magic | Protect from Magic | ordinary damage estimate |
| evil spirit | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| fareed | Protect from Melee | Protect from Magic | Protect from Magic | ordinary damage estimate |
| flambeed | Protect from Melee | Protect from Magic | Protect from Magic | ordinary damage estimate |
| gadderanks | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| galvek (air) | Protect from Magic | Protect from Missiles | Protect from Missiles | encounter rule |
| galvek (earth) | Protect from Magic | Protect from Missiles | Protect from Missiles | encounter rule |
| galvek (fire) | Protect from Magic | Protect from Magic | Protect from Magic | encounter rule |
| galvek (water) | Protect from Magic | Protect from Missiles | Protect from Missiles | encounter rule |
| gelatinnoth mother | Protect from Magic | Protect from Magic | Protect from Magic | ordinary damage estimate |
| general graardor | Protect from Melee | Protect from Missiles | Protect from Missiles | encounter rule |
| general khazard | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| giant mole | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| giant roc | Protect from Missiles | Protect from Missiles | Protect from Missiles | ordinary damage estimate |
| giant scarab | Protect from Melee | Protect from Missiles | Protect from Missiles | ordinary damage estimate |
| giant sea snake | Protect from Melee | Protect from Missiles | Protect from Missiles | ordinary damage estimate |
| glod | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| glough | Protect from Melee | Protect from Magic | Protect from Magic | encounter rule |
| great olm (head) | Switch: Magic / Missiles | Switch: Magic / Missiles | Switch: Magic / Missiles | encounter rule |
| great olm (mage hand) | Switch: Magic / Missiles | Switch: Magic / Missiles | Switch: Magic / Missiles | encounter rule |
| great olm (melee hand) | Switch: Magic / Missiles | Switch: Magic / Missiles | Switch: Magic / Missiles | encounter rule |
| grey golem | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| guthan the infested | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| hespori | Protect from Magic | Protect from Magic | Protect from Magic | ordinary damage estimate |
| ice demon (normal) | Protect from Missiles | Protect from Missiles | Protect from Missiles | encounter rule |
| ice troll king | Protect from Melee | Protect from Magic | Protect from Magic | ordinary damage estimate |
| judge of yama (a kingdom divided) | Protect from Magic | Protect from Magic | Protect from Magic | ordinary damage estimate |
| jungle demon | Protect from Magic | Protect from Magic | Protect from Magic | ordinary damage estimate |
| k'ril tsutsaroth | Protect from Melee | Protect from Missiles | Protect from Missiles | encounter rule |
| kalphite queen (airborne) | Protect from Magic | Protect from Magic | Protect from Magic | encounter rule |
| kalphite queen (crawling) | Protect from Magic | Protect from Magic | Protect from Magic | encounter rule |
| kamil | Protect from Melee | Protect from Magic | Protect from Magic | ordinary damage estimate |
| karamel | Protect from Missiles | Protect from Missiles | Protect from Missiles | ordinary damage estimate |
| karil the tainted | Protect from Missiles | Protect from Missiles | Protect from Missiles | ordinary damage estimate |
| kephri (aggressive) | None needed | None needed | None needed | encounter rule |
| kephri (shielded) | None needed | None needed | None needed | encounter rule |
| king black dragon | Protect from Melee | Protect from Melee | Protect from Melee | encounter rule |
| koschei the deathless | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| koschei the deathless (npc 3898) | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| koschei the deathless (npc 3899) | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| koschei the deathless (npc 3900) | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| kraken (kraken) | None needed | None needed | None needed | no blockable attack in snapshot |
| kraken (whirlpool) | None needed | None needed | None needed | no blockable attack in snapshot |
| kree'arra (normal) | Protect from Missiles | Protect from Missiles | Protect from Missiles | encounter rule |
| lowerniel drakan | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| mad angel (post-quest) | Protect from Magic | Protect from Magic | Protect from Magic | ordinary damage estimate |
| mad angel (quest) | Protect from Magic | Protect from Magic | Protect from Magic | ordinary damage estimate |
| maggot king (far) | Switch: Missiles / Magic / Melee | Switch: Missiles / Magic / Melee | Switch: Missiles / Magic / Melee | encounter rule |
| maggot king (nearby) | Switch: Missiles / Magic / Melee | Switch: Missiles / Magic / Melee | Switch: Missiles / Magic / Melee | encounter rule |
| maggot king (roaring) | Switch: Missiles / Magic / Melee | Switch: Missiles / Magic / Melee | Switch: Missiles / Magic / Melee | encounter rule |
| me (type a) | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| me (type b) | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| melzar the mad | Protect from Magic | Protect from Magic | Protect from Magic | ordinary damage estimate |
| moss guardian | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| muttadile (large) | Protect from Melee | Protect from Missiles | Protect from Missiles | encounter rule |
| muttadile (small) | Protect from Melee | Protect from Missiles | Protect from Missiles | encounter rule |
| nex | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| nezikchened | Protect from Magic | Protect from Magic | Protect from Magic | ordinary damage estimate |
| nylocas vasilias (magic) (entry) | Protect from Magic | Protect from Magic | Protect from Magic | encounter rule |
| nylocas vasilias (magic) (hard) | Protect from Magic | Protect from Magic | Protect from Magic | encounter rule |
| nylocas vasilias (magic) (normal) | Protect from Magic | Protect from Magic | Protect from Magic | encounter rule |
| nylocas vasilias (melee) (entry) | Protect from Melee | Protect from Melee | Protect from Melee | encounter rule |
| nylocas vasilias (melee) (hard) | Protect from Melee | Protect from Melee | Protect from Melee | encounter rule |
| nylocas vasilias (melee) (normal) | Protect from Melee | Protect from Melee | Protect from Melee | encounter rule |
| nylocas vasilias (ranged) (entry) | Protect from Missiles | Protect from Missiles | Protect from Missiles | encounter rule |
| nylocas vasilias (ranged) (hard) | Protect from Missiles | Protect from Missiles | Protect from Missiles | encounter rule |
| nylocas vasilias (ranged) (normal) | Protect from Missiles | Protect from Missiles | Protect from Missiles | encounter rule |
| obor | Protect from Missiles | Protect from Missiles | Protect from Missiles | encounter rule |
| penance queen | None needed | None needed | None needed | encounter rule |
| pestilent bloat (entry) | None needed | None needed | None needed | encounter rule |
| pestilent bloat (hard) | None needed | None needed | None needed | encounter rule |
| pestilent bloat (normal) | None needed | None needed | None needed | encounter rule |
| phantom muspah (melee) | Protect from Melee | Protect from Melee | Protect from Melee | encounter rule |
| phantom muspah (post-shield) | Switch: Missiles / Magic | Switch: Missiles / Magic | Switch: Missiles / Magic | encounter rule |
| phantom muspah (ranged) | Switch: Missiles / Magic | Switch: Missiles / Magic | Switch: Missiles / Magic | encounter rule |
| phantom muspah (shielded) | Switch: Missiles / Magic | Switch: Missiles / Magic | Switch: Missiles / Magic | encounter rule |
| phantom muspah (teleporting) | None needed | None needed | None needed | encounter rule |
| phosani's nightmare | Switch: Melee / Magic / Missiles | Switch: Melee / Magic / Missiles | Switch: Melee / Magic / Missiles | encounter rule |
| revenant maledictus | Protect from Magic | Protect from Magic | Protect from Magic | ordinary damage estimate |
| salarin the twisted | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| sarachnis | Switch: Melee / Missiles | Switch: Missiles / Melee | Switch: Missiles / Melee | encounter rule |
| scorpia | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| scurrius (group) | Switch: Melee / Magic / Missiles | Switch: Melee / Magic / Missiles | Switch: Melee / Magic / Missiles | encounter rule |
| scurrius (solo) | Switch: Melee / Magic / Missiles | Switch: Melee / Magic / Missiles | Switch: Melee / Magic / Missiles | encounter rule |
| sea troll queen | Protect from Magic | Protect from Magic | Protect from Magic | ordinary damage estimate |
| shellbane gryphon | Protect from Melee | Protect from Melee | Protect from Melee | encounter rule |
| sigmund | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| sigmund (npc 5139) | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| sir leye | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| sir mordred | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| skotizo | Protect from Magic | Protect from Magic | Protect from Magic | ordinary damage estimate |
| slagilith | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| slash bash | Protect from Melee | Protect from Missiles | Protect from Missiles | ordinary damage estimate |
| slug prince | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| sol heredit | Protect from Melee | Protect from Melee | Protect from Melee | encounter rule |
| sotetseg (entry) | Switch: Melee / Magic / Missiles | Switch: Magic / Missiles | Switch: Magic / Missiles | encounter rule |
| sotetseg (hard) | Switch: Melee / Magic / Missiles | Switch: Magic / Missiles | Switch: Magic / Missiles | encounter rule |
| sotetseg (normal) | Switch: Melee / Magic / Missiles | Switch: Magic / Missiles | Switch: Magic / Missiles | encounter rule |
| spindel | Protect from Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| tarn | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| tarn (npc 6477) | Protect from Magic | Protect from Magic | Protect from Magic | ordinary damage estimate |
| tekton (enraged) | Protect from Melee | Protect from Melee | Protect from Melee | encounter rule |
| tekton (normal) | Protect from Melee | Protect from Melee | Protect from Melee | encounter rule |
| the draugen | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| the everlasting | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| the hueycoatl | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| the hueycoatl (broken) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| the hueycoatl (defeated) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| the hueycoatl (head) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| the hueycoatl (shielded) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| the hueycoatl (tail) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| the illusive | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| the inadequacy | Protect from Melee | Protect from Missiles | Protect from Missiles | ordinary damage estimate |
| the leviathan (awakened) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| the leviathan (post-quest) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| the leviathan (quest) | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| the maiden of sugadinti (30% health) | Protect from Magic | Protect from Magic | Protect from Magic | encounter rule |
| the maiden of sugadinti (50% health) | Protect from Magic | Protect from Magic | Protect from Magic | encounter rule |
| the maiden of sugadinti (70% health) | Protect from Magic | Protect from Magic | Protect from Magic | encounter rule |
| the maiden of sugadinti (entry mode) | Protect from Magic | Protect from Magic | Protect from Magic | encounter rule |
| the maiden of sugadinti (hard mode) | Protect from Magic | Protect from Magic | Protect from Magic | encounter rule |
| the maiden of sugadinti (normal) | Protect from Magic | Protect from Magic | Protect from Magic | encounter rule |
| the mimic | Protect from Melee | Protect from Melee | Protect from Melee | encounter rule |
| the nightmare | Switch: Melee / Magic / Missiles | Switch: Melee / Magic / Missiles | Switch: Melee / Magic / Missiles | encounter rule |
| the untouchable | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| the whisperer (awakened) | Switch: Magic / Missiles | Switch: Magic / Missiles | Switch: Magic / Missiles | encounter rule |
| the whisperer (post-quest) | Switch: Magic / Missiles | Switch: Magic / Missiles | Switch: Magic / Missiles | encounter rule |
| the whisperer (quest) | Switch: Magic / Missiles | Switch: Magic / Missiles | Switch: Magic / Missiles | encounter rule |
| thermonuclear smoke devil | None needed | None needed | None needed | no blockable attack in snapshot |
| tolna | Protect from Melee | Protect from Missiles | Protect from Missiles | ordinary damage estimate |
| tolna (npc 1076) | Protect from Missiles | Protect from Missiles | Protect from Missiles | ordinary damage estimate |
| tolna (npc 1077) | Protect from Melee | Protect from Missiles | Protect from Missiles | ordinary damage estimate |
| torag the corrupted | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| tree spirit (lost city) | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| treus dayth | Protect from Melee | Protect from Missiles | Protect from Missiles | ordinary damage estimate |
| tumeken's warden (core-ejected) | None needed | None needed | None needed | encounter rule |
| tumeken's warden (invulnerable) | Switch: Melee / Magic / Missiles | Switch: Magic / Missiles | Switch: Magic / Missiles | encounter rule |
| tumeken's warden (phase 2) | Switch: Melee / Magic / Missiles | Switch: Magic / Missiles | Switch: Magic / Missiles | encounter rule |
| tumeken's warden (phase 3 enraged) | Switch: Melee / Magic / Missiles | Switch: Magic / Missiles | Switch: Magic / Missiles | encounter rule |
| tumeken's warden (phase 3) | Switch: Magic / Missiles | Switch: Magic / Missiles | Switch: Magic / Missiles | encounter rule |
| tzkal-zuk (enraged) | None needed | None needed | None needed | no blockable attack in snapshot |
| tzkal-zuk (normal) | None needed | None needed | None needed | no blockable attack in snapshot |
| tztok-jad | Switch: Melee / Magic / Missiles | Switch: Magic / Missiles | Switch: Magic / Missiles | encounter rule |
| ulfric | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| vanguard (magic) | Protect from Magic | Protect from Magic | Protect from Magic | encounter rule |
| vanguard (melee) | Protect from Melee | Protect from Melee | Protect from Melee | encounter rule |
| vanguard (ranged) | Protect from Missiles | Protect from Missiles | Protect from Missiles | encounter rule |
| vardorvis (awakened) | Switch: Melee / Missiles | Switch: Melee / Missiles | Switch: Melee / Missiles | encounter rule |
| vardorvis (post-quest) | Switch: Melee / Missiles | Switch: Melee / Missiles | Switch: Melee / Missiles | encounter rule |
| vardorvis (quest) | Switch: Melee / Missiles | Switch: Melee / Missiles | Switch: Melee / Missiles | encounter rule |
| vasa nistirio (normal) | Switch: Missiles / Magic | Switch: Missiles / Magic | Switch: Missiles / Magic | encounter rule |
| venenatis | Protect from Melee | Switch: Magic / Missiles / Melee | Switch: Magic / Missiles / Melee | encounter rule |
| verac the defiled | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| verzik vitur (phase 1) | Protect from Magic | Protect from Magic | Protect from Magic | encounter rule |
| verzik vitur (phase 1) (entry mode) | Protect from Magic | Protect from Magic | Protect from Magic | encounter rule |
| verzik vitur (phase 1) (hard mode) | Protect from Magic | Protect from Magic | Protect from Magic | encounter rule |
| verzik vitur (phase 2) | Protect from Missiles | Protect from Missiles | Protect from Missiles | encounter rule |
| verzik vitur (phase 2) (entry mode) | Protect from Missiles | Protect from Missiles | Protect from Missiles | encounter rule |
| verzik vitur (phase 2) (hard mode) | Protect from Missiles | Protect from Missiles | Protect from Missiles | encounter rule |
| verzik vitur (phase 3) | Switch: Magic / Missiles | Switch: Magic / Missiles | Switch: Magic / Missiles | encounter rule |
| verzik vitur (phase 3) (entry mode) | Switch: Magic / Missiles | Switch: Magic / Missiles | Switch: Magic / Missiles | encounter rule |
| verzik vitur (phase 3) (hard mode) | Switch: Magic / Missiles | Switch: Magic / Missiles | Switch: Magic / Missiles | encounter rule |
| vespula (normal) | Protect from Missiles | Protect from Missiles | Protect from Missiles | encounter rule |
| vet'ion (enraged) | Protect from Melee | Protect from Melee | Protect from Melee | encounter rule |
| vet'ion (normal) | Protect from Melee | Protect from Melee | Protect from Melee | encounter rule |
| vorkath (dragon slayer ii) | Protect from Magic | Protect from Missiles | Protect from Magic | encounter rule |
| vorkath (post-quest) | Protect from Magic | Protect from Missiles | Protect from Magic | encounter rule |
| white golem | Protect from Melee | Protect from Melee | Protect from Melee | ordinary damage estimate |
| xamphur | Protect from Magic | Protect from Magic | Protect from Magic | ordinary damage estimate |
| xamphur (npc 10954) | Protect from Magic | Protect from Magic | Protect from Magic | ordinary damage estimate |
| xarpus | None needed | None needed | None needed | encounter rule |
| xarpus (npc 10768) | None needed | None needed | None needed | encounter rule |
| xarpus (npc 10772) | None needed | None needed | None needed | encounter rule |
| yama (normal) | Switch: Melee / Magic / Missiles | Switch: Magic / Missiles | Switch: Magic / Missiles | encounter rule |
| yama (phase 3) | Switch: Melee / Magic / Missiles | Switch: Magic / Missiles | Switch: Magic / Missiles | encounter rule |
| zalcano (armoured) | None needed | None needed | None needed | encounter rule |
| zalcano (weakened) | None needed | None needed | None needed | encounter rule |
| zebak (enraged) | Switch: Missiles / Magic / Melee | Switch: Missiles / Magic | Switch: Missiles / Magic | encounter rule |
| zebak (normal) | Switch: Missiles / Magic / Melee | Switch: Missiles / Magic | Switch: Missiles / Magic | encounter rule |
| zulrah (magma) | None needed | None needed | None needed | encounter rule |
| zulrah (serpentine) | Protect from Missiles | Protect from Missiles | Protect from Missiles | encounter rule |
| zulrah (tanzanite) | Protect from Magic | Protect from Magic | Protect from Magic | encounter rule |

## Explicit gaps

An unknown attack record is not interpreted as *None needed*. An unverified special mode is not allowed to borrow the standard boss's timing. Insufficient fire protection is a gear problem in this representative profile (for example, an anti-dragon shield is not a wyvern shield), not a finding that the monster's mechanics are unknown.

| Variant | Gap | Detail |
| --- | --- | --- |
| ancient wyvern | insufficient fire protection | This setup does not meet the selected fire protection requirements with any overhead. Check the shield and antifire potion. |
| armoured foe | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| armoured zombie (the curse of arrav) (1) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| armoured zombie (the curse of arrav) (2) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| black dragon (echo) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| blink (1) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| blink (2) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| blood spawn (entry) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| blood spawn (normal) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| captain cotus | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| captain darvald | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| cerberus (deadman) (annihilation) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| cerberus (deadman) (apocalypse, armageddon) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| cerberus (deadman) (permanent) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| cerberus (echo) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| commander zilyana (deadman) (annihilation) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| commander zilyana (deadman) (apocalypse) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| commander zilyana (deadman) (armageddon) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| commander zilyana (deadman) (permanent) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| commander zilyana (pvm arena) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| cursed baboon | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| dark energy core | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| fremennik warrior | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| general graardor (deadman) (annihilation) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| general graardor (deadman) (apocalypse) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| general graardor (deadman) (armageddon) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| general graardor (deadman) (permanent) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| general graardor (pvm arena) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| giant mole (lanscape) (bandos) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| giant mole (lanscape) (guthix) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| giant mole (lanscape) (saradomin) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| giant mole (lanscape) (zamorak) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| golem (unused npc) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| guard (another slice of h.a.m.) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| guard (port roberts) (1) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| guard (port roberts) (2) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| guard (port roberts) (3) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| guard (port roberts) (4) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| healing totem | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| jal-mejjak | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| k'ril tsutsaroth (deadman) (annihilation) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| k'ril tsutsaroth (deadman) (apocalypse) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| k'ril tsutsaroth (deadman) (armageddon) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| k'ril tsutsaroth (deadman) (permanent) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| k'ril tsutsaroth (pvm arena) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| kalphite queen (echo) (airborne) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| kalphite queen (echo) (crawling) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| kalphite queen (pvm arena) (airborne) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| kalphite queen (pvm arena) (crawling) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| king black dragon (deadman) (annihilation) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| king black dragon (deadman) (apocalypse) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| king black dragon (deadman) (armageddon) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| king black dragon (deadman) (permanent) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| king black dragon (pvm arena) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| knight of ardougne (unused) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| kree'arra (deadman) (apocalypse) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| kree'arra (deadman) (armageddon) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| kree'arra (pvm arena) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| long-tailed wyvern | insufficient fire protection | This setup does not meet the selected fire protection requirements with any overhead. Check the shield and antifire potion. |
| maxilla beast | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| nylocas athanatos (entry) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| nylocas athanatos (normal) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| nylocas matomenos (hard mode verzik) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| nylocas matomenos (normal mode verzik) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| ogre shaman (swan song) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| phantom muspah (deadman) (annihilation) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| phantom muspah (deadman) (permanent) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| pirate (feldip gulf) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| pirate captain (feldip gulf) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| reef snake | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| rowdy guard | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| scarab swarm (tombs of amascut) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| scorpia's guardian | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| skeletal wyvern (1) | insufficient fire protection | This setup does not meet the selected fire protection requirements with any overhead. Check the shield and antifire potion. |
| skeletal wyvern (2) | insufficient fire protection | This setup does not meet the selected fire protection requirements with any overhead. Check the shield and antifire potion. |
| skeletal wyvern (3) | insufficient fire protection | This setup does not meet the selected fire protection requirements with any overhead. Check the shield and antifire potion. |
| skeletal wyvern (4) | insufficient fire protection | This setup does not meet the selected fire protection requirements with any overhead. Check the shield and antifire potion. |
| skeleton (forthos ruin) (1) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| skeleton (forthos ruin) (2) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| skeleton (forthos ruin) (3) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| skeleton (forthos ruin) (4) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| skeleton (forthos ruin) (5) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| skeleton (stronghold of security) (1) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| skeleton (stronghold of security) (2) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| skeleton (stronghold of security) (3) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| skeleton (stronghold of security) (4) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| skeleton (stronghold of security) (5) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| skeleton (stronghold of security) (6) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| skeleton (stronghold of security) (7) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| skeleton (wrath altar) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| sleepwalker (monster) (1) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| sleepwalker (monster) (2) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| sleepwalker (monster) (3) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| sleepwalker (monster) (4) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| sleepwalker (monster) (5) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| sleepwalker (monster) (6) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| sleepwalker (phosani's nightmare) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| sol heredit (deadman) (annihilation) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| sol heredit (deadman) (permanent) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| sparring partner (1) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| sparring partner (10) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| sparring partner (11) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| sparring partner (2) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| sparring partner (3) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| sparring partner (4) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| sparring partner (5) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| sparring partner (6) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| sparring partner (7) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| sparring partner (8) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| sparring partner (9) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| spinolyp (suspicious water) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| spitting wyvern | insufficient fire protection | This setup does not meet the selected fire protection requirements with any overhead. Check the shield and antifire potion. |
| taloned wyvern | insufficient fire protection | This setup does not meet the selected fire protection requirements with any overhead. Check the shield and antifire potion. |
| tztok-jad (deadman) (annihilation) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| tztok-jad (deadman) (armageddon) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| tztok-jad (deadman) (permanent) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| tztok-jad (pvm arena) | unverified special mode | Special-mode attack timing and protection mechanics haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice must not be assumed. |
| volatile baboon | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| warrior of murahs (mace) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| warrior of murahs (scimitar) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| warrior of murahs (spear) | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| well-armed foe | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |
| yeti | unknown attack data | The Wiki attack data for this target can't be read, so check its Wiki page. |

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
