package de.Z7534.ztone.claims.model;

import java.util.Objects;

public final class ChunkPos {

    private final String world;
    private final int x;
    private final int z;

    public ChunkPos(String world, int x, int z) {
        this.world = world;
        this.x = x;
        this.z = z;
    }

    public String world() {
        return world;
    }

    public int x() {
        return x;
    }

    public int z() {
        return z;
    }

    public ChunkPos north() {
        return new ChunkPos(world, x, z - 1);
    }

    public ChunkPos south() {
        return new ChunkPos(world, x, z + 1);
    }

    public ChunkPos east() {
        return new ChunkPos(world, x + 1, z);
    }

    public ChunkPos west() {
        return new ChunkPos(world, x - 1, z);
    }

    public ChunkPos[] neighbours() {
        return new ChunkPos[]{north(), south(), east(), west()};
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChunkPos other)) return false;
        return x == other.x && z == other.z && world.equals(other.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, x, z);
    }

    @Override
    public String toString() {
        return world + ":" + x + "," + z;
    }
}
