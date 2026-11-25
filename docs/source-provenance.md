# Calculator source-provenance review

Reviewed 7 October 2026, against plugin commit
`7bc6e7052f58d21ae7f6d23b7e100e1dd33fc7ab` and OSRS Wiki DPS calculator revision
`89c3e25b344aea90d0189746e4b5f73dde0f0383`. Line numbers below refer to those versions.

## Result and scope

This scoped engineering review found **no confirmed copied GPL routine or substantial copied comment**
in the inspected passages. The similarity flags do not establish a GPL distribution problem. Shared
mechanics, arithmetic and constants must be distinguished from copied implementation text; changing
names or language alone would not establish independent authorship.

The review covered every one of the checker's 35 code flags and three comment flags, plus the complete
raid-scaling implementation and selected corresponding routines for hit chance, enchanted bolts, claws,
defence drains, Vardorvis health scaling and damage averaging. It did not reconstruct the original
development process or examine every calculator routine. The repository has one initial commit, so its
history cannot independently demonstrate how the implementation was first written. This is a bounded
source comparison, not a certification of independent authorship or a legal opinion.

The reference is GPL-3.0 at the [reviewed revision](https://github.com/weirdgloop/osrs-dps-calc/blob/89c3e25b344aea90d0189746e4b5f73dde0f0383/LICENSE).
All 33 cached TypeScript library, enum, type and utility files selected by the checker were compared with
the GitHub tree at that revision. Their Git blob hashes matched, and the tree was not truncated. The
reference files reside in ignored `build/combat-reference/dps-calc`; they are neither runtime
dependencies nor JAR resources. `build.gradle` and the built JAR were checked for that boundary.

The [U.S. Copyright Office's Circular 61](https://www.copyright.gov/circs/circ61.pdf) distinguishes
copyrightable program expression from functional algorithms and logic. The
[GPL text](https://www.gnu.org/licenses/gpl-3.0.html) governs covered works; attribution alone does not
provide permission to redistribute covered code under BSD. These principles guide the distinction in
this review; they do not decide whether any particular historical implementation is derivative.

## How to interpret the checker

Run `python tools/reference_similarity.py` from the repository root. It is a triage aid:

- Names and member-access expressions become the same token, strings lose their text, and punctuation
  and some language constructs are discarded. Semantically unrelated code can consequently match.
- Each eight-token window is indexed to its first reference occurrence. Neighbouring windows matching
  different files or unrelated positions are merged into one plugin-side region. The displayed token
  count is **not** the length of a contiguous matching routine, and the displayed reference location is
  only an example window's position.
- Comment flags compare unordered word sets. Shared subject vocabulary can trigger them without a
  copied sentence.
- A refactored translation can escape the checker. An empty result would not prove independence.

An additional comparison found no identical whole calculator comment of nine or more alphabetic words
after case and punctuation normalization. This does not exclude shorter copying or edited prose.

## Disposition of all code flags

`Pattern` means the displayed pair contains unrelated operations or ordinary control-flow syntax.
`Mechanic` means a short shared calculation or game value is visible, without a matching reference
implementation surrounding it. These labels describe the inspected evidence, not the author's history.

| # | Plugin passage | Reported reference passage | Disposition and evidence |
| --- | --- | --- | --- |
| 1 | `SpecialAttack.java:102-141` | `BaseCalc.ts:97` | Pattern: immutable special-attack constructors versus detail-tracking helpers; no matching routine. |
| 2 | `DpsCalculator.java:966-984` | `PlayerVsNPCCalc.ts:2378` | Pattern: Magic scoring, Brimstone and encounter bounds versus HP-dependent distribution recalculation. |
| 3 | `Optimizer.java:1534-1548` | `EquipmentCategory.ts:5` | Pattern: candidate-stat arrays and nested iteration versus a weapon-category enum. |
| 4 | `DpsCalculator.java:124-139` | `NPCVsPlayerCalc.ts:49` | Pattern: player attack dispatch versus choosing a player's defensive bonus; ordinary style-switch syntax. |
| 5 | `DpsCalculator.java:716-726` | `claws.ts:31` | Pattern: ranged double-hit scoring versus the dragon-claw hitsplat cascade. |
| 6 | `DpsCalculator.java:949-961` | `DefenceReduction.ts:74` | Pattern: sunfire/tomes/Dawnbringer versus stat-floor clamping. |
| 7 | `SpecialDamage.java:312-329` | `NPCVsPlayerCalc.ts:208` | Mechanic/pattern: ordinary max-hit arithmetic shares game constants; adjacent built-in-staff branches do not match the NPC routine. |
| 8 | `StatusEffects.java:54-69` | `DefenceReduction.ts:158` | Pattern: status-source checks and serpentine identity versus Eye of Ayak's Magic-defence drain. |
| 9 | `DpsCalculator.java:533-539` | `EquipmentCategory.ts:37` | Pattern: crystal-bow identification and armour weights versus category names. |
| 10 | `DpsCalculator.java:222-230` | `PlayerVsNPCCalc.ts:470` | Pattern: special-attack defence choice and ordinary max hit versus Fang's minimum-hit branch. |
| 11 | `SpecialRotation.java:213-228` | `HitDist.ts:518` | Pattern: expected kill remainder and regeneration versus distribution transforms. |
| 12 | `DpsCalculator.java:1046-1054` | `Spell.ts:25` | Pattern: freeze eligibility and sceptre accuracy versus elemental spell-tier lookup. |
| 13 | `Optimizer.java:1054-1058` | `EquipmentCategory.ts:37` | Pattern: lightbearer/budget trial scoring versus category names. |
| 14 | `Optimizer.java:1158-1159` | `PlayerVsNPCCalc.ts:384` | Pattern: counts of unpriced equipment versus crystal armour weights. |
| 15 | `HitDamage.java:23-31` | `HitDist.ts:518` | Pattern: summing transformed rolls versus constructing a distribution transformer. |
| 16 | `DpsCalculator.java:916-924` | `PlayerVsNPCCalc.ts:470` | Pattern: demonbane accuracy multipliers versus Fang's minimum hit. |
| 17 | `SpecialDamage.java:293-303` | `MonsterAttribute.ts:22` | Pattern: binomial coefficients and probability accumulation versus a vampyre attribute predicate. |
| 18 | `DpsCalculator.java:48-56` | `claws.ts:11` | Pattern: HP integration breakpoints versus claw-roll probabilities. |
| 19 | `DpsCalculator.java:870-875` | `PlayerVsNPCCalc.ts:827` | Mechanic: effective Magic level times gear bonus plus 64; common base-roll rule. The plugin combines accuracy and damage, unlike the reference's separate routines. |
| 20 | `DpsCalculator.java:303-310` | `PlayerVsNPCCalc.ts:436` | Pattern: silver/dragon-hunter accuracy versus demonbane and leaf-weapon damage. |
| 21 | `DpsCalculator.java:340-344` | `PlayerVsNPCCalc.ts:834` | Pattern: inquisitor armour weights versus Magic avarice/Salve bonuses. |
| 22 | `DpsCalculator.java:1008-1013` | `PlayerVsNPCCalc.ts:580` | Pattern: expected Magic damage and Mark of Darkness versus ranged multipliers/distance. |
| 23 | `DpsCalculator.java:323-329` | `PlayerVsNPCCalc.ts:239` | Pattern: golem weapon bonuses versus avarice/Salve/Slayer stacking. |
| 24 | `EnchantedBolts.java:39-42` | `bolts.ts:118` | Mechanic: ruby bolt caps and percentage of target HP. Java directly mixes mean damage and encounter rules; reference creates weighted hit distributions. |
| 25 | `DpsCalculator.java:1218-1224` | `BaseCalc.ts:251` | Pattern: quiver/ammunition bonus versus full void armour checks. |
| 26 | `EnchantedBolts.java:78-80` | `bolts.ts:44` | Mechanic: pearl bolt activation rate and fiery-target/Zaryte divisors. The short arithmetic agrees; the enclosing implementations differ. |
| 27 | `SpecialAttacks.java:90-94` | `DefenceReduction.ts:101` | Mechanic: repeated elder-maul/warhammer percentage drains. Java accumulates scalar levels then publishes a copy; reference updates immutable monster records through a helper. |
| 28 | `DpsCalculator.java:278-284` | `PlayerVsNPCCalc.ts:855` | Pattern: melee damage/Keris accuracy versus the aggregate Magic accuracy percentage. |
| 29 | `DpsCalculator.java:629-638` | `PlayerVsNPCCalc.ts:414` | Pattern: ranged flat bonuses and Tonalztics versus melee dragon-hunter/Keris bonuses. |
| 30 | `SpecialDamage.java:252-254` | `claws.ts:144` | Pattern: direct claw hitsplat bounds versus a burn truth table. Related weapon, different computation; see the separate claw review below. |
| 31 | `EquipmentEffects.java:135-139` | `PlayerVsNPCCalc.ts:165` | Pattern: silver-weapon per-hit damage versus special-attack defence-style selection. |
| 32 | `SpecialAttacks.java:86-90` | `DefenceReduction.ts:101` | Mechanic/pattern: vulnerability followed by elder-maul drain; percentage arithmetic and loops, not a copied surrounding helper. |
| 33 | `DpsCalculator.java:528-532` | `PlayerVsNPCCalc.ts:541` | Mechanic: ranged void scaling and base attack roll. The plugin also computes ranged damage using separate strength/atlatl inputs. |
| 34 | `StatusEffects.java:384-387` | `claws.ts:160` | Pattern: capped Poisson burn stacks versus weighting first-success claw rolls. |
| 35 | `StatusEffects.java:363-366` | `claws.ts:160` | Pattern: stationary poison severity expectation versus weighting first-success claw rolls. |

## Disposition of all comment flags

| Plugin comment | Reference comment | Assessment |
| --- | --- | --- |
| `AttackReach.java:56` | `constants.ts:236` | Both identify Vespula's abyssal portal in CoX. Reference describes ID classification and scaling; plugin documents encounter identity. No copied sentence found. |
| `RaidScaling.java:41` | `Monster.ts:88` | Both describe average party Mining. Plugin documents its configuration input; reference documents a deprecated data field. No copied sentence found. |
| `SpecialAttack.java:45` | `PlayerVsNPCCalc.ts:1450` | Both describe defence reduction from the first Tonalztics hit affecting the second. Plugin's brief enum description does not reproduce the reference's subcalculator explanation. |

## Review beyond checker flags

### Chambers of Xeric

`RaidScaling.java:177-355` was compared with the entire reference
[`ChambersOfXeric.ts`](https://github.com/weirdgloop/osrs-dps-calc/blob/89c3e25b344aea90d0189746e4b5f73dde0f0383/src/lib/scaling/ChambersOfXeric.ts).

The numerical rules agree: linked offensive/defensive levels excluding level 1; guardian HP from Mining;
party and player-level factors; separate scavenger/vespine scaling; Challenge Mode and Tekton/crystal
exceptions; clamps; and Olm's head/hand HP, phase-size adjustment and mage-hand Magic reduction.
Those agreements are expected because the reference supplies these rules. This review did not verify
all of them through independent game measurements.

The implementation comparison is more specific than "different language":

- Reference builds arrays of skill keys, returns a metadata object and repeatedly produces immutable
  monster records. Java finds scalar maxima, produces an integer result array and assigns the selected
  levels to a copied `Monster`.
- Reference uses separately exported NPC-ID groups and transformation functions. Java combines
  numeric IDs with names and attributes, and dispatches three raids through one settings object.
- Java expresses percentage and square-root factors in small helpers; both necessarily retain the
  same arithmetic and truncation order. The same branch decisions are not evidence of independence
  by themselves.

No distinctive reference prose or concrete translated routine was identified in this comparison.
However, source comparison cannot resolve historical authorship, and the reference-derived rule set
must remain attributed. Do not describe the CoX rules as independently verified or erase attribution
merely because the implementation differs.

### Other reference-derived mechanics

The previous notice's "one exception" wording was incomplete. The following dependencies on the
reference are visible in the inspected code or audit:

| Area | Retained reference information | Implementation assessment |
| --- | --- | --- |
| ToB entry scaling | HP fractions 10/40, 19/40, 27/40, 34/40 and 40/40, plus fallback NPC IDs | Java uses an indexed fraction array and name-first raid classification; reference uses a keyed fraction map and ID groups. |
| ToA | Core HP/scaling, rounding details and target classification | Java combines HP scaling with a stored defence-roll multiplier and named-target predicates; reference's HP transform returns a new monster record. Some values lack separate primary evidence in this review. |
| Burning-claw burns | First-two-burn overlap removes one damage, an observation credited to the reference authors | Java derives the expectation as `30p - p^2`; reference enumerates eight burn combinations. The observation is retained, not its truth-table implementation. |
| Encounter classifications | Examples include burn-immunity membership, raid/phase NPC IDs and defence-floor values | ID membership and numeric mechanics are reference-derived; no copied implementation was identified in the inspected predicates. |
| Regression coverage | Named special cases used to choose tests | `ReferenceParityTest` contains Java setups and hand-worked expected values. Reference test sources were not included in the checker corpus; this was not an exhaustive comparison of test-file provenance. |

For claws, Java uses tables of hitsplat splits and directly aggregates mitigated expected damage and
maximum; the reference builds weighted distributions through per-roll switch statements. Both retain
the same first-success probabilities, roll bounds and hitsplat rules. For enchanted bolts, Java mixes
the on-hit/on-miss expectations directly; reference composes distribution transformers. For hit chance,
Java sums clamped integer powers over possible defence rolls, rather than adopting the reference's
piecewise normal/Fang formulas. For Vardorvis, numeric endpoints agree but Java uses explicit
interpolation and variant checks instead of a range object and shared interpolation helper.

## Submission implication and maintenance

The inspected evidence does not establish a GPL-expression blocker to a Plugin Hub review submission.
Retain the accurate reference disclosures, the existing Wiki-data licence and all existing RuneLite
notices. Neither numeric parity nor a passing similarity script should be advertised as proof that all
code was independently authored. This review also does not replace gameplay testing.

When mechanics change, record the source of the rule separately from the implementation: a Wiki page,
game observation, reference observation, or an authored derivation. Re-run the checker and inspect each
new flag. If actual copied or translated protected implementation is identified, resolve its permission
or replace it from a documented specification; cosmetic renaming does not resolve provenance.

No calculator behavior was changed during this review. Documentation was corrected to describe the
reference-derived mechanics and the checker's limitations accurately.

Validation after those changes: the Java 11 wrapper build passed (463 tests passed, two benchmarks
skipped, no failures); all ten Python data-tool tests and the plugin manifest check passed;
`git diff --check` passed. The rebuilt JAR's notices matched the repository file byte for byte, and no
TypeScript or reference-library paths were present in the archive. The checker's wording changed, but
its matching algorithm and all 35 code/three comment flags were preserved. Changes remain local and
uncommitted; the previously successful GitHub workflow does not yet validate these documentation edits.
