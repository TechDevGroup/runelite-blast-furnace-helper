# Blast Furnace Helper

A RuneLite plugin that provides a trip computer, click-target highlights, and coffer balance tracking for Blast Furnace runs. **This plugin is highlight-only and never performs any automated input**, as required by RuneLite's Plugin Hub rules.

## Features

- **Click-target highlights**: Highlights the next object to click (conveyor belt, bar dispenser, bank chest) based on the current trip state.
- **Bank item highlights**: When the bank is open, highlights the items you need to withdraw (coal, ore, coal bag, stamina potions, coins for coffer refill).
- **Trip computer overlay**: Shows session runtime, bar type, current state, per-hour rates (bars/hr, ore/hr), coffer balance, estimated time remaining, and standing coffer cost.
- **Coffer tracking**: Reads the coffer balance from a game varbit every tick and highlights the coffer and/or bank coins when the balance is low or critical.
- **State machine**: Tracks your trip phase automatically, self-correcting from inventory observations each tick.

## Coffer (GP Renewal) Tracking

The Blast Furnace coffer drains 72,000 gp/hr (1,200 gp/min, 12 gp/tick) on Blast Furnace worlds while the furnace is operating (OSRS Wiki "Blast Furnace", retrieved 2026-07-07). The foreman fee (for players below 60 Smithing) is a separate 2,500 gp per 10 minutes. The coffer maximum capacity is 20,000,000 gp.

### Coffer States

| State | Condition | Highlight |
|---|---|---|
| OK | Balance above low threshold | No coffer highlight |
| LOW | Time remaining < `cofferLowMinutes` (default 20 min ≈ 24,000 gp) | Coffer object highlighted in warning color; trip computer shows time left in warning color |
| CRITICAL | Balance ≤ `cofferCriticalGp` (default 0 = empty only) | Coffer highlighted in critical color; trip computer shows "EMPTY — refill!" alert |

Additional behaviour (highlight-only, no automation):
- **Bank open + low/critical**: Coins (item 995) are highlighted in the bank so you know to withdraw and top up the coffer.
- **Holding coins + low/critical**: The coffer is highlighted in the world so you can walk to it and deposit.

### Coffer Source Citations

- **Varbit `VAR_COFFER = 5357`** (`VarbitID.BLAST_FURNACE_COFFER`): RuneLite built-in `BlastFurnaceCofferOverlay` reads the coffer balance via `client.getVarbitValue(VarbitID.BLAST_FURNACE_COFFER)`. Source: `runelite-api/src/main/java/net/runelite/api/gameval/VarbitID.java` ([github.com/runelite/runelite](https://github.com/runelite/runelite), BSD-2-Clause).
- **Coffer object IDs** (29328 = empty, 29329 = full, 29330 = active — `BLAST_FURNACE_AUTOMATA_COFFER_*`): Source: `runelite-api/src/main/java/net/runelite/api/gameval/ObjectID.java` ([github.com/runelite/runelite](https://github.com/runelite/runelite), BSD-2-Clause). Cross-checked against OSRS Wiki "Coffer (Blast Furnace)" (object IDs 29328/29329).
- **Drain rates** (72,000 gp/hr, 1,200 gp/min, 12 gp/tick; max 20,000,000 gp; foreman 2,500/10 min): [OSRS Wiki — Blast Furnace](https://oldschool.runescape.wiki/w/Blast_Furnace), retrieved 2026-07-07.

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

| Key | Default | Description |
|---|---|---|
| `barType` | AUTO | Override bar type; AUTO detects from inventory ore |
| `staminaThreshold` | 50% | Highlight stamina potions when run energy is below this |
| `bankItemColor` | Green | Color for bank item highlights |
| `objectColor` | Orange | Color for conveyor belt / dispenser / bank chest highlights |
| `showPanel` | On | Toggle the stats overlay |
| `cofferEnabled` | On | Enable coffer balance tracking and highlights |
| `cofferLowMinutes` | 20 min | Highlight coffer as LOW when time remaining is below this |
| `cofferCriticalGp` | 0 gp | Highlight coffer as CRITICAL when balance ≤ this amount |
| `cofferLowColor` | Yellow | Color for coffer LOW state highlights and panel text |
| `cofferCriticalColor` | Red | Color for coffer CRITICAL/EMPTY highlights and panel alert |

## Source Citations

- **Region ID (7757) and object IDs** (conveyor belt 9100, bar dispenser 9104, bank chest 26707): RuneLite built-in `BlastFurnacePlugin` ([github.com/runelite/runelite](https://github.com/runelite/runelite), BSD-2-Clause)
- **Varbit IDs** (coal stored 1611, bar dispenser state 1617): RuneLite `Varbits` enum
- **Varbit 5357** (coffer balance): RuneLite `gameval/VarbitID.java` — `VarbitID.BLAST_FURNACE_COFFER` (BSD-2-Clause)
- **Coffer object IDs** (29328/29329/29330): RuneLite `gameval/ObjectID.java` — `BLAST_FURNACE_AUTOMATA_COFFER_*` (BSD-2-Clause); cross-checked OSRS Wiki "Coffer (Blast Furnace)"
- **Item IDs**: RuneLite `ItemID` constants and OSRS Wiki
- **Coal ratios** (0/1/2/3/4 coal per bar for iron/steel/mithril/adamantite/runite): [OSRS Wiki — Blast Furnace](https://oldschool.runescape.wiki/w/Blast_Furnace)
- **Coal bag capacity** (27): OSRS Wiki
- **Coffer drain rates** (72,000 gp/hr, 1,200 gp/min, max 20M gp): OSRS Wiki "Blast Furnace", 2026-07-07

## Structural Reference

quest-helper (github.com/Zoinkwiz/quest-helper, BSD-2) served as the pattern reference for overlay layering conventions (ABOVE_SCENE for world highlights, ALWAYS_ON_TOP for widget highlights) and hub registration structure.

## Changelog

### v0.2.0
- **Coffer tracking**: reads coffer balance from varbit 5357 (`VarbitID.BLAST_FURNACE_COFFER`) every game tick.
- **Coffer scene highlight**: highlights the coffer object (IDs 29328/29329/29330) in LOW (yellow) or CRITICAL (red) state when balance is low or empty.
- **Bank coins highlight**: when bank is open and coffer is low/critical, coins (item 995) are highlighted to prompt withdrawal for refill.
- **Holding-coins hint**: when holding coins and coffer is low/critical, the coffer object is highlighted for deposit.
- **Trip computer coffer section**: adds coffer balance, estimated time remaining, and 72,000 gp/hr cost line to the panel overlay.
- **Coffer config**: `cofferEnabled`, `cofferLowMinutes` (default 20), `cofferCriticalGp` (default 0), `cofferLowColor`, `cofferCriticalColor`.
- Bumped version to 0.2.0.

### v0.1.0
- Initial release: trip computer, click-target highlights, bank item highlights, state machine.

## Sideloading / Development

To load locally without Plugin Hub:

1. Build: `./gradlew build` (requires JDK 11)
2. In RuneLite launcher, add `--dev` flag or use **RuneLite → Plugin Hub → Load from file**
3. Point to `build/libs/runelite-blast-furnace-helper-0.2.0.jar`

Alternatively, place the jar in `~/.runelite/plugins/` (external plugin folder if configured).

## License

BSD-2-Clause © 2024 TechDevGroup
