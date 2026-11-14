# Changelog

Notable changes to Best Gear Setup. Version numbers match `version=` in `runelite-plugin.properties`, which
is what the Plugin Hub shows.

## 1.1.4

- The Fractured Archive's bosses and Moia's undead binders now scale with **Raid party size**: a solo counts as
  a duo and a duo has 10% more than the listed hitpoints, as in the update posts, and three or more players
  have the listed hitpoints x (1.45 + 0.42 per player after the third), as on the Wiki's team-size tables. Set
  the party size in Fight.
- Defence drains can only take the Archive Guardian, Bork, Ezekiel, Moia, Xarth and Ocellus to 60% of their
  Defence, following the raid's 21 October hotfix. Ocellus's second phase and the other NPCs can still be
  drained to 0.
- Refresh The Fractured Archive's NPCs from the Wiki after the 11 November update: the Archive Guardian has 55
  light ranged defence and 130 magic defence when flying, 50 magic defence and an 80% fire weakness when grounded; Bork
  has 1,650 Hitpoints and the Smorks 650; Xarth has 2,450 Hitpoints and 60 stab defence. The undead binders are
  undead. Xarth, Ocellus, the Dagon'hai and the undead binders gain the Wiki's poison, venom, freeze and burn
  data, and the Dagon'hai, Xarth, Ocellus and the undead binders their max hits. Ezekiel's animated swords and the
  inert guardians are added as raid NPCs.
- Refresh the other Wiki monster data, which adds the Leech and Snelmlover500.
- Treat Karil's crossbow as heavy ranged like every other crossbow, as the Wiki DPS calculator does: it rolls
  against heavy ranged defence and gets the unshielded Tormented Demon bonus.
- Only magic and standard ranged hits (bows and salamanders) on Yama's void flares are guaranteed maximum, per
  the Wiki. Crossbows, chinchompas, thrown weapons and melee roll normally.
- Count a special attack weapon, and an off-hand or ammunition held only for its spec, in the Wilderness
  expensive-item limit alongside the worn gear.
- Show the thrall that can damage the target: ghostly unless the target is immune to magic, then skeletal, then
  zombified, with no thrall where no style can hurt it or thralls can't be resurrected (Galvek, Robert the Strong,
  Vanstrom Klause, Fragment of Seren, Lowerniel Drakan, Nightmare Zone).
- Leave a slot empty when its item makes no difference to the setup's rates or its special attack (a strength
  bonus short of the next max hit, a seeded set's leftover, a piece with only defence), instead of wearing or
  buying it. Fill empty slots still fills such slots with prayer or defence pieces.
- Zeal is assumed once your account has unlocked it (+25% Attack and +28% Strength over Piety), and each broken
  Fractured Archive elemental fragment adds 2 to its element's spells.
- Hover text and setting descriptions are cut to under 10 words: item tooltips keep the name, where it's stored
  and what matters most, and drop the rest when it doesn't fit. A few lock reasons and long names can run longer.
- Add **Kills per trip** (default 1). Each setup now shows its prayer points per kill (its offensive prayer and
  the target's overhead), the trip's combat time from its TTK, and the boost and prayer restore bottles the trip
  needs: super restores when you own them or Sanfew serums, otherwise prayer potions. Nightmare Zone potions are
  only packed for Nightmare Zone targets, and a wild pie or butterfly jar counts its own bites or sips.
- Fill the trip's free inventory slots with the best food you own (sharks if none), with owned cooked karambwans
  and Saradomin brews (beside super restores) mixed in from four free slots. The bank layout places the restores
  and food after the boosts and outlines them; up to three other owned foods are offered in the food's place.
  A two-handed spec weapon leaves a slot for the shield it takes off.
  Turn it off with **Fill inventory with food**.
- Against a monster whose attacks poison or envenom you (125 poison, 40 envenom, from the Wiki's monster data),
  the trip brings a superantipoison or an anti-venom+, unless a serpentine helm makes you immune.
- Each setup shows the target's overhead protection prayer: one prayer, a switch between several, or none needed,
  from the Wiki's strategy pages and the monster's attack styles, with a melee setup's own advice where it stands
  in melee range. It is static advice per target. Prayer per kill counts that overhead, and none when the target needs none or
  has no advice.
- Gear searches are exact: each weapon's setup is now the best combination of your allowed gear for its
  ordinary attacks, where the old search could stop a few pieces short. Across 1,872 test searches (every boss,
  each class, a 10m budget and best in slot) 280 found a better setup and none a worse one, in under half
  the time. The **Search depth** setting is gone.
- A setup killing faster with poison, venom or burns is never credited a slightly slower kill than a weaker one
  (up to 0.07% DPS).

## 1.1.3 (2026-11-10)

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

## 1.1.2 (2026-11-07)

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

## 1.1.1 (2026-10-30)

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

## 1.1.0 (2026-10-10)

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

## 1.0.0 (2026-10-09)

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
