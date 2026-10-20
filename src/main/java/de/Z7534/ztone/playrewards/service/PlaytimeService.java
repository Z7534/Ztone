package de.Z7534.ztone.playrewards.service;

import de.Z7534.ztone.playrewards.PlayReward;
import de.Z7534.ztone.playrewards.model.PlayerData;
import de.Z7534.ztone.playrewards.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PlaytimeService {

    private final PlayReward plugin;
    private final Map<UUID, Long> cachedPlaytimeSeconds = new ConcurrentHashMap<>();

    public PlaytimeService(PlayReward plugin) {
        this.plugin = plugin;
    }

    /**
     * Ermittelt die aktive Netto-Spielzeit für einen Spieler.
     * Priorität: 1. Plan API (falls aktiv), 2. SQLite Fallback + laufende Session.
     */
    public CompletableFuture<Long> getActivePlaytimeSeconds(UUID uuid) {
        // Asynchron Plan oder PAPI Plan abfragen
        return plugin.getPlanHook().queryActivePlaytimeSeconds(uuid).thenApply(optSeconds -> {
            if (optSeconds.isPresent() && optSeconds.get() > 0) {
                long sec = optSeconds.get();
                setPlaytimeSeconds(uuid, sec);
                return sec;
            } else {
                return getFallbackPlaytimeSeconds(uuid);
            }
        });
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
     * Aktualisiert die AFK-Status aller Online-Spieler.
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
            // Cache mit Plan aktualisieren
            getActivePlaytimeSeconds(player.getUniqueId());
        }
    }
}
