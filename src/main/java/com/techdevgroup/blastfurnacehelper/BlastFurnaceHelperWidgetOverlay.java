package com.techdevgroup.blastfurnacehelper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.HashSet;
import java.util.Set;
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
 * the belt. Also highlights coins in the bank when the coffer is low/critical, and the best
 * run-energy restorative the player carries when run energy is low (to withdraw if the bank is
 * open, otherwise to drink). Overlay-only.
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
        // Run energy: highlight the single best restorative present in the bank to withdraw.
        int energyItem = lowEnergy() ? preferredRunEnergyItem(children) : -1;

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
            else if (energyItem >= 0 && id == energyItem)
            {
                paint(graphics, child, config.bankItemColor());
            }
        }
    }

    /** Highlights the coal bag to empty (policy), and the best run-energy item to drink. */
    private void renderInventoryHighlights(Graphics2D graphics, BFGuidance g)
    {
        Widget invContainer = client.getWidget(BFConstants.INVENTORY_GROUP_ID,
            BFConstants.INVENTORY_CONTAINER_CHILD);
        if (invContainer == null || invContainer.isHidden()) return;

        Widget[] children = invContainer.getDynamicChildren();
        if (children == null) return;

        int invItem = g.getInvItemId();
        // Run energy: highlight the single best restorative present in the inventory to drink.
        int energyItem = lowEnergy() ? preferredRunEnergyItem(children) : -1;

        for (Widget child : children)
        {
            if (child == null || child.isHidden()) continue;
            int id = child.getItemId();

            // Coal bag appears as 12019 (empty) or 12020 (full) depending on contents.
            if (invItem >= 0 && (id == invItem || id == BFConstants.ITEM_COAL_BAG_FULL))
            {
                paint(graphics, child, config.objectColor());
            }
            else if (energyItem >= 0 && id == energyItem)
            {
                paint(graphics, child, config.bankItemColor());
            }
        }
    }

    private boolean lowEnergy()
    {
        return config.highlightRunEnergy() && client.getEnergy() < config.staminaThreshold() * 100;
    }

    /**
     * Returns the highest-preference run-energy item present among the given widget children,
     * following the family/dose order in {@link BFConstants#RUN_ENERGY_ITEMS}, or -1 if none.
     */
    private static int preferredRunEnergyItem(Widget[] children)
    {
        Set<Integer> present = new HashSet<>();
        for (Widget child : children)
        {
            if (child != null && !child.isHidden())
            {
                present.add(child.getItemId());
            }
        }
        for (int id : BFConstants.RUN_ENERGY_ITEMS)
        {
            if (present.contains(id))
            {
                return id;
            }
        }
        return -1;
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
