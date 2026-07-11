package com.techdevgroup.blastfurnacehelper;

import java.awt.Rectangle;
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
 * Remembers where each relevant bank item was last SEEN on screen, keyed by item id, so the
 * predictive "pre-aim" marker can show where a withdrawal will appear before the bank is opened.
 *
 * <p>Geometry is read live from the Widget API ({@code Widget.getBounds()} of the bank item
 * container's children — the interface's own laid-out canvas rectangle), never computed from
 * hardcoded grid math. Each seen layout is persisted to
 * {@code ~/.runelite/blast-furnace-helper/bank-layout.json} and reloaded on the next session, so
 * predictions work from the start of a run using previously-seen data and refresh the moment the
 * bank is opened again. The item id is the key, so a remembered position survives bank reordering
 * as long as that slot was seen at least once.
 */
@Slf4j
class BankLayoutCache
{
    /** item id -> {slot, x, y, w, h}. */
    private final Map<Integer, int[]> layout = new ConcurrentHashMap<>();

    private static final Pattern ENTRY = Pattern.compile(
        "\"(\\d+)\":\\{\"slot\":(-?\\d+),\"x\":(-?\\d+),\"y\":(-?\\d+),\"w\":(-?\\d+),\"h\":(-?\\d+)\\}");

    /** Records/updates the last-seen slot + canvas bounds for an item id. */
    void record(int itemId, int slot, Rectangle bounds)
    {
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
        {
            return;
        }
        layout.put(itemId, new int[] { slot, bounds.x, bounds.y, bounds.width, bounds.height });
    }

    /** Last-seen canvas bounds for an item id, or null if never seen. */
    Rectangle bounds(int itemId)
    {
        int[] v = layout.get(itemId);
        return v == null ? null : new Rectangle(v[1], v[2], v[3], v[4]);
    }

    void load(Path file)
    {
        try
        {
            if (!Files.exists(file))
            {
                return;
            }
            String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            Matcher m = ENTRY.matcher(json);
            while (m.find())
            {
                layout.put(Integer.parseInt(m.group(1)), new int[] {
                    Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)),
                    Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)),
                    Integer.parseInt(m.group(6)),
                });
            }
        }
        catch (Exception e)
        {
            log.debug("Failed to load bank layout cache", e);
        }
    }

    void save(Path file)
    {
        try
        {
            Files.createDirectories(file.getParent());
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<Integer, int[]> e : layout.entrySet())
            {
                int[] v = e.getValue();
                if (!first)
                {
                    sb.append(',');
                }
                first = false;
                sb.append('"').append(e.getKey()).append("\":{\"slot\":").append(v[0])
                    .append(",\"x\":").append(v[1]).append(",\"y\":").append(v[2])
                    .append(",\"w\":").append(v[3]).append(",\"h\":").append(v[4]).append('}');
            }
            sb.append('}');
            Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException e)
        {
            log.debug("Failed to save bank layout cache", e);
        }
    }
}
