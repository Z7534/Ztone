package de.Z7534.ztone.claims.model;

import java.util.UUID;

public final class ClaimLogEntry {

    private final long id;
    private final Long claimId;
    private final String claimName;
    private final String world;
    private final UUID playerUuid;
    private final String playerName;
    private final String action;
    private final long timestamp;
    private final String details;

    public ClaimLogEntry(long id, Long claimId, String claimName, String world,
                         UUID playerUuid, String playerName, String action,
                         long timestamp, String details) {
        this.id = id;
        this.claimId = claimId;
        this.claimName = claimName;
        this.world = world;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.action = action;
        this.timestamp = timestamp;
        this.details = details;
    }

    public long id() {
        return id;
    }

    public Long claimId() {
        return claimId;
    }

    public String claimName() {
        return claimName;
    }

    public String world() {
        return world;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public String playerName() {
        return playerName;
    }

    public String action() {
        return action;
    }

    public long timestamp() {
        return timestamp;
    }

    public String details() {
        return details;
    }
}
