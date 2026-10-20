package de.Z7534.ztone.stoneattack.util;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * Hilfsfunktionen fuer die automatischen Zeitraum-Resets der
 * Leaderboards. Arbeitet konsequent mit ZoneId, damit Sommer-/
 * Winterzeit automatisch korrekt beruecksichtigt wird.
 */
public final class TimeUtil {

    private TimeUtil() {
    }

    /** Beginn der aktuellen Woche (Montag 00:00) in der gegebenen Zeitzone. */
    public static Instant currentWeekStart(ZoneId zone) {
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime monday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toLocalDate().atStartOfDay(zone);
        return monday.toInstant();
    }

    /** Beginn des aktuellen Monats (1. um 00:00) in der gegebenen Zeitzone. */
    public static Instant currentMonthStart(ZoneId zone) {
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime firstOfMonth = now.with(TemporalAdjusters.firstDayOfMonth())
                .toLocalDate().atStartOfDay(zone);
        return firstOfMonth.toInstant();
    }

    public static String formatPlaytime(long totalTicks, String pattern) {
        // Vanilla-Statistik PLAY_ONE_MINUTE (bzw. PLAY_TIME) liefert Ticks (20 pro Sekunde).
        long totalMinutes = totalTicks / 20 / 60;
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return pattern.replace("%hours%", String.valueOf(hours))
                .replace("%minutes%", String.valueOf(minutes));
    }

    public static String todayLogFileName(String prefix) {
        ZonedDateTime now = ZonedDateTime.now();
        return String.format("%s-%04d-%02d-%02d.log", prefix, now.getYear(), now.getMonthValue(), now.getDayOfMonth());
    }
}
