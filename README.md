# Blast Furnace Helper

A RuneLite plugin that provides a trip computer, click-target highlights, and coffer balance tracking for Blast Furnace runs. **This plugin is highlight-only and never performs any automated input**, as required by RuneLite's Plugin Hub rules.

## Features

- **State-derived guidance**: Every tick the plugin recomputes the single correct next action purely from observed game state (furnace coal/ore/bar varbits, dispenser state, inventory, coal-bag fullness, coffer balance). It is a pure function of state — no positional step counter — so it self-corrects if you arrive mid-cycle or act out of sequence.
- **Click-target highlights**: Highlights the one object the guidance points at (conveyor belt, bar dispenser, or bank chest).
- **Bank item highlights**: When the bank is open, highlights the single next item to withdraw — coal (bag fill first) **before** ore — plus coins when the coffer is low, and stamina potions when run energy is low.
- **Coal-bag emptying**: At the belt, highlights the coal bag in your inventory when it still holds coal and you have a free slot, so the bagged coal drops in to be dumped.
- **Trip computer overlay**: One consolidated panel showing next action, runtime, bar type, per-hour rates (bars/hr, ore/hr), coffer balance, estimated time remaining, and standing coffer cost — all auto-stacking so nothing overlaps.
- **Coffer tracking**: Reads the coffer balance from a game varbit every tick and highlights the coffer and/or bank coins when the balance is low or critical.
- **Reset stats**: Configurable hotkey, right-click **Reset stats** on the panel, and automatic reset when the bar type changes.

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

## State-Derived Guidance (Architecture)

`BFPolicy.derive(BFStateSnapshot)` is a pure, idempotent function: given only the current
observed state it returns exactly one next action. It keeps no step index, so it is
self-correcting — the same state always yields the same suggestion regardless of how you got
there. Inputs: coal in furnace (varbit 949), this bar type's ore/bar counts in the furnace,
dispenser state (varbit 936), inventory coal/ore/bar counts and free slots, coal-bag fullness,
and coffer balance. Priority order:

1. **Coffer critical** — withdraw coins (bank) or deposit them into the coffer (holding coins); the furnace halts when the coffer empties.
2. **Bars in inventory** → deposit at bank.
3. **Bars in the dispenser** → collect (or wait while still smelting).
4. **Bank open** → acquire the next material: **coal first** (fill bag, then loose coal), and only once coal is satisfied, the primary ore. One target at a time.
5. **At the belt** → empty the coal bag (if it holds coal and a slot is free), then deposit coal, then deposit ore.
6. Otherwise → return to the bank to restock.

Whether to bring coal or ore is decided from furnace state: coal is needed while
`furnaceCoal < max(furnaceOre, 1) × coalPerBar`, which naturally alternates coal and ore trips
the way the real Blast Furnace method does.

### Bar Types & Coal Ratios

Selectable via the `barType` config (or AUTO, inferred from inventory ore). Coal-per-bar at the
Blast Furnace (half the standard furnace, per OSRS Wiki, retrieved 2026-07-11):

| Bar | Coal per bar (BF) |
|---|---|
| Iron | 0 |
| Steel | 1 |
| Mithril | 2 |
| Adamantite | 3 |
| Runite | 4 |

Note: runite is **4** at the Blast Furnace (8 at a standard furnace — do not use the
standard value here).

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
| `resetHotkey` | (unset) | Hotkey to zero the trip computer and restart the timer |

## Source Citations

All identifiers below are cache-verified against the RuneLite `gameval` sources
([github.com/runelite/runelite](https://github.com/runelite/runelite), BSD-2-Clause), used for
factual constants only:

- **Region ID** (7757): RuneLite built-in `BlastFurnacePlugin`
- **Conveyor belt** (9100 = `BLAST_FURNACE_CONVEYER_BELT_CLICKABLE`, "Put-ore-on"): `gameval/ObjectID.java`
- **Bar dispenser** (9092 base + state variants 9093–9096 = `BLAST_FURNACE_DISPENSER` / `BLAST_FURNACE_ORE_DISPENSER_*`): `gameval/ObjectID.java`. The plugin tracks the whole family so the highlight follows the dispenser through its states. (The old value 9104 was wrong — it is `BLAST_FURNACE_CONVEYER_COGS2`, a cog.)
- **Coffer objects** (29328/29329/29330 = `BLAST_FURNACE_AUTOMATA_COFFER_*`): `gameval/ObjectID.java`; cross-checked OSRS Wiki "Coffer (Blast Furnace)"
- **Furnace varbits**: coal-stored 949 (`BLAST_FURNACE_COAL`), iron-ore 951, mithril-ore 952, adamantite-ore 953, runite-ore 954; bars iron 942 / steel 943 / mithril 944 / adamantite 945 / runite 946; dispenser state 936 (`BLAST_FURNACE_BARS_HOT`); coffer balance 5357 (`BLAST_FURNACE_COFFER`) — all from `gameval/VarbitID.java`, the same varbits RuneLite core's `BlastFurnaceOverlay`/`BlastFurnaceCofferOverlay` read.
- **Item IDs**: RuneLite `ItemID` constants and OSRS Wiki
- **Coal ratios** (0/1/2/3/4 for iron/steel/mithril/adamantite/runite) and **coffer drain** (72,000 gp/hr, 1,200 gp/min, 12 gp/tick; max 20M gp; foreman 2,500/10 min): [OSRS Wiki — Blast Furnace](https://oldschool.runescape.wiki/w/Blast_Furnace), retrieved 2026-07-11
- **Coal bag capacity** (27): OSRS Wiki

## Structural Reference

quest-helper (github.com/Zoinkwiz/quest-helper, BSD-2) served as the pattern reference for overlay layering conventions (ABOVE_SCENE for world highlights, ALWAYS_ON_TOP for widget highlights) and hub registration structure.

## Changelog

### v0.2.1
- **State-derived guidance (new architecture)**: replaced the positional trip-state step machine with `BFPolicy` — a pure, idempotent function from an observed-state snapshot to the single correct next action. Reads furnace coal (varbit 949), this type's ore/bars, dispenser state (varbit 936), inventory, coal-bag fullness, and coffer balance. Self-corrects mid-cycle.
- **Corrected object IDs**: bar dispenser fixed from **9104** (a cog — `BLAST_FURNACE_CONVEYER_COGS2`) to the real dispenser family **9092–9096**; conveyor belt confirmed **9100** (`BLAST_FURNACE_CONVEYER_BELT_CLICKABLE`). Corrected furnace varbits (coal 949, dispenser state 936) from stale values.
- **Bank order — coal before ore**: the policy highlights coal (fill bag, then loose) and only highlights the primary ore once coal is satisfied; one target at a time.
- **Empty coal bag at belt**: highlights the coal bag (item 12019/12020) in the inventory when it holds coal and a slot is free.
- **Single status panel**: all text consolidated into one auto-stacking `OverlayPanel`; no hardcoded-coordinate strings.
- **Bar type ratios verified** (OSRS Wiki): iron 0, steel 1, mithril 2, adamantite 3, **runite 4** (BF value; not the standard-furnace 8). Furnace ore/bar varbits added per type.
- **Reset stats**: `resetHotkey` config keybind, panel right-click **Reset stats**, and automatic reset on bar-type change.

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
3. Point to `build/libs/runelite-blast-furnace-helper-0.2.1.jar`

Alternatively, place the jar in `~/.runelite/plugins/` (external plugin folder if configured).

## License

BSD-2-Clause © 2024 TechDevGroup
