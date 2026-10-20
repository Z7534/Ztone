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

public final class SafeSpotFinder {

    private static final Set<Material> UNSAFE_GROUND = EnumSet.of(
            Material.SAND, Material.RED_SAND, Material.GRAVEL,
            Material.LAVA, Material.MAGMA_BLOCK, Material.CACTUS,
            Material.FIRE, Material.SOUL_FIRE, Material.WATER
    );

    public Optional<Location> findSafeSpot(Location origin, Claim excludeClaim, int radius) {
        World world = origin.getWorld();
        if (world == null) return Optional.empty();
        int baseX = origin.getBlockX();
        int baseZ = origin.getBlockZ();

        for (int r = 3; r <= radius; r += 3) {
            for (int angleDeg = 0; angleDeg < 360; angleDeg += 15) {
                double rad = Math.toRadians(angleDeg);
                int x = baseX + (int) Math.round(Math.cos(rad) * r);
                int z = baseZ + (int) Math.round(Math.sin(rad) * r);

                ChunkPos chunk = new ChunkPos(world.getName(), x >> 4, z >> 4);
                if (excludeClaim.chunks().contains(chunk)) continue;
                if (!world.isChunkLoaded(chunk.x(), chunk.z())) {
                    world.loadChunk(chunk.x(), chunk.z());
                }

                Optional<Location> spot = checkPlatform(world, x, z);
                if (spot.isPresent() && noHostilesNearby(spot.get(), 10)) {
                    return spot;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Location> checkPlatform(World world, int centerX, int centerZ) {
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

    private boolean noHostilesNearby(Location loc, double radius) {
        World world = loc.getWorld();
        if (world == null) return true;
        return world.getNearbyEntities(loc, radius, radius, radius).stream()
                .noneMatch(e -> e instanceof Monster);
    }
}
