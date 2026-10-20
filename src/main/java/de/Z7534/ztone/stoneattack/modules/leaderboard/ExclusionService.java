package de.Z7534.ztone.stoneattack.modules.leaderboard;

import de.Z7534.ztone.stoneattack.data.dao.ExclusionDao;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Haelt zusaetzlich zur Datenbank einen In-Memory-Cache der
 * Ausschlussliste je Leaderboard-Typ, damit der haeufige Check
 * "ist Spieler X ausgeschlossen?" beim Berechnen der Ranglisten nicht
 * jedes Mal die Datenbank treffen muss. Aenderungen (add/remove)
 * greifen sofort, ohne Reload.
 */
public class ExclusionService {

    private final ExclusionDao dao;
    private final Map<LeaderboardType, Set<UUID>> cache = new ConcurrentHashMap<>();

    public ExclusionService(ExclusionDao dao) {
        this.dao = dao;
    }

    /** Einmalig beim Start (bzw. bei Bedarf) - synchron, da schnelle lokale DB-Abfrage. */
    public void loadAll() {
        for (LeaderboardType type : LeaderboardType.values()) {
            cache.put(type, new HashSet<>(dao.loadExcluded(type.key())));
        }
    }

    public boolean isExcluded(LeaderboardType type, UUID uuid) {
        Set<UUID> set = cache.get(type);
        return set != null && set.contains(uuid);
    }

    public void exclude(UUID uuid, LeaderboardType type) {
        dao.exclude(uuid, type.key());
        cache.computeIfAbsent(type, t -> new HashSet<>()).add(uuid);
    }

    public void include(UUID uuid, LeaderboardType type) {
        dao.include(uuid, type.key());
        Set<UUID> set = cache.get(type);
        if (set != null) {
            set.remove(uuid);
        }
    }

    public void excludeFromAll(UUID uuid) {
        for (LeaderboardType type : LeaderboardType.values()) {
            exclude(uuid, type);
        }
    }

    public void includeInAll(UUID uuid) {
        for (LeaderboardType type : LeaderboardType.values()) {
            include(uuid, type);
        }
    }

    public Set<UUID> getExcluded(LeaderboardType type) {
        return Set.copyOf(cache.getOrDefault(type, Set.of()));
    }
}
