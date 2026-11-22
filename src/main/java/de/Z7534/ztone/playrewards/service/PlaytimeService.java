package de.Z7534.ztone.playrewards.service;

import de.Z7534.ztone.playrewards.PlayReward;
import de.Z7534.ztone.playrewards.model.PlayerData;
import de.Z7534.ztone.playrewards.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaytimeService {

    private final PlayReward plugin;
    private final Map<UUID, Long> cachedPlaytimeSeconds = new ConcurrentHashMap<>();

    public PlaytimeService(PlayReward plugin) {
        this.plugin = plugin;
    }

    /**
     * Ermittelt die aktive Netto-Spielzeit für einen Spieler.
     * Priorität: 1. Konfigurierter Placeholder via PlaceholderAPI, 2. SQLite Fallback + laufende Session.
     */
    public CompletableFuture<Long> getActivePlaytimeSeconds(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String placeholder = plugin.getConfig().getString("playtime_tracking.total_playtime.placeholder",
                    plugin.getConfig().getString("playtime_tracking.placeholder", "%stonesmpcore_playtime_seconds%"));
            String format = plugin.getConfig().getString("playtime_tracking.total_playtime.format",
                    plugin.getConfig().getString("playtime_tracking.total_playtime.unit", "SECONDS"));

            OfflinePlayer op = Bukkit.getPlayer(uuid);
            if (op == null) {
                op = Bukkit.getOfflinePlayer(uuid);
            }

            Optional<Long> optSeconds = resolvePlaceholderToSeconds(op, placeholder, format);
            if (optSeconds.isPresent() && optSeconds.get() > 0) {
                long sec = optSeconds.get();
                setPlaytimeSeconds(uuid, sec);
                return sec;
            } else {
                return getFallbackPlaytimeSeconds(uuid);
            }
        });
    }

    /**
     * Löst einen konfigurierten PlaceholderAPI-Placeholder für einen Spieler auf und rechnet
     * den Wert anhand des konfigurierten Formats (SECONDS, MINUTES, HOURS, etc.) in Sekunden um.
     */
    public Optional<Long> resolvePlaceholderToSeconds(OfflinePlayer player, String placeholder, String formatUnit) {
        if (placeholder == null || placeholder.isBlank()) {
            return Optional.empty();
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return Optional.empty();
        }
        try {
            String raw = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, placeholder);
            if (raw == null || raw.isBlank() || raw.equals(placeholder) || raw.contains("%")) {
                return Optional.empty();
            }
            long seconds = parseTimeToSeconds(raw.trim(), formatUnit);
            return Optional.of(seconds);
        } catch (Throwable t) {
            plugin.getLogger().fine("Fehler beim Auflösen des Placeholders '" + placeholder + "': " + t.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Parst einen beliebigen Rückgabewert (Zahl oder formatierter String) in Sekunden.
     * Unterstützte Einheiten: SECONDS, MINUTES, HOURS, DAYS, TICKS, MILLISECONDS, FORMATTED, AUTO.
     */
    public static long parseTimeToSeconds(String text, String formatUnit) {
        if (text == null || text.isBlank()) {
            return 0L;
        }
        String clean = text.trim();

        // Prüfen, ob der String Zeiteinheiten wie 'd', 'h', 'm', 's' oder Wörter enthält (z. B. "2d 4h 30m" oder "1 Stunde 30 Min")
        if (containsTimeUnits(clean) || "FORMATTED".equalsIgnoreCase(formatUnit) || "AUTO".equalsIgnoreCase(formatUnit)) {
            long formatted = parseFormattedDuration(clean);
            if (formatted > 0) {
                return formatted;
            }
        }

        // Reine Zahl parsen (ggf. Tausendertrennzeichen entfernen oder Komma zu Punkt konvertieren)
        double numValue;
        try {
            String numStr = clean.replace("'", "").replace(" ", "");
            if (numStr.contains(".") && numStr.contains(",")) {
                if (numStr.lastIndexOf(',') > numStr.lastIndexOf('.')) {
                    // Deutsch: 1.250,50 -> 1250.50
                    numStr = numStr.replace(".", "").replace(',', '.');
                } else {
                    // Englisch: 1,250.50 -> 1250.50
                    numStr = numStr.replace(",", "");
                }
            } else if (numStr.contains(",")) {
                numStr = numStr.replace(',', '.');
            }
            numValue = Double.parseDouble(numStr);
        } catch (NumberFormatException e) {
            return 0L;
        }

        if (numValue <= 0) {
            return 0L;
        }

        String unit = (formatUnit != null) ? formatUnit.trim().toUpperCase(Locale.ROOT) : "SECONDS";
        return switch (unit) {
            case "HOURS", "HOUR", "H", "STUNDEN", "STUNDE", "STD" -> Math.round(numValue * 3600.0);
            case "MINUTES", "MINUTE", "MIN", "M", "MINUTEN" -> Math.round(numValue * 60.0);
            case "DAYS", "DAY", "D", "TAGE", "TAG" -> Math.round(numValue * 86400.0);
            case "TICKS", "TICK", "T" -> Math.round(numValue / 20.0);
            case "MILLISECONDS", "MILLISECOND", "MS" -> Math.round(numValue / 1000.0);
            case "SECONDS", "SECOND", "SEC", "S", "SEKUNDEN" -> Math.round(numValue);
            default -> {
                if (numValue > 100_000_000.0) {
                    yield Math.round(numValue / 1000.0);
                }
                yield Math.round(numValue);
            }
        };
    }

    private static boolean containsTimeUnits(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.matches(".*\\d+\\s*(d|tage?|days?|h|stunden?|std|hours?|m|minuten?|min|minutes?|s|sekunden?|sek|sec|seconds?).*");
    }

    /**
     * Parst formatierte Strings wie "3d 4h 12m", "3 Tage, 4 Stunden", "5h 30m 10s" in reine Sekunden.
     */
    public static long parseFormattedDuration(String text) {
        if (text == null || text.isBlank()) return 0L;
        long totalSeconds = 0L;
        boolean matched = false;

        Matcher days = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:d|tage?|days?)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (days.find()) {
            double d = Double.parseDouble(days.group(1).replace(',', '.'));
            totalSeconds += Math.round(d * 86400.0);
            matched = true;
        }

        Matcher hours = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:h|stunden?|std|hours?)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (hours.find()) {
            double h = Double.parseDouble(hours.group(1).replace(',', '.'));
            totalSeconds += Math.round(h * 3600.0);
            matched = true;
        }

        Matcher minutes = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:m|minuten?|min|minutes?)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (minutes.find()) {
            double m = Double.parseDouble(minutes.group(1).replace(',', '.'));
            totalSeconds += Math.round(m * 60.0);
            matched = true;
        }

        Matcher seconds = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:s|sekunden?|sek|sec|seconds?)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (seconds.find()) {
            double s = Double.parseDouble(seconds.group(1).replace(',', '.'));
            totalSeconds += Math.round(s);
            matched = true;
        }

        return matched ? totalSeconds : 0L;
    }

    public void setPlaytimeSeconds(UUID uuid, long seconds) {
        cachedPlaytimeSeconds.put(uuid, seconds);
        PlayerData data = plugin.getDatabaseManager().getCachedData(uuid);
        if (data != null) {
            data.setLocalPlaytimeSeconds(seconds);
            plugin.getDatabaseManager().savePlayerDataAsync(data);
        }
    }

    public long getCachedPlaytimeSeconds(UUID uuid) {
        Long cached = cachedPlaytimeSeconds.get(uuid);
        if (cached != null && cached > 0) {
            return cached;
        }

        // Falls noch nicht im Cache, sofort im Hintergrund abfragen
        getActivePlaytimeSeconds(uuid);

        return getFallbackPlaytimeSeconds(uuid);
    }

    /**
     * Ermittelt die aktive Online-Spielzeit von HEUTE (ab Mitternacht 00:00 Uhr Berlin).
     * Priorität: 1. Konfigurierter Daily-Placeholder via PlaceholderAPI, 2. Lokale Berechnung ab Mitternacht.
     */
    public long getDailyPlaytimeSeconds(UUID uuid) {
        PlayerData data = plugin.getDatabaseManager().getCachedData(uuid);
        if (data == null) {
            return 0L;
        }

        LocalDate today = TimeUtil.getTodayInBerlin();
        if (data.getLastDailyDate() == null || !data.getLastDailyDate().equals(today)) {
            data.setLastDailyDate(today);
            data.getClaimedDailyTiers().clear();
            data.setDailyPlaytimeSeconds(0L);
        }

        // 1. Placeholder-Abfrage für heutige Spielzeit (falls konfiguriert)
        String dailyPlaceholder = plugin.getConfig().getString("playtime_tracking.daily_playtime.placeholder", "%stonesmpcore_playtime_today_seconds%");
        String dailyFormat = plugin.getConfig().getString("playtime_tracking.daily_playtime.format",
                plugin.getConfig().getString("playtime_tracking.daily_playtime.unit", "SECONDS"));

        OfflinePlayer op = Bukkit.getPlayer(uuid);
        if (op == null) {
            op = Bukkit.getOfflinePlayer(uuid);
        }

        Optional<Long> placeholderSeconds = resolvePlaceholderToSeconds(op, dailyPlaceholder, dailyFormat);
        if (placeholderSeconds.isPresent()) {
            long sec = Math.max(0L, placeholderSeconds.get());
            data.setDailyPlaytimeSeconds(sec);
            return sec;
        }

        // 2. Fallback: Lokale Berechnung basierend auf Sitzungen seit Mitternacht
        long total = data.getDailyPlaytimeSeconds();
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            long now = System.currentTimeMillis();
            long sessionStart = data.getSessionStartTime();
            long midnightMillis = today.atStartOfDay(ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli();
            long effectiveStart = Math.max(sessionStart, midnightMillis);
            long sessionDurationSeconds = Math.max(0, (now - effectiveStart) / 1000L);
            long activeSessionSeconds = Math.max(0, sessionDurationSeconds - data.getTotalAfkSecondsInSession());
            total += activeSessionSeconds;
        }
        return total;
    }

    /**
     * Fallback-Berechnung: SQLite-Basiswert + aktive Netto-Sekunden der aktuellen Online-Session.
     */
    public long getFallbackPlaytimeSeconds(UUID uuid) {
        PlayerData data = plugin.getDatabaseManager().getCachedData(uuid);
        if (data == null) {
            return 0L;
        }
        long total = data.getLocalPlaytimeSeconds();
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            long sessionDurationSeconds = (System.currentTimeMillis() - data.getSessionStartTime()) / 1000L;
            long activeSessionSeconds = Math.max(0, sessionDurationSeconds - data.getTotalAfkSecondsInSession());
            total += activeSessionSeconds;
        }
        cachedPlaytimeSeconds.put(uuid, total);
        return total;
    }

    /**
     * Aktualisiert den AFK-Status aller Online-Spieler und frischt die Spielzeit-Caches auf.
     */
    public void updateAfkAndSessions() {
        long now = System.currentTimeMillis();
        long afkThresholdSec = plugin.getConfig().getLong("playtime_tracking.afk_threshold_seconds", 300);

        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.getDatabaseManager().getCachedData(player.getUniqueId());
            if (data == null) continue;

            long idleSeconds = (now - data.getLastActiveTime()) / 1000L;
            if (idleSeconds >= afkThresholdSec) {
                if (!data.isAfk()) {
                    data.setAfk(true);
                }
                // AFK-Zeit hinzufügen (Intervall sind ca. 10 Sekunden)
                data.addAfkSeconds(10);
            } else {
                data.setAfk(false);
            }
            // Gesamt- und Tages-Spielzeit aktualisieren
            getActivePlaytimeSeconds(player.getUniqueId());
            getDailyPlaytimeSeconds(player.getUniqueId());
        }
    }
}
