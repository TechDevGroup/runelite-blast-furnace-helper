package com.techdevgroup.blastfurnacehelper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Highlights the single world object the state-derived policy points at (conveyor belt,
 * bar dispenser, or bank chest), draws a persistent bobbing arrow above it, and highlights the
 * coffer when it is low/critical or the player is holding coins to top it up. Overlay-only —
 * never automates input.
 */
public class BlastFurnaceHelperSceneOverlay extends Overlay
{
    // Height (local units) above the object tile where the arrow tip floats, and the bob params.
    private static final int ARROW_HEIGHT = 200;
    private static final double BOB_PERIOD = 15.0;
    private static final double BOB_AMPLITUDE = 8.0;

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

        // Policy object highlight + persistent bobbing arrow (skip while the bank is open — the
        // widget overlay leads there). The arrow persists over the object every frame while the
        // policy still points at it, and vanishes only when the action's state is satisfied and
        // the derived guidance moves on — so the player never assumes a step is done early.
        if (!plugin.isBankOpen())
        {
            GameObject target = resolveTargetObject(plugin.getGuidance().objectTarget());
            if (target != null)
            {
                highlight(graphics, target, config.objectColor(), 20);
                if (config.showWorldArrow())
                {
                    renderWorldArrow(graphics, target);
                }
            }
        }

        // Coffer highlight: low/critical, or holding coins while low/critical → walk to deposit.
        if (config.cofferEnabled())
        {
            renderCofferHighlight(graphics);
        }

        return null;
    }

    /**
     * Draws a bobbing arrow floating above the target object. The canvas anchor is obtained via
     * {@link Perspective#localToCanvas(Client, LocalPoint, int, int)} at a fixed height above the
     * object's tile; the vertical bob is a sine of the game cycle so it animates every frame.
     */
    private void renderWorldArrow(Graphics2D graphics, GameObject target)
    {
        LocalPoint lp = target.getLocalLocation();
        if (lp == null) return;

        // Canvas point ~200 game units above the object's tile.
        Point anchor = Perspective.localToCanvas(client, lp, client.getPlane(), ARROW_HEIGHT);
        if (anchor == null) return;

        int bob = (int) (Math.sin(client.getGameCycle() / BOB_PERIOD) * BOB_AMPLITUDE);
        WorldArrow.draw(graphics, config.worldArrowColor(), anchor.getX(), anchor.getY() + bob);
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
