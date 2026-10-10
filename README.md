# Best Gear Setup

Find the best gear for any monster or boss without leaving RuneLite. Stick to what you own, shop within a
budget, or go straight to best in slot.

![Best setups for Vorkath (best in slot), Yama (owned items) and Nylocas Vasilias (inventory + equipped)](https://raw.githubusercontent.com/aknaus1/best-gear-setup/main/docs/images/results.png)

## Getting started

1. Install **Best Gear Setup** from the Plugin Hub.
2. Log in and open your bank once so the plugin can see what you own.
3. Open the **Best Gear Setup** sidebar panel, or right-click an NPC and choose **Best setup**.
4. Search for a monster, pick a mode and click **Find best setup**.

Right-click an item in a result to lock it, exclude it or mark it as owned.

## Features

- **Any monster or boss**, with versions and phases (Vorkath, Verzik, Yama and so on) in one search.
- **Four modes:** *Owned items only*, *Inventory + equipped only*, *Owned + budget* (e.g. `50m`) and
  *Best in slot*.
- **A result per attack style** with DPS, max hit, accuracy, time to kill, kills per hour and the cost of
  anything you'd need to buy, plus the next-best alternatives.
- **Knows your account:** owned items, levels, prayers, Slayer task and diaries, remembered per profile.
- **Potions, prayers, thralls and special attacks** are all counted, with the best spec weapon suggested.
- **Bank gear layout and highlights** lay out the setup's gear, runes and potions in your bank, ready to
  withdraw.
- **Encounter rules are enforced:** damage caps, immunities, dragonfire protection, raid scaling and
  required Slayer gear.

![The RuneLite client with the bank gear layout, highlighted inventory and Verzik Vitur result in the side panel](https://raw.githubusercontent.com/aknaus1/best-gear-setup/main/docs/images/bank-layout.png)

The **Fight** panel sets your HP, Slayer task, raid party size, Wilderness and pre-fight defence
reductions. **Settings** covers search options, attack styles, equipment, spellbooks, boosts and
highlights.

![Fight panel and plugin settings](https://raw.githubusercontent.com/aknaus1/best-gear-setup/main/docs/images/settings.png)

## Good to know

- Results are expected DPS, not a simulated fight: switch timing, overkill, movement and line of sight
  aren't modelled. See [COMBAT_AUDIT.md](COMBAT_AUDIT.md) for known gaps and
  [docs/mechanics.md](docs/mechanics.md) for how everything is worked out.
- All data is bundled and the search runs locally, so the plugin makes no network requests. Prices come
  from RuneLite.

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
