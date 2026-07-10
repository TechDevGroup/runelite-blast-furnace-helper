package com.techdevgroup.blastfurnacehelper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

public class BlastFurnaceHelperSceneOverlay extends Overlay
{
    private final Client client;
    private final BlastFurnaceHelperPlugin plugin;
    private final BlastFurnaceHelperConfig config;

    @Inject
    BlastFurnaceHelperSceneOverlay(BlastFurnaceHelperPlugin plugin,
                                    BlastFurnaceHelperConfig config,
                                    Client client)
    {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        this.client = client;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!plugin.isInBlastFurnace() || plugin.isBankOpen()) return null;

        BFTripState state = plugin.getTripState();
        GameObject target = resolveTargetObject(state);
        if (target == null) return null;

        Shape clickbox = target.getClickbox();
        if (clickbox == null) return null;

        Color c = config.objectColor();
        Color fill = new Color(c.getRed(), c.getGreen(), c.getBlue(), 20);
        Point mousePos = client.getMouseCanvasPosition();
        OverlayUtil.renderHoverableArea(graphics, clickbox, mousePos, fill, c.darker(), c);

        return null;
    }

    private GameObject resolveTargetObject(BFTripState state)
    {
        switch (state)
        {
            case BELT_DEPOSIT_COAL:
            case BELT_DEPOSIT_ORE:
                return plugin.getConveyorBelt();

            case AWAITING_BARS:
            case COLLECT_BARS:
                return plugin.getBarDispenser();

            case BANK_WITHDRAW_COAL_1:
            case BANK_WITHDRAW_ORE:
            case BANK_DEPOSIT_BARS:
            case IDLE:
                return plugin.getBankChest();

            default:
                return null;
        }
    }
}
