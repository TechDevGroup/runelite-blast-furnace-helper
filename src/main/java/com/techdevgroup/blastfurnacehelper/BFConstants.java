package com.techdevgroup.blastfurnacehelper;
/**
 * Constants for the Blast Furnace minigame.
 * Region ID and object IDs: RuneLite built-in BlastFurnacePlugin
 *   (github.com/runelite/runelite, BSD-2-Clause).
 * Varbit IDs: RuneLite Varbits enum.
 * Item IDs: RuneLite ItemID constants and OSRS Wiki.
 * Coal bag capacity and coal ratios: OSRS Wiki "Blast Furnace".
 * Coffer constants: RuneLite gameval/VarbitID.java + gameval/ObjectID.java (BSD-2-Clause).
 * Coffer drain rates: OSRS Wiki "Blast Furnace", retrieved 2026-07-07.
 */
public final class BFConstants {
    private BFConstants() {}

    // BF minigame region (RuneLite BlastFurnacePlugin)
    public static final int BF_REGION = 7757;

    // Game object IDs (RuneLite BlastFurnacePlugin / ObjectID)
    public static final int CONVEYOR_BELT  = 9100;
    public static final int BAR_DISPENSER  = 9104;
    public static final int BANK_CHEST     = 26707;

    // Coffer game object IDs
    // Source: github.com/runelite/runelite runelite-api/.../gameval/ObjectID.java (BSD-2-Clause)
    // BLAST_FURNACE_AUTOMATA_COFFER_EMPTY = 29328
    // BLAST_FURNACE_AUTOMATA_COFFER_FULL  = 29329
    // BLAST_FURNACE_AUTOMATA_COFFER       = 29330
    public static final int COFFER_EMPTY  = 29328;
    public static final int COFFER_FULL   = 29329;
    public static final int COFFER_ACTIVE = 29330;

    // Varbit IDs (RuneLite Varbits enum)
    /** Coal stored in the blast furnace. */
    public static final int VAR_COAL_STORED   = 1611;
    /** Bar dispenser state; > 0 means bars are ready. */
    public static final int VAR_BAR_DISPENSER = 1617;
    /**
     * Coffer balance in GP.
     * Source: github.com/runelite/runelite runelite-api/.../gameval/VarbitID.java (BSD-2-Clause)
     * VarbitID.BLAST_FURNACE_COFFER = 5357.
     * Used by RuneLite's built-in BlastFurnaceCofferOverlay via
     *   client.getVarbitValue(VarbitID.BLAST_FURNACE_COFFER).
     */
    public static final int VAR_COFFER = 5357;

    // Item IDs (RuneLite ItemID / OSRS Wiki)
    public static final int ITEM_COAL            = 453;
    public static final int ITEM_COAL_BAG        = 12019;
    public static final int ITEM_COAL_BAG_FULL   = 12020;
    public static final int ITEM_IRON_ORE        = 440;
    public static final int ITEM_MITHRIL_ORE     = 447;
    public static final int ITEM_ADAMANTITE_ORE  = 449;
    public static final int ITEM_RUNITE_ORE      = 451;
    public static final int ITEM_IRON_BAR        = 2351;
    public static final int ITEM_STEEL_BAR       = 2353;
    public static final int ITEM_MITHRIL_BAR     = 2359;
    public static final int ITEM_ADAMANTITE_BAR  = 2361;
    public static final int ITEM_RUNITE_BAR      = 2363;
    public static final int ITEM_STAMINA_4       = 12625;
    public static final int ITEM_STAMINA_3       = 12627;
    public static final int ITEM_STAMINA_2       = 12629;
    public static final int ITEM_STAMINA_1       = 12631;
    public static final int ITEM_ENERGY_4        = 3010;
    public static final int ITEM_ENERGY_3        = 3008;
    public static final int ITEM_ENERGY_2        = 3006;
    public static final int ITEM_ENERGY_1        = 3004;
    /** Coins — used for coffer refill bank-withdrawal highlight. */
    public static final int ITEM_COINS           = 995;

    // Coal bag capacity (OSRS Wiki)
    public static final int COAL_BAG_CAPACITY = 27;

    // Coffer drain rates — OSRS Wiki "Blast Furnace", retrieved 2026-07-07.
    // 72,000 gp/hr = 1,200 gp/min = 12 gp/tick on BF worlds while furnace is operating.
    // Foreman fee (Smithing < 60): 2,500 gp per 10 min (separate from coffer drain).
    // Maximum coffer capacity: 20,000,000 gp.
    public static final int COFFER_DRAIN_PER_HOUR   = 72_000;
    public static final int COFFER_DRAIN_PER_MINUTE = 1_200;
    public static final int COFFER_MAX              = 20_000_000;

    // Bank widget group ID (RuneLite WidgetID)
    public static final int BANK_GROUP_ID = 12;
    public static final int BANK_ITEM_CONTAINER_CHILD = 12;
}
