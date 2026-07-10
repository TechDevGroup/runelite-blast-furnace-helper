package com.techdevgroup.blastfurnacehelper;

import com.google.inject.Provides;
import java.time.Instant;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GameObject;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
    name = "Blast Furnace Helper",
    description = "Trip computer and click-target highlighter for Blast Furnace runs. Highlight-only — never automates input.",
    tags = {"blast furnace", "smithing", "minigame", "overlay", "highlight"}
)
public class BlastFurnaceHelperPlugin extends Plugin
{
    @Inject private Client client;
    @Inject private OverlayManager overlayManager;
    @Inject private BlastFurnaceHelperSceneOverlay sceneOverlay;
    @Inject private BlastFurnaceHelperWidgetOverlay widgetOverlay;
    @Inject private BlastFurnaceHelperPanel panel;
    @Inject private BlastFurnaceHelperConfig config;

    // State
    @Getter private BFTripState tripState = BFTripState.IDLE;
    @Getter private boolean bankOpen = false;
    @Getter private boolean coalBagFull = false;
    @Getter private BarType detectedBarType = null;

    // Tracked objects
    @Getter private GameObject conveyorBelt = null;
    @Getter private GameObject barDispenser = null;
    @Getter private GameObject bankChest = null;

    // Trip computer
    @Getter private Instant sessionStart = Instant.now();
    @Getter private int coalDeposited = 0;
    @Getter private int oreDeposited = 0;
    @Getter private int barsCollected = 0;

    private Item[] prevInventory = null;
    private boolean prevBankOpen = false;

    @Override
    protected void startUp()
    {
        overlayManager.add(sceneOverlay);
        overlayManager.add(widgetOverlay);
        overlayManager.add(panel);
        resetStats();
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(sceneOverlay);
        overlayManager.remove(widgetOverlay);
        overlayManager.remove(panel);
        tripState = BFTripState.IDLE;
        prevInventory = null;
        bankOpen = false;
        prevBankOpen = false;
        conveyorBelt = null;
        barDispenser = null;
        bankChest = null;
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
            tripState = BFTripState.IDLE;
            prevInventory = null;
            bankOpen = false;
            prevBankOpen = false;
            conveyorBelt = null;
            barDispenser = null;
            bankChest = null;
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick)
    {
        if (!isInBlastFurnace())
        {
            if (tripState != BFTripState.IDLE) tripState = BFTripState.IDLE;
            return;
        }

        // Poll bank open/closed state
        boolean bankNow = client.getWidget(BFConstants.BANK_GROUP_ID, 0) != null;
        if (bankNow != prevBankOpen)
        {
            if (bankNow)
            {
                bankOpen = true;
                onBankOpened();
            }
            else
            {
                bankOpen = false;
                onBankClosed();
            }
            prevBankOpen = bankNow;
        }

        detectBarType();
        selfCorrectState();
    }

    private void onBankOpened()
    {
        switch (tripState)
        {
            case IDLE:
            case BELT_DEPOSIT_COAL:
                tripState = BFTripState.BANK_WITHDRAW_COAL_1;
                break;
            case COLLECT_BARS:
                tripState = BFTripState.BANK_DEPOSIT_BARS;
                break;
            default:
                break;
        }
    }

    private void onBankClosed()
    {
        switch (tripState)
        {
            case BANK_WITHDRAW_COAL_1:
                tripState = BFTripState.BELT_DEPOSIT_COAL;
                break;
            case BANK_WITHDRAW_ORE:
                tripState = BFTripState.BELT_DEPOSIT_ORE;
                break;
            case BANK_DEPOSIT_BARS:
                tripState = BFTripState.BANK_WITHDRAW_COAL_1;
                break;
            default:
                break;
        }
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
                    if (tripState == BFTripState.BELT_DEPOSIT_ORE)
                    {
                        tripState = BFTripState.AWAITING_BARS;
                    }
                }

                int barDelta = countItem(current, bt.getBarItemId())
                    - countItem(prevInventory, bt.getBarItemId());
                if (barDelta > 0 && !bankOpen)
                {
                    barsCollected += barDelta;
                    tripState = BFTripState.BANK_DEPOSIT_BARS;
                }

                if (barDelta < 0 && bankOpen && tripState == BFTripState.BANK_DEPOSIT_BARS)
                {
                    tripState = BFTripState.BANK_WITHDRAW_COAL_1;
                }
            }
        }

        prevInventory = current.clone();
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        if (!isInBlastFurnace()) return;
        int dispenserState = client.getVarbitValue(BFConstants.VAR_BAR_DISPENSER);
        if (dispenserState > 0
            && (tripState == BFTripState.AWAITING_BARS || tripState == BFTripState.BELT_DEPOSIT_ORE))
        {
            tripState = BFTripState.COLLECT_BARS;
        }
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
            if ("Fill".equalsIgnoreCase(option))
            {
                coalBagFull = true;
            }
            else if ("Empty".equalsIgnoreCase(option))
            {
                coalBagFull = false;
            }
        }

        if (event.getId() == BFConstants.CONVEYOR_BELT)
        {
            if (tripState == BFTripState.BELT_DEPOSIT_COAL && !coalBagFull)
            {
                tripState = BFTripState.BANK_WITHDRAW_ORE;
            }
        }
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
        switch (obj.getId())
        {
            case BFConstants.CONVEYOR_BELT:
                conveyorBelt = spawned ? obj : null;
                break;
            case BFConstants.BAR_DISPENSER:
                barDispenser = spawned ? obj : null;
                break;
            case BFConstants.BANK_CHEST:
                bankChest = spawned ? obj : null;
                break;
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

    private void selfCorrectState()
    {
        ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
        if (inv == null) return;

        Item[] items = inv.getItems();
        BarType bt = getEffectiveBarType();

        if (bt != null && countItem(items, bt.getBarItemId()) > 0)
        {
            if (tripState != BFTripState.BANK_DEPOSIT_BARS && tripState != BFTripState.COLLECT_BARS)
            {
                tripState = BFTripState.BANK_DEPOSIT_BARS;
            }
        }

        if (bt != null && countItem(items, bt.getOreItemId()) > 0 && !bankOpen)
        {
            if (tripState == BFTripState.IDLE)
            {
                tripState = BFTripState.BELT_DEPOSIT_ORE;
            }
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
}
