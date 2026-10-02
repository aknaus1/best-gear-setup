# Best Gear Setup second fix retest

Date: 2026-10-02. Tested the updated, uncommitted working tree on `ea74ece`. The [first fix verification](fix-verification-2026-10-02.md) remains the historical record of the preceding defects.

## Current result

After the final refresh correction, the normal Java 11 wrapper build passed **310 tests across 46 suites**, with zero failures, errors or skips. The **10 Python tool tests** also passed. Quest sourhog protection and thrown-weapon quantity accounting passed the preceding live checks in the rebuilt client, using the live bank snapshot.

The final refresh correction passes both independent test probe cases: an isolated ammunition change and the same change followed by unrelated inventory updates. **The remaining P2 is resolved in source and automated verification; no outstanding reproduced finding remains from these targeted checks.** The newest refresh correction has not been reloaded or exercised in the live client. The preceding immediate-rerun probe's expectation remains obsolete under the deliberately debounced design.

## Resolved P2 — Unrelated container events postpone supply refresh

The following describes the original reproduction. Final verification is recorded below.

`BestGearSetupPlugin.invalidateIfOwnershipChanged()` at lines 423–441 compares current supplies with the original search snapshot. Once they differ, every subsequent container event schedules a timer restart; it does not check whether the relevant supply changed since the preceding event. `scheduleSupplyRefresh()` at lines 445–452 restarts the timer unconditionally.

Reproduction with simulated containers, without moving or consuming any live items:

1. Capture a search with 159 Ruby bolts (e), requesting 100,000.
2. Change the simulated bolt stack once, to 158, and leave it unchanged.
3. Set the test settle interval to 200ms.
4. Emit unrelated food-stack updates every 50ms for 800ms: four complete settle intervals with no further ammunition change.
5. Expect one refresh for the settled ammunition stack; observe zero.

The independent control performs the same ammunition change with no intervening inventory events and refreshes once, as intended. Both tests flush Swing work, and the probe stops its timer afterward. The production interval is five seconds; the probe shortens it to verify timing without a long wait.

Impact: continuing unrelated food, potion or other container notifications can postpone the refreshed recommendation and purchase calculation. The stated contract is to wait for the *relevant supply* to settle; the implementation instead waits for all relevant container notifications to stop while that supply differs from the original search. Compare against the last observed relevant supply to decide whether to restart the timer. Keep the last-search supply snapshot separately to decide whether a refresh is still needed.

Probe: `build/evaluation-probe/src/com/bestgearsetup/DebouncedQuantityProbeTest.java`. Command:

```text
gradlew.bat --init-script build/evaluation-probe/probe.init.gradle evaluateQuantityInvalidation --tests com.bestgearsetup.DebouncedQuantityProbeTest --offline --console=plain
```

Original results: **2 tests, 1 passed, 1 failed**. `isolatedStackChangeRefreshesAfterSettling` passed. `unrelatedInventoryEventsDoNotPostponeSettledSupplyRefresh` failed with `expected:<1> but was:<0>`.

Final recheck against the corrected source, with the same probe: **2 tests passed, zero failures or errors**. `observedQuantities` now tracks the last relevant change separately from `searchQuantities`; an unset observed snapshot falls back to the searched snapshot. Only changes in relevant capped quantities restart the settle timer. When it fires, the search snapshot still determines whether a refresh is necessary. The normal suite also covers unrelated inventory events, rapid ammunition changes and a stack restored to its searched size. HTML output: `build/reports/tests/evaluateQuantityInvalidation/index.html`.

## Verified implementation changes

- Quest sourhog now requires reinforced goggles specifically; the ordinary sourhog still accepts goggles or a slayer helmet. The revised test rejects a helmet for the quest fight and includes that variant in optimizer searches. The [Wiki quest guide](https://oldschool.runescape.wiki/w/A_Porcine_of_Interest/Quick_guide) has the player receive and equip goggles before the fight; existing slayer helmets gain goggles as a quest reward.
- `WeaponRules.consumedPerAttack()` is shared by eligibility, shortfall pricing, cost display, tooltips and supply tracking. It distinguishes consumed thrown weapons from reusable blowpipes, Tonalztics of Ralos and the Hunter's spear. Regression coverage includes six Rune knives against a request for 100,000, zero budget, owned-only rejection, exact stock, purchase cost and unknown prices. The two uncommon new weapons were not re-checked against their Wiki pages in this retest, so their semantics are not separately certified here.
- Quantity changes within the requested supply now schedule a debounced refresh. Counts above the requested quantity are ignored, and an unchanged count after refresh does not schedule another rerun. Continuous ammunition changes deliberately postpone rerunning until the stack settles; this behavior is a stated design choice, not a failure of the old immediate-rerun contract.

## Validation and live retest

The final independent rerun of `gradlew.bat build --rerun-tasks --offline --console=plain` returned **BUILD SUCCESSFUL**, with 310 tests passing on the configured Java 11 runtime. `python -m unittest discover -s tools -p 'test_*.py' -v` passed 10 tests, with bytecode generation disabled. `git diff --check` had no whitespace errors. The separate test probe passed both cases. The live checks below preceded the final refresh-only correction.

The old client was closed through its own exit flow, and the newly built plugin was launched in RuneLite 1.13.1. After logging in and opening the bank, the following checks then used the live owned quantities:

| Scenario | Observed result | Verdict |
| --- | --- | --- |
| Quest sourhog, off task, best in slot, best available potions | Leading Toxic Blowpipe setup used Reinforced Goggles, confirmed by its item menu. DPS 10.445. Search details explicitly required goggles on or off task. | Pass |
| Quest sourhog, owned only, goggles unowned | No usable setup; owned headgear did not substitute for the required goggles. | Pass |
| Zebak Normal, raid level 300, 100,000 ammunition, owned only or zero purchase budget | Undersupplied ranged recommendations disappeared. | Pass |
| Rune Knife, six held, six requested, zero budget | Locked knife setup accepted; purchase cost zero. Bank stack and item menu confirmed six Rune knives. | Pass |
| Same knife setup, seven requested, zero budget | No usable setup; lock explanation quoted a 110 gp shortfall against the zero budget. | Pass |
| Rune Knife, six held, 100,000 requested, 300m budget | Cost box credited exactly 660 gp of owned equipment, matching six knives at the current 110 gp quote. Displayed total cost 221.58m and total to buy 221.58m after rounding; these totals include the other selected gear. | Pass |

The last scenario implies 99,994 knives to buy, costing 10,999,340 gp for the knife component. This component is calculated from the verified stock, request and unit quote; the item tooltip's exact shortfall text was not independently captured. The visible whole-setup total is rounded, so it cannot establish that component by itself. The six/seven boundary and owned-value credit provide independent live checks alongside the shortfall pricing tests.

Temporary Rune Knife locking and Rune Crossbow/Toxic Blowpipe exclusions were removed. Restored Vorkath post-quest, bosses filter, best-in-slot mode, 300m retained budget, off-task status, ammunition zero and raid level zero; fight and equipment options collapsed. Best available potions retained. Bank left open and unfiltered. No live items were moved, consumed or bought during the retest.

Implementation files were not modified during this retest. Added only this report and an ignored test probe; nothing committed. Closure of the refresh finding is supported by the independent simulated-container probe and normal regression suite, not by an actual live consumption test. This targeted retest does not certify every combat mechanic or every uncommon weapon classification.
