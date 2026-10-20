package de.Z7534.ztone.pvpmanager.utils;

import de.Z7534.ztone.pvpmanager.Pvpmanager;

public final class TimeFormatter {

    private TimeFormatter() {
    }

    public static String format(Pvpmanager plugin, long totalSeconds) {
        if (totalSeconds <= 0) {
            return plugin.getLocaleManager().raw("Time_Now");
        }

        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder builder = new StringBuilder();
        if (days > 0) {
            builder.append(days).append(plugin.getLocaleManager().raw("Time_Days"));
        }
        if (hours > 0) {
            builder.append(hours).append(plugin.getLocaleManager().raw("Time_Hours"));
        }
        if (minutes > 0) {
            builder.append(minutes).append(plugin.getLocaleManager().raw("Time_Minutes"));
        }
        if (seconds > 0 || builder.length() == 0) {
            builder.append(seconds).append(plugin.getLocaleManager().raw("Time_Seconds"));
        }
        return builder.toString().trim();
    }
}
