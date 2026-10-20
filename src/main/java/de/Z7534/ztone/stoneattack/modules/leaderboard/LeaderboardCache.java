package de.Z7534.ztone.stoneattack.modules.leaderboard;

import de.Z7534.ztone.stoneattack.Stoneattack;
import de.Z7534.ztone.stoneattack.data.model.SnapshotRecord;
import de.Z7534.ztone.stoneattack.util.FormatUtil;
import de.Z7534.ztone.stoneattack.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Haelt die fertig sortierten Leaderboard-Daten im Speicher.
 *
 * Es gibt ZWEI Aktualisierungen:
 * <ul>
 *   <li><b>Vollstaendig</b> ({@code cache-refresh-interval-seconds}, Standard 60 s):
 *       liest ALLE je bekannten Spieler neu ein (offline ueber die stats-JSONs) und
 *       baut die komplette Rangliste neu auf. Das ist die teure Variante.</li>
 *   <li><b>Live</b> ({@code live-refresh-interval-seconds}, Standard 3 s):
 *       fragt nur die ONLINE-Spieler ab und schreibt deren neue Werte in die
 *       bestehende Rangliste (inkl. Neusortierung). Damit wandern Spielzeit, Kills,
 *       Tode und Kontostand praktisch sofort sichtbar durchs Board, ohne dass
 *       dafuer jedes Mal die gesamte Spielerdatenbank gelesen werden muss.</li>
 * </ul>
 *
 * Jede erfolgreiche Aenderung erhoeht {@link #getVersion()}. Die Hologramme zeichnen
 * ihren Text nur dann neu, wenn sich diese Zahl (oder Seite/Zeitraum) geaendert hat.
 */
public class LeaderboardCache {

    private final Stoneattack plugin;
    private final StatisticsService statisticsService;
    private final PeriodResetManager periodResetManager;
    private final ExclusionService exclusionService;

    private volatile Map<LeaderboardType, Map<TimePeriod, List<LeaderboardEntry>>> data = new EnumMap<>(LeaderboardType.class);
    private volatile Map<LeaderboardType, Map<TimePeriod, Map<UUID, Integer>>> ranks = new EnumMap<>(LeaderboardType.class);
    private volatile Map<LeaderboardType, Map<TimePeriod, Map<UUID, LeaderboardEntry>>> entryByUuid = new EnumMap<>(LeaderboardType.class);
    /**
     * Basiswerte je Zeitraum aus dem letzten vollstaendigen Durchlauf. Damit kann die
     * Live-Aktualisierung den Zeitraum-Wert (Rohwert minus Basis) berechnen, ohne
     * dafuer erneut in die Datenbank zu gehen.
     */
    private volatile Map<LeaderboardType, Map<TimePeriod, Map<UUID, Double>>> baseValues = new EnumMap<>(LeaderboardType.class);

    private final AtomicLong version = new AtomicLong();

    private BukkitTask refreshTask;
    private BukkitTask liveTask;
    private volatile boolean refreshInProgress = false;
    private volatile boolean liveRefreshInProgress = false;

    public LeaderboardCache(Stoneattack plugin, StatisticsService statisticsService,
                             PeriodResetManager periodResetManager, ExclusionService exclusionService) {
        this.plugin = plugin;
        this.statisticsService = statisticsService;
        this.periodResetManager = periodResetManager;
        this.exclusionService = exclusionService;
    }

    public void start() {
        long intervalTicks = plugin.getConfigManager().get().getLong("leaderboard.cache-refresh-interval-seconds", 60) * 20L;
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin.getPlugin(), this::triggerRefresh, 40L, Math.max(20L, intervalTicks));

        long liveTicks = Math.max(1L, plugin.getConfigManager().get()
                .getLong("leaderboard.live-refresh-interval-seconds", 3)) * 20L;
        liveTask = Bukkit.getScheduler().runTaskTimer(plugin.getPlugin(), this::triggerLiveRefresh, 60L, liveTicks);
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        if (liveTask != null) {
            liveTask.cancel();
            liveTask = null;
        }
    }

    public void forceRefresh() {
        version.incrementAndGet();
        triggerRefresh();
    }

    /** Zaehler, der sich bei jeder inhaltlichen Aenderung erhoeht (fuer die Hologramme). */
    public long getVersion() {
        return version.get();
    }

    // ------------------------------------------------------------
    // Vollstaendiger Durchlauf
    // ------------------------------------------------------------

    /** Wird auf dem Main-Thread aufgerufen (Timer-Task). */
    private void triggerRefresh() {
        if (refreshInProgress) {
            return; // vorheriger Durchlauf laeuft noch (z.B. sehr grosse Playerbase) -> ueberspringen
        }
        refreshInProgress = true;

        // Schritt 1 (MUSS Main-Thread sein): lebende Online-Werte + Namen einsammeln.
        Map<LeaderboardType, Map<UUID, Double>> onlineValues = new EnumMap<>(LeaderboardType.class);
        for (LeaderboardType type : activeTypes()) {
            onlineValues.put(type, statisticsService.collectOnlineValues(type));
        }
        Map<UUID, String> nameCache = new HashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            nameCache.put(p.getUniqueId(), p.getName());
        }
        OfflinePlayer[] allKnown = Bukkit.getOfflinePlayers();

        // Schritt 2: alles Weitere komplett asynchron.
        Bukkit.getScheduler().runTaskAsynchronously(plugin.getPlugin(), () -> {
            try {
                periodResetManager.checkAndPerformScheduledResets();
                computeAll(onlineValues, nameCache, allKnown);
            } catch (Exception e) {
                plugin.getLogger().warning("Fehler beim Aktualisieren der Leaderboard-Daten: " + e.getMessage());
            } finally {
                refreshInProgress = false;
            }
        });
    }

    private List<LeaderboardType> activeTypes() {
        var config = plugin.getConfigManager().get();
        List<LeaderboardType> result = new ArrayList<>();
        for (LeaderboardType type : LeaderboardType.values()) {
            if (!config.getBoolean("leaderboard.types." + type.key(), true)) {
                continue;
            }
            if (type == LeaderboardType.MONEY && plugin.getEconomy() == null) {
                continue; // kein Vault/keine Economy -> Money-Leaderboard bleibt inaktiv
            }
            result.add(type);
        }
        return result;
    }

    private void computeAll(Map<LeaderboardType, Map<UUID, Double>> onlineValues,
                             Map<UUID, String> nameCache, OfflinePlayer[] allKnown) {

        Map<LeaderboardType, Map<TimePeriod, List<LeaderboardEntry>>> newData = new EnumMap<>(LeaderboardType.class);
        Map<LeaderboardType, Map<TimePeriod, Map<UUID, Integer>>> newRanks = new EnumMap<>(LeaderboardType.class);
        Map<LeaderboardType, Map<TimePeriod, Map<UUID, LeaderboardEntry>>> newEntryByUuid = new EnumMap<>(LeaderboardType.class);
        Map<LeaderboardType, Map<TimePeriod, Map<UUID, Double>>> newBaseValues = new EnumMap<>(LeaderboardType.class);

        for (LeaderboardType type : activeTypes()) {
            Map<UUID, Double> online = onlineValues.getOrDefault(type, Map.of());

            Map<UUID, SnapshotRecord> monthSnapshots = periodResetManager.loadSnapshots(type, TimePeriod.MONTH);
            Map<UUID, SnapshotRecord> weekSnapshots = periodResetManager.loadSnapshots(type, TimePeriod.WEEK);

            // Rohwerte fuer ALLE bekannten Spieler (online bevorzugt aus dem
            // frischen Main-Thread-Snapshot, sonst offline nachgeladen).
            Map<UUID, Double> rawValues = new HashMap<>();
            for (OfflinePlayer offline : allKnown) {
                UUID uuid = offline.getUniqueId();
                if (exclusionService.isExcluded(type, uuid)) {
                    continue;
                }
                nameCache.putIfAbsent(uuid, offline.getName() != null ? offline.getName() : "???");
                double raw = online.containsKey(uuid) ? online.get(uuid) : statisticsService.readOfflineValue(offline, type);
                rawValues.put(uuid, raw);
            }
            // Online-Spieler, die (theoretisch) noch nicht in getOfflinePlayers()
            // auftauchen (brandneue Spieler beim allerersten Join).
            for (Map.Entry<UUID, Double> entry : online.entrySet()) {
                if (exclusionService.isExcluded(type, entry.getKey())) {
                    continue;
                }
                rawValues.putIfAbsent(entry.getKey(), entry.getValue());
            }

            Map<TimePeriod, List<LeaderboardEntry>> perPeriod = new EnumMap<>(TimePeriod.class);
            Map<TimePeriod, Map<UUID, Integer>> perPeriodRanks = new EnumMap<>(TimePeriod.class);
            Map<TimePeriod, Map<UUID, LeaderboardEntry>> perPeriodEntryByUuid = new EnumMap<>(TimePeriod.class);
            Map<TimePeriod, Map<UUID, Double>> perPeriodBases = new EnumMap<>(TimePeriod.class);

            for (TimePeriod period : TimePeriod.values()) {
                Map<UUID, SnapshotRecord> snapshots = period == TimePeriod.MONTH ? monthSnapshots
                        : period == TimePeriod.WEEK ? weekSnapshots : Map.of();

                List<LeaderboardEntry> entries = new ArrayList<>();
                for (Map.Entry<UUID, Double> rawEntry : rawValues.entrySet()) {
                    UUID uuid = rawEntry.getKey();
                    String name = nameCache.getOrDefault(uuid, "???");
                    double periodValue = periodResetManager.resolvePeriodValue(type, period, uuid, name, rawEntry.getValue(), snapshots);
                    entries.add(new LeaderboardEntry(uuid, name, periodValue, formatValue(type, periodValue)));
                }
                entries.sort(Comparator.comparingDouble(LeaderboardEntry::rawValue).reversed());

                Map<UUID, Integer> rankMap = new HashMap<>();
                Map<UUID, LeaderboardEntry> entryMap = new HashMap<>();
                for (int i = 0; i < entries.size(); i++) {
                    rankMap.put(entries.get(i).uuid(), i + 1);
                    entryMap.put(entries.get(i).uuid(), entries.get(i));
                }

                // Basiswerte merken - resolvePeriodValue hat fehlende Eintraege bereits angelegt.
                Map<UUID, Double> bases = new HashMap<>();
                if (period != TimePeriod.GLOBAL) {
                    for (Map.Entry<UUID, SnapshotRecord> snap : snapshots.entrySet()) {
                        bases.put(snap.getKey(), snap.getValue().baseValue());
                    }
                }

                perPeriod.put(period, entries);
                perPeriodRanks.put(period, rankMap);
                perPeriodEntryByUuid.put(period, entryMap);
                perPeriodBases.put(period, bases);
            }

            newData.put(type, perPeriod);
            newRanks.put(type, perPeriodRanks);
            newEntryByUuid.put(type, perPeriodEntryByUuid);
            newBaseValues.put(type, perPeriodBases);
        }

        // Atomarer Austausch - Leser (Render-Task) sehen entweder komplett
        // die alten oder komplett die neuen Daten, nie einen Mischzustand.
        this.data = newData;
        this.ranks = newRanks;
        this.entryByUuid = newEntryByUuid;
        this.baseValues = newBaseValues;
        version.incrementAndGet();
    }

    // ------------------------------------------------------------
    // Live-Aktualisierung (nur Online-Spieler)
    // ------------------------------------------------------------

    /** Main-Thread: aktuelle Werte der Online-Spieler abgreifen, Rest asynchron. */
    private void triggerLiveRefresh() {
        if (refreshInProgress || liveRefreshInProgress || Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        Map<LeaderboardType, Map<TimePeriod, List<LeaderboardEntry>>> current = this.data;
        if (current.isEmpty()) {
            return; // der erste vollstaendige Durchlauf war noch nicht fertig
        }
        liveRefreshInProgress = true;

        Map<LeaderboardType, Map<UUID, Double>> onlineValues = new EnumMap<>(LeaderboardType.class);
        for (LeaderboardType type : activeTypes()) {
            onlineValues.put(type, statisticsService.collectOnlineValues(type));
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin.getPlugin(), () -> {
            try {
                applyLiveValues(onlineValues);
            } catch (Exception e) {
                plugin.getLogger().warning("Fehler bei der Live-Aktualisierung der Leaderboards: " + e.getMessage());
            } finally {
                liveRefreshInProgress = false;
            }
        });
    }

    private void applyLiveValues(Map<LeaderboardType, Map<UUID, Double>> onlineValues) {
        Map<LeaderboardType, Map<TimePeriod, List<LeaderboardEntry>>> oldData = this.data;
        Map<LeaderboardType, Map<TimePeriod, Map<UUID, Double>>> bases = this.baseValues;

        Map<LeaderboardType, Map<TimePeriod, List<LeaderboardEntry>>> newData = new EnumMap<>(LeaderboardType.class);
        Map<LeaderboardType, Map<TimePeriod, Map<UUID, Integer>>> newRanks = new EnumMap<>(LeaderboardType.class);
        Map<LeaderboardType, Map<TimePeriod, Map<UUID, LeaderboardEntry>>> newEntryByUuid = new EnumMap<>(LeaderboardType.class);
        boolean anyChange = false;

        for (Map.Entry<LeaderboardType, Map<TimePeriod, List<LeaderboardEntry>>> typeEntry : oldData.entrySet()) {
            LeaderboardType type = typeEntry.getKey();
            Map<UUID, Double> online = onlineValues.getOrDefault(type, Map.of());

            Map<TimePeriod, List<LeaderboardEntry>> perPeriod = new EnumMap<>(TimePeriod.class);
            Map<TimePeriod, Map<UUID, Integer>> perPeriodRanks = new EnumMap<>(TimePeriod.class);
            Map<TimePeriod, Map<UUID, LeaderboardEntry>> perPeriodEntryByUuid = new EnumMap<>(TimePeriod.class);

            for (Map.Entry<TimePeriod, List<LeaderboardEntry>> periodEntry : typeEntry.getValue().entrySet()) {
                TimePeriod period = periodEntry.getKey();
                List<LeaderboardEntry> old = periodEntry.getValue();
                Map<UUID, Double> baseMap = bases.getOrDefault(type, Map.of()).getOrDefault(period, Map.of());

                List<LeaderboardEntry> updated = new ArrayList<>(old.size());
                boolean changed = false;
                for (LeaderboardEntry entry : old) {
                    Double raw = online.get(entry.uuid());
                    if (raw == null) {
                        updated.add(entry);
                        continue;
                    }
                    double value;
                    if (period == TimePeriod.GLOBAL) {
                        value = raw;
                    } else {
                        Double base = baseMap.get(entry.uuid());
                        if (base == null) {
                            // Basis noch unbekannt (neuer Spieler) -> bis zum naechsten
                            // vollstaendigen Durchlauf unveraendert lassen.
                            updated.add(entry);
                            continue;
                        }
                        value = Math.max(0.0, raw - base);
                    }
                    if (value == entry.rawValue()) {
                        updated.add(entry);
                        continue;
                    }
                    changed = true;
                    updated.add(new LeaderboardEntry(entry.uuid(), entry.name(), value, formatValue(type, value)));
                }

                if (changed) {
                    anyChange = true;
                    updated.sort(Comparator.comparingDouble(LeaderboardEntry::rawValue).reversed());
                    Map<UUID, Integer> rankMap = new HashMap<>();
                    Map<UUID, LeaderboardEntry> entryMap = new HashMap<>();
                    for (int i = 0; i < updated.size(); i++) {
                        rankMap.put(updated.get(i).uuid(), i + 1);
                        entryMap.put(updated.get(i).uuid(), updated.get(i));
                    }
                    perPeriod.put(period, updated);
                    perPeriodRanks.put(period, rankMap);
                    perPeriodEntryByUuid.put(period, entryMap);
                } else {
                    perPeriod.put(period, old);
                    perPeriodRanks.put(period, ranks.getOrDefault(type, Map.of()).getOrDefault(period, Map.of()));
                    perPeriodEntryByUuid.put(period, entryByUuid.getOrDefault(type, Map.of()).getOrDefault(period, Map.of()));
                }
            }

            newData.put(type, perPeriod);
            newRanks.put(type, perPeriodRanks);
            newEntryByUuid.put(type, perPeriodEntryByUuid);
        }

        if (!anyChange) {
            return;
        }
        // Nur uebernehmen, wenn in der Zwischenzeit kein vollstaendiger Durchlauf
        // fertig geworden ist - sonst wuerden wir dessen frischere Daten ueberschreiben.
        if (this.data != oldData) {
            return;
        }
        this.data = newData;
        this.ranks = newRanks;
        this.entryByUuid = newEntryByUuid;
        version.incrementAndGet();
    }

    // ------------------------------------------------------------

    private String formatValue(LeaderboardType type, double value) {
        var config = plugin.getConfigManager().get();
        return switch (type) {
            case PLAYTIME -> TimeUtil.formatPlaytime((long) value, config.getString("leaderboard.format.playtime-format", "%hours%h %minutes%m"));
            case KILLS -> (long) value + config.getString("leaderboard.format.kills-suffix", " Kills");
            case DEATHS -> (long) value + config.getString("leaderboard.format.deaths-suffix", " Tode");
            case MONEY -> {
                var economy = plugin.getEconomy();
                if (economy != null) {
                    yield economy.format(value);
                }
                yield FormatUtil.applyMoneyFormat(value, config.getString("leaderboard.format.money-fallback-format", "#,##0.00 $"));
            }
        };
    }

    public List<LeaderboardEntry> getPage(LeaderboardType type, TimePeriod period, int page, int perPage) {
        List<LeaderboardEntry> all = data.getOrDefault(type, Map.of()).getOrDefault(period, List.of());
        int from = Math.max(0, (page - 1) * perPage);
        if (from >= all.size()) {
            return List.of();
        }
        int to = Math.min(all.size(), from + perPage);
        return all.subList(from, to);
    }

    public int getEntryCount(LeaderboardType type, TimePeriod period) {
        return data.getOrDefault(type, Map.of()).getOrDefault(period, List.of()).size();
    }

    /** Eigene Platzierung eines Spielers, oder -1 falls (noch) kein Wert vorhanden. */
    public int getRank(LeaderboardType type, TimePeriod period, UUID uuid) {
        return ranks.getOrDefault(type, Map.of()).getOrDefault(period, Map.of()).getOrDefault(uuid, -1);
    }

    public LeaderboardEntry getEntryForRank(LeaderboardType type, TimePeriod period, int rank) {
        List<LeaderboardEntry> all = data.getOrDefault(type, Map.of()).getOrDefault(period, List.of());
        if (rank < 1 || rank > all.size()) {
            return null;
        }
        return all.get(rank - 1);
    }

    public LeaderboardEntry getEntry(LeaderboardType type, TimePeriod period, UUID uuid) {
        return entryByUuid.getOrDefault(type, Map.of()).getOrDefault(period, Map.of()).get(uuid);
    }
}
