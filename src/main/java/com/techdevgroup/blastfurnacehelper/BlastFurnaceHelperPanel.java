package com.techdevgroup.blastfurnacehelper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Duration;
import java.time.Instant;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class BlastFurnaceHelperPanel extends OverlayPanel
{
    private final BlastFurnaceHelperPlugin plugin;
    private final BlastFurnaceHelperConfig config;

    @Inject
    BlastFurnaceHelperPanel(BlastFurnaceHelperPlugin plugin,
                             BlastFurnaceHelperConfig config)
    {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showPanel() || !plugin.isInBlastFurnace()) return null;

        panelComponent.getChildren().clear();
        panelComponent.getChildren().add(
            TitleComponent.builder().text("Blast Furnace Helper").build());

        Duration elapsed = Duration.between(plugin.getSessionStart(), Instant.now());
        long seconds = elapsed.getSeconds();
        long hrs = seconds / 3600;
        long mins = (seconds % 3600) / 60;
        long secs = seconds % 60;
        String elapsedStr = String.format("%d:%02d:%02d", hrs, mins, secs);

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Runtime").right(elapsedStr).build());

        double hours = seconds / 3600.0;

        BarType bt = plugin.getEffectiveBarType();
        String barLabel = bt != null ? bt.getDisplayName() : "Unknown";
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Bar Type").right(barLabel).build());

        panelComponent.getChildren().add(LineComponent.builder()
            .left("State").right(plugin.getTripState().name()).build());

        int coal = plugin.getCoalDeposited();
        int ore = plugin.getOreDeposited();
        int bars = plugin.getBarsCollected();

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Coal deposited").right(String.valueOf(coal)).build());
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Ore deposited").right(String.valueOf(ore)).build());
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Bars collected").right(String.valueOf(bars)).build());

        if (hours > 0.01)
        {
            int barsHr = (int) Math.round(bars / hours);
            int oreHr = (int) Math.round(ore / hours);
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Bars/hr").right(String.valueOf(barsHr)).build());
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Ore/hr").right(String.valueOf(oreHr)).build());
        }

        // Coffer section — balance, time remaining, standing cost line.
        // Drain rate: 72,000 gp/hr (OSRS Wiki "Blast Furnace", 2026-07-07).
        if (config.cofferEnabled() && plugin.getCofferBalance() >= 0)
        {
            int cofferBal = plugin.getCofferBalance();
            double minsLeft = plugin.getCofferMinutesLeft();

            panelComponent.getChildren().add(LineComponent.builder()
                .left("─ Coffer ─").right("").build());

            panelComponent.getChildren().add(LineComponent.builder()
                .left("Balance").right(String.format("%,d gp", cofferBal)).build());

            if (plugin.isCofferCritical())
            {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status").right("EMPTY — refill!")
                    .rightColor(config.cofferCriticalColor()).build());
            }
            else if (plugin.isCofferLow())
            {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Time left").right(String.format("~%.0f min", minsLeft))
                    .rightColor(config.cofferLowColor()).build());
            }
            else
            {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Time left").right(String.format("~%.0f min", minsLeft)).build());
            }

            panelComponent.getChildren().add(LineComponent.builder()
                .left("Coffer cost/hr").right("72,000 gp").build());
        }

        return super.render(graphics);
    }
}
