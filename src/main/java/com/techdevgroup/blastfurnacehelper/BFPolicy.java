package com.techdevgroup.blastfurnacehelper;

/**
 * State-derived guidance policy.
 *
 * <p>{@link #derive(BFStateSnapshot)} is a pure, idempotent function: given only the
 * currently observed game state (furnace varbits, inventory, coal-bag fullness, dispenser
 * state, coffer status) it returns the single correct next action. It holds no memory of a
 * step index, so it self-corrects when the player arrives mid-cycle or acts out of sequence —
 * the same inputs always yield the same suggestion.
 *
 * <p>Priority order:
 * <ol>
 *   <li>Coffer critical: get / deposit coins (the furnace stops when the coffer empties).</li>
 *   <li>Finished bars in inventory: bank them.</li>
 *   <li>Bank open: acquire the next material — coal (bag first, then loose) BEFORE ore.</li>
 *   <li>At the belt: empty the coal bag (if it holds coal and a slot is free), then deposit
 *       coal, then deposit ore — dump the carried load first.</li>
 *   <li>Return leg: collect bars OPPORTUNISTICALLY while passing the dispenser — whenever at
 *       least one bar is ready and a free inventory slot exists. Never wait for a full 27-bar
 *       dispenser.</li>
 *   <li>Otherwise: return to the bank to restock.</li>
 * </ol>
 */
final class BFPolicy {
    private BFPolicy() {}

    static BFGuidance derive(BFStateSnapshot s) {
        final BarType bt = s.getBarType();
        if (bt == null) {
            return BFGuidance.of(BFAction.IDLE);
        }
        final int ratio = bt.getCoalPerBar();

        // 1. Coffer critical overrides the smithing loop — without coins the furnace halts.
        if (s.isCofferCritical()) {
            if (s.isBankOpen()) {
                return BFGuidance.bankItem(BFAction.WITHDRAW_COINS, BFConstants.ITEM_COINS);
            }
            if (s.isHoldingCoins()) {
                return BFGuidance.of(BFAction.REFILL_COFFER);
            }
            // No coins available — fall through and keep smithing; the panel shows the alert.
        }

        // 2. Finished bars in the inventory → bank them.
        if (s.getInvBars() > 0) {
            if (s.isBankOpen()) {
                return BFGuidance.of(BFAction.DEPOSIT_BARS);
            }
            return BFGuidance.of(BFAction.GO_TO_BANK);
        }

        // 3. Bank open → acquire the next material. Coal before ore.
        if (s.isBankOpen()) {
            if (needsCoal(s, ratio)) {
                if (ratio > 0 && !s.isCoalBagHasCoal()) {
                    return BFGuidance.bankItem(BFAction.FILL_COAL_BAG, BFConstants.ITEM_COAL);
                }
                if (s.getInvCoal() < BFConstants.COAL_INV_LOAD) {
                    return BFGuidance.bankItem(BFAction.WITHDRAW_COAL, BFConstants.ITEM_COAL);
                }
                // Coal loaded — leave the bank and carry it to the belt.
                return BFGuidance.of(BFAction.GO_TO_BELT);
            }
            // Coal satisfied → withdraw ore (only now, after coal is handled).
            if (s.getInvOre() < BFConstants.ORE_LOAD) {
                return BFGuidance.bankItem(BFAction.WITHDRAW_ORE, bt.getOreItemId());
            }
            return BFGuidance.of(BFAction.GO_TO_BELT);
        }

        // 4. At the belt (bank closed) → dump the carried load first, before collecting.
        if (s.isCoalBagHasCoal() && s.getFreeSlots() > 0) {
            return BFGuidance.invItem(BFAction.EMPTY_COAL_BAG, BFConstants.ITEM_COAL_BAG);
        }
        if (s.getInvCoal() > 0) {
            return BFGuidance.of(BFAction.DEPOSIT_COAL);
        }
        if (s.getInvOre() > 0) {
            return BFGuidance.of(BFAction.DEPOSIT_ORE);
        }

        // 5. Return leg: opportunistically collect bars while passing the dispenser.
        //    Fires as soon as >= 1 bar is ready and a slot is free — the player takes whatever
        //    has smelted on the way back, never waiting for a full 27-bar dispenser. This is
        //    below the belt-deposit block so a carried load is always dumped first; it is only
        //    reached once the inventory has nothing left to deposit (i.e. the return leg).
        if (s.getFurnaceBars() >= 1 && s.getFreeSlots() > 0) {
            return BFGuidance.of(BFAction.COLLECT_BARS);
        }

        // 6. Nothing in hand and nothing to collect — go restock at the bank.
        return BFGuidance.of(BFAction.GO_TO_BANK);
    }

    /**
     * True when we should be bringing coal rather than ore. Iron (ratio 0) never needs coal.
     * For coal-using bars we bring coal whenever the furnace's coal cannot cover the ore
     * already inside it (or the furnace is essentially out of coal). This alternates coal and
     * ore trips the way the real Blast Furnace method does, driven entirely by furnace state.
     */
    private static boolean needsCoal(BFStateSnapshot s, int ratio) {
        if (ratio <= 0) {
            return false;
        }
        int oreForCoal = Math.max(s.getFurnaceOre(), 1);
        return s.getFurnaceCoal() < oreForCoal * ratio;
    }
}
