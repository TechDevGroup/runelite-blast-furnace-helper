package com.techdevgroup.blastfurnacehelper;

/**
 * State machine phases for a Blast Furnace trip.
 */
public enum BFTripState {
    IDLE,
    BANK_WITHDRAW_COAL_1,
    BELT_DEPOSIT_COAL,
    BANK_WITHDRAW_ORE,
    BELT_DEPOSIT_ORE,
    AWAITING_BARS,
    COLLECT_BARS,
    BANK_DEPOSIT_BARS
}
