package de.Z7534.ztone.pvpmanager.managers;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class OverrideManager {

    private final Pvpmanager plugin;

    private final Set<UUID> overriding = ConcurrentHashMap.newKeySet();

    private final Map<String, Boolean> dimensionDefaultEnabled = new ConcurrentHashMap<>();

    private final Map<String, Boolean> dimensionForced = new ConcurrentHashMap<>();

    public OverrideManager(Pvpmanager plugin) {
        this.plugin = plugin;
        loadDimensionSettings();
        updateWorldMetadata();
    }

    public boolean toggle(UUID uuid) {
        if (overriding.contains(uuid)) {
            overriding.remove(uuid);
            return false;
        }
        overriding.add(uuid);
        return true;
    }

    public boolean isOverriding(UUID uuid) {
        return overriding.contains(uuid);
    }

    public void setDimensionDefault(String dimension, boolean enabled, boolean forced) {
        String key = normalizeDimensionKey(dimension);
        dimensionDefaultEnabled.put(key, enabled);
        dimensionForced.put(key, forced);
        saveDimensionSetting(key, enabled, forced);
        updateWorldMetadata();
    }

    public Set<String> getConfiguredDimensions() {
        Set<String> set = new LinkedHashSet<>(dimensionDefaultEnabled.keySet());
        set.addAll(dimensionForced.keySet());
        return set;
    }

    public void setAllDimensionsDefault(boolean enabled, boolean forced) {
        Set<String> dimensions = new LinkedHashSet<>();
        dimensions.add("minecraft:overworld");
        dimensions.add("minecraft:the_nether");
        dimensions.add("minecraft:the_end");
        for (World world : Bukkit.getWorlds()) {
            dimensions.add(world.getName());
            dimensions.add("minecraft:" + world.getName().toLowerCase());
            dimensions.add(getDimensionKey(world));
        }
        dimensions.addAll(getConfiguredDimensions());

        for (String dim : dimensions) {
            String key = normalizeDimensionKey(dim);
            dimensionDefaultEnabled.put(key, enabled);
            dimensionForced.put(key, forced);
            saveDimensionSetting(key, enabled, forced);
        }
        updateWorldMetadata();
    }

    public void updateWorldMetadata() {
        for (World world : Bukkit.getWorlds()) {
            updateWorldMetadata(world);
        }
    }

    public void updateWorldMetadata(World world) {
        if (world == null) return;
        boolean enabled = isDimensionPvPEnabled(world);
        boolean forced = isDimensionPvPForced(world);
        world.setMetadata("ztone_pvp", new FixedMetadataValue(plugin.getPlugin(), enabled));
        world.setMetadata("ztone_pvp_forced", new FixedMetadataValue(plugin.getPlugin(), forced));
        world.setMetadata("ztone_pvp_force", new FixedMetadataValue(plugin.getPlugin(), forced));
        world.setMetadata("ztone_force", new FixedMetadataValue(plugin.getPlugin(), forced));
        world.setMetadata("ztone_forced", new FixedMetadataValue(plugin.getPlugin(), forced));
    }

    public boolean hasDimensionDefault(String dimension) {
        if (dimension == null) return false;
        String key = normalizeDimensionKey(dimension);
        return dimensionDefaultEnabled.containsKey(key) || dimensionDefaultEnabled.containsKey(dimension);
    }

    public boolean hasDimensionDefault(World world) {
        if (world == null) return false;
        return hasDimensionDefault(getDimensionKey(world));
    }

    public boolean getDimensionDefault(String dimension) {
        if (dimension == null) {
            return getDefaultPvPFallback();
        }
        String key = normalizeDimensionKey(dimension);
        Boolean def = dimensionDefaultEnabled.get(key);
        if (def == null && !key.equals(dimension)) {
            def = dimensionDefaultEnabled.get(dimension);
        }
        return def != null ? def : getDefaultPvPFallback();
    }

    private boolean getDefaultPvPFallback() {
        if (plugin.getConfigManager() != null && plugin.getConfigManager().config() != null) {
            return plugin.getConfigManager().config().getBoolean("PvP Toggle.Default PvP", false);
        }
        return false;
    }

    public boolean isDimensionForced(String dimension) {
        if (dimension == null) return false;
        String key = normalizeDimensionKey(dimension);
        Boolean forced = dimensionForced.get(key);
        if (forced == null && !key.equals(dimension)) {
            forced = dimensionForced.get(dimension);
        }
        return forced != null && forced;
    }

    public boolean isDimensionForced(World world) {
        if (world == null) return false;
        return isDimensionForced(getDimensionKey(world));
    }

    public boolean isDimensionPvPForced(String dimension) {
        return isDimensionForced(dimension);
    }

    public boolean isDimensionPvPForced(World world) {
        return isDimensionForced(world);
    }

    public boolean isDimensionPvPEnabled(String dimension) {
        return getDimensionDefault(dimension);
    }

    public boolean isDimensionPvPEnabled(World world) {
        if (world == null) return true;
        return getDimensionDefault(getDimensionKey(world));
    }

    public boolean getDimensionPvPStatus(String dimension) {
        return isDimensionPvPEnabled(dimension);
    }

    public boolean getDimensionPvPStatus(World world) {
        return isDimensionPvPEnabled(world);
    }

    public String normalizeDimensionKey(String input) {
        if (input == null || input.isBlank()) {
            return "minecraft:overworld";
        }
        String trimmed = input.trim();
        String lower = trimmed.toLowerCase();

        World world = Bukkit.getWorld(trimmed);
        if (world != null) {
            return getDimensionKey(world);
        }

        switch (lower) {
            case "minecraft:overworld":
            case "overworld":
            case "world":
            case "normal":
                return "minecraft:overworld";
            case "minecraft:the_nether":
            case "the_nether":
            case "nether":
            case "world_nether":
                return "minecraft:the_nether";
            case "minecraft:the_end":
            case "the_end":
            case "end":
            case "world_the_end":
                return "minecraft:the_end";
            default:
                if (dimensionDefaultEnabled.containsKey(trimmed)) {
                    return trimmed;
                }
                if (dimensionDefaultEnabled.containsKey(lower)) {
                    return lower;
                }
                return trimmed;
        }
    }

    public String getDimensionKey(World world) {
        if (world == null) return "minecraft:overworld";
        String worldName = world.getName();

        if (dimensionDefaultEnabled.containsKey(worldName)) {
            return worldName;
        }
        if (dimensionDefaultEnabled.containsKey("minecraft:" + worldName.toLowerCase())) {
            return "minecraft:" + worldName.toLowerCase();
        }

        if (worldName.equalsIgnoreCase("world")) {
            return "minecraft:overworld";
        }
        if (worldName.equalsIgnoreCase("world_nether")) {
            return "minecraft:the_nether";
        }
        if (worldName.equalsIgnoreCase("world_the_end")) {
            return "minecraft:the_end";
        }

        switch (world.getEnvironment()) {
            case NORMAL:
                return "minecraft:overworld";
            case NETHER:
                return "minecraft:the_nether";
            case THE_END:
                return "minecraft:the_end";
            default:
                return world.getName();
        }
    }

    private void loadDimensionSettings() {
        if (plugin.getConfigManager() == null || plugin.getConfigManager().config() == null) {
            return;
        }
        var cfg = plugin.getConfigManager().config();
        if (!cfg.isConfigurationSection("DimensionSettings")) {
            return;
        }
        var section = cfg.getConfigurationSection("DimensionSettings");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            var sub = section.getConfigurationSection(key);
            if (sub != null) {
                boolean enabled = sub.getBoolean("enabled", true);
                boolean forced = sub.getBoolean("forced", false);
                dimensionDefaultEnabled.put(key, enabled);
                dimensionForced.put(key, forced);
            }
        }
    }

    private void saveDimensionSetting(String dimension, boolean enabled, boolean forced) {
        if (plugin.getConfigManager() == null || plugin.getConfigManager().config() == null) {
            return;
        }
        var cfg = plugin.getConfigManager().config();
        cfg.set("DimensionSettings." + dimension + ".enabled", enabled);
        cfg.set("DimensionSettings." + dimension + ".forced", forced);
        plugin.saveConfig();
    }
}
