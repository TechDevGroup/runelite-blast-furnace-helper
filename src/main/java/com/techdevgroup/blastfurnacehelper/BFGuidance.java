package com.techdevgroup.blastfurnacehelper;

import lombok.Value;

/**
 * The result of the state-derived policy: the single next action plus its highlight targets.
 * Immutable; produced fresh from a state snapshot each tick.
 *
 * @param action     the suggested next action
 * @param bankItemId item id to highlight in the bank (-1 = none)
 * @param invItemId  item id to highlight in the inventory (-1 = none)
 */
@Value
public class BFGuidance {
    BFAction action;
    int bankItemId;
    int invItemId;

    public static BFGuidance of(BFAction action) {
        return new BFGuidance(action, -1, -1);
    }

    public static BFGuidance bankItem(BFAction action, int itemId) {
        return new BFGuidance(action, itemId, -1);
    }

    public static BFGuidance invItem(BFAction action, int itemId) {
        return new BFGuidance(action, -1, itemId);
    }

    public BFAction.ObjTarget objectTarget() {
        return action.getObjectTarget();
    }

    public String label() {
        return action.getLabel();
    }
}
