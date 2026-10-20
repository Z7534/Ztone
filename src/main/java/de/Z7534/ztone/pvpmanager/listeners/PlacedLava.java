package de.Z7534.ztone.pvpmanager.listeners;

import java.util.Objects;
import java.util.UUID;

public final class PlacedLava {

    private final UUID placer;
    private final long placedAt;

    public PlacedLava(UUID placer, long placedAt) {
        this.placer = placer;
        this.placedAt = placedAt;
    }

    public UUID placer() {
        return placer;
    }

    public long placedAt() {
        return placedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlacedLava that = (PlacedLava) o;
        return placedAt == that.placedAt && Objects.equals(placer, that.placer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(placer, placedAt);
    }

    @Override
    public String toString() {
        return "PlacedLava[placer=" + placer + ", placedAt=" + placedAt + "]";
    }
}
