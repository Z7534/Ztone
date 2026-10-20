package de.Z7534.ztone.stoneattack.modules.leaderboard;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Haelt pro Spieler und pro Leaderboard-Typ eine eigene Auswahl
 * (Zeitraum + Seite). Wird beim Verlassen des Servers, Weltwechsel
 * oder Serverwechsel (Proxy) auf Standard (Global/Seite 1)
 * zurueckgesetzt bzw. komplett entfernt - nichts davon ist persistent.
 */
public class PlayerSessionManager {

    private final Map<UUID, Map<LeaderboardType, PlayerSessionState>> sessions = new ConcurrentHashMap<>();

    public PlayerSessionState getState(UUID player, LeaderboardType type) {
        return sessions
                .computeIfAbsent(player, p -> new ConcurrentHashMap<>())
                .computeIfAbsent(type, t -> new PlayerSessionState());
    }

    /** Weltwechsel: Spieler bleibt online, Auswahl wird nur zurueckgesetzt. */
    public void resetPlayer(UUID player) {
        Map<LeaderboardType, PlayerSessionState> map = sessions.get(player);
        if (map != null) {
            map.values().forEach(PlayerSessionState::reset);
        }
    }

    /** Quit/Serverwechsel: Eintrag komplett entfernen (Speicher freigeben). */
    public void forget(UUID player) {
        sessions.remove(player);
    }
}
