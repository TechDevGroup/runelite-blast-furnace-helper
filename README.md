# Blast Furnace Helper

A RuneLite plugin that provides a trip computer and click-target highlights for Blast Furnace runs. **This plugin is highlight-only and never performs any automated input**, as required by RuneLite's Plugin Hub rules.

## Features

- **Click-target highlights**: Highlights the next object to click (conveyor belt, bar dispenser, bank chest) based on the current trip state.
- **Bank item highlights**: When the bank is open, highlights the items you need to withdraw (coal, ore, coal bag, stamina potions).
- **Trip computer overlay**: Shows session runtime, bar type, current state, and per-hour rates (bars/hr, ore/hr).
- **State machine**: Tracks your trip phase automatically, self-correcting from inventory observations each tick.

## State Machine Phases

| Phase | Description |
|---|---|
| `IDLE` | Outside Blast Furnace or plugin just started |
| `BANK_WITHDRAW_COAL_1` | Bank open; withdraw coal + coal bag (+ stamina if low energy) |
| `BELT_DEPOSIT_COAL` | Walk to conveyor belt; deposit coal, empty coal bag |
| `BANK_WITHDRAW_ORE` | Bank open; withdraw ore (+ refill coal bag for high-ratio bars) |
| `BELT_DEPOSIT_ORE` | Walk to conveyor belt; deposit ore |
| `AWAITING_BARS` | Waiting for furnace to smelt (bar dispenser varbit > 0) |
| `COLLECT_BARS` | Click bar dispenser to collect bars |
| `BANK_DEPOSIT_BARS` | Bank open; deposit bars, start next cycle |

## Adamantite 3:1 Loop

Adamantite bars require 3 coal per bar at Blast Furnace (half the normal 6:1 ratio, per OSRS Wiki). The loop:
1. Withdraw 27 coal + fill coal bag (27 more) → 54 coal total
2. Deposit all coal on belt
3. Withdraw 14 adamantite ore
4. Deposit ore on belt
5. Wait for dispenser → collect bars
6. Deposit bars → repeat

## Configuration

| Option | Default | Description |
|---|---|---|
| Bar Type | AUTO | Override bar type; AUTO detects from inventory ore |
| Stamina Threshold (%) | 50 | Highlight stamina potions when run energy is below this |
| Bank Item Highlight Color | Green (semi-transparent) | Color for bank item highlights |
| Object Highlight Color | Orange | Color for conveyor belt / dispenser / bank chest highlights |
| Show Trip Computer | On | Toggle the stats overlay |

## Source Citations

- **Region ID (7757) and object IDs** (conveyor belt 9100, bar dispenser 9104, bank chest 26707): RuneLite built-in `BlastFurnacePlugin` ([github.com/runelite/runelite](https://github.com/runelite/runelite), BSD-2-Clause)
- **Varbit IDs** (coal stored 1611, bar dispenser state 1617): RuneLite `Varbits` enum
- **Item IDs**: RuneLite `ItemID` constants and OSRS Wiki
- **Coal ratios** (0/1/2/3/4 coal per bar for iron/steel/mithril/adamantite/runite): [OSRS Wiki — Blast Furnace](https://oldschool.runescape.wiki/w/Blast_Furnace)
- **Coal bag capacity** (27): OSRS Wiki

## Structural Reference

quest-helper (github.com/Zoinkwiz/quest-helper, BSD-2) served as the pattern reference for overlay layering conventions (ABOVE_SCENE for world highlights, ALWAYS_ON_TOP for widget highlights) and hub registration structure.

## Sideloading / Development

To load locally without Plugin Hub:

1. Build: `./gradlew build` (requires JDK 11)
2. In RuneLite launcher, add `--dev` flag or use **RuneLite → Plugin Hub → Load from file**
3. Point to `build/libs/runelite-blast-furnace-helper-0.1.0.jar`

Alternatively, place the jar in `~/.runelite/plugins/` (external plugin folder if configured).

## License

BSD-2-Clause © 2024 TechDevGroup
