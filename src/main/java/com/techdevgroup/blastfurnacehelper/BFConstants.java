package com.techdevgroup.blastfurnacehelper;
/**
 * Constants for the Blast Furnace minigame.
 * Region ID and object IDs: RuneLite built-in BlastFurnacePlugin
 *   (github.com/runelite/runelite, BSD-2-Clause).
 * Varbit IDs: RuneLite Varbits enum.
 * Item IDs: RuneLite ItemID constants and OSRS Wiki.
 * Coal bag capacity and coal ratios: OSRS Wiki "Blast Furnace".
 */
public final class BFConstants {
    private BFConstants() {}

    // BF minigame region (RuneLite BlastFurnacePlugin)
    public static final int BF_REGION = 7757;

    // Game object IDs (RuneLite BlastFurnacePlugin / ObjectID)
    public static final int CONVEYOR_BELT  = 9100;
    public static final int BAR_DISPENSER  = 9104;
    public static final int BANK_CHEST     = 26707;

    // Varbit IDs (RuneLite Varbits enum)
    /** Coal stored in the blast furnace. */
    public static final int VAR_COAL_STORED   = 1611;
    /** Bar dispenser state; > 0 means bars are ready. */
    public static final int VAR_BAR_DISPENSER = 1617;

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

    // Coal bag capacity (OSRS Wiki)
    public static final int COAL_BAG_CAPACITY = 27;

    // Bank widget group ID (RuneLite WidgetID)
    public static final int BANK_GROUP_ID = 12;
    public static final int BANK_ITEM_CONTAINER_CHILD = 12;
}
