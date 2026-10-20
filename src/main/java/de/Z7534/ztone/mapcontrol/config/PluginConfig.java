package de.Z7534.ztone.mapcontrol.config;

import de.Z7534.ztone.mapcontrol.MapControl;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

public class PluginConfig {

    private final MapControl plugin;

    // General
    private int sendDelayTicks;
    private String bypassPermission;
    private boolean debug;

    // Xaero
    private boolean xaeroEnabled;
    private boolean xaeroDisableRadar;
    private boolean xaeroDisableCaveMode;
    private boolean xaeroAllowNetherCaveMode;
    private boolean xaeroDisableMinimap;

    // JourneyMap
    private boolean journeyMapEnabled;
    private boolean journeyMapAllowSurfaceMapping;
    private boolean journeyMapAllowCaveMapping;
    private boolean journeyMapAllowRadar;
    private boolean journeyMapAllowPlayerRadar;
    private boolean journeyMapAllowMobRadar;
    private boolean journeyMapAllowAnimalRadar;
    private boolean journeyMapAllowVillagerRadar;
    private boolean journeyMapAllowTeleport;
    private boolean journeyMapShowWaypoints;
    private boolean journeyMapAllowDeathPoints;

    // VoxelMap
    private boolean voxelMapEnabled;
    private boolean voxelMapDisableRadar;
    private boolean voxelMapDisableCaveMode;

    public PluginConfig(MapControl plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        this.sendDelayTicks = config.getInt("general.send-delay-ticks", 15);
        this.bypassPermission = config.getString("general.bypass-permission", "mapcontrol.bypass");
        this.debug = config.getBoolean("general.debug", false);

        // Xaero
        this.xaeroEnabled = config.getBoolean("xaero.enabled", true);
        this.xaeroDisableRadar = config.getBoolean("xaero.disable-radar", true);
        this.xaeroDisableCaveMode = config.getBoolean("xaero.disable-cave-mode", true);
        this.xaeroAllowNetherCaveMode = config.getBoolean("xaero.allow-nether-cave-mode", false);
        this.xaeroDisableMinimap = config.getBoolean("xaero.disable-minimap", false);

        // JourneyMap
        this.journeyMapEnabled = config.getBoolean("journeymap.enabled", true);
        this.journeyMapAllowSurfaceMapping = config.getBoolean("journeymap.allow-surface-mapping", true);
        this.journeyMapAllowCaveMapping = config.getBoolean("journeymap.allow-cave-mapping", false);
        this.journeyMapAllowRadar = config.getBoolean("journeymap.allow-radar", false);
        this.journeyMapAllowPlayerRadar = config.getBoolean("journeymap.allow-player-radar", false);
        this.journeyMapAllowMobRadar = config.getBoolean("journeymap.allow-mob-radar", false);
        this.journeyMapAllowAnimalRadar = config.getBoolean("journeymap.allow-animal-radar", false);
        this.journeyMapAllowVillagerRadar = config.getBoolean("journeymap.allow-villager-radar", false);
        this.journeyMapAllowTeleport = config.getBoolean("journeymap.allow-teleport", false);
        this.journeyMapShowWaypoints = config.getBoolean("journeymap.show-waypoints", true);
        this.journeyMapAllowDeathPoints = config.getBoolean("journeymap.allow-death-points", true);

        // VoxelMap
        this.voxelMapEnabled = config.getBoolean("voxelmap.enabled", true);
        this.voxelMapDisableRadar = config.getBoolean("voxelmap.disable-radar", true);
        this.voxelMapDisableCaveMode = config.getBoolean("voxelmap.disable-cave-mode", true);
    }

    public int getSendDelayTicks() {
        return sendDelayTicks;
    }

    public String getBypassPermission() {
        return bypassPermission;
    }

    public boolean isDebug() {
        return debug;
    }

    // Xaero getters with optional world override
    public boolean isXaeroEnabled(World world) {
        if (!xaeroEnabled) return false;
        if (world != null && plugin.getConfig().isSet("worlds." + world.getName() + ".xaero.enabled")) {
            return plugin.getConfig().getBoolean("worlds." + world.getName() + ".xaero.enabled");
        }
        return xaeroEnabled;
    }

    public boolean isXaeroDisableRadar(World world) {
        if (world != null && plugin.getConfig().isSet("worlds." + world.getName() + ".xaero.disable-radar")) {
            return plugin.getConfig().getBoolean("worlds." + world.getName() + ".xaero.disable-radar");
        }
        return xaeroDisableRadar;
    }

    public boolean isXaeroDisableCaveMode(World world) {
        if (world != null && plugin.getConfig().isSet("worlds." + world.getName() + ".xaero.disable-cave-mode")) {
            return plugin.getConfig().getBoolean("worlds." + world.getName() + ".xaero.disable-cave-mode");
        }
        return xaeroDisableCaveMode;
    }

    public boolean isXaeroAllowNetherCaveMode(World world) {
        if (world != null && plugin.getConfig().isSet("worlds." + world.getName() + ".xaero.allow-nether-cave-mode")) {
            return plugin.getConfig().getBoolean("worlds." + world.getName() + ".xaero.allow-nether-cave-mode");
        }
        return xaeroAllowNetherCaveMode;
    }

    public boolean isXaeroDisableMinimap(World world) {
        if (world != null && plugin.getConfig().isSet("worlds." + world.getName() + ".xaero.disable-minimap")) {
            return plugin.getConfig().getBoolean("worlds." + world.getName() + ".xaero.disable-minimap");
        }
        return xaeroDisableMinimap;
    }

    // JourneyMap getters with optional world override
    public boolean isJourneyMapEnabled(World world) {
        if (!journeyMapEnabled) return false;
        if (world != null && plugin.getConfig().isSet("worlds." + world.getName() + ".journeymap.enabled")) {
            return plugin.getConfig().getBoolean("worlds." + world.getName() + ".journeymap.enabled");
        }
        return journeyMapEnabled;
    }

    public boolean isJourneyMapAllowSurfaceMapping(World world) {
        if (world != null && plugin.getConfig().isSet("worlds." + world.getName() + ".journeymap.allow-surface-mapping")) {
            return plugin.getConfig().getBoolean("worlds." + world.getName() + ".journeymap.allow-surface-mapping");
        }
        return journeyMapAllowSurfaceMapping;
    }

    public boolean isJourneyMapAllowCaveMapping(World world) {
        if (world != null && plugin.getConfig().isSet("worlds." + world.getName() + ".journeymap.allow-cave-mapping")) {
            return plugin.getConfig().getBoolean("worlds." + world.getName() + ".journeymap.allow-cave-mapping");
        }
        return journeyMapAllowCaveMapping;
    }

    public boolean isJourneyMapAllowRadar(World world) {
        if (world != null && plugin.getConfig().isSet("worlds." + world.getName() + ".journeymap.allow-radar")) {
            return plugin.getConfig().getBoolean("worlds." + world.getName() + ".journeymap.allow-radar");
        }
        return journeyMapAllowRadar;
    }

    public boolean isJourneyMapAllowPlayerRadar(World world) {
        if (world != null && plugin.getConfig().isSet("worlds." + world.getName() + ".journeymap.allow-player-radar")) {
            return plugin.getConfig().getBoolean("worlds." + world.getName() + ".journeymap.allow-player-radar");
        }
        return journeyMapAllowPlayerRadar;
    }

    public boolean isJourneyMapAllowMobRadar(World world) {
        if (world != null && plugin.getConfig().isSet("worlds." + world.getName() + ".journeymap.allow-mob-radar")) {
            return plugin.getConfig().getBoolean("worlds." + world.getName() + ".journeymap.allow-mob-radar");
        }
        return journeyMapAllowMobRadar;
    }

    public boolean isJourneyMapAllowAnimalRadar(World world) {
        if (world != null && plugin.getConfig().isSet("worlds." + world.getName() + ".journeymap.allow-animal-radar")) {
            return plugin.getConfig().getBoolean("worlds." + world.getName() + ".journeymap.allow-animal-radar");
        }
        return journeyMapAllowAnimalRadar;
    }

    public boolean isJourneyMapAllowVillagerRadar(World world) {
        if (world != null && plugin.getConfig().isSet("worlds." + world.getName() + ".journeymap.allow-villager-radar")) {
            return plugin.getConfig().getBoolean("worlds." + world.getName() + ".journeymap.allow-villager-radar");
        }
        return journeyMapAllowVillagerRadar;
    }

    public boolean isJourneyMapAllowTeleport(World world) {
        if (world != null && plugin.getConfig().isSet("worlds." + world.getName() + ".journeymap.allow-teleport")) {
            return plugin.getConfig().getBoolean("worlds." + world.getName() + ".journeymap.allow-teleport");
        }
        return journeyMapAllowTeleport;
    }

    public boolean isJourneyMapShowWaypoints(World world) {
        if (world != null && plugin.getConfig().isSet("worlds." + world.getName() + ".journeymap.show-waypoints")) {
            return plugin.getConfig().getBoolean("worlds." + world.getName() + ".journeymap.show-waypoints");
        }
        return journeyMapShowWaypoints;
    }

    public boolean isJourneyMapAllowDeathPoints(World world) {
        if (world != null && plugin.getConfig().isSet("worlds." + world.getName() + ".journeymap.allow-death-points")) {
            return plugin.getConfig().getBoolean("worlds." + world.getName() + ".journeymap.allow-death-points");
        }
        return journeyMapAllowDeathPoints;
    }

    // VoxelMap getters with optional world override
    public boolean isVoxelMapEnabled(World world) {
        if (!voxelMapEnabled) return false;
        if (world != null && plugin.getConfig().isSet("worlds." + world.getName() + ".voxelmap.enabled")) {
            return plugin.getConfig().getBoolean("worlds." + world.getName() + ".voxelmap.enabled");
        }
        return voxelMapEnabled;
    }

    public boolean isVoxelMapDisableRadar(World world) {
        if (world != null && plugin.getConfig().isSet("worlds." + world.getName() + ".voxelmap.disable-radar")) {
            return plugin.getConfig().getBoolean("worlds." + world.getName() + ".voxelmap.disable-radar");
        }
        return voxelMapDisableRadar;
    }

    public boolean isVoxelMapDisableCaveMode(World world) {
        if (world != null && plugin.getConfig().isSet("worlds." + world.getName() + ".voxelmap.disable-cave-mode")) {
            return plugin.getConfig().getBoolean("worlds." + world.getName() + ".voxelmap.disable-cave-mode");
        }
        return voxelMapDisableCaveMode;
    }
}
