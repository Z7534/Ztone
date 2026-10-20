package de.Z7534.ztone.stonelog.util;

import org.bukkit.World;

/**
 * Liefert statt des technischen Weltnamens ("world_nether", "lobby_end", ...)
 * nur die Dimension: Overworld, Nether oder End.
 */
public final class DimensionUtil {

    private DimensionUtil() {
    }

    public static String of(World world) {
        if (world == null) return "Overworld";
        return switch (world.getEnvironment()) {
            case NETHER -> "Nether";
            case THE_END -> "End";
            default -> "Overworld";
        };
    }
}
