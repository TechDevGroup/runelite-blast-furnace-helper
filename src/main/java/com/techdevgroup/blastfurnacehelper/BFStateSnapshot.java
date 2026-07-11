package com.techdevgroup.blastfurnacehelper;

import lombok.Builder;
import lombok.Value;

/**
 * An immutable snapshot of everything the policy needs, gathered from observed game state
 * each tick. Kept free of RuneLite API types so the policy is a pure, testable function.
 */
@Value
@Builder
public class BFStateSnapshot {
    BarType barType;      // selected/effective bar type (null = unknown)
    boolean bankOpen;

    // Inventory (observed)
    int invCoal;
    int invOre;           // primary ore for the selected bar type
    int invBars;          // finished bars of the selected type
    int freeSlots;
    boolean coalBagHasCoal;

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
