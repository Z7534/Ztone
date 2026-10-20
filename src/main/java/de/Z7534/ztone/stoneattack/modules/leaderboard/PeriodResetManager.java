package de.Z7534.ztone.stoneattack.modules.leaderboard;

import de.Z7534.ztone.stoneattack.Stoneattack;
import de.Z7534.ztone.stoneattack.data.dao.SnapshotDao;
import de.Z7534.ztone.stoneattack.data.model.SnapshotRecord;
import de.Z7534.ztone.stoneattack.util.TimeUtil;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

/**
 * Verwaltet die automatischen (Montag 00:00 / 1. des Monats 00:00,
 * Zeitzone konfigurierbar) und manuellen Resets der Zeitraum-Daten
 * fuer "Week" und "Month". "Global" wird nie zurueckgesetzt.
 *
 * Die eigentlichen Basiswerte werden lazy angelegt: Sobald fuer einen
 * Spieler in einem Zeitraum noch kein Basiswert existiert (neuer
 * Spieler, oder gerade erst zurueckgesetzter Zeitraum), wird sein
 * AKTUELL gemessener Rohwert als neue Basis gespeichert - er startet
 * dann bei 0 fuer diesen Zeitraum. Das deckt automatisch auch Spieler
 * ab, die waehrend eines Resets offline waren: Sie bekommen ihre
 * Basis beim naechsten Scan, sobald sie das erste Mal erfasst werden.
 */
public class PeriodResetManager {

    private final Stoneattack plugin;
    private final SnapshotDao snapshotDao;

    public PeriodResetManager(Stoneattack plugin, SnapshotDao snapshotDao) {
        this.plugin = plugin;
        this.snapshotDao = snapshotDao;
    }

    private ZoneId zone() {
        String zoneName = plugin.getConfigManager().get().getString("leaderboard.reset-timezone", "Europe/Berlin");
        return ZoneId.of(zoneName);
    }

    /**
     * Wird bei JEDEM Cache-Refresh (async) aufgerufen. Erkennt sowohl
     * einen laufenden Reset (Server war beim exakten Zeitpunkt online)
     * als auch einen verpassten Reset (Server war offline und die
     * gespeicherte period_start liegt vor dem aktuell erwarteten
     * Beginn) - beide Faelle werden identisch behandelt.
     */
    public void checkAndPerformScheduledResets() {
        checkPeriod(TimePeriod.WEEK, TimeUtil.currentWeekStart(zone()));
        checkPeriod(TimePeriod.MONTH, TimeUtil.currentMonthStart(zone()));
    }

    private void checkPeriod(TimePeriod period, Instant expectedStart) {
        long expectedMillis = expectedStart.toEpochMilli();
        long storedMillis = snapshotDao.getPeriodStart(period.key());

        if (storedMillis != expectedMillis) {
            for (LeaderboardType type : LeaderboardType.values()) {
                snapshotDao.deleteAll(type.key(), period.key());
            }
            snapshotDao.setPeriodStart(period.key(), expectedMillis);
            plugin.getLogger().info("[Leaderboard] Zeitraum '" + period.key() + "' wurde zurueckgesetzt "
                    + "(neuer Beginn: " + expectedStart + ").");
        }
    }

    /** Fuer /leaderboard reset <week|month> - erzwingt einen sofortigen Reset. */
    public void manualReset(TimePeriod period) {
        Instant now = Instant.now();
        for (LeaderboardType type : LeaderboardType.values()) {
            snapshotDao.deleteAll(type.key(), period.key());
        }
        snapshotDao.setPeriodStart(period.key(), now.toEpochMilli());
    }

    public Map<UUID, SnapshotRecord> loadSnapshots(LeaderboardType type, TimePeriod period) {
        if (period == TimePeriod.GLOBAL) {
            return Map.of();
        }
        return snapshotDao.loadAll(type.key(), period.key());
    }

    /**
     * Liefert den periodenbezogenen Wert (aktueller Rohwert minus
     * Basiswert). Legt bei Bedarf lazy einen neuen Basiswert an (siehe
     * Klassenkommentar).
     */
    public double resolvePeriodValue(LeaderboardType type, TimePeriod period, UUID uuid, String name,
                                      double rawValue, Map<UUID, SnapshotRecord> snapshotsForPeriod) {
        if (period == TimePeriod.GLOBAL) {
            return rawValue;
        }

        SnapshotRecord existing = snapshotsForPeriod.get(uuid);
        if (existing == null) {
            long periodStart = snapshotDao.getPeriodStart(period.key());
            SnapshotRecord created = new SnapshotRecord(uuid, name, type.key(), period.key(), rawValue, periodStart);
            snapshotDao.upsert(created);
            snapshotsForPeriod.put(uuid, created);
            return 0.0;
        }

        return Math.max(0.0, rawValue - existing.baseValue());
    }
}
