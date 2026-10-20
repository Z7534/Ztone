package de.Z7534.ztone.stoneattack.data.model;

import java.util.UUID;

/** Ein Basiswert-Snapshot fuer einen Spieler/Statistik-Typ/Zeitraum. */
public record SnapshotRecord(UUID uuid, String lastName, String leaderboardType, String period,
                              double baseValue, long periodStart) {
}
