package de.Z7534.ztone.claims.hook;

import de.Z7534.ztone.claims.model.ChunkPos;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.lang.reflect.Method;
import java.util.Map;

public class WorldGuardHook {

    private boolean available;
    private volatile boolean reflectionResolved;
    private Object worldGuardPlatform;
    private Method getRegionContainerMethod;
    private Method adaptWorldMethod;
    private Method getManagerMethod;
    private Method getRegionsMethod;
    private Method getMinPointMethod;
    private Method getMaxPointMethod;
    private Method getXMethod;
    private Method getZMethod;

    public WorldGuardHook() {
        try {
            Class.forName("com.sk89q.worldguard.WorldGuard");
            this.available = true;
        } catch (ClassNotFoundException e) {
            this.available = false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    private synchronized void resolveReflection() {
        if (reflectionResolved || !available) {
            return;
        }
        try {
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object wg = wgClass.getMethod("getInstance").invoke(null);
            worldGuardPlatform = wgClass.getMethod("getPlatform").invoke(wg);
            getRegionContainerMethod = worldGuardPlatform.getClass().getMethod("getRegionContainer");

            Class<?> bukkitAdapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            adaptWorldMethod = bukkitAdapter.getMethod("adapt", World.class);

            Class<?> regionContainerClass = getRegionContainerMethod.getReturnType();
            getManagerMethod = regionContainerClass.getMethod("get", Class.forName("com.sk89q.worldedit.world.World"));

            Class<?> regionManagerClass = Class.forName("com.sk89q.worldguard.protection.managers.RegionManager");
            getRegionsMethod = regionManagerClass.getMethod("getRegions");

            Class<?> protectedRegionClass = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion");
            getMinPointMethod = protectedRegionClass.getMethod("getMinimumPoint");
            getMaxPointMethod = protectedRegionClass.getMethod("getMaximumPoint");

            Class<?> vectorClass = getMinPointMethod.getReturnType();
            getXMethod = vectorClass.getMethod("getX");
            getZMethod = vectorClass.getMethod("getZ");

            reflectionResolved = true;
        } catch (Throwable t) {
            this.available = false;
        }
    }

    /**
     * Prüft, ob der angegebene Chunk in eine definierte WorldGuard-Region (außer __global__) hineinragt.
     * Ist dies der Fall, darf der Chunk nicht von Spielern geclaimt werden.
     */
    public boolean isChunkInRegion(ChunkPos pos) {
        if (!available || pos == null) {
            return false;
        }
        if (!reflectionResolved) {
            resolveReflection();
            if (!reflectionResolved) {
                return false;
            }
        }
        World world = Bukkit.getWorld(pos.world());
        if (world == null) {
            return false;
        }

        try {
            Object container = getRegionContainerMethod.invoke(worldGuardPlatform);
            Object adaptedWorld = adaptWorldMethod.invoke(null, world);
            if (container == null || adaptedWorld == null) {
                return false;
            }
            Object regionManager = getManagerMethod.invoke(container, adaptedWorld);
            if (regionManager == null) {
                return false;
            }

            @SuppressWarnings("unchecked")
            Map<String, ?> regions = (Map<String, ?>) getRegionsMethod.invoke(regionManager);
            if (regions == null || regions.isEmpty()) {
                return false;
            }

            int chunkMinX = pos.x() << 4;
            int chunkMaxX = chunkMinX + 15;
            int chunkMinZ = pos.z() << 4;
            int chunkMaxZ = chunkMinZ + 15;

            for (Map.Entry<String, ?> entry : regions.entrySet()) {
                String id = entry.getKey();
                if ("__global__".equalsIgnoreCase(id)) {
                    continue;
                }

                Object region = entry.getValue();
                if (region == null) {
                    continue;
                }
                Object min = getMinPointMethod.invoke(region);
                Object max = getMaxPointMethod.invoke(region);

                int regMinX = ((Number) getXMethod.invoke(min)).intValue();
                int regMaxX = ((Number) getXMethod.invoke(max)).intValue();
                int regMinZ = ((Number) getZMethod.invoke(min)).intValue();
                int regMaxZ = ((Number) getZMethod.invoke(max)).intValue();

                // 2D AABB Überschneidung prüfen
                if (chunkMinX <= regMaxX && chunkMaxX >= regMinX && chunkMinZ <= regMaxZ && chunkMaxZ >= regMinZ) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
