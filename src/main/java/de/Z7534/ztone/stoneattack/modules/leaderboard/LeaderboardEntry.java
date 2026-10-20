package de.Z7534.ztone.stoneattack.modules.leaderboard;

import java.util.UUID;

/** Ein fertig berechneter Eintrag (Platz X) fuer einen Zeitraum. */
public record LeaderboardEntry(UUID uuid, String name, double rawValue, String formattedValue) {
}
