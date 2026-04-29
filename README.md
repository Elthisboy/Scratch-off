# Scratch-off

## Project Identity
- **Name:** Scratch-off
- **Mod ID:** `scratch-off`
- **Version:** `1.0.1` (Resolved at build time)

## Technical Summary
The **Scratch-off** mod introduces a lottery-style scratch ticket mechanic to Fabric. It registers three functional items into the `TOOLS` creative tab corresponding to different rarities (Common, Rare, Epic). The core logic relies on a weighted Random Number Generator (RNG) paired with distinct JSON configuration files for each tier. When a player interacts with a ticket, the mod calculates the outcome securely on the server and directly executes configured server commands (e.g., `/give`) to distribute rewards, supporting both primary jackpots and secondary consolation prizes.

## Feature Breakdown
- **Tiered Ticket System:** Introduces `SCRATCH_TICKET_COMMON`, `SCRATCH_TICKET_RARE`, and `SCRATCH_TICKET_EPIC` physical items.
- **Weighted RNG Calculation:** Implements a robust weighted selection algorithm to ensure reliable, configurable odds for specific prizes.
- **Command-Driven Rewards:** Instead of hardcoding item drops, winning a scratch-off triggers dynamic server commands (replacing `%player%` with the user), allowing seamless integration with economy mods or custom loot.
- **Consolation Logic:** The JSON schema supports assigning separate commands for perfect matches (jackpots) and partial matches (consolation prizes).

## Command Registry
*Note: This mod does not introduce any traditional chat commands. All mechanics are triggered by right-clicking the physical scratch ticket items in-game.*

## Configuration Schema
The mod generates three separate configuration files in the `config/` folder corresponding to the ticket tiers (`scratch-off-common.json`, `scratch-off-rare.json`, `scratch-off-epic.json`). 

Example schema for a tier file:

```json
{
  "prizes": [
    {
      "itemId": "minecraft:iron_ingot",
      "nameKey": "prize.scratch-off.common.iron",
      "color": "FFDDDDDD",
      "weight": 1,
      "isLose": false,
      "command": "give %player% minecraft:iron_ingot 8",
      "consolationCommand": "give %player% minecraft:iron_ingot 2"
    },
    {
      "itemId": "minecraft:skeleton_skull",
      "nameKey": "prize.scratch-off.lose",
      "color": "FF884444",
      "weight": 4,
      "isLose": true
    }
  ]
}
```

## Developer Info
- **Author:** El_this_boy
- **Platform:** Fabric 1.21.1
