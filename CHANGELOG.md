# Changelog

Notable changes to Best Gear Setup. Version numbers match `version=` in `runelite-plugin.properties`, which
is what the Plugin Hub shows. A branch lists its changes under Unreleased until it merges into `main`, where
they join the newest version: *in progress* while it collects changes, *released* from the day it is sent to
the Plugin Hub.

## Unreleased

## 1.1.5 (in progress)

- Field Guide: right-click a result item to look it up in Field Guide (shown while that plugin is on); Field Guide
  can open the best setup for a monster.
- Add **No GE (shop items only)** under the gear source. Owned + budget then buys only items a shop stocks, at the
  cheapest shop's coin price (items sold only for Tokkul, tickets or points cost no coins and say what they cost),
  and Best in slot picks from shop items only. Owned gear works as before.
- **Use my GP** beside the budget sets it to your coins and platinum tokens (bank, inventory and other tracked
  storage; bank coins count once you've opened the bank).
- The changelog gets an Unreleased section for branches, and each version says whether it is in progress or
  released. CONTRIBUTING.md explains where new lines go and how a release is marked.
- Add a **Theme** setting (Display & interaction), switched at once:
  - **Classic**, the current look and the default.
  - **Forged**: a gold *Find best setup* that light sweeps across, grained stone buttons and chamfered warm-stone
    cards and boxes.
  - **Edge**: flat buttons with an orange edge like the open cards.
  - **Runite** (blue forged metal and slate), **Ancient** (Zarosian purple) and **Dragon** (dragon-red buttons with
    red-edged cards).
  - **Midnight**: high contrast, near-black buttons and cards with light outlines.
  - **Crystal**: faceted cyan crystal buttons that a glint runs across on hover.
  - **Woodcut**: oak plank buttons with grain and brass studs, and plank cards.
  - **Inferno**: obsidian buttons with molten cracks that glow and shed embers on hover; the main button is lava
    that flows while hovered.
  - **Arcane**: starfield buttons whose outline pulses on hover while a spark orbits the rim.
  - **Saradomin**, **Zamorak**, **Guthix**, **Armadyl** and **Bandos** in their gods' colours; **Barrows** (still
    grey crypt stone); **Retro** (the 2004 interface: brown stone, yellow text, square and still); **Desert**
    (sandstone); **Void** (Pest Control purple with a green spark); **Frost** (ice crystal); **Venom** (toxic green
    lava); **Gilded** (gold on gold).
  - **Custom**: your own. **Custom theme** in Settings starts it from any theme and sets its painters, card frame,
    corner cut, animation and textures, and every colour: panel, text and inputs, cards, buttons and result
    boxes, plus fonts and the smaller parts (text buttons, list rows, the Bosses badge, scrollbars, the progress
    bar, boss names and warnings), previewed as you pick. **Surprise me** makes a random one from a recoloured
    built-in theme. Export and Import share it as text through the clipboard. The editor previews the main
    button, Wiki banner and result boxes that Settings hides, folds its colours into groups you open,
    **Change every use of a colour** recolours every part that shares the one you pick, **Undo** steps back
    through the session's changes, and text under 3:1 contrast with its fill is flagged. Until Custom is first
    saved the editor shows the panel's theme, which the first change starts from. Imports need at least one
    theme key.
  - Themes colour the whole panel, its text and inputs, not just the buttons; Midnight has square corners and no
    textures.
- The **Wiki** button is a banner like the game's own Wiki button, in every theme.

## 1.1.4 (released 2026-11-25)

- Add **Kills per trip** (Assumptions → Trip, default 1). Each setup shows its prayer points per kill, the trip's
  combat time and the boost and restore bottles it needs (super restores when owned, or Sanfew serums, otherwise
  prayer potions). Wave and challenge fights (Fight Caves, Inferno, Fortis Colosseum, TzHaar-Ket-Rak, the
  Gauntlet) count one kill per trip.
- Fill the trip's free slots with your best owned food (sharks if none), with owned Saradomin brews and, against
  hard hitters, karambwans. No food against a target that can't hurt you, and three emergency food when your
  overhead, fire protection or distance blocks its every attack. Turn it off with **Fill inventory with food**.
- Trips bring a superantipoison or anti-venom+ against monsters that poison or envenom you (not with a serpentine
  helm), and the antifire a dragon's fire protection relies on (not for wyverns).
- The bank layout shows the inventory as you'd pack it, in a 4x7 grid beside the equipment: spec switch, rune
  pouch, boosts, restores, then food, with other owned foods on a row below. Repeated items use the bank's
  spare slots and withdraw from the real stack.
- The bank layout opens again with the bank when it was showing as the bank closed.
- Bank highlights stay off over the bank layout, which only shows the setup's items. A two-handed spec weapon no
  longer keeps an inventory slot free for the worn shield, so food fills it and the layout has no gap.
- The trip's superantipoison or anti-venom+ is no longer dropped for targets with a boss phase, raid scaling or a
  special attack's drain, such as Yama and Vorkath. The cure also shows beside the boosts and antifire,
  and extended anti-venom+ counts as either cure.
- A (super) combat potion is preferred to the (super) attack and strength potions it matches when allowed: one
  bottle and one inventory slot instead of two.
- Each setup shows the target's overhead protection prayer (one prayer, a switch, or none needed) as an icon
  beside its boosts and in its statistics, with static advice from the Wiki's strategy pages and the monster's
  attack styles.
- Gear searches are exact: each weapon gets the best combination of your allowed gear. Across 1,872 test
  searches 280 found a better setup and none a worse one, in under half the time. **Search depth** is gone.
- Searching again with unchanged gear and settings returns at once, and turning styles on or off reuses the
  styles already searched.
- Setups tied on score wear the cheapest gear, and a slot whose item changes nothing is left empty instead of
  worn or bought (**Fill empty slots** still fills it).
- Add **Excluded prayers** to Rules: click any prayer in the prayer book to exclude it. An excluded offensive
  prayer is never assumed, and an excluded protection prayer leaves the overhead advice and its prayer drain.
- Right-click a result's prayer, spell, potion, antifire or Mark of Darkness icon to exclude it (excluded
  potions are listed with the excluded items), or its thrall icon to turn thralls off.
- Remove the **Ancient Magicks**, **Arceuus spellbook**, **Offensive prayers**, **Include raid potions**, potion
  per style, **Mark of Darkness**, **Antifire potion** and **Protect from Magic** settings: exclude the spell,
  prayer or potion instead. The strongest antifire you own (or can buy) is assumed and shown with the boosts.
  Anything these settings turned off is assumed again until excluded.
- Zeal is assumed once unlocked, and each broken Fractured Archive elemental fragment adds 2 to its element's
  spells.
- Show the thrall that can damage the target (ghostly, then skeletal, then zombified), or none.
- A special attack weapon, and an off-hand or ammunition held only for it, counts in the Wilderness
  expensive-item limit.
- The atlatl only gets the Strength boost, and damage over time never credits a faster setup with a slower kill.
- The Fractured Archive: bosses and Moia's undead binders scale with **Raid party size** (solos and duos have the
  listed hitpoints, as since the 18 November update), defence drains stop at 60% on the Archive Guardian, Bork,
  Ezekiel, Moia, Xarth and Ocellus (21 October hotfix), and the NPCs are refreshed after the 11 November update,
  adding Ezekiel's animated swords and the inert guardians.
- Refresh the other Wiki monster data with poison and venom, adding the Leech and Snelmlover500.
- Karil's crossbow is heavy ranged like every other crossbow, and only magic and standard ranged hits on Yama's
  void flares are guaranteed maximum.
- The sidebar's **Fight** and **Gear** buttons are now **Assumptions** and **Rules**. Every card in them opens
  and collapses on its own, with **Expand all** and **Collapse all**. The picked
  style tab stays selected when results recalculate.
- A new **Trip** card in Assumptions holds **Kills per trip**, **Spec energy** (moved from Search options) and
  **Fill inventory with food** (moved from Display & interaction); neither is on RuneLite's settings page
  any more. **Prevent manual casting** moves to Attack styles, replacing the Spellbooks section, and each
  section's description matches its items. Fight conditions and Trip open expanded, and *Back to results* is a
  full-width button.
- Remove **Require fire protection** and the Dragonfire section: protection is always required against dragons
  and wyverns when an allowed shield, antifire or Protect from Magic can give it. Without a usable antifire, a
  fire shield such as the anti-dragon shield is still required, since it cuts dragonfire to 5. Exclude them all
  to go without, and a warning says the breath isn't blocked. Anyone who had the setting off gets protection
  required again.
- Tidy the result panel (style box under the gear, a "<Style> setups" list, two-decimal DPS, whole-second TTK
  from 10 seconds, the statistics collapsed to their DPS line until clicked, the spec weapon shown even when it is
  the one worn, and a colour edge per box), keep hover text under 10 words (but the overhead's advice), and make
  the *Open in RuneLite settings* link look like one.
- Right-click the special attack weapon, off-hand or ammo in a result to exclude it or mark it owned.

## 1.1.3 (released 2026-11-10)

- A slot locked to an item you don't own is now worn in *Owned items only* and *Inventory + equipped only*
  searches instead of being left empty (or, for a weapon, finding no setup at all). The rest of the setup still
  uses only what you own, and the locked item still costs its price, so the result shows what buying it would
  cost. *Owned + budget* is unchanged: a lock you don't own must still fit the budget.
- **Require Ava's / quiver** (Settings, Equipment; was *Require Ava's / quiver for atlatl*) now covers every ranged
  attack whose ammunition an Ava's device recovers: arrows, bolts, javelins, atlatl darts, blowpipe darts and
  thrown darts, knives and thrownaxes. Chinchompas, bolt racks, salamanders, crystal bows and other weapons
  without ammunition are unaffected, and so are special-attack switches. It applies only when the search could
  wear one of those capes, so an account without one, or a cape locked to something else, keeps its ranged
  setups. It is now on for new installs;
  existing installs keep their saved choice. Dizana's max cape counts as a recovery cape.
- With [Dude, Where's My Stuff?](https://runelite.net/plugin-hub/show/dude-wheres-my-stuff) enabled, gear it has
  recorded in STASH units, POH storage (costume room, armour case, cape rack, toy box, treasure chests), active
  deathbanks and death piles, group storage, bags and sailing storage counts as owned. Hovering an item in a
  result shows where it is. Without that plugin nothing changes.
- A deposit, withdrawal or equip whose two container updates arrive on different game ticks no longer searches
  again. Ownership is now published once it has held for two ticks, so gaining or losing gear updates the
  results one tick later than before.
- When two items are equal in everything the search scores, a kitted or upgraded version is now chosen over the
  base item, so a ranged setup shows the Masori assembler rather than Ava's assembler. An item you hold still wins
  over one you don't.
- Magic setups can cast a spell by hand with any weapon, including one that can't autocast it. A weapon locked to
  a bow or melee weapon, an allowed spell no staff of yours autocasts, or an account with no Magic weapon now
  still gets a Magic setup, shown as *Manual cast*. Spells that need their own staff (Iban Blast, Magic Dart and
  the god spells) are still only cast with it, with a warning when they are the only allowed spells and no
  such staff is available. The plain Saradomin, Guthix and Zamorak staves cast their god spell by hand, since
  they can't autocast it, and the harmonised nightmare staff's faster casts apply to autocasting only. Turn on **Prevent manual casting** (Settings, Spellbooks) to keep to autocast only.
- Removed the "no Magic setup is left" spell warning, which blamed spell rules when something else emptied the
  Magic tab. A spell warning now shows only when no allowed spell can be cast at all.
- Magic and Ranged searches are faster. A spell is no longer scored when a stronger tier of the same element or
  ancient school can be cast (Fire Wave beside Fire Surge, Ice Blitz beside Ice Barrage), unless chaos gauntlets, the
  twinflame staff or grouped targets could make the weaker one win. With **Require Ava's / quiver** on, a search
  starts only from the recovery capes no other one beats. Results are unchanged.
- Searches are faster at every depth, most of all at *Best*. Gear with a named effect (Dharok's, void,
  obsidian, a slayer helmet off task) is only tried with weapons and targets its effect can apply to, unless its
  bonuses alone earn it a place. Results are unchanged.
- Owned items are only added up again on a game tick after something moved.

## 1.1.2 (released 2026-11-07)

- Keep Gear's searches open after adding a rule, so one search can add several items or spells. Suggestions drop
  what you just added, a lock suggestion says when it replaces the slot's current lock, and Escape clears a search.
- Bank placeholders no longer count as owned items, so *Owned items only* searches don't suggest gear you've
  only kept a placeholder for. Open your bank once to refresh a bank remembered by an earlier version.
- Keep the previous results in the sidebar, and the gear layout open in the bank, while a new search runs. The
  new best setup replaces both when the search finishes, so the bank layout no longer needs selecting again.
- Depositing gear into the bank, or withdrawing it, no longer searches again: the item is still owned, it has
  only moved. Losing or gaining gear still updates the results, on the next game tick.
- Zorya's tome's max hit now scales with Magic level, as the Wiki now gives it: floor(Magic / 3) - 9, so 24 at
  99 Magic instead of a flat 18.
- Refresh The Fractured Archive's NPCs from the Wiki, which corrected many of them after release: the grounded
  Archive Guardian has 1,100 Hitpoints, Moia 1,900 and Xarth's corpses 62; Ezekiel has 200 Defence and Bork 10
  crush defence; the undead binders and void augmenters have their full defences and elemental weaknesses; and
  the Dagon'hai and the Smorks count as bosses. Ocellus's second phase is added now that the Wiki has its stats.
- Refresh the other Wiki monster data. The quest's Strange Creature keeps its own name now that the Wiki lists it
  on the Phantom Muspah page, so "phantom muspah" still finds the boss. Some variants take the names of the
  Wiki's new version tabs: Brutus is now *quest*, *charging* and *post-quest*, and the Slayer-only Nechryael and
  Waterfiend are *slayer tower basement* and *slayer task area*.

## 1.1.1 (released 2026-10-30)

- Add The Fractured Archive's NPCs from the Wiki: the Archive Guardian (flying and grounded), Ezekiel, Bork and
  the Smorks, Moia, Dreadnoth the Jellied and his jellies, Xarth and his corpses, the Dagon'hai Elder and Monks,
  the undead binders, and Ocellus's first and third phases with his void rifts, augmenters and siphons, with
  their poison, venom and freeze resistances. Ocellus's second phase is left out until the Wiki has its stats, as
  is any monster page without them.
- Refresh the other Wiki monster data (Blink's forms are now named *combo eating* and *special attacks*).
- Add the raid's equipment from the Wiki and the game cache: the Ascension crossbows with their bolts (plain,
  diamond and onyx, enchanted or not), the Obligator, Zorya's tome and the Rondache. Zorya's tome is a 3-tick
  powered staff with its base max hit of 18. The Obligator's Thrust (accurate or aggressive, 5 ticks) keeps the
  best of two damage rolls, three if crush is the target's second-weakest melee defence and four if its weakest,
  and deals 20% more to a 2x2 target and 40% more to 3x3 or larger; its Smash is a plain 3-tick crush attack.
  Thrust's hits on several small targets in a line and the Rondache's flat armour aren't modelled.
- Add Allowed and Excluded spell cards to Gear, with spell icons. They restrict autocast choices while leaving
  powered staves and salamanders available, show spells by unlock level, and explain when no permitted spell can
  be cast.
- Show expected attack and support runes per kill below estimated kills per hour. Counts include rune saving,
  staff and tome supply, special attack time, and the correct rune costs for each thrall tier. Powered staff
  charges, tome pages and the Master Combat Achievements thrall duration bonus are not counted.
- The bank layout shows your rune pouch or divine rune pouch beside the spell runes, and a rune's bank
  placeholder now keeps its cell ahead of combination runes, since runes in a pouch aren't visible to the bank.
- Searching a monster now picks its normal version instead of a Leagues (echo), Deadman, Nightmare Zone or PvP
  Arena one, so "black dragon" uses the level 227 dragon and its water weakness.

## 1.1.0 (released 2026-10-10)

- New *Inventory + equipped only* mode: searches use only what you carry and wear, ignoring the bank.
- New **Gear** panel lists your locks, excluded items and extra owned items, with a search to add more and
  separate buttons to clear locks and exclusions.
- New in-panel **Settings** view with the same options as the RuneLite configuration page.
- With *Use active defence* (on by default), defence reductions from specials that land on your target,
  yours and your party's, replace the pre-fight preparation values. Own specs are tracked without the
  Special Attack Counter plugin.
- Searches are about 40% faster, with the same results.
- Cosmetic copies with identical stats (recolours, ornament kits) are searched once. Tooltips list the
  alternatives and the bank layout accepts any of them. The blazing blowpipe and Dinh's blazing bulwark now
  count as their base items.
- Cosmetic copies use their original's ammo and spell compatibility.
- Results header shows raid party scaling and the target's Defence before and after drains.
- OSRS Wiki snapshots refreshed (retrieved 10 October 2026 UTC).

## 1.0.0 (released 2026-10-09)

First Plugin Hub submission.

- Finds the best melee, ranged and magic setup for any monster or boss, from the side panel or by
  right-clicking an NPC, in *Owned items only*, *Owned + budget* or *Best in slot* mode.
- Reads owned items from your bank, inventory and equipment and remembers them per account. You can also
  mark items as owned by hand, lock slots and exclude items.
- DPS calculator based on the OSRS Wiki formulas, covering encounter rules, raid scaling, boss phases,
  special attacks, AoE, damage over time and effects that change with HP over the fight. See
  [COMBAT_AUDIT.md](COMBAT_AUDIT.md).
- Mandatory slayer protection (facemasks, earmuffs, nose pegs, mirror shields, Karuulm boots and others)
  and dragonfire protection are enforced as gear requirements.
- Ships with OSRS Wiki snapshots (retrieved 1 October 2026 UTC), so the plugin makes no network requests.
