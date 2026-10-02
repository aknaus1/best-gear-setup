# Best Gear Setup fix verification

Date: 2026-10-02. Tested the updated, uncommitted working tree on top of `ea74ece`, in RuneLite 1.13.1 with Java 11. The [original evaluation](evaluation-2026-10-02.md) is preserved as the historical baseline.

## Outcome

**Several original findings are resolved in the tested cases; three accuracy defects remain.** The new equipment rules substantially improve protection, including off-task results and conflicting locks. Ammunition in the ammunition slot now uses stack quantities, but consumable thrown weapons bypass that accounting. Quantity-only ownership changes also leave an existing result stale. The quest sourhog is incorrectly exempted from protection.

The normal build passed all **304 Java tests across 46 suites**, with no failures, errors or skips. All **10 Python tool tests** passed. A separate test probe under ignored `build/evaluation-probe/` ran two tests: its item-removal control passed, while its quantity-only refresh test failed. The passing main suite therefore does not establish that all findings are fixed.

## Remaining findings

### P1 — Quest sourhog results omit required goggles

Live reproduction after logging in and opening the bank:

1. Search Sourhog under All monsters; select **A porcine of interest (level 37)**.
2. Use Owned items only, off task, ammunition quantity zero, no AoE and Best available potions.
3. Find the leading setup and inspect its head item through the context menu.

Observed: **Dual Macuahuitl with Blood Moon Helm**, at **9.446 DPS**, max hit 42 (21 + 21), 95.6% accuracy and approximately 4.2 seconds TTK. The setup reported 41.52m total and owned value, with zero to buy. It wore neither reinforced goggles nor a slayer helmet, and did not warn about missing goggles. The regular Standard level-37 sourhog result did select a slayer helmet; the failure is specific to the quest variant.

Expected: apply the quest variant's protection requirement. The [quest quick guide](https://oldschool.runescape.wiki/w/A_Porcine_of_Interest/Quick_guide) has the player receive goggles from Spria and equip them before killing the quest sourhog. The [reinforced goggles page](https://oldschool.runescape.wiki/w/Reinforced_goggles) also describes receiving the free pair during the quest. The assumption that goggles are unavailable during the fight is incorrect. The quest reward adds goggles functionality to slayer helmets, so a fix should also consider quest progress when accepting a helmet for a pre-completion fight.

Cause: `src/main/java/com/bestgearsetup/calc/SlayerEquipment.java:32–34` explicitly exempts the quest and matches only the exact name `sourhog`. `src/test/java/com/bestgearsetup/calc/SlayerEquipmentTest.java:123` includes the quest variant among cases expected to have no enforced requirement. That passing assertion preserves the incorrect behavior.

### P2 — Thrown weapons ignore the requested supply quantity

Live reproduction with bank data:

1. Select Zebak, raid level 300, path level zero and party size one.
2. Set Ammo quantity to **100,000**.
3. Run Owned items only, then Owned + budget with a zero budget.
4. Inspect the ranged recommendation and the bank's Rune knife stack.

Observed in both modes: **Rune Knife**, **2.581 DPS**, max hit 17, 36.2% accuracy and approximately 495.9 seconds TTK. The result reported **2.93m total / 2.93m owned / zero to buy**. The bank contained **six Rune knives**, confirmed by its stack count and item context menu. Worn equipment was empty when inspected. The result did not represent the requested supply quantity or reject the zero-budget setup.

The original Ruby bolts (e) recommendation with its 159-unit bank stack no longer survived this 100,000-unit, zero-budget case. However, the replacement reveals a remaining path: knives and darts are consumable ammunition equipped in the weapon slot.

Cause: `calc/Optimizer.java:1337` multiplies quantity only for `Slot.AMMO`; `ammoShortfall` at lines 1362–1369 returns zero for other slots. `ui/ResultView.java:324` likewise enables quantity valuation only for the ammunition slot. The bundled catalog identifies Rune knife 868 and Rune dart 811 as thrown weapons. This is a slot-classification gap, not evidence about how many projectiles a particular fight consumes: the test concerns the supply quantity explicitly entered by the user.

Expected: account for consumable thrown weapons consistently in eligibility, shortfall, purchase cost and displayed value. Include positive-stock, insufficient-stock and zero-budget cases in regression coverage.

### P2 — Changing only stack quantity does not refresh existing results

Confirmed through source inspection and a separate simulated regression probe. No live bank items were moved or consumed to trigger this test.

The probe initialized tracked Ruby bolts (e), item 9242, at 159 units; captured the ownership state for a quantity-dependent search; changed the simulated stock to 158; verified that `quantitySnapshot()` returned 158; then invoked the plugin's ownership invalidation method. Expected one search rerun, observed **zero**. Its positive control removed the item entirely and observed one rerun, so the failure is specific to quantity-only change rather than a disconnected test harness.

Failure: `changingOnlyStackQuantityRefreshesSearch` — `expected:<1> but was:<0>`.

Cause: `OwnedItems.java:109` defines change by the set of IDs, and `rebuild` retains the expanded ID set when only counts change. `BestGearSetupPlugin.java:386–396` compares those ID sets and returns without refreshing. Searches capture quantities at lines 640–642, but retain only the ID set for later ownership invalidation.

Impact: a stack can cross a supply or budget threshold while the displayed recommendation and purchase amount still describe the previous quantity. Manually finding the setup again refreshes it. The invalidation mechanism should compare the quantity state relevant to the active search as well as item presence.

Probe artifacts, disposable and ignored by Git:

- Source: `build/evaluation-probe/src/com/bestgearsetup/QuantityRefreshProbeTest.java`.
- Gradle setup: `build/evaluation-probe/probe.init.gradle`.
- Results: `build/reports/tests/evaluateQuantityInvalidation/index.html` and `build/test-results/evaluateQuantityInvalidation/TEST-com.bestgearsetup.QuantityRefreshProbeTest.xml`.

## Fix coverage

| Area | Evidence and result |
| --- | --- |
| Dust devil protection | Live logged-out, off-task Smoke Dungeon level 93 search selected **Slayer Helmet (i)**; its name was confirmed through the context menu. Forcing the head slot empty returned No usable setup with an explicit protection/lock explanation. Removing the lock restored a usable protected result. Automated tests cover all search modes, styles and single-target/AoE results. |
| Additional slayer equipment | Live Basilisk Knight selected **V's Shield**, confirmed by its context menu. The new suite exercises head rules for banshees/spectres/sourhogs, shield rules for basilisk/cockatrice/harpie targets and Karuulm boots, including a diary exemption and two-handed conflicts. Those rules are covered by tests; every named variant and environmental exception was not independently exercised in the client or re-certified against live references. Quest sourhog is a confirmed exception to correctness above. |
| Ordinary ammunition shortfall | The former insufficient Ruby-bolt setup disappeared in the live 100,000-unit zero-budget reproduction. Automated coverage tests stack quantities, shortfall purchase cost, exact stock, owned-only rejection and unknown prices. Thrown weapons and quantity-only refresh remain incomplete. |
| Family exclusion | The live item menu now offers separate **Exclude all variants** and **Exclude only this variant** actions. Source and `ExclusionFamilyTest` cover family rejection, including Dragon Hunter Crossbow variants. The full Vorkath crossbow exclusion scenario was verified by automated coverage in this follow-up, rather than repeated through a live exclusion action. |
| Invalid budget feedback | Live Owned + budget entry `nonsense` retained 300m and displayed a red validation message with supported example amounts. Entering a valid amount cleared the error. |
| No-match and filter feedback | Live Dust devil search with Bosses selected displayed an explicit no-boss match message and All monsters recovery action; the recovery action produced the target. A search with no matching name also visibly identified the retained target. |
| Potion override warning | Live explicit **Smelling Salts** against Vorkath produced a prominent yellow warning above the result tabs, explaining its Tombs of Amascut restriction, the override assumption and the Best available alternative. Restoring Best available removed the warning. This closes the sampled applicability-warning reproduction; it is an intentional scenario override. |
| Bank recognition and defaults | After logging into the rebuilt client and opening the bank, the plugin's tracked-item count matched the bank's used slots in this snapshot. Account data replaced the logged-out assumptions. All three potion defaults were Best available at the end, with no test locks, exclusions or manual ownership entries left active. |

## Validation and cleanup

Ran the plugin's wrapper with the configured Java 11 runtime. `build --rerun-tasks --console=plain` completed the main validation; a subsequent `build --offline --console=plain` explicitly returned **BUILD SUCCESSFUL**. The plugin applies Java only, so Checkstyle/PMD are not part of this build. Python verification used `python -m unittest discover -s tools -p 'test_*.py' -v`, with bytecode generation disabled. `git diff --check` reported no whitespace errors.

The isolated probe invocation was `gradlew.bat --init-script build/evaluation-probe/probe.init.gradle evaluateQuantityInvalidation --offline --console=plain`. It returned **BUILD FAILED** because one of its two assertions failed. It does not modify or join the normal test source set.

No purchases, withdrawals, deposits, equipment changes, consumable use or game messages were performed. No implementation files were edited. This follow-up adds the report and an ignored test probe only.

Left the rebuilt client running, logged in with the bank open and the bank's normal view restored. Restored post-quest Vorkath, Bosses filter, Best in slot (no limit), retained budget 300m, off task, ammunition quantity zero, raid level zero and Best available potions. Test constraints were removed.

This review verifies the sampled workflows and identifies reproducible residual defects. It does not certify every combat formula, all environmental exceptions, live projectile consumption, or a globally optimal setup for every monster.
