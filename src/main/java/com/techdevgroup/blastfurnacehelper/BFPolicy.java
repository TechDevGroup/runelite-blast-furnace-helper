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
 * <p>The loop is LOCATION-AWARE (a coal-bag fill and a belt-deposit look similar but happen at
 * different stations): fills/withdrawals are only recommended at the bank, and deposits/empties
 * only at the belt, using {@code bankOpen} plus belt/bank proximity from the snapshot.
 *
 * <p>Priority order:
 * <ol>
 *   <li>Coffer critical: get / deposit coins (the furnace stops when the coffer empties).</li>
 *   <li>Finished bars in inventory: bank them.</li>
 *   <li>Bank interface open: acquire the next material (see {@link #bankAcquire}). Coal-before-ore
 *       is preserved — the coal bag is always filled first — but whether a trip additionally
 *       needs a loose coal LOAD or goes straight to ore is derived from the FURNACE coal/ore
 *       varbits and the ratio, not from inventory coal.</li>
 *   <li>At the belt (interface closed): deposit loose coal, then ore, then empty the coal bag —
 *       never a fill/withdraw here.</li>
 *   <li>At the bank chest (interface closed): finish filling the bag if coal is still in hand
 *       (the "exited-bank to fill" case), else carry a load to the belt, else open the bank.</li>
 *   <li>En route: carry any load to the belt.</li>
 *   <li>Empty-handed return leg: collect bars at the dispenser (before any bank trip), then a
 *       coffer top-up if low and carrying coins, else go to the bank to restock.</li>
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

        // 3. AT THE BANK (interface open) → acquire the next material. Coal-before-ore; the
        //    loose-coal-vs-ore decision comes from the furnace state (see bankAcquire).
        if (s.isBankOpen()) {
            return bankAcquire(s, bt, ratio);
        }

        // ── Context-gated smithing loop (bank interface closed). ────────────────────────────
        // The policy is location-aware so it never confuses a belt-deposit with a coal-bag fill:
        //   • Fill/withdraw only happen at the bank; deposit/empty only at the belt.

        // 4. AT THE BELT → unload only. Deposit the carried coal/ore, then empty the bag and
        //    deposit that too. Never a fill/withdraw here.
        if (s.isAtBelt()) {
            if (s.getInvCoal() > 0) {
                return BFGuidance.of(BFAction.DEPOSIT_COAL);
            }
            if (s.getInvOre() > 0) {
                return BFGuidance.of(BFAction.DEPOSIT_ORE);
            }
            if (s.isCoalBagHasCoal() && s.getFreeSlots() > 0) {
                return BFGuidance.invItem(BFAction.EMPTY_COAL_BAG, BFConstants.ITEM_COAL_BAG);
            }
            // Nothing left to deposit → fall through to the return-leg tail (collect/bank).
        }
        // 5. AT THE BANK CHEST but the interface is closed → finish filling the bag if we still
        //    hold loose coal for it (the "exited-bank to fill" case), else head to the belt with
        //    a load, else open the bank to withdraw. Never a belt-deposit here.
        else if (s.isAtBank()) {
            if (ratio > 0 && !s.isCoalBagFull() && s.getInvCoal() > 0) {
                return BFGuidance.invItem(BFAction.FILL_COAL_BAG, BFConstants.ITEM_COAL_BAG);
            }
            if (s.getInvCoal() > 0 || s.getInvOre() > 0 || s.isCoalBagHasCoal()) {
                return BFGuidance.of(BFAction.GO_TO_BELT);
            }
            return BFGuidance.of(BFAction.GO_TO_BANK);
        }
        // 6. EN ROUTE (between stations) → carry any load to the belt.
        else if (s.getInvCoal() > 0 || s.getInvOre() > 0 || s.isCoalBagHasCoal()) {
            return BFGuidance.of(BFAction.GO_TO_BELT);
        }

        // ── Return-leg tail (reached with an empty inventory). ──────────────────────────────
        // 7. Collect bars while passing the dispenser — strictly before any bank trip.
        if (s.getFurnaceBars() >= 1 && s.getFreeSlots() > 0) {
            return BFGuidance.of(BFAction.COLLECT_BARS);
        }
        // 8. Coffer low and carrying coins → top it up on the way past.
        if (s.isCofferLow() && s.isHoldingCoins()) {
            return BFGuidance.of(BFAction.REFILL_COFFER);
        }
        // 9. Nothing to do → head to the bank to restock.
        return BFGuidance.of(BFAction.GO_TO_BANK);
    }

    /**
     * Bank-phase material acquisition. Coal-before-ore is preserved (the bag is always filled
     * first), but whether a trip additionally needs a loose coal LOAD — or goes straight to ore
     * after the bag — is derived from the FURNACE coal/ore varbits and the ratio, never from
     * inventory coal. This fixes the stuck transition where filling the bag consumed the
     * inventory coal and a heuristic {@code invCoal > 0} ore gate could never be met.
     *
     * <p>Sequence:
     * <ol>
     *   <li>Fill the coal bag (withdraw coal if needed, then fill) — the bag holds coal only.</li>
     *   <li>With the bag full, ask the furnace: even after the bag's coal is deposited, would the
     *       furnace still be short of coal for its ore? If yes → this is a "coal trip": withdraw a
     *       loose coal load, then go to the belt. If no → this is an "ore trip": go straight to
     *       ore. The loose-coal-load requirement can be ZERO (fill bag → ore).</li>
     * </ol>
     */
    private static BFGuidance bankAcquire(BFStateSnapshot s, BarType bt, int ratio) {
        // Iron (ratio 0) uses no coal at all.
        if (ratio <= 0) {
            if (s.getInvOre() < BFConstants.ORE_LOAD) {
                return BFGuidance.bankItem(BFAction.WITHDRAW_ORE, bt.getOreItemId());
            }
            return BFGuidance.of(BFAction.GO_TO_BELT);
        }

        // 1. Fill the coal bag first (coal-before-ore). The bag can only hold coal.
        if (!s.isCoalBagFull()) {
            if (s.getInvCoal() <= 0) {
                return BFGuidance.bankItem(BFAction.WITHDRAW_COAL, BFConstants.ITEM_COAL);
            }
            // Fill the bag — an inventory-item action (highlighted in the bankside inventory).
            return BFGuidance.invItem(BFAction.FILL_COAL_BAG, BFConstants.ITEM_COAL_BAG);
        }

        // 2. Bag is full. Does the furnace need a loose coal load too (a "coal trip")?
        if (furnaceNeedsLooseCoal(s, ratio)) {
            if (s.getInvCoal() < BFConstants.COAL_INV_LOAD) {
                return BFGuidance.bankItem(BFAction.WITHDRAW_COAL, BFConstants.ITEM_COAL);
            }
            return BFGuidance.of(BFAction.GO_TO_BELT);
        }

        // 3. Ore trip: bag full and no loose coal needed → withdraw ore.
        //    (Crucially, this does NOT require invCoal > 0 — the bag's coal already suffices.)
        if (s.getInvOre() < BFConstants.ORE_LOAD) {
            return BFGuidance.bankItem(BFAction.WITHDRAW_ORE, bt.getOreItemId());
        }
        return BFGuidance.of(BFAction.GO_TO_BELT);
    }

    /**
     * Furnace-derived loose-coal decision. Even after the coal bag's full capacity is deposited,
     * would the furnace still be short of coal for the ore it holds (plus one more unit we would
     * add)? Pure function of the furnace varbits and ratio — no inventory heuristics. Returns
     * false (→ go straight to ore) for low ratios and whenever the bag alone covers the furnace,
     * which is the "fill bag, then ore" trip the coal→ore transition must reach.
     */
    private static boolean furnaceNeedsLooseCoal(BFStateSnapshot s, int ratio) {
        return s.getFurnaceCoal() + BFConstants.COAL_BAG_CAPACITY
            < ratio * (s.getFurnaceOre() + 1);
    }
}
