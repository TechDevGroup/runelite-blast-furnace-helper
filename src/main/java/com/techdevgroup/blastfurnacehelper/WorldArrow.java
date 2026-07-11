package com.techdevgroup.blastfurnacehelper;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;

/**
 * Draws a downward-pointing "here" arrow above a world target, in the style of quest-helper's
 * {@code DirectionArrow.drawWorldArrow} (github.com/Zoinkwiz/quest-helper, BSD-2-Clause). This is
 * an independent re-implementation of the same visual technique — a short vertical stalk plus a
 * downward arrowhead, drawn with a black outline then a colored fill — cited as the approach
 * reference. Overlay-only; purely cosmetic.
 */
final class WorldArrow
{
    private WorldArrow() {}

    private static final int HEAD_WIDTH = 14;
    private static final int HEAD_HEIGHT = 16;
    private static final int STALK_HEIGHT = 22;
    private static final int STALK_WIDTH = 4;

    /**
     * Draws the arrow with its tip at canvas point (tipX, tipY), pointing straight down at the
     * target. The stalk rises above the arrowhead. A black outline is stroked first, then the
     * colored shapes are filled on top.
     */
    static void draw(Graphics2D graphics, Color color, int tipX, int tipY)
    {
        // Downward arrowhead: tip at (tipX, tipY), base HEAD_HEIGHT above.
        int baseY = tipY - HEAD_HEIGHT;
        Polygon head = new Polygon(
            new int[] { tipX, tipX - HEAD_WIDTH / 2, tipX + HEAD_WIDTH / 2 },
            new int[] { tipY, baseY, baseY },
            3);

        // Vertical stalk sitting on top of the arrowhead base.
        int stalkTop = baseY - STALK_HEIGHT;
        Polygon stalk = new Polygon(
            new int[] { tipX - STALK_WIDTH / 2, tipX + STALK_WIDTH / 2,
                        tipX + STALK_WIDTH / 2, tipX - STALK_WIDTH / 2 },
            new int[] { baseY, baseY, stalkTop, stalkTop },
            4);

        Stroke oldStroke = graphics.getStroke();

        // Black outline first.
        graphics.setColor(Color.BLACK);
        graphics.setStroke(new BasicStroke(3f));
        graphics.draw(head);
        graphics.draw(stalk);

        // Colored fill on top.
        graphics.setColor(color);
        graphics.fill(head);
        graphics.fill(stalk);

        graphics.setStroke(oldStroke);
    }
}
