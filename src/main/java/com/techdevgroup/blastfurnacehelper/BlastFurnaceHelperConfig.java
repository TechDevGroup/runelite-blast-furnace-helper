package com.techdevgroup.blastfurnacehelper;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
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
}
