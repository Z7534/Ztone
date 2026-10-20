package de.Z7534.ztone.claims.util;

import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

import java.lang.reflect.Method;

public final class VanishUtil {

    private VanishUtil() {}

    public static boolean isVanished(Player viewer, Player player) {
        if (player == null) return false;
        if (viewer != null && !viewer.canSee(player)) {
            return true;
        }
        return isVanished(player);
    }

    public static boolean isVanished(Player player) {
        if (player == null) return false;

        // 1. Metadata checks (Standard, Essentials, SuperVanish, PremiumVanish, StoneSMPCore)
        try {
            if (player.hasMetadata("vanished")) {
                for (MetadataValue val : player.getMetadata("vanished")) {
                    if (val.asBoolean()) return true;
                }
            }
            if (player.hasMetadata("vanish")) {
                for (MetadataValue val : player.getMetadata("vanish")) {
                    if (val.asBoolean()) return true;
                }
            }
        } catch (Throwable ignored) {}

        // 2. StoneSMPCore VanishManager via Reflection
        try {
            Class<?> vmClass = Class.forName("de.Z7534.stonesmp.core.vanish.VanishManager");
            Method m = vmClass.getMethod("isVanished", Player.class);
            Object res = m.invoke(null, player);
            if (res instanceof Boolean b && b) {
                return true;
            }
        } catch (Throwable ignored) {}

        // 3. Alternative StoneAttack VanishManager via Reflection
        try {
            Class<?> vmClass = Class.forName("de.Z7534.stoneattack.vanish.VanishManager");
            Method m = vmClass.getMethod("isVanished", Player.class);
            Object res = m.invoke(null, player);
            if (res instanceof Boolean b && b) {
                return true;
            }
        } catch (Throwable ignored) {}

        return false;
    }
}
