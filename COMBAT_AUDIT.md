# Combat calculations

This page explains how the calculator handles each sustained-attack mechanic, where the formulas come from
and how they're tested. It **doesn't claim that every OSRS mechanic, phase, item state or encounter
rotation is implemented**. The gaps, and what it would take to close them, are listed at the end.

## Evidence

Monsters, equipment, spells, prayers and potions come from bundled Wiki snapshots
(`wiki-monsters.json.gz`, 3,110 variants; `wiki-gamedata.json.gz`, 638 weapons and 1,305 other slot
entries, 48 spells). The plugin makes no network requests. Wear requirements combine cache parameters,
Wiki item text and the reviewed rules, and the live client cache can only raise them; see
[the bundled data notes](docs/equipment-requirements.md). Sources and licences are listed in
[the source notices](THIRD_PARTY_NOTICES.md).

Some encounter rules also recognise variant names from before the Wiki snapshot (such as
"(100% accuracy)" or "(unshielded)" variants). These are harmless aliases: the Wiki variants and the
Boss phase option select the same states.

The main formula reference is the
[OSRS Wiki calculator at revision 89c3e25](https://github.com/weirdgloop/osrs-dps-calc/tree/89c3e25b344aea90d0189746e4b5f73dde0f0383),
especially `PlayerVsNPCCalc.ts`, `BaseCalc.ts`, `dists/bolts.ts` and the scaling modules. Access to
historical equipment was also checked against the
[Raging Echoes equipment list](https://oldschool.runescape.wiki/w/Raging_Echoes_League/Guide/Magic)
and [Drygore blowpipe](https://oldschool.runescape.wiki/w/Drygore_blowpipe).
Matching another calculator is good evidence, but it isn't independent proof from the game itself.

## Calculation order

For ordinary weapons, the model uses the weapon's speed, attack styles, equipment bonuses, requirements
and compatible ammunition. Attack and strength use your boosted visible levels, prayer, stance and the
effective-level constants. Ranged defence picks light, standard or heavy, or the salamander average.
Magic normally uses the monster's Magic level, with the known Defence-based exceptions handled
separately.

The key distinction is between changing the maximum of a roll and changing each hitsplat after it's
rolled. Values are rounded down at every stage where the game does so:

0. Scale raid targets (CoX, ToB, then ToA, as in the reference), attach the selected
   boss phase, apply health-dependent stats (Vardorvis), then pre-fight drains.
1. Build effective levels, summed applicable equipment bonuses, and base rolls.
2. Apply equipment, task, monster-family, spell, and elemental maximum/accuracy
   effects in their documented order. Salve, slayer equipment, and avarice use
   their applicable mutually exclusive branches.
3. Construct each weapon's accuracy and damage distribution, including conditional
   second hits, bounded rolls, and equipment procs.
4. Apply per-roll equipment transformations. A **successful zero becomes one**
   after player-side effects. A miss remains a miss unless an explicit encounter
   rule changes it. Twinflame's secondary hit can remain zero.
5. Apply the monster's division, random cap/reroll, and flat armour **to each
   hitsplat**, then average. Negative armour increases successful damage without
   granting extra damage to ordinary misses.
6. Divide expected attack damage by the mean attack interval. Add compatible
   thrall damage afterward, once on the primary target.
7. When the attack depends on the target's remaining HP, integrate kill time
   `∫ dHP / DPS(HP)` from the selected HP to zero and report that HP divided by the
   time. Breakpoints split the integral (ruby's cap at 500 HP, the Sun keris at a
   quarter of maximum HP); three Gauss–Legendre points per segment evaluate the
   exact per-HP calculation. This is the fluid kill time without overkill.

This avoids mistakes such as multiplying a mean that's already been rounded, capping the total of a
scythe's three hits, or treating two linked hits as if they were independent.

## Weapons and equipment

| Case | How it's calculated |
| --- | --- |
| Ordinary melee weapons, tools, shields, decorative weapons and unarmed styles | Use actual style, speed, accuracy/strength stats and requirements. Bulwark's block stance is not an attack. Non-offensive items can contribute defence/prayer through the existing fill rules. |
| Void and elite void | Effective-level bonuses remain before roll calculation. Elite ranged strength and elite Magic damage use their respective branches. A complete set is seeded in optimization. |
| Slayer helmets/black masks, salve variants and avarice | Preserve exclusions and style-specific eligibility. Atlatl and Hunter's spear damage accept melee task/salve eligibility while accuracy still requires ranged eligibility. Avarice applies to revenants: 24/20 melee/ranged and +20% Magic, or 27/20 and +35% with the optional Forinthry Surge. |
| Obsidian and berserker necklace | Armour adds one tenth of the **base** max, after applicable task/salve effects. Necklace multiplies each rolled melee hit by 6/5; it does not simply create a larger uniform roll. |
| Inquisitor | Crush bonus weights are helm 1, body 2, legs 2, each over 200; so partial sets get a partial bonus. Full set is 2.5%. |
| Crystal bows and armour | Weighted accuracy/damage bonuses apply to ordinary crystal bow/Bow of faerdhinen. Gauntlet bow tiers do not receive ordinary crystal-armour bonuses. |
| Dizana's quiver | Charged quiver, blessed quiver or Dizana's max cape: +10 ranged accuracy and +1 ranged strength only when the weapon fires the equipped arrows or bolts (reference `Equipment.ts`). The Wiki lists the bonus separately from the base stats. |
| Dragon hunter weapons | Lance, crossbow and wand bonuses are style-specific. Crossbow's task and dragon damage bonuses are additive. Wand bonuses also apply when melee bashing. |
| Demonbane weapons | Arclight/emberlight, silverlight/darklight, burning claws and scorching bow use the monster's vulnerability, including Duke, Yama, Void flare and Ice Demon exceptions. |
| Keris family | Kalphite damage uses 133/100, or the amascut variant's 115/100. Breaching adds accuracy. The 1/51 triple roll is averaged before encounter caps. Sun accuracy depends on ToA and the target's health threshold. Inside/outside ToA variants are filtered by encounter. |
| Twisted bow | Clamp the appropriate Magic input and multipliers; apply task damage **before** bow scaling. P2 Wardens apply its accuracy scaling twice before deriving bounded damage. |
| Fang | Stab alone receives the accuracy reroll. Inside ToA, reroll both ordinary accuracy checks; elsewhere, reroll attack against the same defence roll. Bounded 15–85% damage remains on other styles. |
| Scythe | One, two or three independently accurate hits according to target size, each with its own maximum and encounter transformation. Spare AoE hits remain subject to the existing geometry assumptions. |
| Torag's hammers, sulphur blades, glacial temotli, earthbound tecpatl | Split the maximum into floor(max/2) and the remainder, with independent accuracy and per-splat mitigation. |
| Dual macuahuitl | Second attack is conditional on the first accuracy success: its unconditional success probability is accuracy squared. Each splat receives armour/caps separately. |
| Blood Moon | Complete set shortens the mean interval by `accuracy/3 + 2*accuracy^2/9`. Display and DPS use the mean interval; thrall DPS is unaffected by that speed change. |
| Dharok | Complete set transforms every rolled hit by `(10000 + (baseHP-currentHP)*baseHP)/10000`. Uses an explicit health snapshot; defaults to full HP. |
| Verac | Complete set mixes ordinary damage with a 25% guaranteed-accuracy 1..max+1 roll, followed by NPC mitigation. This also adjusts reported accuracy. |
| Hunter's spear | Like the eclipse atlatl, damage uses visible Strength, melee strength bonus and the melee slayer/salve branches; accuracy stays ranged. Candidate pruning keeps melee-strength items for ranged searches when the spear is usable. It stays on the Ranged tab. |
| Ahrim/Karil with amulet of the damned | Ahrim has a 25% per-hit 13/10 damage branch. Karil has a 25% secondary splat at half the first rolled damage. Sets and necklace are seeded together. |
| Guthan, defensive Barrows passives, resource-saving gear and healing jewellery | Their healing, stat restoration, resource saving or defensive benefits don't directly add sustained damage, so they get no made-up DPS multiplier. Benefits to rotations or downtime are outside this model. |
| Bulwark | Defence-derived strength is included, and defensive equipment stats participate in candidate ranking for that weapon. |
| Colossal blade, leaf-bladed battleaxe, barronite mace, granite hammer | Size bonus capped at +10, leafy damage modifier, and distinct golem bonuses apply in the correct family contexts. |
| Gadderhammer | Shade damage mixes the normal 5/4 modifier with the 5% double-damage branch before NPC mitigation. |
| Rat bone weapons | Rat-only legality and the +10 damage bonus are enforced. Scurry rat adds have their separate one-hit rule and rat-weapon speed. |
| Soulreaper | Explicit 0–5 stack snapshot adds boosted Strength separately from prayer; stack buildup and lost HP are not simulated. |
| Wilderness revenant weapons | Charged weapon bonuses apply only when the Wilderness option is selected. Uncharged ranged variants cannot produce a normal ranged attack. |
| Vampyrebane, silver and Efaritay | Tier legality, flail/sickle/stake/rod/sunspear rolled damage, silver accuracy, silver cap and Efaritay reduction branches are shared between scoring and filtering. Degraded/name-suffixed variants are recognized. |
| Dark bow and Tonalztics | Dark bow has two independent full rolls. Tonalztics uses a 3/4 maximum and the charged multi-hit branch. Ordinary special-attack damage remains excluded. |
| Seeking broad arrows | Successful hits deal at least 3 before encounter mitigation (as in the reference); misses are unchanged. |
| Ogre bows and holy water | Ogre bow damage uses visible Ranged +10 and ammunition strength, ignoring normal gear/prayer damage boosts. Holy water is demon-only with its own level/strength formula and Nezikchened bonus. |
| Salamanders | All three attack styles are available with tar. Magic uses the built-in salamander formula; ranged uses tar and mixed ranged defence. Range is one tile for every style. Melee is the aviansie exception. |
| Enchanted bolts | Opal/pearl include damage on misses; diamond/ruby can replace misses with accurate effects; dragonstone/onyx require success and obey their immunities. Zaryte strengths and Kandarin hard diary probabilities apply. Sapphire/jade/emerald status effects and poison are not direct-hit DPS additions here. |
| Ruby bolts | Use current target HP, player HP >=10, normal/Zaryte caps, and the gemstone crab's lower cap. Ruby replacement bypasses Corp halving but still receives later encounter caps such as Zulrah's reroll. Ranking uses the whole-fight integral from full target HP; the displayed max hit and accuracy are the starting state. |
| Venator, chinchompas, Hallowfell and Ancient AoE | Existing target-count/reach rules remain; accurate-zero and per-splat changes also apply to secondary damage. Chinchompa fuse distance affects accuracy. Mixed groups and actual geometry are not simulated. |
| Historical Leagues and custom items | Past Echo rewards are left out of ordinary searches even when they have real cache IDs. Custom, unranked and Fractured Archive entries stay excluded. Supported DMM variants that are listed separately keep their category. This stops the plugin recommending items you can't get just because their stats look good. |
| Gauntlet gear and Dawnbringer | Retained in the data loader, then filtered by encounter. Gauntlet targets accept their activity's gear, and that gear is excluded elsewhere. Dawnbringer is restricted to the supported Verzik P1 context. |

The optimizer keeps equipment with special effects that ordinary stat pruning would throw away, and
seeds sets that work together. Stat pruning only drops an item when another is at least as good in
every damage bonus, no dearer and no riskier under the expensive-item cap. Each equipment change is
judged with its best allowed stance or spell, since some only win together. The initial weapon
shortlist also keeps the relevant set weapons, Twinflame and the Bulwark. It's still a bounded local
search, so it can't prove that a combination is the best possible one under every budget: compared
with an exhaustive search over 6,000 generated small banks, Best depth ends below the optimum in
about 0.1% of them (by at most 1.9% DPS), each needing three or four equipment changes at once.

## Special attacks ("Only special attacks")

| Case | How it's calculated |
| --- | --- |
| Mode | Every result scores its weapon's special attack, repeated at the weapon's speed; weapons without a damaging special are excluded from the candidate pool. Whole-fight HP integration is off because a spec is one hit at the selected HP. |
| Accuracy and max hit | Reference spec multipliers are applied at the reference positions: melee after inquisitor (godswords ×2 accuracy and ×1.1 damage before the per-sword multiplier, each truncated), ranged before the Royal Titans ×6, Magic before the elemental weakness bonus (Eye of Ayak's ×1.3 at base damage). |
| Defence style | Specs roll against their fixed style regardless of stance (dragon dagger/claws/halberds/godswords slash, arclight/emberlight/dragon sword stab, dragon mace/kisten crush, Voidwaker/Saradomin's blessed sword Magic with the Magic level). |
| Hit structures | Dragon claws (4-roll cascade, 1+1 on total failure) and burning claws (3-roll cascade with the reference burn table), dragon dagger/knife/rosewood ×2, abyssal dagger (second hit lands with the first), Crimson kisten (4 accuracy rolls widening one hit), halberds (second hit at ¾ attack roll on size > 1), Saradomin sword (+1–16 magic hit), granite hammer (+5 even on misses), Sunspear (70% hit), dark bow (two arrows, min 5/8 even on misses, cap 48), magic shortbow (two arrows), Webweaver (four hits at 40%), Tonalztics (second throw after the first throw's Defence reduction), Blood Moon dual macuahuitl (raised minimum, conditional second hit, attack-speed proc). Each hitsplat is mitigated separately; inaccurate damage splats skip flat armour. |
| Guaranteed and fixed | Voidwaker (50–150%, mitigated as Magic), magic longbow/comp bow/Seercull (ammunition-only max, always accurate), Dawnbringer (75–150), accursed sceptre and nightmare staves (built-in spell even when autocasting). |
| Bolts and burns | The Zaryte crossbow spec guarantees the bolt effect on accurate hits while misses keep their usual chance. Burning claws, Arkan blade and scorching bow burns are added unless the target is burn-immune. |
| Wiki-page specials | Specials the reference leaves out follow their Wiki pages: Armadyl crossbow ×2 accuracy, abyssal tentacle an ordinary hit (no accuracy bonus on its Wiki page), ursine chainmace ×2 accuracy vs slash, rune claws +10% damage and +1 tick, Dinh's bulwark ×1.2 accuracy vs crush, Statius's warhammer 25–125%, Vesta's longsword 20–120%, Morrigan's throwing axe ×1.5 accuracy and 50–150%, Morrigan's javelin ×1.5 accuracy. Side effects (binds, bleeds, drains, Vesta's quarter-Defence roll, ACB's bolt chance) are not scored. |

## Poison, venom, burns and freezing

Sources: OSRS Wiki [Poison](https://oldschool.runescape.wiki/w/Poison), [Venom](https://oldschool.runescape.wiki/w/Venom),
[Burn](https://oldschool.runescape.wiki/w/Burn), [Serpentine helm](https://oldschool.runescape.wiki/w/Serpentine_helm),
[Emerald bolts (e)](https://oldschool.runescape.wiki/w/Emerald_bolts_(e)), [Smoke spells](https://oldschool.runescape.wiki/w/Smoke_spells),
[Ice spells](https://oldschool.runescape.wiki/w/Ice_spells) and the `infobox_monster` bucket. The Wiki DPS calculator
that the direct-damage formulas follow doesn't model poison or venom.

| Case | How it's calculated |
| --- | --- |
| Poison | Damage floor((S + 4) / 5) every 30 ticks, severity S falling by 1 per hit. Players' poison needs a successful (non-zero) hit: 1/4 melee, 1/8 ranged and smoke spells. Ranged weapon poison is 14 severity lower; emerald bolts and smoke spells are not. A new application resets severity with the same chance as the first. Each hit's expected damage accounts for the chance r = 1 - (1 - q)^(30 / speed) of at least one reset since the previous hit. |
| Poison sources | Weapon poison option (20/25/30 severity) on poisonable daggers, spears, hastae, metal darts/knives/javelins/arrows/bolts; abyssal tentacle (20, 1/4); swamp lizard melee (30, 1/4 assumed); emerald bolts (e) 55% × Kandarin diary, 25 or 27 with the Zaryte; smoke rush/burst 10, blitz/barrage 20, ×1.1 with any ancient sceptre. Neither catalogue lists poisoned copies separately (the Wiki snapshot folds them into the base item), so the option stands in for them. |
| Venom | 6, 8, ... 20 per 30 ticks, never wearing off; venom replaces poison on the shared timer (any earlier poison is ignored). Toxic blowpipe, trident of the swamp, toxic staff of the dead 25% (100% with serpentine helm against NPCs); noxious halberd 33%/50%; serpentine helm alone 1/6 with unpoisoned melee, 1/2 with poisoned weapons, nothing with unpoisoned ranged/magic per the Wiki; emerald bolts do not count as poisoned. Venom-immune but poison-susceptible targets take severity-26 poison instead. |
| Burns | 1 damage per stack every 4 ticks for 10 hits (scorching bow special: 5), at most 5 stacks with extras discarded (expected via a Poisson cap on active stacks). Burns are ranged damage: ranged-immune targets take none and ranged reductions apply. Severity immunity: weak, normal (Cerberus, red Hydra) and strong (Zuk, Moons, Mad Angel, Gemstone Crab...) from the Wiki data plus the reference's Tekton/Dusk/crystal/cyclops list. Eclipse set burns are strong (20% of successful atlatl hits); burning claws and scorching bow special burns are normal, Arkan blade strong. The scorching bow special fails against non-demons, so it is not scored there. |
| Kill integration | Attacks at 0, speed, 2 × speed...; first application per source is geometric; poison/venom hits follow the target's own 30-tick timer with a uniformly random phase, burns a 4-tick timer. Kill time T solves direct damage per tick × T + expected damage over time by T = target HP (bisection); the reported DPS is HP / T. Targets with unknown or infinite HP use the steady-state rate. Only the primary target is poisoned; AoE secondaries are not credited. |
| Immunities | Monster `immune_poison`/`immune_venom` flags are unreliable (Akkha, Alchemical Hydra and the Dagannoth Kings would read as susceptible), so a bundled Wiki snapshot (3,255 variants, 4,137 NPC ids) is consulted by NPC id, then base name, combined with the monster's own flags. |
| Ice ancient sceptre | +10% attack roll for ice spells against freezable, unfrozen targets. A landed cast freezes for 8/17/26/35 ticks (with the sceptre's +10%), reduced by partial freeze resistance, then 5 immune ticks; the casts in that window get no bonus, and later casts get it until one lands. Mean accuracy = share × bonus + (1 - share) × normal, share = (1/bonus) / (1/bonus + casts in window). Freezability: Wiki freeze resistance below 100%; without data, non-bosses only. |

## Magic

| Case | How it's calculated |
| --- | --- |
| Elemental strike/bolt/blast/wave/surge | Every element in a tier scales to the highest elemental spell unlocked at the boosted Magic level. Search uses boosted spell eligibility. |
| Elemental weakness | Read `weakness_type` and `weakness`; add weakness-scaled **base** attack roll and **base** spell maximum. Do not multiply all equipment bonuses by the weakness. |
| Powered staffs | Support the verified built-in formulas, their minimums, accurate stance +2, and activity staff fixed maxima. Unknown built-in formulas return no supported Magic score. |
| Tumeken's shadow | Multiply gear accuracy and magic damage by 3, or 4 in ToA. Cap the amplified equipment damage at 100%; prayer damage remains separate. DMM canonicalization also reaches this predicate. |
| Smoke staff, Magic salve, task equipment and Virtus | Smoke's standard-spell bonus and Magic salve damage are additive with equipment damage; task damage applies afterward. Ancient spells receive Virtus's additional per-piece damage. |
| Magic Dart | Level-dependent maximum; enhanced slayer staff on task uses its distinct formula. |
| Chaos gauntlets, elemental amulets and Charge | Modify base spell damage only for eligible spells. Charge requires the matching god cape. These are explicit fight assumptions, not always-on bonuses. |
| Charged elemental tomes | Matching standard spells receive the damage bonus. Water's accuracy bonus also requires a charged tome. Empty/uncharged variants do not receive those effects. |
| Sunfire | Eligible fire spells get a minimum floor(max/10) before tome damage. |
| Demonbane and Purging staff | Mark of Darkness changes accuracy and applies per-roll damage, scaled by demon vulnerability; Purging doubles the supported Mark modifiers. |
| Brimstone and Confliction | Mix the 25% reduced-defence branch with normal Magic accuracy. Confliction uses its one-handed formula in both branches. |
| Sanguinesti | Model the current reference's 20% +8 damage branch before accurate-zero/NPC mitigation, distinct from its healing benefit. |
| Ahrim/Twinflame/Harmonised | Ahrim's proc requires its set/necklace. Twinflame's second bolt/blast/wave splat uses 40% of first damage and six ticks. Harmonised standard spells use four ticks. |

## Monsters, bosses and phases

| Case | How it's calculated |
| --- | --- |
| Ordinary monster variants | Use the selected record's supplied stats, attributes, ranged defence split, size and elemental weakness. Copying/draining preserves those fields; replacing attributes invalidates the derived cache. |
| Flat armour | Read the `flat` key, with `flat_armour` as an alternate. Apply it to each non-Magic splat, including negative armour on Amoxliatl/Dusk/Moons and positive armour on zombies/drakes. |
| Warden P2/core/P3 | Named variants take priority over IDs. P2 has guaranteed accuracy and attack-roll-dependent damage bounds. Core's supported melee maximum is separate. Phase IDs are not reused across core/P2/P3. |
| Perilous Moons | Blue/Blood/Eclipse armour is applied per splat. The Eclipse clone, matched by name, has melee-only, guaranteed-accuracy, armour-4 behaviour. |
| Vardorvis | Quest, post-quest and Awakened defence/strength interpolate from health before drains; defence cannot be drained further. The search supplies the state at every HP and integrates the whole fight. Unknown/Echo modes keep supplied stats. |
| Chambers of Xeric scaling | Every `xerician` target: offensive and defensive levels by the highest party HP level and party size, HP by highest combat level and party size, with the reference clamps. Magic is defensive for Tekton, Olm hands, Vespula, the portal, vespine soldiers and deathly rangers. Challenge Mode adds 50% (Tekton defence +20%/+35%, crystal unscaled). Scavengers and vespine soldiers use solo scaling. Olm HP is 800/600 + 400/300 per effective extra player, mage-hand Magic halved; guardians use 151 + average Mining. Levels of 1 stay 1. Solo at 126 combat/99 HP leaves the base stats unchanged. |
| Theatre of Blood scaling | HP only. Normal/hard: × (party + 3)/8 with party clamped to 3–5. Entry/story: 10, 19, 27, 34, 40 /40 for 1–5 players. Variant names decide the mode because some IDs are shared between modes. |
| Tombs of Amascut scaling | Boss and path HP: +0.4% per raid level (core-ejected 4,500 at +0.1%), path +8% then +5% per level, party +90%/+180% then +60% each, rounded to 5/10. Defence rolls are multiplied by (250 + raid level)/250 except Kephri's overlord scarabs. Puzzle-room NPCs (baboons, agile scarab) keep their base stats, as in the reference. Fang, Shadow and Keris ToA rules use the scaled roll. |
| Boss phase input | Hueycoatl head/tail with pillar: each outcome × 13/10 after the tail's cap and minimum (body excluded). Royal Titans out of melee: ranged attack roll × 6. Abyssal Sire transition: per-hit ÷ 2. Mokhaiotl shielded: only demonbane deals damage and always hits; burrowing always hits. A phase never alters other targets and is reported as ignored. |
| Named phase variants | Names containing "(100% accuracy)" always hit. Enraged/mage-tank/range variants already carry their stat changes, so the reference phase stat changes are not applied again. |
| Royal Titans elementals | Magic accuracy is min(1, magic attack bonus/100 + 0.3), × 1.45 with void mage (capped), and successful hits are maximum. |
| Tormented Demon | Shielded and defenceless-shielded variants retain 20% mitigation except eligible demonbane/abyssal attacks. Unshielded is accurate and eligible crush/heavy/cast-spell attacks gain speed-squared-minus-16 rolled damage and a one-tick shorter interval. Powered spells do not get that bonus. |
| Zulrah, Seren, Verzik P1 and Huey tail | Exact random per-hit caps/rerolls are averaged. Huey crush requires a strictly higher crush bonus than stab/slash; its minimum one also applies to misses. Earth spells have the larger cap without the crush minimum. |
| Nightmare totems | Double Magic damage. Totems are recognised by name rather than applying Wiki totem IDs indiscriminately. |
| Corp | Qualifying stab weapons/Magic avoid halving; other rolls are halved individually. Ruby has its separate replacement order. |
| CoX Olm, Tekton, Vasa crystal, Ice Demon and guardians | Existing off-style divisions/immunities remain. Ice Demon permits fire and demonbane exceptions. Guardian damage requires a melee pickaxe and uses Mining plus pickaxe tier. |
| Aviansies, flying targets, Kraken, Zuk, Leviathan and other reach restrictions | Shared calculator/search legality and distance checks. Aviansies reject halberds and permit melee salamanders. Auto Zuk distance covers shield corners. Auto Vespula distance is 7 tiles, and the abyssal portal always needs 7-tile reach. Unknown ranged reach cannot be assumed to safespot. |
| Kurasks/turoths, vampyres and quest targets | Required leaf weapons/ammo/Magic Dart, vampyre-tier weapons, ice arrows for Fire Warrior, and Fareed's spell/ammunition restrictions are enforced. |
| Acidic/Mirrorback araxytes | Supported weapon and highest-accuracy-bonus conditions receive guaranteed maximum damage; other attacks retain ordinary scoring. |
| Respiratory system | Ordinary supported attacks have a raised minimum; demonbane attacks have their one-hit-kill branch with ordinary accuracy. |
| Scurrius rat adds and other one-hit targets | Use the target's HP as damage, and the supported rat-weapon one-tick interval. |
| Baboon/Fremennik style weaknesses, Void flare and Judge | Supported class-specific guaranteed maximum damage is distinct from ordinary accuracy bonuses. |
| Defence-drain floors and absorption | Preserve full immunity for supported absorption targets, while respecting Akkha's distinct drain floor despite the API's broad tag. Restore known boss floor fallbacks when the API supplies zero. BGS drain stops at a positive floor; Ayak cannot push positive Magic defence below zero or raise existing negative defence. Arclight includes its +1. |

## Remaining adjustments and limits

These limits can noticeably change results:

| Not modelled | What full support would need |
| --- | --- |
| Raid party composition | CoX uses your own combat/HP levels as the party's highest and the Mining option as the party average. Other members' levels would need separate inputs. Raid NPC offensive levels are scaled, but supplied attack max hits are not rescaled, so incoming-damage checks use supplied values. |
| Health changing through the fight | Target-HP effects are integrated as a fluid kill time. Overkill and the discrete hit distribution are not modelled, so very low-HP targets and large single hits can differ from the reference `getTtkDistribution`. Player-state effects (Dharok's missing HP, Soulreaper stacks) remain explicit snapshots because they depend on incoming damage and the rotation. |
| Boss transitions, protection prayers and scripted timing | Single states are selectable (variants or Boss phase), but rotations between them, attackable windows, healing, Olm immunity, Hydra vents, Araxxor enrage timing, Yama's Glyphic Attenuation and Huey pillar uptime are not weighted. The Mad Angel's Sword Cleave/Perfect Lightning and the Maggot King's Melee Punish need first-hit distribution changes and unambiguous variant mappings; both are left unmodelled rather than approximated. |
| Special-attack rotations | Normal searches mix one spec weapon (with an off-hand after a two-handed main weapon, and its own ammunition when the worn ammunition doesn't suit it; ammunition is chosen first, then the off-hand) into the kill (`SpecialRotation`). Damage specs take the expected-value share of fight time that regenerated energy (10% per 30 seconds, doubled by lightbearer) pays for, plus the whole specs in a full bar when chosen. Draining specs open the kill: every hit/miss sequence is weighed, each spec rolling at the stats left by the earlier ones, and the kill finishes with ordinary attacks at the drained stats; with regeneration only, the opening uses one kill's regenerated energy (a fixed point, fractional specs mixing the neighbouring whole counts), up to a full bar; energy regenerated beyond that during a long kill is spent mid-fight for damage, as after a full-bar opening. A landed spec's drain uses its expected damage given a hit; Tonalztics drains once per landed special; drains from specs regenerated mid-fight are not applied. The Drain specs setting can rank on that expected value, on damage alone, or on the all-miss worst case. Weapon-switch timing is not modelled. Spec-only mode scores the special itself. Pre-fight landed stat drains remain a separate option. The bludgeon assumes full Prayer; Ancient godsword's blood sacrifice and the Sunspear finisher are excluded, as are underwater-only Brine sabre and specials neither the reference nor the weapon's Wiki page defines. |
| Statuses beyond the model | Damage over time uses expected values (mean field), not a joint kill-time distribution, and gives no damage to AoE secondaries. Poison applied before venom is ignored once venom is possible. Multi-hit attacks get one application chance per attack. Projectile delay, the out-of-combat venom pause, NPC healing, smoke sceptre heal reduction, sapphire/jade/topaz bolt statuses (PvP-only or non-damaging), recoil and player-inflicted bleed (none exists) are not modelled. The serpentine helm's ranged/magic behaviour follows the Wiki's wording; freeze immunity without Wiki data is inferred from the boss attribute. |
| Healing, sustain, defensive prayers and resource saving | Model incoming attacks, healing, and time lost to eating, movement and banking, to turn sustain into an effective kill rate. Plain offensive DPS doesn't count these benefits. |
| Historical Leagues profiles | A supported profile would need relic/mastery inputs, Echo item passives, modified speeds/accuracy, variant access and the full set interactions. Historic gear is currently excluded from ordinary search. |
| Unknown/new/synthetic equipment formulas | Obtain a primary formula and independent regression vectors before adding a powered attack or passive multiplier. The row-level inventory flags Nature's reprisal, Nature's recurve, Drygore, Ice ancient sceptre, Infernal tecpatl and Shadowflame cases; most synthetic/historical entries are excluded from ordinary search. |
| Quest, skill, diary and environmental access | The optimizer uses provided equipment requirements and explicit options, not a complete account progression/environment model. Mount Karuulm protection, task/quest access, underwater rules, finisher items and NPC scripting may require additional eligibility inputs. |
| Overkill, ammo cost per kill, accuracy vs TTK optimization | Add full hit/kill-time distributions and consumable accounting. Expected DPS optimization and current ammo budget counts are not equivalent to minimum real kill time or operating cost. |
| Rare simultaneous proc/phase interactions | Independently validate complex combinations, including Brimstone with Warden bounds, spell procs with TD/phase overrides and forced-max multi-hit distributions. Existing exact vectors cover common interactions, not the complete combinatorial space. |

## Validation and reproduction

`./gradlew build` (JDK 11, no network) runs the unit tests:

- Exact numeric and enumerated regression cases cover modifier order, Fang styles, ToA rerolls,
  conditional hits, Barrows and Moon sets, magic weakness, tomes, the Shadow, bolts, HP thresholds,
  mitigation, health scaling, drain floors and access rules.
- `RaidScalingTest` has hand-worked cases from the reference scaling modules for every CoX, ToB and ToA
  branch. `WholeFightTest` checks the ruby and Vardorvis integrals against a brute-force sum over every HP
  value (within 0.2%; the Sun keris breakpoint is exact). `ReferenceParityTest` covers the 295 named
  equipment and monster special cases taken from `PlayerVsNPCCalc.ts` and `BaseCalc.ts`.
- `CombatCatalogTest` sweeps the bundled Wiki catalogue: every weapon style against representative
  targets, five weapons against every monster variant, special-attack mode for every weapon, and budget,
  whole-fight, raid-scaled and damage-over-time searches. These check that results are finite,
  non-negative and bounded, and that budget and access rules hold; they **don't independently verify
  every formula**.
- `python tools/reference_similarity.py` compares the calculator code with the GPL-3.0 reference, to make
  sure none of it is a translation; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
- The build only applies the Java plugin, so there are **no Checkstyle or PMD tasks**. The tests don't
  cover the UI or in-game combat.

To regenerate the bundled data, follow [the bundled data notes](docs/equipment-requirements.md).
