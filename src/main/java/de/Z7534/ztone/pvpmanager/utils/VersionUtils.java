package de.Z7534.ztone.pvpmanager.utils;

import org.bukkit.Bukkit;

public final class VersionUtils {

    private static final int MAJOR;
    private static final int MINOR;

    static {
        String version = Bukkit.getBukkitVersion();
        String[] parts = version.split("-")[0].split("\\.");
        int major = 1;
        int minor = 21;
        try {
            major = Integer.parseInt(parts[0]);
            minor = Integer.parseInt(parts[1]);
        } catch (Exception ignored) {

        }
        MAJOR = major;
        MINOR = minor;
    }

    private VersionUtils() {
    }

    public static boolean isAtLeast(int major, int minor) {
        return MAJOR > major || (MAJOR == major && MINOR >= minor);
    }

    public static boolean supportsGlowing() {
        return isAtLeast(1, 9);
    }

    public static boolean supportsHexColors() {
        return isAtLeast(1, 16);
    }

    public static boolean supportsWindCharge() {
        return isAtLeast(1, 21);
    }
}
