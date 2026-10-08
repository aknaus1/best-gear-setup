# Best Gear Setup

A [RuneLite](https://runelite.net/) plugin that works out the best gear for any monster or boss without
leaving the client. It can stick to what you own, shop within a budget, or go straight to best in slot.
Monster, equipment, spell, prayer and potion data comes from [OSRS Wiki](https://oldschool.runescape.wiki/)
snapshots bundled with the plugin, so it makes no network requests. Damage follows the OSRS Wiki's combat
formulas and prices come from RuneLite; see [source attribution](THIRD_PARTY_NOTICES.md).

## Getting started

1. In RuneLite, open **Configuration** (the wrench icon), go to **Plugin Hub**, search for *Best Gear Setup*
   and install it.
2. Log in and open your bank once so the plugin can see what you own.
3. Open the **Best Gear Setup** panel from the sidebar, or right-click an attackable NPC and choose
   **Best setup**.
4. Search for a monster, pick its version, choose a mode (and a budget, if you're using one), then click
   **Find best setup**.

You'll get the best melee, ranged and magic setups, each with its DPS and anything you'd need to buy.
Right-click an item in a result to lock it, exclude it or mark it as owned.

## Features

- **Any monster or boss.** Search by name or right-click an NPC. Variants and phases (Vorkath, Verzik's
  modes, the Abyssal Sire's phases and so on) share one result with a version picker.
- **Three modes:** *Owned items only*, *Owned + budget* (your gear plus anything you can afford, e.g. `50m`)
  and *Best in slot*.
- **One result per attack style** (stab, slash, crush, ranged, magic and the eclipse atlatl), showing DPS,
  max hit, accuracy, time to kill and what any missing pieces cost at live GE prices.
- **It knows your account.** Owned items, combat levels, prayer unlocks, your Slayer task and diaries are
  read in game and remembered per account, so a search while logged out matches one while logged in.
- **Boosts and specials.** Potions, prayers, thralls and special attacks all count, and specials are worked
  into each kill as far as your spec energy allows.
- **Bank and inventory highlights** outline the selected setup. The gear button in the bank title bar opens
  a **bank gear layout** with the setup's gear, runes, potions and spec weapon, ready to withdraw.
- **Encounter rules are enforced**, including reach restrictions, damage caps, immunities, dragonfire
  protection and mandatory Slayer gear.
- **Optional atlatl ammo recovery.** In Settings → Equipment, enable **Require Ava's / quiver for atlatl**
  to require an Ava's device, an assembler cape or Dizana's quiver in atlatl setups. It is off by default;
  ownership, budget, exclusions and cape locks still apply.

## Using the panel

The sidebar has the monster search, version and boss phase, mode, budget, **Find best setup** and
**Clear search**, with the results underneath. It also has three buttons:

- **Fight**: your HP, Slayer task override, AoE, Wilderness (including a cap on expensive items), raid
  settings and other assumptions about the fight.
- **Gear**: your locks, exclusions and the items you've marked as owned.
- **Settings**: opens the plugin's page in RuneLite's configuration panel, covering attack styles,
  equipment, spellbooks, potions and boosts, experience filters, slot filling, pre-fight preparation and
  highlights.

Fight and Gear open in the sidebar with a **Back to results** button.

**Search details**, below the gear, lists the snapshot dates and every assumption behind a result.

For the full rules on ownership, pricing, potions, encounter mechanics and special attacks, see
[How Best Gear Setup works](docs/mechanics.md).

## Limitations

Results are the expected DPS of a setup, not a simulated fight. The plugin doesn't model weapon-switch
timing, overkill, protection-prayer timing, when a boss can be attacked, movement or line of sight.
[COMBAT_AUDIT.md](COMBAT_AUDIT.md) lists the known gaps.

## Data and privacy

Everything the plugin needs is bundled and the search runs on your computer, so it makes no network
requests. Item prices come from RuneLite's own price data. Your owned items, remembered levels and unlocks
are saved in RuneLite's configuration for each RuneScape profile. Like any other plugin setting, they only
sync if you're signed in to a RuneLite account.

The bundled monster, equipment and immunity snapshots come from the
[Old School RuneScape Wiki](https://oldschool.runescape.wiki/) contributors under
[CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/), with requirement parameters read from
the game cache. See [data attribution and licensing](THIRD_PARTY_NOTICES.md), and
[CONTRIBUTING.md](CONTRIBUTING.md#bundled-data) if you want to regenerate the data.

## Support

Report bugs and request features on the [issue tracker](https://github.com/aknaus1/best-gear-setup/issues).
If a result looks wrong, include the monster, version, mode, budget and the text under **Search details**,
which records the snapshot dates and assumptions behind the search. Please don't post your account name or
your whole bank.

## Documentation

- [docs/mechanics.md](docs/mechanics.md): ownership, pricing, the side panel and settings, encounter rules
  and how the numbers are worked out.
- [COMBAT_AUDIT.md](COMBAT_AUDIT.md): calculation order, adjustments, sources, validation and known gaps.
- [docs/equipment-requirements.md](docs/equipment-requirements.md): how the bundled snapshots and wear
  requirements are built.
- [CONTRIBUTING.md](CONTRIBUTING.md): running from source, tests, regenerating data and releasing.
- [CHANGELOG.md](CHANGELOG.md): what changed in each version.

## License

The plugin code is released under the [BSD 2-Clause licence](LICENSE). The bundled OSRS Wiki data stays
under [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/); see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Best Gear Setup isn't affiliated with Jagex or the
OSRS Wiki.
