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

    // Game object IDs — RuneLite gameval/ObjectID.java (BSD-2-Clause).
    /** Clickable conveyor belt ("Put-ore-on"): BLAST_FURNACE_CONVEYER_BELT_CLICKABLE = 9100. */
    public static final int CONVEYOR_BELT  = 9100;
    /** BF bank chest object. */
    public static final int BANK_CHEST     = 26707;

    // Bar dispenser objects — RuneLite gameval/ObjectID.java (BSD-2-Clause).
    // The dispenser cycles through several object IDs depending on its state; we track
    // the whole family so the highlight follows it regardless of state. RuneLite core's
    // BlastFurnacePlugin tracks BLAST_FURNACE_DISPENSER (9092). The earlier 9104 value was
    // wrong — 9104 is BLAST_FURNACE_CONVEYER_COGS2 (a cog), not the dispenser.
    public static final int DISPENSER_BASE    = 9092; // BLAST_FURNACE_DISPENSER
    public static final int DISPENSER_EMPTY   = 9093; // BLAST_FURNACE_ORE_DISPENSER_EMPTY
    public static final int DISPENSER_FORANIM = 9094; // BLAST_FURNACE_ORE_DISPENSER_FORANIM
    public static final int DISPENSER_FULL    = 9095; // BLAST_FURNACE_ORE_DISPENSER_FULL (Take)
    public static final int DISPENSER_COOLED  = 9096; // BLAST_FURNACE_ORE_DISPENSER_COOLED (Take/Check)
    public static final int[] DISPENSER_IDS = {
        DISPENSER_BASE, DISPENSER_EMPTY, DISPENSER_FORANIM, DISPENSER_FULL, DISPENSER_COOLED
    };

    public static boolean isDispenserObject(int id) {
        for (int d : DISPENSER_IDS) { if (d == id) return true; }
        return false;
    }

    // Coffer game object IDs
    // Source: github.com/runelite/runelite runelite-api/.../gameval/ObjectID.java (BSD-2-Clause)
    // BLAST_FURNACE_AUTOMATA_COFFER_EMPTY = 29328
    // BLAST_FURNACE_AUTOMATA_COFFER_FULL  = 29329
    // BLAST_FURNACE_AUTOMATA_COFFER       = 29330
    public static final int COFFER_EMPTY  = 29328;
    public static final int COFFER_FULL   = 29329;
    public static final int COFFER_ACTIVE = 29330;

    // Furnace-content varbit IDs — RuneLite gameval/VarbitID.java (BSD-2-Clause),
    // the same varbits RuneLite core's BlastFurnaceOverlay reads via the BarsOres enum.
    /** Coal currently stored in the furnace: BLAST_FURNACE_COAL = 949. */
    public static final int VAR_FURNACE_COAL       = 949;
    /** Iron ore in furnace (also the ore used for steel): BLAST_FURNACE_IRON_ORE = 951. */
    public static final int VAR_FURNACE_IRON_ORE   = 951;
    public static final int VAR_FURNACE_MITHRIL_ORE   = 952;
    public static final int VAR_FURNACE_ADAMANTITE_ORE = 953;
    public static final int VAR_FURNACE_RUNITE_ORE = 954;
    /** Finished bars waiting in the dispenser, per type. */
    public static final int VAR_FURNACE_IRON_BARS       = 942;
    public static final int VAR_FURNACE_STEEL_BARS      = 943;
    public static final int VAR_FURNACE_MITHRIL_BARS    = 944;
    public static final int VAR_FURNACE_ADAMANTITE_BARS = 945;
    public static final int VAR_FURNACE_RUNITE_BARS     = 946;
    /**
     * Dispenser / smelting state: BLAST_FURNACE_BARS_HOT = 936.
     * Semantics (from RuneLite core BlastFurnaceClickBoxOverlay):
     *   1 = ore on belt not yet smelted (belt busy), 2 = bars hot (need ice gloves to take),
     *   3 = bars cooled (safe to take).
     */
    public static final int VAR_DISPENSER_STATE = 936;
    /**
     * Coffer balance in GP.
     * Source: RuneLite gameval/VarbitID.java (BSD-2-Clause) VarbitID.BLAST_FURNACE_COFFER = 5357.
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
    /** Coins — used for coffer refill bank-withdrawal highlight. */
    public static final int ITEM_COINS           = 995;

    // ── Run-energy restoratives ────────────────────────────────────────────────
    // All ids cache-verified against RuneLite gameval/ItemID.java (BSD-2-Clause).
    // There is NO distinct "extended stamina" item in the game; this is the full run-energy
    // restore family. RUN_ENERGY_ITEMS lists them in highlight-preference order: Stamina
    // potion > Stamina mix > Super energy > Super energy mix > Energy potion, highest dose
    // first within each family (stamina is the Blast Furnace standard, but we highlight
    // whatever the player actually carries).
    public static final int ITEM_STAMINA_4       = 12625; // _4DOSESTAMINA
    public static final int ITEM_STAMINA_3       = 12627; // _3DOSESTAMINA
    public static final int ITEM_STAMINA_2       = 12629; // _2DOSESTAMINA
    public static final int ITEM_STAMINA_1       = 12631; // _1DOSESTAMINA
    public static final int ITEM_STAMINA_MIX_2   = 12633; // BRUTAL_2DOSESTAMINA (stamina mix)
    public static final int ITEM_STAMINA_MIX_1   = 12635; // BRUTAL_1DOSESTAMINA (stamina mix)
    public static final int ITEM_SUPER_ENERGY_4  = 3016;  // _4DOSE2ENERGY
    public static final int ITEM_SUPER_ENERGY_3  = 3018;  // _3DOSE2ENERGY
    public static final int ITEM_SUPER_ENERGY_2  = 3020;  // _2DOSE2ENERGY
    public static final int ITEM_SUPER_ENERGY_1  = 3022;  // _1DOSE2ENERGY
    public static final int ITEM_SUPER_ENERGY_MIX_2 = 11481; // BRUTAL_2DOSE2ENERGY
    public static final int ITEM_SUPER_ENERGY_MIX_1 = 11483; // BRUTAL_1DOSE2ENERGY
    public static final int ITEM_ENERGY_4        = 3008;  // _4DOSE1ENERGY
    public static final int ITEM_ENERGY_3        = 3010;  // _3DOSE1ENERGY
    public static final int ITEM_ENERGY_2        = 3012;  // _2DOSE1ENERGY
    public static final int ITEM_ENERGY_1        = 3014;  // _1DOSE1ENERGY

    /** Run-energy restoratives in highlight-preference order (best first). */
    public static final int[] RUN_ENERGY_ITEMS = {
        ITEM_STAMINA_4, ITEM_STAMINA_3, ITEM_STAMINA_2, ITEM_STAMINA_1,
        ITEM_STAMINA_MIX_2, ITEM_STAMINA_MIX_1,
        ITEM_SUPER_ENERGY_4, ITEM_SUPER_ENERGY_3, ITEM_SUPER_ENERGY_2, ITEM_SUPER_ENERGY_1,
        ITEM_SUPER_ENERGY_MIX_2, ITEM_SUPER_ENERGY_MIX_1,
        ITEM_ENERGY_4, ITEM_ENERGY_3, ITEM_ENERGY_2, ITEM_ENERGY_1,
    };

    // Coal bag capacity (OSRS Wiki)
    public static final int COAL_BAG_CAPACITY = 27;
    /** Loose coal to withdraw per coal trip (fills inventory alongside the bag). */
    public static final int COAL_INV_LOAD = 27;
    /** Ore to withdraw per ore trip (near-full inventory, leaving room to work). */
    public static final int ORE_LOAD = 27;

    // Inventory widget group — RuneLite WidgetID (INVENTORY group = 149, container child 0).
    public static final int INVENTORY_GROUP_ID = 149;
    public static final int INVENTORY_CONTAINER_CHILD = 0;

    // Bankside inventory (the inventory shown beside the bank while it is open).
    // Source: RuneLite gameval/InterfaceID.java — Bankside (group 15), ITEMS child = 3.
    public static final int BANKSIDE_GROUP_ID = 15;
    public static final int BANKSIDE_ITEMS_CHILD = 3;

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
