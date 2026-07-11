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
there. Inputs: **location context** (bank interface open, and belt/bank proximity), coal in
furnace (varbit 949), this bar type's ore/bar counts in the furnace, dispenser state (varbit
936), inventory coal/ore/bar counts and free slots, coal-bag contents, and coffer balance.

The loop is **location-aware** — a coal-bag *fill* and a belt *deposit* are easy to confuse
because both consume inventory coal, but they happen at different stations. Fills and
withdrawals are only ever recommended at the bank; deposits and bag-empties only at the belt.

Priority order:

1. **Coffer critical** — withdraw coins (bank) or deposit them into the coffer (holding coins); the furnace halts when the coffer empties.
2. **Bars already in inventory** → deposit at bank.
3. **Bank interface open** → acquire the next material (coal-before-ore; loose-coal-vs-ore from the furnace — see below).
4. **At the belt** → deposit loose coal, then ore, then empty the coal bag. Never a fill/withdraw here.
5. **At the bank chest, interface closed** → finish filling the bag if coal is still in hand (the "exited-bank to fill" case), else carry a load to the belt, else open the bank.
6. **En route** → carry any load to the belt.
7. **Empty-handed return leg** → collect bars at the dispenser (strictly before any bank trip), then a coffer top-up if low and carrying coins, else go to the bank.

### Location Context

`atBank` = bank interface open **or** the player within `PROXIMITY_RADIUS` (3 tiles) of the bank
chest; `atBelt` = within 3 tiles of the conveyor belt. Proximity uses the live tracked
GameObjects plus fixed BF anchors (bank ≈ 1948,4957; belt ≈ 1940,4965 — the Blast Furnace is a
single fixed location; anchors taken from ground-truth `actions.log` positions). This context is
what stops the policy recommending a belt-deposit while you are filling the bag at the bank, and
vice-versa.

### Coal-Before-Ore and the Furnace-Derived Loose-Coal Decision

The coal bag holds **only** coal, so it is always filled first (coal-before-ore is preserved).
Whether a trip then needs a **loose coal load** or goes straight to ore is derived from the
**furnace** coal/ore varbits and the ratio, **not** from inventory coal: after the bag is full,
if even the bag's coal would leave the furnace short for its ore, it is a "coal trip" (withdraw a
loose load); otherwise it is an "ore trip" (straight to ore). Crucially the ore step does **not**
require `invCoal > 0` — filling the bag consumes the inventory coal, and the earlier
`coalBagFull && invCoal > 0` gate could therefore never unlock ore (it stuck on coal forever).

Coal-bag contents are tracked as an **authoritative count**, not a boolean: primarily from the
coal-bag **chat messages** ("The coal bag is empty/now full", "…contains N pieces of coal"), with
Fill/Empty menu clicks + inventory inference secondary. An "unknown" count (`-1`, e.g. after a
relog) is treated as *not full* → coal-first.

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
| `predictNextTarget` | On | Pre-aim: ghost marker where the next bank withdrawal will appear |
| `predictColor` | Purple | Color of the predicted-position (pre-aim) marker |
| `showTileHotspots` | On | Mark the learned tile to stand on before the current interaction |
| `tileHotspotColor` | Green | Color of the standing-tile hotspot marker |
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
cited as the approach reference. Because objects in the small BF region are always loaded, the
arrow shows the whole time you walk toward a target (not only when adjacent) — the object
"pre-aim" case.

## Predictive Pre-Aim (Bank Items)

When the bank is **closed** but the next action will be a bank withdrawal (you are walking to the
bank), a ghost marker shows **where that item will appear** once the bank opens, so you can
pre-move the mouse. It reuses the interface's own geometry and previously-seen data rather than
hardcoded grid math:

- **Geometry from the Widget API**: while the bank is open, each relevant item's canvas rectangle
  is read from the live bank item container's child widgets — `Widget.getBounds()` of
  `client.getWidget(12, 12)` (the `Bankmain.ITEMS` container, group 12 child 12) — never computed
  from column/cell math.
- **Persisted "seen" layout**: each seen position (item id → slot + canvas bounds) is saved to
  `~/.runelite/blast-furnace-helper/bank-layout.json` and reloaded on the next session, so pre-aim
  works from the start of a run and refreshes the moment the bank is reopened.
- **Which item**: computed by asking the policy what it would withdraw *if the bank were open now*
  (`snapshot.toBuilder().bankOpen(true)` → `derive()`), only while heading to the bank.
- **Precedence**: live (bank open — the real highlight) > persisted/seen position > none (no
  marker if that item was never seen — no wild guessing). Item id is the key, so a remembered
  position survives bank reordering as long as the slot was seen at least once.

### Close-Button Prestage (runtime-discovered)

When withdrawals are done and the next step is to leave for the belt (`bankOpen && guidance ==
GO_TO_BELT`), the plugin prestages the bank **close button** so you can pre-move the mouse. The
close button has **no hardcoded widget id** — it is discovered at runtime by walking the bank
interface (group 12) widget tree for the child whose menu op contains **"Close"** (case-insensitive
`Widget.getActions()`), then taking that child's live canvas bounds. Those bounds are cached under a
reserved key (`-1`) in `bank-layout.json` with the same **live > persisted-seen > none** precedence,
so the marker survives across sessions. If no "Close" child is found (a different interface layout),
nothing is cached and no marker is shown — never a guess. The discovered widget id + bounds are
logged once (`log.debug`, "discovered bank close button, widgetId=…") so the concrete id can be
recorded.

## Standing-Tile Hotspots

For the current world-object target, the plugin marks the **tile to stand on** before interacting
(you often walk to a specific tile to reach the dispenser/belt). This is **self-learning**: every
time you interact with a tracked object the plugin records the tile you stood on (dispenser state
variants are canonicalised to one id), and the most-frequent tile is highlighted. It is **seeded**
from observed play (bar dispenser (1940,4962) ×30 / (1942,4967) ×60; belt & bank chest tiles) so
it is useful immediately, and real interactions accumulate on top and take over. Persisted to
`~/.runelite/blast-furnace-helper/hotspots.json`. If nothing is learned for an object, it simply
shows no tile (the world arrow still points at the object). Config `showTileHotspots` +
`tileHotspotColor`. Rendered as a tile polygon via `Perspective.getCanvasTilePoly`.

## Structural Reference

quest-helper ([github.com/Zoinkwiz/quest-helper](https://github.com/Zoinkwiz/quest-helper), BSD-2)
served as the pattern reference for overlay layering conventions (ABOVE_SCENE for world
highlights, ALWAYS_ON_TOP for widget highlights), hub registration structure, and the world-arrow
drawing technique (`DirectionArrow.drawWorldArrow`, re-implemented independently).

## Changelog

### v0.2.6
- **Fixed: adamantite ore never highlighted (bug 3).** Two causes: (a) the coal-bag id constant was **12020, which is actually `GEM_BAG`** — it is now the real coal bag ids (`12019` / open `24480`), and Fill/Empty detection also falls back to the menu target text, so the coal-bag *full* state is tracked and the policy can advance past the coal phase; (b) `furnaceNeedsLooseCoal` added `furnaceOre` to the coal requirement, which — whenever the furnace held ore mid-smelt — made the decision *always* a coal trip, so `WITHDRAW_ORE` was never reached. The threshold is now furnace-coal-only (`furnaceCoal + bag < ratio × ORE_LOAD`), still matching the observed 2/56 split but never stalling. Both coal (453) and primary ore (adamantite 449) now highlight in their phases.
- **Fixed: coal-bag empty/fill highlight not showing (bug 2).** Same wrong `12020` id — the highlight now matches any coal-bag id (`isCoalBag`). Debug log added for whether the coal-bag slot is located.
- **Fixed: predictive bank ghost markers not showing (bug 1).** While walking to the bank the player is usually carrying finished bars, so the 1-step prediction returned `DEPOSIT_BARS` (no bank item). It now looks past that (`invBars = 0`) to surface the coal/ore that will be withdrawn next. Debug log added for markers emitted per frame (distinguishes "no pending withdrawal" from "position never seen").
- **Bank close-button prestage (runtime-discovered, no hardcoded id)**: while the bank is open, walks the bank interface (group 12) widget tree for the child whose menu op contains "Close" and takes its live canvas bounds; cached under a reserved key (`-1`) in `bank-layout.json` (live > persisted-seen > none). Shown when `bankOpen && guidance == GO_TO_BELT`. If no "Close" child exists, no marker — never a guess. The discovered widget id is logged once.

### v0.2.5
- **Coal-sufficiency threshold fixed** (data-backed): `furnaceNeedsLooseCoal` now compares against a full ore load — `furnaceCoal + bagCapacity < ratio × (furnaceOre + ORE_LOAD)` — instead of `+ 1`. A small residual coal amount no longer reads as "enough". For adamantite (ratio 3, ORE_LOAD 27, bag 27) the switch point is `fcoal < 54`, matching the user's observed furnace-coal-driven split: `fcoal≈2` → 2-coal trip, `fcoal≈56` → 1-coal+1-ore trip.
- **Bar collection corrected to a return-leg step** (data-backed, supersedes a wrong preempt idea): ready bars do **not** preempt the bank withdrawal. Verbatim `actions.log` shows the player preps the next load with bars still queued, walks past the dispenser with a full inventory, deposits on the belt, and only then — empty-handed on the return leg — collects. `COLLECT_BARS` is gated on `furnaceBars > 0 && freeSlots > 0` in the return-leg tail; it never fires while carrying a load or at the bank.
- **Standing-tile hotspots**: self-learning marker for the tile to stand on before interacting with the current object; seeded from observed play, persisted to `~/.runelite/blast-furnace-helper/hotspots.json`. Config `showTileHotspots` + `tileHotspotColor`.
- Verified with trace harnesses (coal threshold 5 cases incl. the 2/56 split; return-leg collect 4 cases; hotspot seed/learn/persist round-trip).

### v0.2.4
- **Location-aware policy (biggest fix)**: the guidance was station-blind and conflated a coal-bag *fill* with a belt *deposit* (both consume inventory coal). Added location context to the snapshot — `bankOpen` plus belt/bank proximity (`atBank`, `atBelt`) — and gated the loop on it: fills/withdrawals only at the bank, deposits/empties only at the belt. Ground-truth `actions.log` anchors (bank ≈ 1948,4957; belt ≈ 1940,4965) + live GameObject proximity, radius 3.
- **Coal→ore transition unstuck**: the loose-coal-vs-ore decision now comes from the **furnace** coal/ore varbits + ratio, not inventory coal. The ore step no longer requires `invCoal > 0` (filling the bag zeroes inventory coal, which had made ore unreachable — the policy looped on coal forever). Verified against the reported row (bagFull, coal=0, fcoal=56 → ore).
- **Coal-bag fill highlight persists after leaving the bank**: `FILL_COAL_BAG` is recommended while at/near the bank (not only while the interface is open) and rendered in whichever inventory is visible — the **bankside** inventory (group 15 child 3) when the bank is open, the **normal** inventory (group 149 child 0) when closed.
- **Predictive pre-aim**: ghost marker showing where the next bank withdrawal will appear before the bank opens, using Widget-API geometry from the bank item container (group 12 child 12) and a persisted seen layout at `~/.runelite/blast-furnace-helper/bank-layout.json` (precedence live > seen > none). Config `predictNextTarget` (default on) + `predictColor`.
- Verified with a trace harness (14 cases) against the routine's real click order — deposit/fill and coal/ore recommendations match at each step.

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
3. Point to `build/libs/runelite-blast-furnace-helper-0.2.6.jar`

Alternatively, place the jar in `~/.runelite/plugins/` (external plugin folder if configured).

## License

BSD-2-Clause © 2024 TechDevGroup
