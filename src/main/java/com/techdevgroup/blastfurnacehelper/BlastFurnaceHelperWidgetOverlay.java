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
        if (!plugin.isInBlastFurnace() || !plugin.isBankOpen()) return null;

        Widget bankContainer = client.getWidget(BFConstants.BANK_GROUP_ID,
            BFConstants.BANK_ITEM_CONTAINER_CHILD);
        if (bankContainer == null || bankContainer.isHidden()) return null;

        Set<Integer> targets = getBankTargetItemIds();
        if (targets.isEmpty()) return null;

        Widget[] children = bankContainer.getDynamicChildren();
        if (children == null) return null;

        Color c = config.bankItemColor();
        Color fill = new Color(c.getRed(), c.getGreen(), c.getBlue(), 65);

        for (Widget child : children)
        {
            if (child == null || child.isHidden()) continue;
            if (targets.contains(child.getItemId()))
            {
                graphics.setColor(fill);
                graphics.fill(child.getBounds());
                graphics.setColor(c);
                graphics.draw(child.getBounds());
            }
        }

        return null;
    }

    private Set<Integer> getBankTargetItemIds()
    {
        Set<Integer> targets = new HashSet<>();
        BFTripState state = plugin.getTripState();
        BarType bt = plugin.getEffectiveBarType();

        switch (state)
        {
            case BANK_WITHDRAW_COAL_1:
                targets.add(BFConstants.ITEM_COAL);
                targets.add(BFConstants.ITEM_COAL_BAG);
                targets.add(BFConstants.ITEM_COAL_BAG_FULL);
                addStaminaIfNeeded(targets);
                break;

            case BANK_WITHDRAW_ORE:
                if (bt != null)
                {
                    targets.add(bt.getOreItemId());
                    if (bt.getCoalPerBar() > 1)
                    {
                        targets.add(BFConstants.ITEM_COAL_BAG);
                        targets.add(BFConstants.ITEM_COAL_BAG_FULL);
                    }
                }
                addStaminaIfNeeded(targets);
                break;

            default:
                break;
        }
        return targets;
    }

    private void addStaminaIfNeeded(Set<Integer> targets)
    {
        int energy = client.getEnergy();
        if (energy < config.staminaThreshold() * 100)
        {
            targets.add(BFConstants.ITEM_STAMINA_4);
            targets.add(BFConstants.ITEM_STAMINA_3);
            targets.add(BFConstants.ITEM_STAMINA_2);
            targets.add(BFConstants.ITEM_STAMINA_1);
        }
    }
}
