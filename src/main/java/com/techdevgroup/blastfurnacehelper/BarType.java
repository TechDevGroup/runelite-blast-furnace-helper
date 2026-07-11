package com.techdevgroup.blastfurnacehelper;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Bar types producible at the Blast Furnace.
 * coalPerBar values are the Blast-Furnace-reduced coal cost (half the standard furnace),
 * confirmed against OSRS Wiki "Blast Furnace" (retrieved 2026-07-11):
 *   iron 0, steel 1, mithril 2, adamantite 3, runite 4.
 * NOTE: runite is 4 at the Blast Furnace (8 at a standard furnace) — the wiki's
 * "half as much coal" rule. Do not use the standard-furnace value of 8 here.
 * Furnace-content varbits: RuneLite gameval/VarbitID.java via BFConstants (BSD-2-Clause).
 */
@Getter
@RequiredArgsConstructor
public enum BarType {
    IRON      ("Iron",       BFConstants.ITEM_IRON_ORE,       BFConstants.ITEM_IRON_BAR,       0,
               BFConstants.VAR_FURNACE_IRON_ORE,       BFConstants.VAR_FURNACE_IRON_BARS),
    STEEL     ("Steel",      BFConstants.ITEM_IRON_ORE,       BFConstants.ITEM_STEEL_BAR,      1,
               BFConstants.VAR_FURNACE_IRON_ORE,       BFConstants.VAR_FURNACE_STEEL_BARS),
    MITHRIL   ("Mithril",    BFConstants.ITEM_MITHRIL_ORE,    BFConstants.ITEM_MITHRIL_BAR,    2,
               BFConstants.VAR_FURNACE_MITHRIL_ORE,    BFConstants.VAR_FURNACE_MITHRIL_BARS),
    ADAMANTITE("Adamantite", BFConstants.ITEM_ADAMANTITE_ORE, BFConstants.ITEM_ADAMANTITE_BAR, 3,
               BFConstants.VAR_FURNACE_ADAMANTITE_ORE, BFConstants.VAR_FURNACE_ADAMANTITE_BARS),
    RUNITE    ("Runite",     BFConstants.ITEM_RUNITE_ORE,     BFConstants.ITEM_RUNITE_BAR,     4,
               BFConstants.VAR_FURNACE_RUNITE_ORE,     BFConstants.VAR_FURNACE_RUNITE_BARS);

    private final String displayName;
    private final int oreItemId;
    private final int barItemId;
    /** Coal required per bar at the Blast Furnace (OSRS Wiki, BF-reduced). */
    private final int coalPerBar;
    /** Varbit holding the count of this type's ore currently in the furnace. */
    private final int furnaceOreVarbit;
    /** Varbit holding the count of this type's finished bars waiting in the dispenser. */
    private final int furnaceBarVarbit;
}
