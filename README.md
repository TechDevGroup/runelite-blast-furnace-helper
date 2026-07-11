# Blast Furnace Helper

A RuneLite plugin that provides a trip computer, click-target highlights, and coffer balance tracking for Blast Furnace runs. **This plugin is highlight-only and never performs any automated input**, as required by RuneLite's Plugin Hub rules.

## Features

- **State-derived guidance**: Every tick the plugin recomputes the single correct next action purely from observed game state (furnace coal/ore/bar varbits, dispenser state, inventory, coal-bag fullness, coffer balance). It is a pure function of state — no positional step counter — so it self-corrects if you arrive mid-cycle or act out of sequence.
- **Click-target highlights**: Highlights the one object the guidance points at (conveyor belt, bar dispenser, or bank chest).
- **Persistent world arrow**: A bobbing arrow floats above the current world-object target and stays there until that step's state is satisfied, so you never assume a step is finished while the arrow is still overhead.
- **Bank item highlights (drawn over the bank UI)**: When the bank is open, highlights the single next item to withdraw — coal **before** ore — plus coins when the coffer is low, and a run-energy restorative when low. Highlights stay prevalent while any interface is open: the item overlay renders on `ABOVE_WIDGETS` so it sits on top of the bank rather than being hidden by it, and the status panel stays visible too. When the step is "fill coal bag", the coal bag is highlighted in the bankside inventory shown next to the bank.
- **Opportunistic bar collection**: On the return leg to the bank, highlights the dispenser to collect bars as soon as at least one bar is ready and you have a free slot — never waits for a full 27-bar dispenser.
- **Coal-bag emptying**: At the belt, highlights the coal bag in your inventory when it still holds coal and you have a free slot, so the bagged coal drops in to be dumped.
- **Run-energy highlight**: When run energy is low, highlights the best restorative you carry (stamina potion → stamina mix → super energy → super energy mix → energy potion, highest dose first) — to withdraw if the bank is open, otherwise to drink from the inventory.
- **Trip computer overlay**: One consolidated panel showing next action, runtime, bar type, per-hour rates (bars/hr, ore/hr), coffer balance, estimated time remaining, and standing coffer cost — all auto-stacking so nothing overlaps.
- **Coffer tracking**: Reads the coffer balance from a game varbit every tick and highlights the coffer and/or bank coins when the balance is low or critical.
- **Reset stats**: Configurable hotkey, right-click **Reset stats** on the panel, and automatic reset when the bar type changes.
- **Action logger (debug)**: Optionally records each click and every change of the recommended action to a rotating log file, pairing what the policy recommended against what you actually did — for validating and tuning the guidance policy.

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
dispenser state (varbit 936), inventory coal/ore/bar counts and free slots, coal-bag contents,
and coffer balance. Priority order:

1. **Coffer critical** — withdraw coins (bank) or deposit them into the coffer (holding coins); the furnace halts when the coffer empties.
2. **Bars already in inventory** → deposit at bank.
3. **Bank open** → acquire the next material under a **strict coal-before-ore invariant** (see below).
4. **Return leg** (bank closed), in order:
   1. loose coal/ore in inventory → deposit on the belt (one belt click deposits both);
   2. else the coal bag still holds coal (and a slot is free) → empty the bag into the inventory;
   3. else the dispenser has ≥ 1 bar ready (and a slot is free) → **collect bars — strictly before any bank trip**, so leftover coal never diverts you to the bank while bars are uncollected;
   4. else the coffer is low and you hold coins → refill the coffer;
   5. else → go to the bank to restock.

### Strict Coal-Before-Ore Invariant

The coal bag holds **only** coal, so from an empty inventory the bank sequence is strictly:
**(1) withdraw coal → (2) fill the coal bag → (3) withdraw the loose coal load → (4) only then
withdraw the primary ore.** Ore is *unreachable* in `derive()` until the bag is confidently full
**and** a loose coal load is present — an explicit ordered guard, not a heuristic. If bag
fullness is unknown/uncertain, it errs coal-first and never highlights ore.

This is possible because coal-bag contents are tracked as an **authoritative count**, not a
boolean: the count comes primarily from the coal-bag **chat messages** ("The coal bag is
empty/now full", "…contains N pieces of coal"), with Fill/Empty menu clicks + inventory
inference as a secondary source. A count that is merely "unknown" (`-1`, e.g. just after a
relog) is treated as *not full* → coal-first. The earlier boolean toggle could falsely read
"bag already full" and skip straight to ore; the count fixes that.

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
| `highlightRunEnergy` | On | Highlight the best run-energy restorative you carry when run energy is low |
| `staminaThreshold` | 50% | Highlight a run-energy restorative when run energy is below this |
| `bankItemColor` | Green | Color for bank item highlights |
| `objectColor` | Orange | Color for conveyor belt / dispenser / bank chest highlights |
| `showWorldArrow` | On | Show the bobbing arrow above the current world-object target |
| `worldArrowColor` | Cyan | Color of the bobbing world arrow |
| `showPanel` | On | Toggle the stats overlay |
| `cofferEnabled` | On | Enable coffer balance tracking and highlights |
| `cofferLowMinutes` | 20 min | Highlight coffer as LOW when time remaining is below this |
| `cofferCriticalGp` | 0 gp | Highlight coffer as CRITICAL when balance ≤ this amount |
| `cofferLowColor` | Yellow | Color for coffer LOW state highlights and panel text |
| `cofferCriticalColor` | Red | Color for coffer CRITICAL/EMPTY highlights and panel alert |
| `resetHotkey` | (unset) | Hotkey to zero the trip computer and restart the timer |
| `logActions` | On | Log clicks + recommendation changes for policy tuning (see below) |

## Run-Energy Restoratives

When run energy drops below the threshold, the plugin highlights the single best restorative you
actually carry. **There is no distinct "extended stamina potion" item in the game** — this is the
full run-energy restore family. Preference order (stamina is the Blast Furnace standard, but
whatever you have is highlighted), highest dose first within each family:

1. Stamina potion (4/3/2/1): 12625 / 12627 / 12629 / 12631
2. Stamina mix (2/1): 12633 / 12635
3. Super energy (4/3/2/1): 3016 / 3018 / 3020 / 3022
4. Super energy mix (2/1): 11481 / 11483
5. Energy potion (4/3/2/1): 3008 / 3010 / 3012 / 3014

If the bank is open it highlights the item to **withdraw**; otherwise it highlights the item in
your inventory to **drink**. All ids cache-verified against RuneLite `gameval/ItemID.java`.

## Action Logger (Debug)

With `logActions` enabled (default on while the policy is being tuned), the plugin appends to a
rotating log at `~/.runelite/blast-furnace-helper/actions.log` (rotates to `actions.log.1..3` at
~1 MB). Two line types are written while in the Blast Furnace region:

- `event=CLICK` — on every menu click: tick, timestamp, the option/target/id/itemId/menuAction/type,
  a snapshot of derived state, and the policy's `recommended=` action at that moment.
- `event=REC_CHANGE` — whenever the recommended action changes (even without a click), capturing
  the full ordered sequence.

State snapshot fields: `coal ore bars free` (inventory), `bag` (coal-bag fullness), `fcoal`
(furnace coal), `fbars` (bars ready in dispenser), `disp` (dispenser state varbit 936), `coffer`
balance, and player `region`/`pos`. Pairing `recommended=` against the actual click makes
policy/behaviour divergences obvious for tuning. This records only your own client-side actions —
no automation, no network.

## Source Citations

All identifiers below are cache-verified against the RuneLite `gameval` sources
([github.com/runelite/runelite](https://github.com/runelite/runelite), BSD-2-Clause), used for
factual constants only:

- **Region ID** (7757): RuneLite built-in `BlastFurnacePlugin`
- **Conveyor belt** (9100 = `BLAST_FURNACE_CONVEYER_BELT_CLICKABLE`, "Put-ore-on"): `gameval/ObjectID.java`
- **Bar dispenser** (9092 base + state variants 9093–9096 = `BLAST_FURNACE_DISPENSER` / `BLAST_FURNACE_ORE_DISPENSER_*`): `gameval/ObjectID.java`. The plugin tracks the whole family so the highlight follows the dispenser through its states. (The old value 9104 was wrong — it is `BLAST_FURNACE_CONVEYER_COGS2`, a cog.)
- **Coffer objects** (29328/29329/29330 = `BLAST_FURNACE_AUTOMATA_COFFER_*`): `gameval/ObjectID.java`; cross-checked OSRS Wiki "Coffer (Blast Furnace)"
- **Furnace varbits**: coal-stored 949 (`BLAST_FURNACE_COAL`), iron-ore 951, mithril-ore 952, adamantite-ore 953, runite-ore 954; bars iron 942 / steel 943 / mithril 944 / adamantite 945 / runite 946; dispenser state 936 (`BLAST_FURNACE_BARS_HOT`); coffer balance 5357 (`BLAST_FURNACE_COFFER`) — all from `gameval/VarbitID.java`, the same varbits RuneLite core's `BlastFurnaceOverlay`/`BlastFurnaceCofferOverlay` read.
- **Item IDs** (ores/bars/coal/coal bag/coins): RuneLite `ItemID` constants and OSRS Wiki
- **Run-energy item IDs** (stamina 12625/12627/12629/12631, stamina mix 12633/12635, super energy 3016/3018/3020/3022, super energy mix 11481/11483, energy potion 3008/3010/3012/3014): cache-verified against `gameval/ItemID.java`. (The pre-0.2.2 energy-potion constants 3004/3006 were wrong — 3004 is a snapdragon vial, 3006 a firework — and have been corrected.)
- **Coal ratios** (0/1/2/3/4 for iron/steel/mithril/adamantite/runite) and **coffer drain** (72,000 gp/hr, 1,200 gp/min, 12 gp/tick; max 20M gp; foreman 2,500/10 min): [OSRS Wiki — Blast Furnace](https://oldschool.runescape.wiki/w/Blast_Furnace), retrieved 2026-07-11
- **Coal bag capacity** (27): OSRS Wiki

## Persistent World Arrow

A bobbing arrow floats above the current world-object target (belt 9100, dispenser 9092–9096,
coffer 29328–29330, or the bank chest) and is drawn every frame while the state-derived policy
still points at that object. Because the target and its completion come from the policy, the
arrow disappears only when the step's state is satisfied — over the belt until coal/ore is
deposited, over the dispenser until bars are collected, over the coffer until it is refilled.
Inventory-item targets (coal bag, coal/ore, potions) keep their widget/inventory highlight and
get no world arrow.

Rendering: `OverlayLayer.ABOVE_SCENE`; the canvas anchor above the object tile is computed with
`net.runelite.api.Perspective.localToCanvas(client, object.getLocalLocation(), plane, 200)`; the
vertical bob is `sin(client.getGameCycle() / 15) * 8`. The arrow shape (a vertical stalk plus a
downward arrowhead, black outline then colored fill) is an independent re-implementation of
quest-helper's `DirectionArrow.drawWorldArrow` technique
([github.com/Zoinkwiz/quest-helper](https://github.com/Zoinkwiz/quest-helper), BSD-2-Clause),
cited as the approach reference.

## Structural Reference

quest-helper ([github.com/Zoinkwiz/quest-helper](https://github.com/Zoinkwiz/quest-helper), BSD-2)
served as the pattern reference for overlay layering conventions (ABOVE_SCENE for world
highlights, ALWAYS_ON_TOP for widget highlights), hub registration structure, and the world-arrow
drawing technique (`DirectionArrow.drawWorldArrow`, re-implemented independently).

## Changelog

### v0.2.3
- **Highlights stay prevalent over open interfaces**: the bank/inventory item overlay moved to `OverlayLayer.ABOVE_WIDGETS` so bank-item highlights draw on top of the open bank UI instead of being occluded; the status panel is now `ABOVE_WIDGETS` too, so it stays visible with the bank open. No highlight is suppressed merely because a UI is open. (Scene/world highlights + the world arrow remain `ABOVE_SCENE`, naturally behind a full-screen bank, and reappear the instant it closes.)
- **Coal-before-ore made a hard invariant**: rewrote the bank branch of `derive()` with an explicit ordered guard — ore literally cannot be returned until the coal bag is confidently full AND a loose coal load is present. Root cause of the ore-first bug: coal-bag fullness was a boolean that could falsely read "full", skipping the coal steps. Now coal-bag contents are tracked as an **authoritative count** parsed from the coal-bag **chat messages** (empty / now full / "contains N pieces of coal"), with Fill/Empty clicks + inventory inference secondary; unknown count → err coal-first. The "fill coal bag" step highlights the bag in the bankside inventory (group 15 child 3).
- **Belt/return-leg ordering fixed**: the belt is highlighted only when the inventory actually has loose coal/ore to deposit; if nothing is depositable but the coal bag still holds coal, the coal bag (Empty) is highlighted instead — evaluated before the belt.
- **Bar collection beats the bank**: when the dispenser has ≥ 1 bar and a free slot exists, `COLLECT_BARS` strictly precedes any bank guidance, so leftover coal no longer diverts you to the bank while bars are uncollected.
- Verified by tracing `derive()`: empty-inventory-at-bank with a not-yet-full/unknown bag returns coal (fill-bag/withdraw-coal), never ore.

### v0.2.2
- **Opportunistic bar collection**: the policy now collects bars on the return leg — as soon as ≥ 1 bar is ready (per-type bar varbit) and a free slot exists — instead of interrupting a deposit or waiting for a full 27-bar dispenser. The collect check moved below the belt-deposit block and gained a free-slot gate.
- **Persistent world arrow**: a bobbing arrow (own re-implementation of quest-helper's `DirectionArrow.drawWorldArrow`, cited) floats above the current world-object target until the step's state is satisfied. `Perspective.localToCanvas` for the anchor; `sin(gameCycle/15)*8` bob. Config: `showWorldArrow` (default on), `worldArrowColor`.
- **Run-energy family highlight**: broadened from base stamina to the full run-energy restore set (stamina/stamina mix/super energy/super energy mix/energy potion). Highlights the single best item you carry — to withdraw if the bank is open, else to drink. Config: `highlightRunEnergy` (default on). Corrected the pre-0.2.2 energy-potion ids (3004/3006 were a snapdragon vial / firework) to the real family (3008/3010/3012/3014). There is no "extended stamina" item.
- **Action logger**: with `logActions` (default on), appends `CLICK` and `REC_CHANGE` lines to `~/.runelite/blast-furnace-helper/actions.log` (rotating), pairing the policy's recommended action against the player's actual clicks and derived state for tuning.

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
3. Point to `build/libs/runelite-blast-furnace-helper-0.2.3.jar`

Alternatively, place the jar in `~/.runelite/plugins/` (external plugin folder if configured).

## License

BSD-2-Clause © 2024 TechDevGroup
