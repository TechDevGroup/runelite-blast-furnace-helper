package com.techdevgroup.blastfurnacehelper;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Bar types producible at the Blast Furnace.
 * Coal ratios (coalPerBar) are BF-reduced (half normal cost).
 * Source: OSRS Wiki "Blast Furnace".
 * Adamantite confirmed 3 coal per bar (3:1 ratio).
 */
@Getter
@RequiredArgsConstructor
public enum BarType {
    IRON      ("Iron",       BFConstants.ITEM_IRON_ORE,       BFConstants.ITEM_IRON_BAR,       0),
    STEEL     ("Steel",      BFConstants.ITEM_IRON_ORE,       BFConstants.ITEM_STEEL_BAR,      1),
    MITHRIL   ("Mithril",    BFConstants.ITEM_MITHRIL_ORE,    BFConstants.ITEM_MITHRIL_BAR,    2),
    ADAMANTITE("Adamantite", BFConstants.ITEM_ADAMANTITE_ORE, BFConstants.ITEM_ADAMANTITE_BAR, 3),
    RUNITE    ("Runite",     BFConstants.ITEM_RUNITE_ORE,     BFConstants.ITEM_RUNITE_BAR,     4);

    private final String displayName;
    private final int oreItemId;
    private final int barItemId;
    /** Coal required per bar at Blast Furnace (OSRS Wiki). */
    private final int coalPerBar;
}
