package de.Z7534.ztone.claims.util;

import org.bukkit.entity.Player;

/**
 * Delegiert an die zentrale {@link de.Z7534.ztone.util.VanishUtil}.
 */
public final class VanishUtil {

    private VanishUtil() {}

    public static boolean isVanished(Player viewer, Player player) {
        return de.Z7534.ztone.util.VanishUtil.isVanished(viewer, player);
    }

    public static boolean isVanished(Player player) {
        return de.Z7534.ztone.util.VanishUtil.isVanished(player);
    }
}
