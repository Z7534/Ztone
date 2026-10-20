package de.Z7534.ztone.playrewards.hook;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlanHook {

    private final Logger logger;

    private static final String ACTIVE_PLAYTIME_SQL =
            "SELECT COALESCE(SUM(s.session_end - s.session_start - s.afk_time), 0) " +
            "FROM plan_sessions s " +
            "JOIN plan_users u ON s.user_id = u.id " +
            "WHERE u.uuid = ?;";

    public PlanHook(Logger logger) {
        this.logger = logger;
        checkAvailability();
    }

    public synchronized void checkAvailability() {
        if (isPlanAvailable()) {
            logger.info("PlayRewards: Plan Plugin erfolgreich erkannt!");
        } else {
            logger.info("PlayRewards: Plan Plugin aktuell noch nicht aktiviert (wird dynamisch geprüft).");
        }
    }

    public boolean isPlanAvailable() {
        Plugin planPlugin = Bukkit.getPluginManager().getPlugin("Plan");
        return planPlugin != null && planPlugin.isEnabled();
    }

    /**
     * Fragt die aktive Netto-Spielzeit (ohne AFK) asynchron ab:
     * 1. PlaceholderAPI zu Plan (nur aktive Zeit: %plan_player_time_active_raw%, %plan_player_server_time_active_raw%)
     * 2. Plan QueryService (APIv5) SQL: SUM(session_end - session_start - afk_time) + laufende Session getActiveTime()
     * 3. Plan Database Connection Fallback (aktive Netto-Zeit)
     */
    public CompletableFuture<Optional<Long>> queryActivePlaytimeSeconds(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            // 1. Priorität: PlaceholderAPI (Plan liefert hierüber direkt die exakte aktive Zeit inkl. aktiver Session)
            Optional<Long> papiResult = checkPapiPlanPlaytime(uuid);
            if (papiResult.isPresent() && papiResult.get() > 0) {
                return papiResult;
            }

            if (!isPlanAvailable()) {
                return Optional.empty();
            }

            long totalActiveMs = 0L;
            boolean found = false;

            // Laufende aktive Session aus Plan SessionCache hinzurechnen
            long currentSessionActiveMs = 0L;
            try {
                Class<?> sessionCacheClass = Class.forName("com.djrapitops.plan.gathering.cache.SessionCache");
                Method getCachedSession = sessionCacheClass.getMethod("getCachedSession", UUID.class);
                Object optActive = getCachedSession.invoke(null, uuid);
                if (optActive instanceof Optional<?> opt && opt.isPresent()) {
                    Object activeSession = opt.get();
                    Method toFinishedMethod = activeSession.getClass().getMethod("toFinishedSessionFromStillActive");
                    Object finishedSession = toFinishedMethod.invoke(activeSession);
                    Method getActiveTimeMethod = finishedSession.getClass().getMethod("getActiveTime");
                    Object activeTimeObj = getActiveTimeMethod.invoke(finishedSession);
                    if (activeTimeObj instanceof Number num && num.longValue() > 0) {
                        currentSessionActiveMs = num.longValue();
                    }
                }
            } catch (Throwable ignored) {}

            // 2. Priorität: Plan QueryService API (APIv5) mit SQL für aktive Spielzeit (Netto)
            try {
                Class<?> qsClass = Class.forName("com.djrapitops.plan.query.QueryService");
                Method getInstanceMethod = qsClass.getMethod("getInstance");
                Object queryService = getInstanceMethod.invoke(null);
                if (queryService instanceof Optional<?> opt) {
                    queryService = opt.orElse(null);
                }

                if (queryService != null) {
                    Class<?> tfClass = Class.forName("com.djrapitops.plan.query.QueryService$ThrowingFunction");
                    InvocationHandler handler = (proxy, method, args) -> {
                        if ("apply".equals(method.getName()) && args != null && args.length == 1 && args[0] instanceof PreparedStatement ps) {
                            ps.setString(1, uuid.toString());
                            try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) {
                                    return rs.getLong(1);
                                }
                            }
                            return 0L;
                        }
                        return null;
                    };

                    Object proxy = Proxy.newProxyInstance(tfClass.getClassLoader(), new Class<?>[]{tfClass}, handler);
                    Method queryMethod = queryService.getClass().getMethod("query", String.class, tfClass);
                    Object res = queryMethod.invoke(queryService, ACTIVE_PLAYTIME_SQL, proxy);
                    if (res instanceof Number num && num.longValue() > 0) {
                        totalActiveMs = num.longValue();
                        found = true;
                    }
                }
            } catch (Throwable t) {
                logger.fine("Hinweis bei Plan QueryService: " + t.getMessage());
            }

            // 3. Priorität: Direkte Plan Database-Connection für aktive Netto-Zeit
            if (!found) {
                try {
                    Plugin planPlugin = Bukkit.getPluginManager().getPlugin("Plan");
                    if (planPlugin != null) {
                        Method getDbMethod = planPlugin.getClass().getMethod("getDatabase");
                        Object db = getDbMethod.invoke(planPlugin);
                        Method getConnectionMethod = db.getClass().getMethod("getConnection");
                        try (Connection conn = (Connection) getConnectionMethod.invoke(db);
                             PreparedStatement ps = conn.prepareStatement(ACTIVE_PLAYTIME_SQL)) {
                            ps.setString(1, uuid.toString());
                            try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) {
                                    long dbMs = rs.getLong(1);
                                    if (dbMs > 0) {
                                        totalActiveMs = dbMs;
                                        found = true;
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    logger.fine("Hinweis bei Plan Database Fallback: " + t.getMessage());
                }
            }

            long totalCombinedMs = totalActiveMs + currentSessionActiveMs;
            if (found || totalCombinedMs > 0) {
                long sec = totalCombinedMs / 1000L;
                logger.fine("PlayRewards: Aktive Spielzeit fuer " + uuid + " erfolgreich von Plan geladen: " + sec + "s (" + (sec / 3600) + "h)");
                return Optional.of(sec);
            }

            return Optional.empty();
        });
    }

    private Optional<Long> checkPapiPlanPlaytime(UUID uuid) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return Optional.empty();
        }

        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);

            // A) Direkte Millisekunden-Platzhalter für AKTIVE Spielzeit (niemals total_playtime)
            String[] rawPlaceholders = new String[]{
                    "%plan_player_time_active_raw%",
                    "%plan_player_server_time_active_raw%",
                    "%plan_player_time_active_milliseconds%"
            };

            for (String ph : rawPlaceholders) {
                String msStr = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(op, ph);
                if (msStr != null && !msStr.contains("%") && !msStr.isBlank()) {
                    try {
                        long ms = Long.parseLong(msStr.trim());
                        if (ms > 0) {
                            long sec = ms / 1000L;
                            logger.fine("PlayRewards: Aktive Spielzeit fuer " + uuid + " via PAPI (" + ph + ") geladen: " + sec + "s");
                            return Optional.of(sec);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }

            // B) Formatierte Platzhalter für AKTIVE Spielzeit parsen
            String[] formattedPlaceholders = new String[]{
                    "%plan_player_time_active%",
                    "%plan_player_server_time_active%"
            };

            for (String ph : formattedPlaceholders) {
                String str = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(op, ph);
                if (str != null && !str.contains("%") && !str.isBlank()) {
                    long parsed = parseFormattedTimeToSeconds(str.trim());
                    if (parsed > 0) {
                        logger.fine("PlayRewards: Aktive Spielzeit fuer " + uuid + " via formatiertem PAPI (" + ph + " = '" + str + "') geparst: " + parsed + "s");
                        return Optional.of(parsed);
                    }
                }
            }
        } catch (Throwable t) {
            logger.fine("PAPI Plan Hook Fehler: " + t.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Parst formatierte Strings wie "3d 4h 12m", "3 Tage, 4 Stunden", "5h 30m 10s" in reine Sekunden.
     */
    public static long parseFormattedTimeToSeconds(String text) {
        if (text == null || text.isBlank()) return 0L;

        long totalSeconds = 0L;
        boolean matched = false;

        // Tage
        Matcher days = Pattern.compile("(\\d+)\\s*(?:d|tage?|days?)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (days.find()) {
            totalSeconds += Long.parseLong(days.group(1)) * 86400L;
            matched = true;
        }

        // Stunden
        Matcher hours = Pattern.compile("(\\d+)\\s*(?:h|stunden?|std|hours?)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (hours.find()) {
            totalSeconds += Long.parseLong(hours.group(1)) * 3600L;
            matched = true;
        }

        // Minuten
        Matcher minutes = Pattern.compile("(\\d+)\\s*(?:m|minuten?|min|minutes?)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (minutes.find()) {
            totalSeconds += Long.parseLong(minutes.group(1)) * 60L;
            matched = true;
        }

        // Sekunden
        Matcher seconds = Pattern.compile("(\\d+)\\s*(?:s|sekunden?|sek|sec|seconds?)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (seconds.find()) {
            totalSeconds += Long.parseLong(seconds.group(1));
            matched = true;
        }

        if (!matched) {
            try {
                long direct = Long.parseLong(text);
                if (direct > 100_000_000L) {
                    return direct / 1000L;
                }
                return direct;
            } catch (NumberFormatException ignored) {}
        }

        return totalSeconds;
    }
}
