package com.techdevgroup.blastfurnacehelper;

import lombok.Builder;
import lombok.Value;

/**
 * An immutable snapshot of everything the policy needs, gathered from observed game state
 * each tick. Kept free of RuneLite API types so the policy is a pure, testable function.
 */
@Value
@Builder(toBuilder = true)
public class BFStateSnapshot {
    BarType barType;      // selected/effective bar type (null = unknown)
    boolean bankOpen;

    // Inventory (observed)
    int invCoal;
    int invOre;           // primary ore for the selected bar type
    int invBars;          // finished bars of the selected type
    int freeSlots;
    boolean coalBagHasCoal; // bag holds > 0 coal (or count unknown → assume has coal)
    boolean coalBagFull;    // bag confidently full (count >= capacity); unknown → false

    // Location context (bank interface open, or player proximity to each station).
    boolean atBank;         // bank interface open OR standing at the bank chest
    boolean atBelt;         // standing at the conveyor belt

    // Furnace (varbits)
    int furnaceCoal;
    int furnaceOre;       // this type's ore currently in the furnace
    int furnaceBars;      // this type's finished bars waiting in the dispenser
    int dispenserState;   // VAR_DISPENSER_STATE (936): 1 belt busy, 2 hot, 3 cooled

    // Coffer
    boolean holdingCoins;
    boolean cofferLow;
    boolean cofferCritical;
}
