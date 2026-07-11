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

/**
 * Highlights the single world object the state-derived policy points at (conveyor belt,
 * bar dispenser, or bank chest), plus the coffer when it is low/critical or the player is
 * holding coins to top it up. Overlay-only — never automates input.
 */
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
        if (!plugin.isInBlastFurnace()) return null;

        // Policy object highlight (skip while the bank is open — the widget overlay leads there).
        if (!plugin.isBankOpen())
        {
            GameObject target = resolveTargetObject(plugin.getGuidance().objectTarget());
            if (target != null)
            {
                highlight(graphics, target, config.objectColor(), 20);
            }
        }

        // Coffer highlight: low/critical, or holding coins while low/critical → walk to deposit.
        if (config.cofferEnabled())
        {
            renderCofferHighlight(graphics);
        }

        return null;
    }

    private void renderCofferHighlight(Graphics2D graphics)
    {
        boolean isCritical = plugin.isCofferCritical();
        boolean isLow = plugin.isCofferLow();
        boolean holdingCoinsAndLow = plugin.isHoldingCoins() && (isLow || isCritical);

        if (!isCritical && !isLow && !holdingCoinsAndLow) return;

        GameObject coffer = plugin.getCofferObject();
        if (coffer == null) return;

        Color c = isCritical ? config.cofferCriticalColor() : config.cofferLowColor();
        highlight(graphics, coffer, c, 30);
    }

    private void highlight(Graphics2D graphics, GameObject obj, Color c, int fillAlpha)
    {
        Shape clickbox = obj.getClickbox();
        if (clickbox == null) return;
        Color fill = new Color(c.getRed(), c.getGreen(), c.getBlue(), fillAlpha);
        Point mousePos = client.getMouseCanvasPosition();
        OverlayUtil.renderHoverableArea(graphics, clickbox, mousePos, fill, c.darker(), c);
    }

    private GameObject resolveTargetObject(BFAction.ObjTarget target)
    {
        switch (target)
        {
            case CONVEYOR:   return plugin.getConveyorBelt();
            case DISPENSER:  return plugin.getBarDispenser();
            case BANK_CHEST: return plugin.getBankChest();
            case COFFER:     return plugin.getCofferObject();
            default:         return null;
        }
    }
}
