# Best Gear Setup

A DPS calculator and gear optimizer for monsters, bosses and Slayer, built into RuneLite. Compare melee,
ranged and magic equipment to find your best loadout. Stick to what you own, shop within a budget, or go
straight to best in slot.

![Best setups for Vorkath (best in slot), Yama (owned + budget) and Verzik Vitur (owned items only)](https://raw.githubusercontent.com/aknaus1/best-gear-setup/main/docs/images/results.png)

## Getting started

1. Install **Best Gear Setup** from the Plugin Hub.
2. Log in and open your bank once so the plugin can see what you own.
3. Open the **Best Gear Setup** sidebar panel, or right-click an NPC and choose **Best setup**.
4. Search for a monster, pick a mode and click **Find best setup**.

Right-click an item in a result to lock it, exclude it or mark it as owned, or add these rules from the
**Gear** panel.

## Features

- **Any monster or boss**, with versions and phases (Vorkath, Verzik, Yama and so on) in one search.
- **Complete raid plans:** search for *Challenge Mode: Chambers of Xeric*, *Tombs of Amascut* or
  *Theatre of Blood* (also `CM CoX`, `ToA` or `ToB`). Start wearing the first room's gear, with a shared
  inventory of switches. The boss selector above **Version** lists the rooms in raid order and changes the
  equipment shown. The bank shows starting worn gear on the left and
  the shared inventory in four columns beside it.
  The summary shows the raid's **estimated combat time**; hover it for each boss's share. It counts each
  boss's party-scaled HP in whole attacks (overkill included), shares one HP pool between a fight's phases,
  counts repeated adds such as Maiden's and the waves' nylocas, and divides by the party size, assuming
  teammates match your DPS. It's kill time only, not puzzles, walking or downtime between rooms.
- Pick each raid encounter's **Attack style** (Auto uses ranged or magic for Mystics, the large Muttadile
  and Zebak), the **ToA path order** (Auto picks the faster of the two orders), and your **ToB role**:
  South freeze, North freeze, Range DPS or Melee DPS.
  Freezers freeze Maiden's nylocas and everyone else kills them: South only freezes, North freezes then
  helps with the unclumped nylocas, Range DPS helps with those until the clump forms then chins it, and
  Melee DPS helps with them too. South mages Nylo, North melees Nylo in 3/4-player teams or mages in 5,
  Range DPS ranges it and Melee DPS melees it. The Vasilias changes form at random after a couple of hits,
  so the plan wears a full setup for one form and quick-switches for the other two: by default the weapon
  and at most one other piece, using the same extra slot for both switches. Every role compares melee, ranged
  and magic full setups and keeps the faster compatible kit. ToB rooms are fought boosted. Maiden is prepotted;
  the kit carries estimated refresh doses when its boost expires, including a long first room or later rooms
  (e.g. a Melee DPS ranging Xarpus brings a ranging potion); a quick
  switch is only boosted when that potion is brought anyway. Potion icons are tagged *Pre*, *Bring* or
  *Both* so you know which to drink before entering and which to carry. Freezers require Ancient and Ice Barrage; the clump setup
  requires chinchompas. Freeze setups can manually cast Ice Barrage with owned magic weapons that cannot
  autocast Ancient spells; the result labels this **Manual cast** and keeps the spell's five-tick speed.
  The freeze is a switch from your Maiden setup with the fewest items that never splash: Maiden's nylocas
  are always frozen once the magic attack roll reaches (base Magic + 9) × 204 (+104 magic attack at 99 with
  Augury), autocasting Ice Barrage rather than casting by hand whenever an autocasting weapon gets there.
  South freeze also hearts first when you have an imbued or saturated heart.
  Everyone still fights each Vasilias form. Select your role in the new selector;
  it defaults to Melee DPS and replaces the separate Maiden and Nylocas choices.
  Overloads are assumed after Tekton and smelling salts after the first two ToA paths. Tekton and the
  first ToA path are prepotted. The initial dose is excluded; estimated refresh doses are packed as individual
  four-dose bottles. Hover bottles for dose totals. Ordinary boosts decay between the five-minute refreshes;
  unknown downtime can require extras. Hearts are reusable, and raid-provided boosts need no carried bottle.
- **Quick switch swaps** under **Fight → Raid** (default 2) sets the most swaps a raid quick switch may
  make, weapon included, for the ToB Vasilias forms and the ToA Apmeken baboons. It's a limit, not a target:
  a piece is only swapped when it makes the switch faster, and quick switches share their extra slots so
  switching between any two forms stays within it. The baboons always take a max hit from the style they're
  weak to, so one baboon wears a full setup and the others switch only the pieces that raise the max hit.
- ToA plans have **ToA starting path level** (0-3), **Walk the Path** and **More Overlords** under **Fight → Raid**.
  Walk the Path estimates HP from the random level increases in your selected path order. Detected live
  levels take precedence for each boss and its supporting targets. Individual encounters retain **ToA path level** (0-6).
  More Overlords counts Kephri's extra soldier and spitting scarabs in the raid's combat time.
- **Protection prayers:** every result names the overhead to use with that setup (or *Switch* with a short
  reason, such as Olm, Zebak or Verzik P3), from its equipment, your attack distance and Prayer level.
  Vorkath uses Missiles with a ranged fire-shield setup and Magic with melee or other ranged setups.
  Other mixed attackers get a fixed prayer based on estimated incoming damage; hover for assumptions.
  Encounter rules distinguish attack-launch checks, delayed / impact checks, counted cycles and phase
  changes. See [the catalogue overhead audit](docs/overhead-audit.md) for coverage and remaining data gaps.
  Melee-only monsters still get Protect from Melee at range unless you safespot them.
- The sidebar shows inventory below worn gear. Runes fill an owned rune pouch (three types) or divine
  rune pouch (four types); hover the pouch to see its planned contents. Overflow rune types stay loose.
  The inventory starts minimized; click its heading to expand it, and it stays that way for later results.
  Sidebar and bank inventories group melee, ranged and magic switches, then specials, tools, potions and runes.
  Each gear group follows weapon, offhand, head, body, legs, cape, neck, gloves, boots, ring and ammo order;
  items sharing a slot sort by name, so encounter order does not shuffle the switches.
  Switch groups pack consecutively across the four columns, with free supply slots at the end.
  Quick weapon switches keep inherited armour in its original combat group.
- Raid setups keep required ammo worn when the slot is free. If several ammo types are needed, empty
  slots use the most-used type; bow and crossbow setups retain their own ammunition. Hover a blowpipe
  to see its loaded darts, and right-click it to exclude those darts. Inventory gear has the same hover
  details and right-click controls as worn equipment.
- **Raid equipment slots** is the most slots carried switches, the rune pouch, loose runes and a carried
  Book of the dead may use (boosts and food don't count); **Raid spellbook** defaults to Auto, comparing complete
  feasible kits by estimated raid combat time with one book throughout the raid. Thralls count when enabled
  and available; Standard fire spells can outweigh their value at CoX Ice Demon. ToB South/North freeze
  always lock Ancient for Ice Barrage. Manual choices remain available for other roles. Lunar/Vengeance
  damage is excluded until an incoming-damage model is available.
  Empty inventory slots are left for food, healing and utility supplies.
- **Active encounter** follows the attackable NPC you are in combat with (your hits land on it, or it attacks
  you back), not one you have only clicked or are standing near, including new spawns and phase changes.
  Another NPC of the monster already shown (the next kill of the same type) keeps the results without searching.
  With **Use active defence** enabled, tracked landed specials update its Defence. In a planned raid,
  recalculations use the selected shared kit.
- **Four modes:** *Owned items only*, *Inventory + equipped only*, *Owned + budget* (e.g. `50m`) and
  *Best in slot*.
- **A result per attack style** with DPS, max hit, accuracy, time to kill, kills per hour and the cost of
  anything you'd need to buy, plus the next-best alternatives.
- **Knows your account:** owned items, levels, prayers, Slayer task and diaries, remembered per profile.
- **Stored gear:** enable [Dude, Where's My Stuff?](https://runelite.net/plugin-hub/show/dude-wheres-my-stuff)
  to include its recorded STASH units, POH costume storage, deathbanks, group storage and other item storage
  in owned gear. Hover gear to see its recorded locations. Inventory + equipped only still uses carried gear.
- **Potions, prayers, thralls and special attacks** are all counted, with the best spec weapon suggested.
- **Bank gear layout and highlights** lay out the setup's gear, runes and potions in your bank, ready to
  withdraw. Inventory highlights mark only the gear to wear for the displayed setup, following the selected
  raid encounter; carried switches and supplies stay unoutlined.
  Owned kitted or recoloured equivalents, such as Masori assembler, take priority over base copies in the
  bank; the base remains a fallback when the decorated item isn't available there.
- **Encounter rules are enforced:** damage caps, immunities, dragonfire protection, raid scaling and
  required Slayer gear.
- **Require Ava's / quiver** under Settings → Equipment applies to every ranged attack whose ammunition an
  Ava's device recovers (arrows, bolts, darts, knives, thrownaxes, javelins, atlatl darts), including raid
  setups and quick switches; chinchompas, ammo-free weapons and special-attack switches are unaffected. A usable recovery cape
  must fit your ownership, budget, exclusions and locks.

![The RuneLite client with the bank gear layout, highlighted inventory and Yama result in the side panel](https://raw.githubusercontent.com/aknaus1/best-gear-setup/main/docs/images/bank-layout.png)

Everything else is in the sidebar too. **Fight** sets active encounter, your HP, Slayer task, thralls,
special attacks, raid scaling and kit limits, Wilderness and pre-fight defence reductions. **Gear** lists
your locks, excluded items and extra owned items, with a search to add more. **Settings** covers search
options, attack styles, equipment, spellbooks, boosts and highlights.

![The Fight and Settings panels in the sidebar](https://raw.githubusercontent.com/aknaus1/best-gear-setup/main/docs/images/settings.png)

## Good to know

- Results are expected DPS, not a simulated fight: switch timing, overkill, movement and line of sight
  aren't modelled. See [COMBAT_AUDIT.md](COMBAT_AUDIT.md) for known gaps and
  [docs/mechanics.md](docs/mechanics.md) for how everything is worked out.
- All data is bundled and the search runs locally, so the plugin makes no network requests. Prices come
  from RuneLite.
- Recent completed searches are saved per RuneScape profile for fast repeats, including after restarting
  the client. Within a session, raid plans can also reuse
  encounter calculations when re-planning the kit. Prices, ownership, levels, fight settings and live raid
  inputs are checked before reuse; moving owned gear between containers preserves the match. The cache
  keeps each account separate and discards incompatible saved results after plugin or bundled-data changes.
- With a raid selected, right-click **Best setup** on one of its monsters to open that encounter in the
  existing plan. It keeps the current search and kit; a click during planning opens the encounter afterward.
- Selected raid plans also follow live progress automatically: show the starting gear on entry and the next
  room's saved setup after a boss is defeated. The sidebar shows completed rooms, and ToA follows the path
  you actually enter. This works with **Active encounter** off and does not launch searches. While tracking
  a raid, consuming ammunition or picking up loot preserves the packed kit; explicitly search again or change
  settings to re-plan it. Tracking resets when you leave the raid or change accounts.

## Support

Report bugs on the [issue tracker](https://github.com/aknaus1/best-gear-setup/issues). If a result looks
wrong, include the monster, version, mode and the text under **Search details**, but not your account name
or whole bank.

## License

Code is under the [BSD 2-Clause licence](LICENSE). Monster, equipment and immunity data comes from the
[Old School RuneScape Wiki](https://oldschool.runescape.wiki/) contributors under
[CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/); see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Not affiliated with Jagex or the OSRS Wiki.
To build from source, see [CONTRIBUTING.md](CONTRIBUTING.md).

<a href="https://www.buymeacoffee.com/aknaus" target="_blank"><img src="https://img.buymeacoffee.com/button-api/?text=Buy%20me%20a%20beer&amp;emoji=%F0%9F%8D%BA&amp;slug=aknaus&amp;button_colour=FFDD00&amp;font_colour=000000&amp;font_family=Cookie&amp;outline_colour=000000&amp;coffee_colour=ffffff" alt="Buy me a beer" height="39"></a>
