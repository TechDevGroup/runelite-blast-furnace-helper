package com.techdevgroup.blastfurnacehelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

/**
 * Append-only, size-rotated logger for the action trajectory. Writes structured lines to
 * {@code ~/.runelite/blast-furnace-helper/actions.log}, rotating to {@code actions.log.1..N}
 * once the active file passes {@link #MAX_BYTES}. All I/O failures are swallowed (debug-logged)
 * so logging never disrupts gameplay. This records only the player's own client-side actions and
 * derived state for policy tuning — no automation, no network.
 */
@Slf4j
class BFActionLogger
{
    static final Path DIR = RuneLite.RUNELITE_DIR.toPath().resolve("blast-furnace-helper");
    static final Path LOG = DIR.resolve("actions.log");
    private static final long MAX_BYTES = 1_000_000L;
    private static final int ROTATIONS = 3;

    /** Appends one line (a trailing newline is added). Best-effort; never throws. */
    synchronized void append(String line)
    {
        try
        {
            Files.createDirectories(DIR);
            rotateIfNeeded();
            Files.write(LOG,
                (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        catch (IOException e)
        {
            log.debug("Blast Furnace action log write failed", e);
        }
    }

    private void rotateIfNeeded() throws IOException
    {
        if (!Files.exists(LOG) || Files.size(LOG) < MAX_BYTES)
        {
            return;
        }
        for (int i = ROTATIONS - 1; i >= 1; i--)
        {
            Path src = DIR.resolve("actions.log." + i);
            if (Files.exists(src))
            {
                Files.move(src, DIR.resolve("actions.log." + (i + 1)),
                    StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Files.move(LOG, DIR.resolve("actions.log.1"), StandardCopyOption.REPLACE_EXISTING);
    }
}
