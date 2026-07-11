package com.techdevgroup.blastfurnacehelper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/**
 * Highlights, in the bank, the single item the policy says to withdraw next (coal before ore,
 * one target at a time), and in the inventory the coal bag when the policy says to empty it at
 * the belt. Also highlights coins in the bank when the coffer is low/critical. Overlay-only.
 */
public class BlastFurnaceHelperWidgetOverlay extends Overlay
{
    private final Client client;
    private final BlastFurnaceHelperPlugin plugin;
    private final BlastFurnaceHelperConfig config;

    @Inject
    BlastFurnaceHelperWidgetOverlay(BlastFurnaceHelperPlugin plugin,
                                     BlastFurnaceHelperConfig config,
                                     Client client)
    {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        this.client = client;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        setPriority(OverlayPriority.HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!plugin.isInBlastFurnace()) return null;

        BFGuidance g = plugin.getGuidance();

        if (plugin.isBankOpen())
        {
            renderBankHighlights(graphics, g);
        }
        else
        {
            renderInventoryHighlights(graphics, g);
        }

        return null;
    }

    /** Highlights the single next bank withdrawal, plus coins when the coffer is low/critical. */
    private void renderBankHighlights(Graphics2D graphics, BFGuidance g)
    {
        Widget bankContainer = client.getWidget(BFConstants.BANK_GROUP_ID,
            BFConstants.BANK_ITEM_CONTAINER_CHILD);
        if (bankContainer == null || bankContainer.isHidden()) return;

        Widget[] children = bankContainer.getDynamicChildren();
        if (children == null) return;

        int primary = g.getBankItemId();
        // Coffer refill: coins highlight is a distinct, urgent concern from the smithing loop.
        boolean coffer = config.cofferEnabled() && (plugin.isCofferLow() || plugin.isCofferCritical());
        // Stamina: opportunistic consumable highlight when run energy is low (v0.1.0 feature).
        boolean lowEnergy = client.getEnergy() < config.staminaThreshold() * 100;

        for (Widget child : children)
        {
            if (child == null || child.isHidden()) continue;
            int id = child.getItemId();

            if (primary >= 0 && id == primary)
            {
                Color c = (id == BFConstants.ITEM_COINS) ? cofferColor() : config.bankItemColor();
                paint(graphics, child, c);
            }
            else if (coffer && id == BFConstants.ITEM_COINS)
            {
                paint(graphics, child, cofferColor());
            }
            else if (lowEnergy && isStamina(id))
            {
                paint(graphics, child, config.bankItemColor());
            }
        }
    }

    private static boolean isStamina(int itemId)
    {
        return itemId == BFConstants.ITEM_STAMINA_4 || itemId == BFConstants.ITEM_STAMINA_3
            || itemId == BFConstants.ITEM_STAMINA_2 || itemId == BFConstants.ITEM_STAMINA_1;
    }

    /** Highlights the coal bag in the inventory when the policy says to empty it at the belt. */
    private void renderInventoryHighlights(Graphics2D graphics, BFGuidance g)
    {
        int invItem = g.getInvItemId();
        if (invItem < 0) return;

        Widget invContainer = client.getWidget(BFConstants.INVENTORY_GROUP_ID,
            BFConstants.INVENTORY_CONTAINER_CHILD);
        if (invContainer == null || invContainer.isHidden()) return;

        Widget[] children = invContainer.getDynamicChildren();
        if (children == null) return;

        for (Widget child : children)
        {
            if (child == null || child.isHidden()) continue;
            int id = child.getItemId();
            // Coal bag appears as 12019 (empty) or 12020 (full) depending on contents.
            if (id == invItem || id == BFConstants.ITEM_COAL_BAG_FULL)
            {
                paint(graphics, child, config.objectColor());
            }
        }
    }

    private void paint(Graphics2D graphics, Widget child, Color c)
    {
        Color fill = new Color(c.getRed(), c.getGreen(), c.getBlue(), 65);
        graphics.setColor(fill);
        graphics.fill(child.getBounds());
        graphics.setColor(c);
        graphics.draw(child.getBounds());
    }

    private Color cofferColor()
    {
        return plugin.isCofferCritical() ? config.cofferCriticalColor() : config.cofferLowColor();
    }
}
