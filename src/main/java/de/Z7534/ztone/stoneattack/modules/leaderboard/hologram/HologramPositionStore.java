package de.Z7534.ztone.stoneattack.modules.leaderboard.hologram;

import de.Z7534.ztone.stoneattack.Stoneattack;
import de.Z7534.ztone.stoneattack.modules.leaderboard.LeaderboardType;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Persistiert die Positionen aller vier moeglichen Hologramme in holograms.yml.
 *
 * Format (pro Leaderboard):
 * <pre>
 * playtime:
 *   world: world
 *   x: 10        # Wandblock
 *   y: 64
 *   z: -5
 *   facing: NORTH  # Seite des Blocks, an der das Board haengt
 *   height: 3.0    # Gesamthoehe in Bloecken (-> Anzahl der Eintragszeilen)
 * </pre>
 */
public class HologramPositionStore {

    private final Stoneattack plugin;
    private File file;
    private final Map<LeaderboardType, HologramDefinition> definitions = new EnumMap<>(LeaderboardType.class);

    public HologramPositionStore(Stoneattack plugin) {
        this.plugin = plugin;
    }

    public void load() {
        file = new File(plugin.getDataFolder(), "holograms.yml");
        definitions.clear();
        if (!file.exists()) {
            try {
                plugin.saveResource("holograms.yml", false);
            } catch (Exception ignored) {
            }
            if (!file.exists()) {
                return;
            }
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (LeaderboardType type : LeaderboardType.values()) {
            String path = type.key();
            if (!yaml.isConfigurationSection(path)) {
                continue;
            }
            if (!yaml.contains(path + ".facing")) {
                // Altes Format (freie Position + Yaw/Pitch, Hologramm drehte sich zum Spieler)
                plugin.getLogger().warning("[Leaderboard] '" + path + "' in holograms.yml stammt aus dem alten Format "
                        + "(freie Position). Bitte mit /leaderboard place " + path + " neu an einer Wand platzieren.");
                continue;
            }
            BlockFace facing;
            try {
                facing = BlockFace.valueOf(yaml.getString(path + ".facing", "SOUTH").toUpperCase());
            } catch (IllegalArgumentException e) {
                facing = BlockFace.SOUTH;
            }
            if (!HologramDefinition.isWallFace(facing)) {
                facing = BlockFace.SOUTH;
            }
            definitions.put(type, new HologramDefinition(
                    type,
                    yaml.getString(path + ".world"),
                    yaml.getInt(path + ".x"),
                    yaml.getInt(path + ".y"),
                    yaml.getInt(path + ".z"),
                    facing,
                    // Fehlt der Eintrag (Datei aus einer aelteren Version), gilt die Standardhoehe.
                    yaml.getDouble(path + ".height", defaultHeight())
            ));
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<LeaderboardType, HologramDefinition> entry : definitions.entrySet()) {
            String path = entry.getKey().key();
            HologramDefinition def = entry.getValue();
            yaml.set(path + ".world", def.getWorldName());
            yaml.set(path + ".x", def.getX());
            yaml.set(path + ".y", def.getY());
            yaml.set(path + ".z", def.getZ());
            yaml.set(path + ".facing", def.getFacing().name());
            yaml.set(path + ".height", def.getHeightBlocks());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte holograms.yml nicht speichern: " + e.getMessage());
        }
    }

    /** Standardhoehe fuer neu platzierte Boards (config.yml, Standard 3 Bloecke). */
    public double defaultHeight() {
        return HologramDefinition.clampHeight(plugin.getConfigManager().get()
                .getDouble("leaderboard.default-height-blocks", HologramLayout.DEFAULT_HEIGHT));
    }

    public HologramDefinition get(LeaderboardType type) {
        return definitions.get(type);
    }

    public void set(LeaderboardType type, HologramDefinition definition) {
        definitions.put(type, definition);
        save();
    }

    public void remove(LeaderboardType type) {
        definitions.remove(type);
        save();
    }

    public Map<LeaderboardType, HologramDefinition> all() {
        return definitions;
    }
}
