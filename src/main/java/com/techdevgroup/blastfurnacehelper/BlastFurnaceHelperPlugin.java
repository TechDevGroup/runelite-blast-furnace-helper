package com.techdevgroup.blastfurnacehelper;

import com.google.inject.Provides;
import java.awt.Rectangle;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GameObject;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
    name = "Blast Furnace Helper",
    description = "Trip computer and click-target highlighter for Blast Furnace runs. Highlight-only — never automates input.",
    tags = {"blast furnace", "smithing", "minigame", "overlay", "highlight"}
)
public class BlastFurnaceHelperPlugin extends Plugin
{
    static final String RESET_MENU_OPTION = "Reset stats";

    @Inject private Client client;
    @Inject private OverlayManager overlayManager;
    @Inject private BlastFurnaceHelperSceneOverlay sceneOverlay;
    @Inject private BlastFurnaceHelperWidgetOverlay widgetOverlay;
    @Inject private BlastFurnaceHelperPanel panel;
    @Inject private BlastFurnaceHelperConfig config;
    @Inject private KeyManager keyManager;

    private final HotkeyListener resetHotkeyListener =
        new HotkeyListener(() -> config.resetHotkey())
        {
            @Override
            public void hotkeyPressed()
            {
                resetStats();
            }
        };

    // State
    @Getter private boolean bankOpen = false;
    /**
     * Authoritative coal-bag contents (pieces of coal). -1 = unknown.
     * Sourced primarily from the coal-bag chat messages (parsed in onChatMessage); Fill/Empty
     * menu clicks and inventory inference are secondary. Never a mere boolean, so the policy can
     * tell "confidently full" (>= capacity) from "unknown" and err coal-first when unsure.
     */
    @Getter private int coalBagCount = -1;
    @Getter private BarType detectedBarType = null;
    /** The single next action, derived from observed state each tick. */
    @Getter private BFGuidance guidance = BFGuidance.of(BFAction.IDLE);

    // Tracked objects
    @Getter private GameObject conveyorBelt = null;
    @Getter private GameObject barDispenser = null;
    @Getter private GameObject bankChest = null;
    /** The Blast Furnace coffer object (any of the three states: empty/full/active). */
    @Getter private GameObject cofferObject = null;

    // Trip computer
    @Getter private Instant sessionStart = Instant.now();
    @Getter private int coalDeposited = 0;
    @Getter private int oreDeposited = 0;
    @Getter private int barsCollected = 0;

    /**
     * Coffer balance in GP, read from varbit 5357 (VarbitID.BLAST_FURNACE_COFFER).
     * Source: RuneLite gameval/VarbitID.java (BSD-2-Clause). -1 = not yet read.
     */
    @Getter private int cofferBalance = -1;

    private Item[] prevInventory = null;
    private BarType lastEffectiveBarType = null;

    // Action logging (ground-truth capture for policy tuning).
    private final BFActionLogger actionLogger = new BFActionLogger();
    private BFAction lastLoggedAction = null;

    // Predictive "pre-aim": remembers last-seen bank item positions, persisted across sessions.
    @Getter private final BankLayoutCache bankLayout = new BankLayoutCache();
    private BFStateSnapshot lastSnapshot = null;
    private boolean bankLayoutDirty = false;

    @Override
    protected void startUp()
    {
        overlayManager.add(sceneOverlay);
        overlayManager.add(widgetOverlay);
        overlayManager.add(panel);
        keyManager.registerKeyListener(resetHotkeyListener);
        // Load the previously-seen bank layout so pre-aim works from the start of the run.
        bankLayout.load(BFActionLogger.DIR.resolve("bank-layout.json"));
        resetStats();
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(sceneOverlay);
        overlayManager.remove(widgetOverlay);
        overlayManager.remove(panel);
        keyManager.unregisterKeyListener(resetHotkeyListener);
        if (bankLayoutDirty)
        {
            bankLayout.save(BFActionLogger.DIR.resolve("bank-layout.json"));
            bankLayoutDirty = false;
        }
        clearTransientState();
    }

    private void clearTransientState()
    {
        guidance = BFGuidance.of(BFAction.IDLE);
        prevInventory = null;
        bankOpen = false;
        conveyorBelt = null;
        barDispenser = null;
        bankChest = null;
        cofferObject = null;
        cofferBalance = -1;
        coalBagCount = -1; // unknown after a relog/region change → err coal-first until observed
    }

    @Provides
    BlastFurnaceHelperConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(BlastFurnaceHelperConfig.class);
    }

    public void resetStats()
    {
        sessionStart = Instant.now();
        coalDeposited = 0;
        oreDeposited = 0;
        barsCollected = 0;
    }

    public boolean isInBlastFurnace()
    {
        if (client.getGameState() != GameState.LOGGED_IN) return false;
        if (client.getLocalPlayer() == null) return false;
        WorldPoint loc = client.getLocalPlayer().getWorldLocation();
        return loc.getRegionID() == BFConstants.BF_REGION;
    }

    public BarType getEffectiveBarType()
    {
        BarTypeConfig override = config.barType();
        if (override != BarTypeConfig.AUTO)
        {
            return BarType.valueOf(override.name());
        }
        return detectedBarType;
    }

    // ── Event handlers ────────────────────────────────────────────────────────

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() != GameState.LOGGED_IN)
        {
            clearTransientState();
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        // Explicit bar-type override change → reset the trip computer.
        if ("blastfurnacehelper".equals(event.getGroup()) && "barType".equals(event.getKey()))
        {
            resetStats();
        }
    }

    @Subscribe
    public void onOverlayMenuClicked(OverlayMenuClicked event)
    {
        OverlayMenuEntry entry = event.getEntry();
        if (event.getOverlay() == panel && RESET_MENU_OPTION.equals(entry.getOption()))
        {
            resetStats();
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick)
    {
        if (!isInBlastFurnace())
        {
            guidance = BFGuidance.of(BFAction.IDLE);
            return;
        }

        // Poll coffer balance (varbit 5357 = VarbitID.BLAST_FURNACE_COFFER).
        if (config.cofferEnabled())
        {
            cofferBalance = client.getVarbitValue(BFConstants.VAR_COFFER);
        }

        // Poll bank open/closed state.
        boolean nowBankOpen = client.getWidget(BFConstants.BANK_GROUP_ID, 0) != null;
        if (nowBankOpen)
        {
            // Refresh the seen bank layout from the live interface geometry.
            updateBankLayout();
        }
        else if (bankOpen && bankLayoutDirty)
        {
            // Bank just closed → persist the freshly-seen layout for next time.
            bankLayout.save(BFActionLogger.DIR.resolve("bank-layout.json"));
            bankLayoutDirty = false;
        }
        bankOpen = nowBankOpen;

        detectBarType();

        // Auto-reset the trip computer when the effective bar type changes mid-session.
        BarType eff = getEffectiveBarType();
        if (eff != null && lastEffectiveBarType != null && eff != lastEffectiveBarType)
        {
            resetStats();
        }
        if (eff != null)
        {
            lastEffectiveBarType = eff;
        }

        // Derive the next action from a fresh state snapshot (pure function of state).
        BFStateSnapshot snap = buildSnapshot(eff);
        lastSnapshot = snap;
        guidance = BFPolicy.derive(snap);

        // Log every change of the recommended action, so the trajectory captures the full
        // ordered sequence even without a click. Lightweight: only on transition.
        if (config.logActions() && guidance.getAction() != lastLoggedAction)
        {
            actionLogger.append(String.format(
                "tick=%d ts=%s event=REC_CHANGE recommended=%s prev=%s | %s",
                client.getTickCount(), Instant.now(), guidance.getAction().name(),
                lastLoggedAction != null ? lastLoggedAction.name() : "none", stateString(snap)));
            lastLoggedAction = guidance.getAction();
        }
    }

    /** Gathers observed game state into an immutable snapshot for the policy. */
    private BFStateSnapshot buildSnapshot(BarType bt)
    {
        ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
        Item[] items = inv != null ? inv.getItems() : null;

        int invCoal = countItem(items, BFConstants.ITEM_COAL);
        int invOre = bt != null ? countItem(items, bt.getOreItemId()) : 0;
        int invBars = bt != null ? countItem(items, bt.getBarItemId()) : 0;
        int freeSlots = countFreeSlots(items);

        int furnaceCoal = client.getVarbitValue(BFConstants.VAR_FURNACE_COAL);
        int furnaceOre = bt != null ? client.getVarbitValue(bt.getFurnaceOreVarbit()) : 0;
        int furnaceBars = bt != null ? client.getVarbitValue(bt.getFurnaceBarVarbit()) : 0;
        int dispenserState = client.getVarbitValue(BFConstants.VAR_DISPENSER_STATE);

        // coalBagFull: confidently full (>= capacity). coalBagHasCoal: holds coal, treating an
        // unknown count (-1) as "has coal" so the belt still prompts an empty. Erring coal-first.
        boolean coalBagFull = coalBagCount >= BFConstants.COAL_BAG_CAPACITY;
        boolean coalBagHasCoal = coalBagCount != 0; // > 0, or unknown (-1)

        return BFStateSnapshot.builder()
            .barType(bt)
            .bankOpen(bankOpen)
            .invCoal(invCoal)
            .invOre(invOre)
            .invBars(invBars)
            .freeSlots(freeSlots)
            .coalBagHasCoal(coalBagHasCoal)
            .coalBagFull(coalBagFull)
            .atBank(isAtBank())
            .atBelt(isAtBelt())
            .furnaceCoal(furnaceCoal)
            .furnaceOre(furnaceOre)
            .furnaceBars(furnaceBars)
            .dispenserState(dispenserState)
            .holdingCoins(countItem(items, BFConstants.ITEM_COINS) > 0)
            .cofferLow(isCofferLow())
            .cofferCritical(isCofferCritical())
            .build();
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (event.getContainerId() != InventoryID.INVENTORY.getId()) return;
        if (!isInBlastFurnace()) return;

        Item[] current = event.getItemContainer().getItems();

        if (prevInventory != null)
        {
            BarType bt = getEffectiveBarType();

            int coalDelta = countItem(current, BFConstants.ITEM_COAL)
                - countItem(prevInventory, BFConstants.ITEM_COAL);
            if (coalDelta < 0 && !bankOpen)
            {
                coalDeposited += Math.abs(coalDelta);
            }

            if (bt != null)
            {
                int oreDelta = countItem(current, bt.getOreItemId())
                    - countItem(prevInventory, bt.getOreItemId());
                if (oreDelta < 0 && !bankOpen)
                {
                    oreDeposited += Math.abs(oreDelta);
                }

                int barDelta = countItem(current, bt.getBarItemId())
                    - countItem(prevInventory, bt.getBarItemId());
                if (barDelta > 0 && !bankOpen)
                {
                    barsCollected += barDelta;
                }
            }
        }

        prevInventory = current.clone();
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (!isInBlastFurnace()) return;
        String option = event.getMenuOption();
        if (option == null) return;

        int itemId = event.getItemId();
        if (itemId == BFConstants.ITEM_COAL_BAG || itemId == BFConstants.ITEM_COAL_BAG_FULL)
        {
            // Secondary inference from the click (chat messages are authoritative and override).
            if ("Fill".equalsIgnoreCase(option))
            {
                // The bag absorbs coal from the inventory, up to capacity.
                ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
                int invCoal = inv != null ? countItem(inv.getItems(), BFConstants.ITEM_COAL) : 0;
                int prev = Math.max(coalBagCount, 0);
                coalBagCount = Math.min(prev + invCoal, BFConstants.COAL_BAG_CAPACITY);
            }
            else if ("Empty".equalsIgnoreCase(option))
            {
                coalBagCount = 0;
            }
        }

        // Ground-truth capture: pair what the player actually clicked against what the policy
        // recommended at that same moment. The snapshot is rebuilt here so it reflects state at
        // click time (coal-bag state above is already applied).
        if (config.logActions())
        {
            logClick(event);
        }
    }

    // Coal-bag chat messages (authoritative). Examples:
    //   "The coal bag contains twenty-seven pieces of coal." / "...contains 27 pieces of coal."
    //   "The coal bag is empty." / "Your coal bag is now empty."
    //   "The coal bag is now full." / "Your coal bag is already full."
    private static final Pattern COAL_BAG_COUNT =
        Pattern.compile("coal bag[^0-9]*?(\\d+) pieces of coal", Pattern.CASE_INSENSITIVE);

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (event.getType() != ChatMessageType.GAMEMESSAGE
            && event.getType() != ChatMessageType.SPAM)
        {
            return;
        }

        String msg = Text.removeTags(event.getMessage());
        String lower = msg.toLowerCase();
        if (!lower.contains("coal bag"))
        {
            return;
        }

        if (lower.contains("empty"))
        {
            coalBagCount = 0;
            return;
        }
        if (lower.contains("full"))
        {
            coalBagCount = BFConstants.COAL_BAG_CAPACITY;
            return;
        }
        Matcher m = COAL_BAG_COUNT.matcher(msg);
        if (m.find())
        {
            try
            {
                int n = Integer.parseInt(m.group(1));
                coalBagCount = Math.max(0, Math.min(n, BFConstants.COAL_BAG_CAPACITY));
            }
            catch (NumberFormatException ignored)
            {
                // Leave the count as-is on parse failure; err coal-first.
            }
        }
    }

    private void logClick(MenuOptionClicked event)
    {
        BFStateSnapshot snap = buildSnapshot(getEffectiveBarType());
        BFGuidance rec = BFPolicy.derive(snap);
        MenuAction ma = event.getMenuAction();
        String target = event.getMenuTarget() != null ? Text.removeTags(event.getMenuTarget()) : "";
        actionLogger.append(String.format(
            "tick=%d ts=%s event=CLICK option=\"%s\" target=\"%s\" id=%d itemId=%d menuAction=%s type=%s "
                + "| %s | recommended=%s",
            client.getTickCount(), Instant.now(),
            event.getMenuOption() != null ? event.getMenuOption() : "",
            target, event.getId(), event.getItemId(),
            ma != null ? ma.name() : "?", targetType(ma),
            stateString(snap), rec.getAction().name()));
    }

    private static String targetType(MenuAction ma)
    {
        if (ma == null) return "UNKNOWN";
        String n = ma.name();
        if (n.contains("GAME_OBJECT")) return "OBJECT";
        if (n.contains("NPC")) return "NPC";
        if (n.contains("ITEM") || n.contains("WIDGET") || n.startsWith("CC_")) return "ITEM/WIDGET";
        return "OTHER";
    }

    private String stateString(BFStateSnapshot s)
    {
        String pos = "?";
        int region = -1;
        if (client.getLocalPlayer() != null)
        {
            WorldPoint p = client.getLocalPlayer().getWorldLocation();
            if (p != null)
            {
                pos = p.getX() + "," + p.getY() + "," + p.getPlane();
                region = p.getRegionID();
            }
        }
        return String.format(
            "state[coal=%d ore=%d bars=%d free=%d bagCount=%d bagFull=%b atBank=%b atBelt=%b "
                + "fcoal=%d fore=%d fbars=%d disp=%d coffer=%d region=%d pos=%s]",
            s.getInvCoal(), s.getInvOre(), s.getInvBars(), s.getFreeSlots(),
            coalBagCount, s.isCoalBagFull(), s.isAtBank(), s.isAtBelt(),
            s.getFurnaceCoal(), s.getFurnaceOre(), s.getFurnaceBars(),
            s.getDispenserState(), cofferBalance, region, pos);
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        trackObject(event.getGameObject(), true);
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event)
    {
        trackObject(event.getGameObject(), false);
    }

    private void trackObject(GameObject obj, boolean spawned)
    {
        int id = obj.getId();
        if (id == BFConstants.CONVEYOR_BELT)
        {
            conveyorBelt = spawned ? obj : null;
        }
        else if (BFConstants.isDispenserObject(id))
        {
            // Dispenser cycles through 9092-9096 by state; keep the highlight on it regardless.
            barDispenser = spawned ? obj : null;
        }
        else if (id == BFConstants.BANK_CHEST)
        {
            bankChest = spawned ? obj : null;
        }
        else if (id == BFConstants.COFFER_EMPTY
            || id == BFConstants.COFFER_FULL
            || id == BFConstants.COFFER_ACTIVE)
        {
            cofferObject = spawned ? obj : null;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void detectBarType()
    {
        if (config.barType() != BarTypeConfig.AUTO) return;

        ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
        if (inv == null) return;

        Item[] items = inv.getItems();
        boolean hasCoal = countItem(items, BFConstants.ITEM_COAL) > 0;

        // Check non-iron ores first (specific detection)
        for (BarType bt : BarType.values())
        {
            if (bt == BarType.IRON || bt == BarType.STEEL) continue;
            if (countItem(items, bt.getOreItemId()) > 0)
            {
                detectedBarType = bt;
                return;
            }
        }

        // Steel: iron ore + coal
        if (hasCoal && countItem(items, BFConstants.ITEM_IRON_ORE) > 0)
        {
            detectedBarType = BarType.STEEL;
            return;
        }

        // Iron: iron ore without coal
        if (countItem(items, BFConstants.ITEM_IRON_ORE) > 0)
        {
            detectedBarType = BarType.IRON;
        }
    }

    int countItem(Item[] items, int itemId)
    {
        if (items == null) return 0;
        int count = 0;
        for (Item item : items)
        {
            if (item != null && item.getId() == itemId)
            {
                count += item.getQuantity();
            }
        }
        return count;
    }

    private int countFreeSlots(Item[] items)
    {
        if (items == null) return 28;
        int used = 0;
        for (Item item : items)
        {
            if (item != null && item.getId() > 0 && item.getQuantity() > 0)
            {
                used++;
            }
        }
        return Math.max(0, 28 - used);
    }

    // ── Coffer helpers ────────────────────────────────────────────────────────

    /** Estimated minutes of coffer time remaining. Drain 1,200 gp/min (OSRS Wiki, 2026-07-11). */
    public double getCofferMinutesLeft()
    {
        if (cofferBalance <= 0) return 0.0;
        return (double) cofferBalance / BFConstants.COFFER_DRAIN_PER_MINUTE;
    }

    public boolean isCofferCritical()
    {
        if (!config.cofferEnabled() || cofferBalance < 0) return false;
        return cofferBalance <= config.cofferCriticalGp();
    }

    public boolean isCofferLow()
    {
        if (!config.cofferEnabled() || cofferBalance < 0) return false;
        if (isCofferCritical()) return false;
        return getCofferMinutesLeft() < config.cofferLowMinutes();
    }

    public boolean isHoldingCoins()
    {
        ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
        if (inv == null) return false;
        return countItem(inv.getItems(), BFConstants.ITEM_COINS) > 0;
    }

    // ── Location context ────────────────────────────────────────────────────────

    /** True when the bank interface is open or the player is standing at the bank chest. */
    public boolean isAtBank()
    {
        if (bankOpen) return true;
        if (nearObject(bankChest)) return true;
        return nearWorldPoint(BFConstants.BANK_ANCHOR_X, BFConstants.BANK_ANCHOR_Y);
    }

    /** True when the player is standing at the conveyor belt. */
    public boolean isAtBelt()
    {
        if (nearObject(conveyorBelt)) return true;
        return nearWorldPoint(BFConstants.BELT_ANCHOR_X, BFConstants.BELT_ANCHOR_Y);
    }

    private WorldPoint playerLocation()
    {
        return client.getLocalPlayer() != null ? client.getLocalPlayer().getWorldLocation() : null;
    }

    private boolean nearWorldPoint(int x, int y)
    {
        WorldPoint p = playerLocation();
        if (p == null) return false;
        return Math.abs(p.getX() - x) <= BFConstants.PROXIMITY_RADIUS
            && Math.abs(p.getY() - y) <= BFConstants.PROXIMITY_RADIUS;
    }

    private boolean nearObject(GameObject obj)
    {
        if (obj == null) return false;
        WorldPoint p = playerLocation();
        WorldPoint o = obj.getWorldLocation();
        if (p == null || o == null || p.getPlane() != o.getPlane()) return false;
        return Math.abs(p.getX() - o.getX()) <= BFConstants.PROXIMITY_RADIUS
            && Math.abs(p.getY() - o.getY()) <= BFConstants.PROXIMITY_RADIUS;
    }

    // ── Predictive pre-aim ──────────────────────────────────────────────────────

    /**
     * Records the on-screen bounds of relevant bank items from the live interface geometry
     * (Widget child bounds of the bank item container, group 12 child 12), keyed by item id.
     */
    private void updateBankLayout()
    {
        Widget container = client.getWidget(BFConstants.BANK_GROUP_ID,
            BFConstants.BANK_ITEM_CONTAINER_CHILD);
        if (container == null || container.isHidden())
        {
            return;
        }
        Widget[] children = container.getDynamicChildren();
        if (children == null)
        {
            return;
        }
        for (int i = 0; i < children.length; i++)
        {
            Widget child = children[i];
            if (child == null || child.isHidden())
            {
                continue;
            }
            int id = child.getItemId();
            if (isRelevantBankItem(id))
            {
                Rectangle b = child.getBounds();
                if (b != null && b.width > 0 && b.height > 0)
                {
                    bankLayout.record(id, i, b);
                    bankLayoutDirty = true;
                }
            }
        }
    }

    /**
     * The predicted next bank-withdrawal item id when the bank is CLOSED and the player is
     * heading to the bank — computed by asking the policy what it would withdraw if the bank were
     * open right now. Returns -1 when the bank is open (the real highlight covers it), when the
     * feature is off, or when no withdrawal is pending. The caller pairs this with
     * {@link #getBankLayout()} to place a ghost marker at the last-seen position.
     */
    public int getPredictedBankItemId()
    {
        if (!config.predictNextTarget() || bankOpen || lastSnapshot == null)
        {
            return -1;
        }
        if (guidance.getAction() != BFAction.GO_TO_BANK)
        {
            return -1;
        }
        BFStateSnapshot asIfOpen = lastSnapshot.toBuilder().bankOpen(true).build();
        return BFPolicy.derive(asIfOpen).getBankItemId();
    }

    private boolean isRelevantBankItem(int id)
    {
        if (id == BFConstants.ITEM_COAL || id == BFConstants.ITEM_COINS
            || id == BFConstants.ITEM_IRON_ORE || id == BFConstants.ITEM_MITHRIL_ORE
            || id == BFConstants.ITEM_ADAMANTITE_ORE || id == BFConstants.ITEM_RUNITE_ORE)
        {
            return true;
        }
        for (int r : BFConstants.RUN_ENERGY_ITEMS)
        {
            if (r == id) return true;
        }
        return false;
    }
}
