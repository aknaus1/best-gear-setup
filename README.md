# Best Gear Setup

A DPS calculator and gear optimizer built into RuneLite. Find melee, ranged and magic setups for
monsters, bosses and Slayer using gear you own, a budget or best in slot.

![Best setups for Vorkath, Yama and Verzik Vitur](https://raw.githubusercontent.com/aknaus1/best-gear-setup/main/docs/images/results.png)

## Getting started

1. Install **Best Gear Setup** from the Plugin Hub.
2. Log in and open your bank once to record your owned gear.
3. Open the sidebar panel, or right-click an NPC and choose **Best setup**.
4. Search for a monster, pick a mode and click **Find best setup**:

   - **Owned items only**
   - **Inventory + equipped only**
   - **Owned + budget** (for example, `50m`)
   - **Best in slot**

Right-click result items to lock, exclude or mark them as owned. Adjust combat options in **Fight**,
manage item rules in **Gear**, and set equipment preferences in **Settings**.

## Features

- Compare DPS, max hit, accuracy, estimated kill time and upgrade costs across attack styles.
- Plan complete **CM CoX, ToA and ToB** kits with shared switches, supplies and encounter setups that
  follow live raid progress. Choose your ToB role, ToA path order and switch limits.
- In **CM CoX**, red means finished for the raid, yellow means unused for the rest of this floor, and
  green means needed for this floor (or the next floor after completing this one). Chest guidance shows
  red/yellow in inventory and green/yellow in private storage. Inventory also highlights the selected
  encounter's gear to equip, including with storage closed. Hover over **Inventory** for chest advice and a
  next-floor preview when everything is still needed; no extra text section expands the side panel.
  Advice includes the selected room and all its phases; supplies and puzzle/prep items stay outside it.
  Enable **Highlight inventory gear** for equip outlines; chest guidance appears only while private storage is open.
- Account for levels, prayers, boosts, thralls, special attacks and encounter requirements.
- Use bank layouts and gear highlights to prepare your setup. Enable
  [Dude, Where's My Stuff?](https://runelite.net/plugin-hub/show/dude-wheres-my-stuff) to include recorded
  gear from other storage.
- Save recent searches per account for quick reuse. Calculations run locally with bundled game data
  and RuneLite prices.

![Bank gear layout, inventory highlights and sidebar results](https://raw.githubusercontent.com/aknaus1/best-gear-setup/main/docs/images/bank-layout.png)

Results are estimates; movement, switch timing and downtime aren't simulated. Raid times cover combat
only. See [mechanics and assumptions](docs/mechanics.md) and [known calculation gaps](COMBAT_AUDIT.md).

## Support

[Join the Discord community](https://discord.gg/gxFXvnUrUs) for setup help, gear discussions, FAQs and
feature requests.

[Report a bug](https://github.com/aknaus1/best-gear-setup/issues) with the monster, version, mode and
**Search details** text. Leave out your account name and whole bank.

<a href="https://www.buymeacoffee.com/aknaus" target="_blank"><img src="https://img.buymeacoffee.com/button-api/?text=Buy%20me%20a%20beer&amp;emoji=%F0%9F%8D%BA&amp;slug=aknaus&amp;button_colour=FFDD00&amp;font_colour=000000&amp;font_family=Cookie&amp;outline_colour=000000&amp;coffee_colour=ffffff" alt="Buy me a beer" height="39"></a>

## License

Code: [BSD 2-Clause](LICENSE). Game data: [OSRS Wiki](https://oldschool.runescape.wiki/) contributors,
[CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/).
See [third-party notices](THIRD_PARTY_NOTICES.md) and [building from source](CONTRIBUTING.md).
Not affiliated with Jagex or the OSRS Wiki.
