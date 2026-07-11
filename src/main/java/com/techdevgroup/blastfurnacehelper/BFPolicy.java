package com.techdevgroup.blastfurnacehelper;

/**
 * State-derived guidance policy.
 *
 * <p>{@link #derive(BFStateSnapshot)} is a pure, idempotent function: given only the
 * currently observed game state (furnace varbits, inventory, coal-bag contents, dispenser
 * state, coffer status) it returns the single correct next action. It holds no memory of a
 * step index, so it self-corrects when the player arrives mid-cycle or acts out of sequence —
 * the same inputs always yield the same suggestion.
 *
 * <p>Priority order:
 * <ol>
 *   <li>Coffer critical: get / deposit coins (the furnace stops when the coffer empties).</li>
 *   <li>Finished bars in inventory: bank them.</li>
 *   <li>Bank open: acquire the next material with a STRICT coal-before-ore guard — coal into
 *       the bag, then a loose coal load, and only then ore. Ore can never be returned while any
 *       coal step is pending; if bag fullness is unknown, err coal-first.</li>
 *   <li>Return leg (bank closed):
 *     <ol type="a">
 *       <li>loose coal/ore in inventory → deposit on the belt;</li>
 *       <li>else the coal bag still holds coal (and a slot is free) → empty the bag;</li>
 *       <li>else the dispenser has ≥ 1 bar ready (and a slot is free) → collect bars — this
 *           strictly precedes any bank trip, so leftover coal never diverts you to the bank
 *           while bars are uncollected;</li>
 *       <li>else the coffer is low and you hold coins → refill the coffer;</li>
 *       <li>else → go to the bank to restock.</li>
 *     </ol>
 *   </li>
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

        // 2. Finished bars already in the inventory → bank them.
        if (s.getInvBars() > 0) {
            if (s.isBankOpen()) {
                return BFGuidance.of(BFAction.DEPOSIT_BARS);
            }
            return BFGuidance.of(BFAction.GO_TO_BANK);
        }

        // 3. Bank open → acquire the next material under a strict coal-before-ore invariant.
        if (s.isBankOpen()) {
            return bankAcquire(s, bt, ratio);
        }

        // 4. Return leg (bank closed).
        // 4a. Loose coal or ore in the inventory → dump it on the belt (one click deposits both).
        if (s.getInvCoal() > 0) {
            return BFGuidance.of(BFAction.DEPOSIT_COAL);
        }
        if (s.getInvOre() > 0) {
            return BFGuidance.of(BFAction.DEPOSIT_ORE);
        }
        // 4b. Nothing loose to deposit, but the coal bag still holds coal → empty it into the
        //     inventory (so its coal can then be dumped on the belt on the next step).
        if (s.isCoalBagHasCoal() && s.getFreeSlots() > 0) {
            return BFGuidance.invItem(BFAction.EMPTY_COAL_BAG, BFConstants.ITEM_COAL_BAG);
        }
        // 4c. Dispenser has bars ready and we have room → collect them BEFORE any bank trip.
        //     Leftover coal must not divert to the bank while bars are still uncollected.
        if (s.getFurnaceBars() >= 1 && s.getFreeSlots() > 0) {
            return BFGuidance.of(BFAction.COLLECT_BARS);
        }
        // 4d. Coffer low and we are carrying coins → top it up on the way past.
        if (s.isCofferLow() && s.isHoldingCoins()) {
            return BFGuidance.of(BFAction.REFILL_COFFER);
        }
        // 4e. Nothing to do here — head to the bank to restock.
        return BFGuidance.of(BFAction.GO_TO_BANK);
    }

    /**
     * Bank-phase material acquisition with a hard coal-before-ore guard.
     *
     * <p>For coal-using bars the order is strictly: (1) withdraw coal to fill the bag, (2) fill
     * the bag, (3) withdraw the loose coal load, (4) only then withdraw the primary ore. Ore is
     * unreachable until the bag is confidently full AND a loose coal load is present. If bag
     * fullness is unknown/uncertain, {@code coalBagFull} is false and we err coal-first, so ore
     * is never highlighted while the inventory could still be needed for coal.
     */
    private static BFGuidance bankAcquire(BFStateSnapshot s, BarType bt, int ratio) {
        if (ratio > 0) {
            // Coal steps first — the bag only holds coal, so it must be loaded before ore fills
            // the inventory.
            if (!s.isCoalBagFull()) {
                // Need loose coal in the inventory to fill the bag with.
                if (s.getInvCoal() <= 0) {
                    return BFGuidance.bankItem(BFAction.WITHDRAW_COAL, BFConstants.ITEM_COAL);
                }
                // Coal in hand → fill the bag (an inventory-item action, highlighted in the
                // bankside inventory while the bank is open).
                return BFGuidance.invItem(BFAction.FILL_COAL_BAG, BFConstants.ITEM_COAL_BAG);
            }
            // Bag is confidently full → ensure a loose coal inventory load is present.
            if (s.getInvCoal() <= 0) {
                return BFGuidance.bankItem(BFAction.WITHDRAW_COAL, BFConstants.ITEM_COAL);
            }
            // Bag full AND a coal load present → ore may finally be withdrawn.
            if (s.getInvOre() < BFConstants.ORE_LOAD) {
                return BFGuidance.bankItem(BFAction.WITHDRAW_ORE, bt.getOreItemId());
            }
            return BFGuidance.of(BFAction.GO_TO_BELT);
        }

        // Iron (ratio 0) uses no coal at all.
        if (s.getInvOre() < BFConstants.ORE_LOAD) {
            return BFGuidance.bankItem(BFAction.WITHDRAW_ORE, bt.getOreItemId());
        }
        return BFGuidance.of(BFAction.GO_TO_BELT);
    }
}
