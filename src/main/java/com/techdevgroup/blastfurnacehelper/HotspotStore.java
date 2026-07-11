package com.techdevgroup.blastfurnacehelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Self-learning standing-tile hotspots: for each interactable object id, a histogram of the world
 * tiles the player stood on when interacting with it. The most-common tile is highlighted as
 * "walk here before the interaction". Seeded from the user's actions.log observations and updated
 * as the plugin observes real interactions; persisted to
 * {@code ~/.runelite/blast-furnace-helper/hotspots.json}. Falls back to the object's own tile
 * (handled by the caller) when nothing is learned.
 */
@Slf4j
class HotspotStore
{
    /** canonical object id -> ("x,y" -> count). */
    private final Map<Integer, Map<String, Integer>> hist = new ConcurrentHashMap<>();

    private static final Pattern OBJ = Pattern.compile("\"(\\d+)\":\\{([^}]*)\\}");
    private static final Pattern TILE = Pattern.compile("\"(-?\\d+),(-?\\d+)\":(\\d+)");

    /** Records one interaction: the player stood at (x,y) when using objId. */
    void record(int objId, int x, int y)
    {
        hist.computeIfAbsent(objId, k -> new ConcurrentHashMap<>()).merge(x + "," + y, 1, Integer::sum);
    }

    /** The most-frequently-used standing tile for objId, as {x, y}, or null if none. */
    int[] best(int objId)
    {
        Map<String, Integer> m = hist.get(objId);
        if (m == null || m.isEmpty())
        {
            return null;
        }
        String bestKey = null;
        int bestCount = -1;
        for (Map.Entry<String, Integer> e : m.entrySet())
        {
            if (e.getValue() > bestCount)
            {
                bestCount = e.getValue();
                bestKey = e.getKey();
            }
        }
        String[] p = bestKey.split(",");
        return new int[] { Integer.parseInt(p[0]), Integer.parseInt(p[1]) };
    }

    /** Loads persisted history, or seeds the observed defaults if no file exists yet. */
    void load(Path file)
    {
        try
        {
            if (!Files.exists(file))
            {
                seedDefaults();
                return;
            }
            String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            Matcher om = OBJ.matcher(json);
            while (om.find())
            {
                int obj = Integer.parseInt(om.group(1));
                Map<String, Integer> m = hist.computeIfAbsent(obj, k -> new ConcurrentHashMap<>());
                Matcher tm = TILE.matcher(om.group(2));
                while (tm.find())
                {
                    m.put(tm.group(1) + "," + tm.group(2), Integer.parseInt(tm.group(3)));
                }
            }
            if (hist.isEmpty())
            {
                seedDefaults();
            }
        }
        catch (Exception e)
        {
            log.debug("Failed to load hotspots", e);
            if (hist.isEmpty())
            {
                seedDefaults();
            }
        }
    }

    void save(Path file)
    {
        try
        {
            Files.createDirectories(file.getParent());
            StringBuilder sb = new StringBuilder("{");
            boolean firstObj = true;
            for (Map.Entry<Integer, Map<String, Integer>> oe : hist.entrySet())
            {
                if (!firstObj)
                {
                    sb.append(',');
                }
                firstObj = false;
                sb.append('"').append(oe.getKey()).append("\":{");
                boolean firstTile = true;
                for (Map.Entry<String, Integer> te : oe.getValue().entrySet())
                {
                    if (!firstTile)
                    {
                        sb.append(',');
                    }
                    firstTile = false;
                    sb.append('"').append(te.getKey()).append("\":").append(te.getValue());
                }
                sb.append('}');
            }
            sb.append('}');
            Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException e)
        {
            log.debug("Failed to save hotspots", e);
        }
    }

    /**
     * Seed weights from the user's actions.log: bar dispenser stood on (1940,4962) ×30 and
     * approached via (1942,4967) ×60 (plus a couple of one-offs); belt/bank chest tiles as
     * observed. Real interactions accumulate on top and quickly dominate.
     */
    private void seedDefaults()
    {
        seed(BFConstants.DISPENSER_BASE, 1940, 4962, 30);
        seed(BFConstants.DISPENSER_BASE, 1942, 4967, 60);
        seed(BFConstants.DISPENSER_BASE, 1938, 4963, 1);
        seed(BFConstants.DISPENSER_BASE, 1940, 4961, 1);
        seed(BFConstants.CONVEYOR_BELT, 1942, 4967, 1);
        seed(BFConstants.CONVEYOR_BELT, 1948, 4957, 1);
        seed(BFConstants.BANK_CHEST, 1948, 4957, 1);
    }

    private void seed(int obj, int x, int y, int count)
    {
        hist.computeIfAbsent(obj, k -> new ConcurrentHashMap<>()).put(x + "," + y, count);
    }
}
