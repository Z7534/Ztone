package de.Z7534.ztone.claims.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class SelectionSession {

    private final UUID player;
    private final Set<ChunkPos> toClaim = new HashSet<>();
    private final Set<ChunkPos> toUnclaim = new HashSet<>();

    public SelectionSession(UUID player) {
        this.player = player;
    }

    public UUID player() {
        return player;
    }

    public Set<ChunkPos> toClaim() {
        return toClaim;
    }

    public Set<ChunkPos> toUnclaim() {
        return toUnclaim;
    }

    public int totalMarked() {
        return toClaim.size() + toUnclaim.size();
    }
}
