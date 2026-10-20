package de.Z7534.ztone.playrewards.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;

public final class TimeUtil {

    public static final ZoneId BERLIN_ZONE = ZoneId.of("Europe/Berlin");

    private TimeUtil() {}

    /**
     * Gibt das aktuelle Datum in der Berliner Zeitzone zurück.
     */
    public static LocalDate getTodayInBerlin() {
        return LocalDate.now(BERLIN_ZONE);
    }

    /**
     * Prüft, ob heute Montag ist (für den Streak-Saver Reset).
     */
    public static boolean isMondayToday() {
        return LocalDate.now(BERLIN_ZONE).getDayOfWeek() == DayOfWeek.MONDAY;
    }

    /**
     * Berechnet die vollen Spielstunden aus den Gesamtekunden als reine Zahl.
     */
    public static long getHours(long totalSeconds) {
        if (totalSeconds <= 0) return 0;
        return TimeUnit.SECONDS.toHours(totalSeconds);
    }

    /**
     * Formatiert Spielzeit detailliert in Tage, Stunden und Minuten in Small Caps (z. B. "3 ᴛᴀɢᴇ, 5 ꜱᴛᴜɴᴅᴇɴ, 12 ᴍɪɴᴜᴛᴇɴ").
     */
    public static String formatDaysAndHours(long totalSeconds) {
        if (totalSeconds <= 0) {
            return "0 ꜱᴛᴜɴᴅᴇɴ, 0 ᴍɪɴᴜᴛᴇɴ";
        }
        long days = TimeUnit.SECONDS.toDays(totalSeconds);
        long hours = TimeUnit.SECONDS.toHours(totalSeconds) % 24;
        long minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append(days == 1 ? " ᴛᴀɢ" : " ᴛᴀɢᴇ");
        }
        if (hours > 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(hours).append(hours == 1 ? " ꜱᴛᴜɴᴅᴇ" : " ꜱᴛᴜɴᴅᴇɴ");
        }
        if (minutes > 0 || sb.length() == 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(minutes).append(minutes == 1 ? " ᴍɪɴᴜᴛᴇ" : " ᴍɪɴᴜᴛᴇɴ");
        }
        return sb.toString();
    }

    /**
     * Formatiert Spielzeit in Sekunden zu lesbarem Text in Small Caps (z. B. "2ᴅ 4ʜ 35ᴍ").
     */
    public static String formatDuration(long totalSeconds) {
        if (totalSeconds <= 0) {
            return "0ᴍ";
        }
        long days = TimeUnit.SECONDS.toDays(totalSeconds);
        long hours = TimeUnit.SECONDS.toHours(totalSeconds) % 24;
        long minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("ᴅ ");
        }
        if (hours > 0 || days > 0) {
            sb.append(hours).append("ʜ ");
        }
        if (minutes > 0 || (days == 0 && hours == 0)) {
            sb.append(minutes).append("ᴍ ");
        }
        if (days == 0 && hours == 0 && minutes < 5) {
            sb.append(seconds).append("ꜱ");
        }
        return sb.toString().trim();
    }

    /**
     * Berechnet die verbleibende Zeit bis zum nächsten Reset um 0:00 Uhr in der Berliner Zeitzone (in Small Caps).
     */
    public static String getTimeUntilMidnight() {
        ZonedDateTime now = ZonedDateTime.now(BERLIN_ZONE);
        ZonedDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(BERLIN_ZONE);
        long diffSeconds = nextMidnight.toEpochSecond() - now.toEpochSecond();
        return formatDuration(diffSeconds);
    }
}
