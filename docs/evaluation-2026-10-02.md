# Best Gear Setup usability and accuracy evaluation

Date: 2026-10-02. Revision tested: `ea74ece` (working tree initially clean).

## Status and scope

**Logged-out and logged-in bank-data evaluations complete. Accuracy defects remain.**

Tested the actual local plugin in RuneLite 1.13.1 using Java 11, first at the logged-out welcome screen and then after logging in and opening the bank. Inspected the bank and account levels, and retested five representative monsters with owned data. No purchases, withdrawals, deposits, equipment changes, consumable use, or code fixes were performed. This report records observed behavior, source inspection, and automated checks; it does not certify every combat formula or a globally optimal result for every monster.

The principal accuracy defects are missing mandatory face protection for dust devils and ignored owned ammunition quantities when calculating purchases. Excluding a weapon also permits an equivalent cosmetic variant to return. The general search, version selection, bank recognition, account levels, locks, manual ownership, spell availability, and raid scaling worked in the sampled cases. Owned-only mode correctly refused to invent a usable setup before bank data was available. Explicit potion selections are documented scenario overrides, but the live UI needs clearer warnings when they contradict ownership or encounter restrictions.

## Findings

### P1 — Dust devil recommendations omit mandatory face protection

Reproduction:

1. While logged out, select All monsters and search Dust devil.
2. Select Smoke Dungeon, level 93; leave On slayer task unchecked.
3. Use Owned + budget mode with a 10m budget and no owned data; leave AoE disabled.
4. Find best setup and inspect the recommended head slot.

Observed: the leading Atlatl setup used **Eclipse Moon Helm**, confirmed by its item context menu. Displayed DPS was 9.018, max hit 33, estimated TTK 11.6 seconds, cost 8.36m. Enabling AoE also produced an Ice Barrage setup with a hood rather than face protection.

Expected: a facemask or suitable slayer helmet is required even when killing dust devils off task. Otherwise the player suffers rapid damage and stat drain, invalidating both practical usability and the displayed combat estimate. The [Wiki strategy guide](https://oldschool.runescape.wiki/w/Dust_devil/Strategies) explicitly describes the requirement.

Source inspection: `calc/CombatRules.java:109` restricts activity-specific equipment but has no dust-devil head protection rule. `calc/Optimizer.java:1098` delegates equipment permission to those rules. A valid full loadout must enforce the required head slot, including owned-only, budget, locks, alternatives, and AoE paths. A warning alone would still leave the advertised usable setup incorrect.

Owned-data reproduction: off task, the leading Dual Macuahuitl setup used **Blood Moon Helm**, at 9.195 DPS and 44.16m total value with zero to buy. Off-task AoE used unprotective blue headgear and Ice Burst, at 35.509 total / 3.945 primary DPS. Turning On slayer task on selected a Slayer Helmet for single-target combat and **Slayer Helmet (i)** for AoE. The imbued helmet was independently confirmed in the bank. Protection is therefore available on this account, but the off-task searches do not enforce it.

### P2 — Owned ammunition quantities are ignored, understating purchases

Reproduction with the logged-in bank:

1. Select Zebak, raid level 300, party size 1 and path level 0.
2. Select Owned items only, set Ammo quantity to **100,000**, and inspect the ranged result.
3. Confirm the recommended Ruby bolts (e) stack in the bank: **159 bolts**. Inventory and worn equipment were empty.
4. Switch to Owned + budget with a **zero budget**, retaining the requested ammunition quantity.

Observed in both modes: the ranged setup used Ruby bolts (e), displayed 3.953 DPS and 323.8s TTK, and reported **22.2m total value / 22.2m owned value / 0 to buy**. The shortage of **99,841 bolts** was not represented, and zero budget did not reject or flag it.

Source inspection: `OwnedItems.java:84–91` retains a set of canonical item IDs after testing only whether quantity is positive. It discards stack quantities. `calc/Optimizer.java:1311–1323` returns zero purchase cost for any owned item, then multiplies that zero by the requested ammunition quantity. Consequently, owning one unit makes the entire requested stack free in the purchase calculation.

Impact: the user's specified supply quantity cannot be fulfilled by the reported zero-purchase setup. Ownership icons may correctly indicate possession of the item type, but the budget and missing-purchase estimate are incomplete. Preserve quantities across bank, inventory and worn containers, and price the shortfall between requested and owned ammunition. Manual ownership entries also need a defined quantity policy. This finding does not establish how many bolts a particular fight consumes; it concerns the explicit quantity entered by the user.

### P2 — Excluding a weapon can immediately recommend its equivalent variant

Reproduction: select post-quest Vorkath, no-limit mode, Super antifire, Require fire protection enabled, Protect from Magic disabled. Exclude Dragon Hunter Crossbow using the weapon context menu.

Observed: the plugin immediately recommended **Dragon Hunter Crossbow (B)**, confirmed by its context menu, with the same 11.164 DPS and 212.56m + 1 unpriced cost. The exclusion list contained the base crossbow only.

Impact: an exclusion intended to remove that weapon does not remove its equivalent cosmetic variant. The apparent recommendation remains unchanged. The current implementation checks exact item IDs (`calc/Optimizer.java:1100`), whereas ownership handles variants as a family. Either apply exclusions consistently to equivalent variants or clearly offer separate variant and family exclusion actions. Removed the test exclusion afterward.

### P3 — Invalid budget input is silently discarded

Entering `nonsense` into a valid 10m budget and pressing Enter restored 10m with no validation message. The retained budget was safe, but the user gets no explanation of the rejected input or supported units. This matches `ui/BestGearSetupPanel.java:606–613`.

### P3 — Search with no matches lacks feedback and retains the previous target

Searching `zzzz-no-monster` removed suggestions but left the previous Vorkath target, results, and enabled Find button. Searching Dust devil with Bosses selected similarly offered no explanation until All monsters was selected. The target label correctly identified the retained target; nevertheless, a visible “No matching monsters” message and a filter hint would make the state clearer.

### P2 usability — Named potion overrides need a visible applicability warning

In Owned items only against Vorkath, selecting **Smelling salts** explicitly increased Fang DPS from 8.013 to 8.534, with zero to buy. The resulting equipment also changed, so this is a comparison of whole setups rather than a controlled boost-only comparison. The selection was accepted outside its raid context without a prominent applicability warning.

This is **documented override behavior**, not an accidental violation of the default Best available policy: `README.md:63–66` explicitly says a potion picked by name is always used. `PotionChoice.resolve` bypasses availability and unrestricted-activity filtering for an explicit selection. The unrestricted choices and special overrides are also documented later in the README. Default Best available and explicit named choices therefore have different semantics.

For a user evaluating a practical owned setup, the override can make the headline estimate misleading. Preserve intentional scenario exploration, but label the active override beside the result and warn when its ownership or activity applicability is not satisfied. The tooltip and collapsed Search details do not adequately explain this at the point of selection. Restored all potion choices to Best available after testing.

### Usability observations

- The narrow sidebar is functional, but fight options, alternatives, and Search details require substantial scrolling. Important assumptions about potions, unlocks, prices, and special attacks are easiest to miss when their sections are collapsed.
- Special-only results disclose special energy cost in the result details, but their TTK/KPH describe repeated specials without a practical energy-recovery cycle. Keep the active special-only mode and this assumption visible alongside the estimate.
- Use current distance while logged out gave a red instruction to select or attack a nearby NPC. It preserved results safely. A logged-out-specific message would be clearer.

## Logged-out test coverage and evidence

Numbers below are observed outputs, not independently certified DPS values. All runs assume level 99 combat stats because this profile had no saved levels. Results also depend on the bundled data and prices available in this client session.

| Case | Observed result | Assessment |
| --- | --- | --- |
| Initial state without bank or saved levels | Clear bank-opening prompt; “Not logged in: assuming 99 in all combat stats.” | Correctly disclosed missing data. |
| Search and grouped versions | Vorkath selected post-quest level 732; Zulrah offered its three forms; Enter and mouse selection worked. | Usable, no stale monster result observed after selection. |
| Bosses / All monsters | Dust devil appeared after switching to All monsters. | Filter works; no-match feedback needs improvement. |
| Vorkath, Super antifire + Protect from Magic, no limit | Toxic Blowpipe, 11.602 DPS, max 37, 75.1% accuracy, 64.6s TTK; 283.07m + 1 unpriced. | Protection setting permits two-handed ranged choice. Unknown cost explicitly retained. |
| Vorkath, Protect from Magic off | Dragon Hunter Crossbow with protective shield, 11.164 DPS, 82.4% accuracy, 67.2s TTK; 212.56m + 1 unpriced. | Protection constraint changed the recommendation appropriately. |
| Exclude Vorkath crossbow | Equivalent (B) crossbow returned with unchanged metrics. | P2 finding above. |
| Disable ranged potion | Crossbow DPS fell from 11.164 to 9.752; leading overall style changed to Dragon Hunter Lance at 10.388 DPS. | Potion change affected calculations and ranking. Restored Best available. |
| Owned items only, no bank data | No usable setup; bank-opening instruction. | Correct refusal with unknown ownership. |
| Vorkath, Owned + budget, 300m | 8.748 DPS, to buy 299.09m, no unpriced cost. | Conservative price treatment; within budget. |
| Vorkath, 10m budget | 6.035 DPS, to buy 9.64m. | Budget reduction produced cheaper and weaker gear. |
| Invalid budget | Previous 10m restored silently. | P3 feedback issue. |
| Zulrah Serpentine, 10m | Fire Surge, 7.224 DPS, max 47; reroll-above-50 note; 9.73m. | Fire choice and damage-cap explanation consistent with published mechanics. |
| Zulrah melee selection | Halberd appearance; 3.710 DPS, reach-compatible attack style, damage-cap note. | Modern melee option present; not treated as blanket melee immunity. |
| Zulrah form change | Atlatl output changed from 4.765 DPS on Serpentine to 7.035 on Tanzanite. | Form selection changed defence-dependent output. |
| Kraken, 10m | Melee immune label; no melee tabs; Magic leading at 7.874 DPS, 32.4s TTK. | Immunity reflected in selectable results. Poison-conversion note shown; no independent poison-model certification. |
| Current distance, logged out | Actionable red message, retained prior results. | Safe failure; wording can improve. |
| Dust devil, single target | Eclipse Moon Helm, Atlatl, 9.018 DPS, 8.36m. | Mandatory protection failure. |
| Dust devil, AoE with 9 enemies | Ice Barrage: 53.405 total DPS, 5.934 primary DPS, 17.7s primary TTK, 1831.0 estimated KPH. | Total/primary and HP/TTK arithmetic coherent; protection failure remains. KPH is theoretical. |
| Zebak, raid level 0, party 1 / path 0 | HP 580; Atlatl, 7.973 DPS, 72.7s TTK. | Baseline recorded. |
| Zebak, raid level 300 | HP 1280; Dragon Crossbow, 5.554 DPS, 230.5s TTK; defence scaling detailed as 550/250; 9.59m. | Scaling changed HP, defence, ranking, and TTK coherently. |
| Alternatives and Search details | Crossbow alternatives accessible; data dates 2026-10-01, price unknowns and boost assumptions visible. | Useful detail, requires scrolling. |
| Zebak special-only | Burning claws, 35% energy note, 9.703 DPS; 131.9s TTK. | Mode operates and labels energy; practical sustainability is not represented by the headline TTK. |

Quick arithmetic checks: 750 / 11.602 ≈ 64.6s, 1280 / 5.554 ≈ 230.5s, 105 / 5.934 ≈ 17.7s, and 53.405 × 3600 / 105 ≈ 1831.0 KPH. These verify display consistency, not the underlying expected-hit equations.

Published-mechanics comparisons included the [dust devil protection requirement](https://oldschool.runescape.wiki/w/Dust_devil/Strategies), [Vorkath protection strategies](https://oldschool.runescape.wiki/w/Vorkath/Strategies), and [Zulrah mechanics](https://oldschool.runescape.wiki/w/Zulrah). Some Wiki content was checked from cached copies rather than the live page; the observed UI and checked source are the primary evidence for this report. No comprehensive external calculator parity test was completed.

## Automated validation

- Java 11 Gradle wrapper full build with `build --rerun-tasks --console=plain`: **passed**.
- XML results: **286 Java tests in 43 suites; 0 failures, errors, or skips**.
- Wiki API Python test suite: **10 passed**.
- Existing test coverage includes combat and encounter rules, data catalog sweeps, prices, ownership, and asynchronous lifecycle behavior. Passing tests did not detect the live protection and ammunition-quantity defects identified here.

The initial sandbox build could not access the normal dependency cache/network; the approved build using the existing environment completed successfully. No source changes were necessary to run the checks.

## Logged-in and owned-data coverage

On login, the plugin cleared the previous account-dependent results and displayed “Account changed. Find best setup again to use this account's levels/items.” Its tracked-item count matched the bank's occupied slots in this session. That agreement is an observation for this bank, not proof that the label always means occupied bank slots. Inventory and worn equipment were empty at the final check; all inspected ownership came from the bank. The cached bank remained available with the bank closed.

Checked the account's Skills tab: its real combat and Slayer levels replaced the earlier level-99 assumptions. Inspected the prayer book without activating prayers: Piety was available, while Augury was not, consistent with the account's Prayer level. The magic result used a lower prayer. This was a visual consistency check, not a complete unlock-varbit audit.

The following values were read from the live results. Unless stated otherwise, potion choices were Best available, fire protection was required, Super antifire was selected, Protect from Magic was enabled, and searches used Owned items only. Costs and to-buy values are those displayed during the session, not fresh market quotations.

| Case | Observed result | Assessment |
| --- | --- | --- |
| Vorkath, post-quest, owned only | Osmumten's Fang; 8.013 DPS; max 47, range 8–47; 87.4% accuracy; 93.6s TTK; 41.03m total and owned value; zero to buy. | Actual account data produced a materially different setup from the logged-out result. Fang and Blood Moon Helm names confirmed using item menus; representative gear checked against the bank. |
| Head slot lock | Blood Moon Helm remained selected; orange outline and one-lock summary appeared. | Lock persisted through the result; removal restored the unlocked state. |
| Exclude owned Fang | Fang disappeared and the leading style changed to Atlatl. | Exclusion worked for this owned item; this does not negate the separately reproduced cosmetic crossbow bypass. |
| Vorkath, melee potion None | Fang fell to 6.178 DPS, max 39, 82.4% accuracy, 121.4s TTK. Atlatl became the overall leader at 6.427 DPS. | Boost selection affected damage, accuracy and ranking. |
| Vorkath, explicit Smelling salts | Fang setup at 8.534 DPS, max 50, 88.3% accuracy, 87.9s TTK; 53.51m value; zero to buy. | Documented explicit override; applicability warning gap described above. |
| Manual ownership: Dragon Hunter Lance | Marked the lance owned manually; result changed to 8.137 DPS, max 55, 71.0% accuracy, 92.2s TTK, 81.44m value, zero to buy. The tracked-item count was unchanged. | Override applied and was removable. Container count and manual ownership are separate; clearer wording would help. Removing the entry restored Fang at 8.013 DPS. |
| Vorkath, Owned + budget, 0 and 1m | Fang remained at 8.013 DPS, 41.03m value, zero to buy. | Gear already owned did not consume the purchase budget. |
| Vorkath, Owned + budget, 50m | Dragon Hunter Crossbow; 8.255 DPS; Ruby dragon bolts (e), max 100; 80.8% accuracy; 90.9s TTK; 50.95m total value; 1.81m owned; 49.15m to buy. | Displayed purchase total was within budget. Ammunition quantity was zero for this case; this does not validate supply costs. |
| Zulrah Serpentine, owned only | Blue Moon Spear, Fire Wave autocast; 5.240 DPS; max 35; 89.7% accuracy; 95.4s TTK; 6.04m value; zero to buy. | Spell choice respected the account's actual Magic level, unlike the level-99 Fire Surge result. Damage-cap explanation retained. |
| Kraken, owned only | Blue Moon Spear, Earth Wave autocast; 5.161 DPS; max 32; 96.8% accuracy; 49.4s TTK; 6.43m value; zero to buy. | Magic availability changed appropriately; melee immunity remained visible. Encounter-access requirements were not independently evaluated. |
| Dust devil, off task, single target | Dual Macuahuitl, Blood Moon Helm; 9.195 DPS; max 42 as 21 + 21; 94.1% accuracy; 11.4s TTK; 44.16m value; zero to buy. | Required face protection omitted with real owned data. |
| Dust devil, on task, single target | Slayer Helmet; 9.965 DPS; max 50; 94.6% accuracy. | Task setting supplied protective headgear, exposing the off-task gap. |
| Dust devil, on task, AoE 9 | Slayer Helmet (i), Ice Burst; 40.001 total / 4.445 primary DPS; max 27; 98.5% accuracy; 23.6s primary TTK; 6.8m value; zero to buy. | Owned imbued helmet confirmed in bank. Lower spell reflected account level. |
| Dust devil, off task, AoE 9 | Unprotective blue headgear, Ice Burst; 35.509 total / 3.945 primary DPS; max 24; 26.6s primary TTK; 6.43m value; zero to buy. | Mandatory protection failure also reproduced in owned AoE mode. |
| Zebak, raid 0, party 1 / path 0 | Fang; 7.199 DPS; max 41, range 7–41; 90.0% accuracy; HP 580; 80.6s TTK; 47.39m value; zero to buy. | Owned baseline recorded. |
| Zebak, raid 300 | Fang; 4.535 DPS; max 40, range 6–40; 59.2% accuracy; HP 1280; 282.3s TTK; 43.88m value; zero to buy. | Raid scaling changed HP, accuracy, gear and TTK. |
| Zebak, raid 300, ranged, Ammo quantity 100,000 | Ruby bolts (e); 3.953 DPS; 52.1% accuracy; 323.8s TTK; 22.2m total and owned value; zero to buy. Bank contained 159 bolts. | Confirmed ammunition quantity defect. |
| Same ranged case, Owned + budget, 0 | Same 3.953 DPS and zero to buy despite the 99,841-bolt shortfall. | Purchase budget failed to account for requested supply. |
| Final Vorkath, no-limit, actual levels | Dragon Hunter Crossbow; 9.261 DPS; max 100 ruby proc; 84.5% accuracy; 81.0s TTK; 220.2m + 1 unpriced total and to buy. | No-limit result used real levels and retained the unknown-price indicator. Not directly comparable to level-99 ranking. |

Owned-phase display consistency checks: 750 / 8.013 ≈ 93.6s, 1280 / 4.535 ≈ 282.3s, and 105 / 4.445 ≈ 23.6s. Bank searches and item context menus confirmed the Ruby bolts (e) stack and Slayer Helmet (i), rather than inferring their identities from icons alone. No items were moved to conduct these checks.

## Final assessment and remaining limits

The sampled flows are usable and respond to live account data. However, the off-task dust-devil setups are not practically usable, ammunition purchase estimates can be understated, and exact-ID exclusions can return an equivalent cosmetic item. These defects prevent treating every advertised best setup and budget as accurate. Correct mandatory protection first, then quantity-aware ammunition costing, then clarify exclusion families and active potion overrides. Smaller improvements are no-match feedback, budget validation messages, and keeping important assumptions near the result.

The owned-data evaluation covered representative bank gear and one ammunition stack, not every item in the bank. It did not independently audit every potion dose, heart, charged state, rune supply, wear rule, unlock varbit, or unknown-price item family. Default boost handling was exercised through result changes and checked against its documented policy; exhaustive stock-by-stock parity remains unverified. Owned unpriced-item handling was not isolated in a dedicated live test. Profile switching after login, deliberate inventory/equipment mutations, world changes, NPC distance while in combat, and every lifecycle transition were not tested. Cached bank use after closure was observed, but no bank mutation was induced.

No actual fights, empirical proc rates, sustained kill rates, or comprehensive external calculator comparisons were measured. Display arithmetic and plausible ranking changes cannot establish global optimizer optimality or certify every expected-hit formula. TTK/KPH and special-only figures remain model outputs.

## Settings restored

Restored: Bosses filter; Best in slot (no limit); budget 300m; off slayer task; special-only off; distance 0; AoE off; grouped enemies 9; ammo quantity 0; target HP 0; party size 1; ToA raid/path levels 0; Require fire protection on; Super antifire; Protect from Magic on; all potion selections Best available. Fight options and constraints collapsed. Removed all test locks, exclusions, and manual ownership entries. Bank search was cleared.

Left post-quest Vorkath selected, the client running and logged in, bank closed, and the game inventory tab selected. The plugin retained its tracked items. No items were withdrawn, deposited, equipped, purchased or consumed. The only workspace change for this evaluation is this report; no plugin implementation was modified.
