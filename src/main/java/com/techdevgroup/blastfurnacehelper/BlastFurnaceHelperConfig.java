package com.techdevgroup.blastfurnacehelper;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup("blastfurnacehelper")
public interface BlastFurnaceHelperConfig extends Config
{
    @ConfigItem(keyName = "barType", name = "Bar Type",
        description = "Bar type to produce; AUTO detects from ore in inventory.",
        position = 1)
    default BarTypeConfig barType() { return BarTypeConfig.AUTO; }

    @ConfigItem(keyName = "staminaThreshold", name = "Stamina Threshold (%)",
        description = "Highlight stamina potions when run energy is below this value.",
        position = 2)
    @Range(min = 0, max = 100)
    default int staminaThreshold() { return 50; }

    @Alpha
    @ConfigItem(keyName = "bankItemColor", name = "Bank Item Highlight Color",
        description = "Color for highlighting items to withdraw in the bank.",
        position = 3)
    default Color bankItemColor() { return new Color(0, 255, 0, 160); }

    @Alpha
    @ConfigItem(keyName = "objectColor", name = "Object Highlight Color",
        description = "Color for highlighting conveyor belt, bar dispenser, and bank chest.",
        position = 4)
    default Color objectColor() { return new Color(255, 165, 0, 200); }

    @ConfigItem(keyName = "showPanel", name = "Show Trip Computer",
        description = "Show the trip computer overlay with session stats.",
        position = 5)
    default boolean showPanel() { return true; }

    @ConfigItem(keyName = "cofferEnabled", name = "Coffer Tracking",
        description = "Enable coffer balance tracking, highlights, and trip-computer cost line.",
        position = 6)
    default boolean cofferEnabled() { return true; }

    @ConfigItem(keyName = "cofferLowMinutes", name = "Coffer Low Threshold (min)",
        description = "Highlight coffer as LOW when estimated time remaining is below this many minutes. "
            + "Default 20 min ≈ 24,000 gp at 1,200 gp/min (OSRS Wiki).",
        position = 7)
    @Range(min = 1, max = 120)
    default int cofferLowMinutes() { return 20; }

    @ConfigItem(keyName = "cofferCriticalGp", name = "Coffer Critical Threshold (gp)",
        description = "Highlight coffer as CRITICAL when balance is at or below this amount (0 = empty only).",
        position = 8)
    @Range(min = 0, max = 50000)
    default int cofferCriticalGp() { return 0; }

    @Alpha
    @ConfigItem(keyName = "cofferLowColor", name = "Coffer Low Color",
        description = "Highlight color for the coffer object and panel text when coffer is low.",
        position = 9)
    default Color cofferLowColor() { return new Color(255, 200, 0, 220); }

    @Alpha
    @ConfigItem(keyName = "cofferCriticalColor", name = "Coffer Critical Color",
        description = "Highlight color for the coffer object and panel alert when coffer is critical or empty.",
        position = 10)
    default Color cofferCriticalColor() { return new Color(255, 0, 0, 220); }

    @ConfigItem(keyName = "resetHotkey", name = "Reset Stats Hotkey",
        description = "Press to zero all trip-computer counters and restart the timer. "
            + "(Stats also auto-reset when the bar type changes; or right-click the panel → Reset stats.)",
        position = 11)
    default Keybind resetHotkey() { return Keybind.NOT_SET; }
}
