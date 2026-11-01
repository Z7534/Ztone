package de.Z7534.ztone.claims.hook;

import de.Z7534.ztone.claims.model.ChunkPos;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;

public class WorldGuardHook {

    public static Object CLAIM_FLAG;

    private boolean available;
    private volatile boolean reflectionResolved;
    private Object worldGuardPlatform;
    private Method getRegionContainerMethod;
    private Method adaptWorldMethod;
    private Method getManagerMethod;
    private Method getRegionsMethod;
    private Method getMinPointMethod;
    private Method getMaxPointMethod;
    private Method getPriorityMethod;
    private Method getFlagMethod;
    private Method getXMethod;
    private Method getZMethod;
    private Object allowState;
    private Object denyState;

    public WorldGuardHook() {
        try {
            Class.forName("com.sk89q.worldguard.WorldGuard");
            this.available = true;
        } catch (ClassNotFoundException e) {
            this.available = false;
        }
    }

    public static void registerClaimFlag() {
        try {
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object wg = wgClass.getMethod("getInstance").invoke(null);
            Object registry = wgClass.getMethod("getFlagRegistry").invoke(wg);

            Method getMethod = registry.getClass().getMethod("get", String.class);
            Object existing = getMethod.invoke(registry, "claim");
            if (existing != null) {
                CLAIM_FLAG = existing;
                return;
            }

            Class<?> stateFlagClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
            Constructor<?> constructor = stateFlagClass.getConstructor(String.class, boolean.class);
            // Default true: ALLOW
            Object flag = constructor.newInstance("claim", true);

            Method registerMethod = registry.getClass().getMethod("register", Class.forName("com.sk89q.worldguard.protection.flags.Flag"));
            registerMethod.invoke(registry, flag);
            CLAIM_FLAG = flag;
            Bukkit.getLogger().info("[Ztone] WorldGuard Custom-Flag 'claim' erfolgreich registriert (Default: ALLOW)!");
        } catch (ClassNotFoundException ignored) {
            // WorldGuard nicht auf dem Server installiert
        } catch (Throwable t) {
            try {
                Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
                Object wg = wgClass.getMethod("getInstance").invoke(null);
                Object registry = wgClass.getMethod("getFlagRegistry").invoke(wg);
                Method getMethod = registry.getClass().getMethod("get", String.class);
                CLAIM_FLAG = getMethod.invoke(registry, "claim");
            } catch (Throwable ignored) {}
            if (CLAIM_FLAG == null) {
                Bukkit.getLogger().warning("[Ztone] Fehler beim Registrieren des WorldGuard-Flags 'claim': " + t.getMessage());
            }
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
            getPriorityMethod = protectedRegionClass.getMethod("getPriority");

            Class<?> flagClass = Class.forName("com.sk89q.worldguard.protection.flags.Flag");
            getFlagMethod = protectedRegionClass.getMethod("getFlag", flagClass);

            Class<?> vectorClass = getMinPointMethod.getReturnType();
            getXMethod = vectorClass.getMethod("getX");
            getZMethod = vectorClass.getMethod("getZ");

            Class<?> stateClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag$State");
            Method valueOf = stateClass.getMethod("valueOf", String.class);
            allowState = valueOf.invoke(null, "ALLOW");
            denyState = valueOf.invoke(null, "DENY");

            if (CLAIM_FLAG == null) {
                try {
                    Object registry = wgClass.getMethod("getFlagRegistry").invoke(wg);
                    Method getMethod = registry.getClass().getMethod("get", String.class);
                    CLAIM_FLAG = getMethod.invoke(registry, "claim");
                } catch (Throwable ignored) {}
            }

            reflectionResolved = true;
        } catch (Throwable t) {
            this.available = false;
        }
    }

    /**
     * Prüft, ob der angegebene Chunk durch eine WorldGuard-Region mit 'claim: DENY' geschützt ist.
     * Standardmäßig ist claim auf ALLOW gesetzt (die alte pauschale Regionensperre ist entfernt).
     * Nur Regionen, bei denen 'claim' explizit auf DENY gesetzt ist, blockieren das Claiming.
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
        if (CLAIM_FLAG == null) {
            return false;
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

            Integer highestPriority = null;
            Object highestPriorityState = null;

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

                // 2D AABB Überschneidung mit dem Chunk prüfen
                if (chunkMinX <= regMaxX && chunkMaxX >= regMinX && chunkMinZ <= regMaxZ && chunkMaxZ >= regMinZ) {
                    Object state = getFlagMethod.invoke(region, CLAIM_FLAG);
                    if (state != null) {
                        int priority = ((Number) getPriorityMethod.invoke(region)).intValue();
                        if (highestPriority == null || priority > highestPriority) {
                            highestPriority = priority;
                            highestPriorityState = state;
                        } else if (priority == highestPriority && state == denyState) {
                            highestPriorityState = denyState;
                        }
                    }
                }
            }

            if (highestPriorityState != null) {
                return highestPriorityState == denyState;
            }

            // Falls keine schneidende Region das Flag gesetzt hat, prüfe __global__
            Object globalRegion = regions.get("__global__");
            if (globalRegion != null) {
                Object globalState = getFlagMethod.invoke(globalRegion, CLAIM_FLAG);
                if (globalState != null) {
                    return globalState == denyState;
                }
            }

            return false;
        } catch (Throwable ignored) {
        }
        return false;
    }
}
