package de.Z7534.ztone.claims.manager;

import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.claims.model.ChunkPos;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Monster;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public final class SafeSpotFinder {

    private static final Set<Material> UNSAFE_GROUND = EnumSet.of(
            Material.SAND, Material.RED_SAND, Material.GRAVEL,
            Material.LAVA, Material.MAGMA_BLOCK, Material.CACTUS,
            Material.FIRE, Material.SOUL_FIRE, Material.WATER
    );

    public Optional<Location> findSafeSpot(Location origin, Claim excludeClaim, int radius) {
        if (excludeClaim == null) {
            return findSafeSpot(origin, chunk -> false, radius);
        }
        return findSafeSpot(origin, chunk -> excludeClaim.chunks().contains(chunk), radius);
    }

    /**
     * Findet einen sicheren Platz außerhalb blockierter Chunks.
     * Unterstützt Overworld, Nether (unter der Bedrock-Decke) und The End.
     * Fällt bei Fehlschlagen der 3x3-Plattform auf einen 1x1-Sicherheitsblock zurück.
     */
    public Optional<Location> findSafeSpot(Location origin, Predicate<ChunkPos> isBlockedChunk, int radius) {
        World world = origin.getWorld();
        if (world == null) return Optional.empty();
        int baseX = origin.getBlockX();
        int baseZ = origin.getBlockZ();

        Location fallback1x1 = null;

        for (int r = 3; r <= radius; r += 3) {
            for (int angleDeg = 0; angleDeg < 360; angleDeg += 15) {
                double rad = Math.toRadians(angleDeg);
                int x = baseX + (int) Math.round(Math.cos(rad) * r);
                int z = baseZ + (int) Math.round(Math.sin(rad) * r);

                ChunkPos chunk = new ChunkPos(world.getName(), x >> 4, z >> 4);
                if (isBlockedChunk.test(chunk)) continue;
                if (!world.isChunkLoaded(chunk.x(), chunk.z())) {
                    world.loadChunk(chunk.x(), chunk.z());
                }

                // 1. Strenge 3x3-Plattform prüfen
                Optional<Location> spot = checkPlatform(world, x, z);
                if (spot.isPresent() && noHostilesNearby(spot.get(), 10)) {
                    return spot;
                }

                // 2. 1x1-Block als Fallback merken, falls 3x3 scheitert
                if (fallback1x1 == null) {
                    Optional<Location> single = checkSingleBlock(world, x, z);
                    if (single.isPresent()) {
                        fallback1x1 = single.get();
                    }
                }
            }
        }

        return Optional.ofNullable(fallback1x1);
    }

    private Optional<Location> checkPlatform(World world, int centerX, int centerZ) {
        if (world.getEnvironment() == World.Environment.NETHER) {
            return checkNetherPlatform(world, centerX, centerZ);
        }

        Integer commonY = null;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block highest = world.getHighestBlockAt(centerX + dx, centerZ + dz);
                if (commonY == null) {
                    commonY = highest.getY();
                } else if (highest.getY() != commonY) {
                    return Optional.empty();
                }
                Material type = highest.getType();
                if (!type.isSolid() || UNSAFE_GROUND.contains(type)) {
                    return Optional.empty();
                }
                Block above1 = world.getBlockAt(centerX + dx, commonY + 1, centerZ + dz);
                Block above2 = world.getBlockAt(centerX + dx, commonY + 2, centerZ + dz);
                if (above1.getType().isSolid() || above2.getType().isSolid()) {
                    return Optional.empty();
                }
            }
        }
        return Optional.of(new Location(world, centerX + 0.5, commonY + 1, centerZ + 0.5));
    }

    private Optional<Location> checkNetherPlatform(World world, int centerX, int centerZ) {
        // Im Nether: Unter Y=120 nach solidem Boden mit 2 Luftblöcken suchen
        int groundY = findNetherGroundY(world, centerX, centerZ);
        if (groundY == -1) return Optional.empty();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block floor = world.getBlockAt(centerX + dx, groundY, centerZ + dz);
                Material type = floor.getType();
                if (!type.isSolid() || UNSAFE_GROUND.contains(type)) {
                    return Optional.empty();
                }
                Block above1 = world.getBlockAt(centerX + dx, groundY + 1, centerZ + dz);
                Block above2 = world.getBlockAt(centerX + dx, groundY + 2, centerZ + dz);
                if (above1.getType().isSolid() || above2.getType().isSolid() || above1.isLiquid() || above2.isLiquid()) {
                    return Optional.empty();
                }
            }
        }
        return Optional.of(new Location(world, centerX + 0.5, groundY + 1, centerZ + 0.5));
    }

    private int findNetherGroundY(World world, int x, int z) {
        for (int y = 115; y >= 32; y--) {
            Block b = world.getBlockAt(x, y, z);
            if (b.getType().isSolid() && !UNSAFE_GROUND.contains(b.getType())) {
                Block a1 = world.getBlockAt(x, y + 1, z);
                Block a2 = world.getBlockAt(x, y + 2, z);
                if (a1.isPassable() && !a1.isLiquid() && a2.isPassable() && !a2.isLiquid()) {
                    return y;
                }
            }
        }
        return -1;
    }

    private Optional<Location> checkSingleBlock(World world, int x, int z) {
        int groundY;
        if (world.getEnvironment() == World.Environment.NETHER) {
            groundY = findNetherGroundY(world, x, z);
        } else {
            Block highest = world.getHighestBlockAt(x, z);
            groundY = highest.getY();
        }

        if (groundY <= world.getMinHeight()) return Optional.empty();

        Block floor = world.getBlockAt(x, groundY, z);
        Material type = floor.getType();
        if (!type.isSolid() || UNSAFE_GROUND.contains(type)) {
            return Optional.empty();
        }
        Block a1 = world.getBlockAt(x, groundY + 1, z);
        Block a2 = world.getBlockAt(x, groundY + 2, z);
        if (a1.getType().isSolid() || a2.getType().isSolid() || a1.isLiquid() || a2.isLiquid()) {
            return Optional.empty();
        }

        return Optional.of(new Location(world, x + 0.5, groundY + 1, z + 0.5));
    }

    private boolean noHostilesNearby(Location loc, double radius) {
        World world = loc.getWorld();
        if (world == null) return true;
        return world.getNearbyEntities(loc, radius, radius, radius).stream()
                .noneMatch(e -> e instanceof Monster);
    }
}
