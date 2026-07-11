package com.techdevgroup.blastfurnacehelper;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The single next action the helper suggests, derived purely from observed game state.
 * Each action names a human-readable label and which world object (if any) to highlight.
 * Overlay-only: these are hints, never automated input.
 */
@Getter
@RequiredArgsConstructor
public enum BFAction {
    IDLE                ("Idle",                 ObjTarget.NONE),
    WITHDRAW_COINS      ("Withdraw coins",       ObjTarget.NONE),      // bank item = coins
    REFILL_COFFER       ("Refill coffer",        ObjTarget.COFFER),
    FILL_COAL_BAG       ("Fill coal bag",        ObjTarget.NONE),      // inventory item = coal bag
    WITHDRAW_COAL       ("Withdraw coal",        ObjTarget.NONE),      // bank item = coal
    WITHDRAW_ORE        ("Withdraw ore",         ObjTarget.NONE),      // bank item = ore
    GO_TO_BELT          ("Go to conveyor belt",  ObjTarget.CONVEYOR),
    EMPTY_COAL_BAG      ("Empty coal bag",       ObjTarget.NONE),      // inventory item = coal bag
    DEPOSIT_COAL        ("Deposit coal on belt", ObjTarget.CONVEYOR),
    DEPOSIT_ORE         ("Deposit ore on belt",  ObjTarget.CONVEYOR),
    COLLECT_BARS        ("Collect bars",         ObjTarget.DISPENSER),
    WAIT_SMELT          ("Smelting…",            ObjTarget.DISPENSER),
    DEPOSIT_BARS        ("Deposit bars",         ObjTarget.BANK_CHEST),
    GO_TO_BANK          ("Go to bank",           ObjTarget.BANK_CHEST);

    private final String label;
    private final ObjTarget objectTarget;

    /** Which world object an action wants highlighted. */
    public enum ObjTarget { NONE, CONVEYOR, DISPENSER, BANK_CHEST, COFFER }
}
