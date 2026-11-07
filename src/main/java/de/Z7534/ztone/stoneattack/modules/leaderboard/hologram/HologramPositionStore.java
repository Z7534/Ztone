package de.Z7534.ztone.stoneattack.modules.leaderboard.hologram;

import de.Z7534.ztone.stoneattack.Stoneattack;
import de.Z7534.ztone.stoneattack.modules.leaderboard.LeaderboardType;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Persistiert die Positionen aller vier moeglichen Hologramme sowie
 * Resourcepack-Einstellungen in holograms.yml.
 */
public class HologramPositionStore {

    private final Stoneattack plugin;
    private File file;
    private final Map<LeaderboardType, HologramDefinition> definitions = new EnumMap<>(LeaderboardType.class);

    private boolean resourcePackEnabled = true;
    private boolean fallbackOnDecline = true;
    private String bedrockMode = "AUTO";
    private boolean bedrockSafeText = true;
    private double maxWidthBlocks = 3.0;
    private int modelBackground = 1000;
    private int modelButtonIdle = 1001;
    private int modelButtonHover = 1002;
    private int modelButtonActive = 1003;

    private String modelBackgroundKey = "stoneattack:item/leaderboard_bg";
    private String modelButtonIdleKey = "stoneattack:item/button_idle";
    private String modelButtonHoverKey = "stoneattack:item/button_hover";
    private String modelButtonActiveKey = "stoneattack:item/button_active";

    // Modularer Hintergrund (Kopf / Reihe / Fueller / Fuss)
    private int modelBgTop = 1004;
    private int modelBgRow = 1005;
    private int modelBgFiller = 1006;
    private int modelBgFooter = 1007;
    private String modelBgTopKey = "stoneattack:leaderboard_bg_top";
    private String modelBgRowKey = "stoneattack:leaderboard_bg_row";
    private String modelBgFillerKey = "stoneattack:leaderboard_bg_filler";
    private String modelBgFooterKey = "stoneattack:leaderboard_bg_footer";

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

        // Resourcepack-Einstellungen
        if (yaml.isConfigurationSection("resourcepack")) {
            ConfigurationSection rp = yaml.getConfigurationSection("resourcepack");
            resourcePackEnabled = rp.getBoolean("enabled", true);
            fallbackOnDecline = rp.getBoolean("fallback-on-decline", true);
            bedrockMode = rp.getString("bedrock-mode", "AUTO");
            bedrockSafeText = rp.getBoolean("bedrock-safe-text", true);
            maxWidthBlocks = rp.getDouble("max-width-blocks", 3.0);
            if (rp.isConfigurationSection("models")) {
                ConfigurationSection m = rp.getConfigurationSection("models");
                modelBackground = m.getInt("background", 1000);
                modelButtonIdle = m.getInt("button-idle", 1001);
                modelButtonHover = m.getInt("button-hover", 1002);
                modelButtonActive = m.getInt("button-active", 1003);
                modelBackgroundKey = m.getString("background-key", "stoneattack:item/leaderboard_bg");
                modelButtonIdleKey = m.getString("button-idle-key", "stoneattack:item/button_idle");
                modelButtonHoverKey = m.getString("button-hover-key", "stoneattack:item/button_hover");
                modelButtonActiveKey = m.getString("button-active-key", "stoneattack:item/button_active");
                modelBgTop = m.getInt("background-top", 1004);
                modelBgRow = m.getInt("background-row", 1005);
                modelBgFiller = m.getInt("background-filler", 1006);
                modelBgFooter = m.getInt("background-footer", 1007);
                modelBgTopKey = m.getString("background-top-key", "stoneattack:leaderboard_bg_top");
                modelBgRowKey = m.getString("background-row-key", "stoneattack:leaderboard_bg_row");
                modelBgFillerKey = m.getString("background-filler-key", "stoneattack:leaderboard_bg_filler");
                modelBgFooterKey = m.getString("background-footer-key", "stoneattack:leaderboard_bg_footer");
            }
        }

        for (LeaderboardType type : LeaderboardType.values()) {
            String path = type.key();
            if (!yaml.isConfigurationSection(path)) {
                continue;
            }
            if (!yaml.contains(path + ".facing")) {
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
                    yaml.getDouble(path + ".height", defaultHeight())
            ));
        }
    }

    public void save() {
        YamlConfiguration yaml = (file != null && file.exists())
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();

        // Resourcepack-Abschnitt sicherstellen
        yaml.set("resourcepack.enabled", resourcePackEnabled);
        yaml.set("resourcepack.fallback-on-decline", fallbackOnDecline);
        yaml.set("resourcepack.bedrock-mode", bedrockMode);
        yaml.set("resourcepack.bedrock-safe-text", bedrockSafeText);
        yaml.set("resourcepack.max-width-blocks", maxWidthBlocks);
        yaml.set("resourcepack.models.background", modelBackground);
        yaml.set("resourcepack.models.button-idle", modelButtonIdle);
        yaml.set("resourcepack.models.button-hover", modelButtonHover);
        yaml.set("resourcepack.models.button-active", modelButtonActive);
        yaml.set("resourcepack.models.background-key", modelBackgroundKey);
        yaml.set("resourcepack.models.button-idle-key", modelButtonIdleKey);
        yaml.set("resourcepack.models.button-hover-key", modelButtonHoverKey);
        yaml.set("resourcepack.models.button-active-key", modelButtonActiveKey);
        yaml.set("resourcepack.models.background-top", modelBgTop);
        yaml.set("resourcepack.models.background-row", modelBgRow);
        yaml.set("resourcepack.models.background-filler", modelBgFiller);
        yaml.set("resourcepack.models.background-footer", modelBgFooter);
        yaml.set("resourcepack.models.background-top-key", modelBgTopKey);
        yaml.set("resourcepack.models.background-row-key", modelBgRowKey);
        yaml.set("resourcepack.models.background-filler-key", modelBgFillerKey);
        yaml.set("resourcepack.models.background-footer-key", modelBgFooterKey);

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
            plugin.getLogger().warning("[Leaderboard] Konnte holograms.yml nicht speichern: " + e.getMessage());
        }
    }

    public double defaultHeight() {
        return plugin.getConfigManager().get().getDouble("leaderboard.layout.height-blocks",
                HologramLayout.DEFAULT_HEIGHT);
    }

    public HologramDefinition get(LeaderboardType type) {
        return definitions.get(type);
    }

    public void set(LeaderboardType type, HologramDefinition def) {
        definitions.put(type, def);
        save();
    }

    public void remove(LeaderboardType type) {
        definitions.remove(type);
        save();
    }

    public Map<LeaderboardType, HologramDefinition> all() {
        return definitions;
    }

    public boolean isResourcePackEnabled() {
        return resourcePackEnabled;
    }

    public boolean isFallbackOnDecline() {
        return fallbackOnDecline;
    }

    public String getBedrockMode() {
        return bedrockMode;
    }

    public boolean isBedrockSafeText() {
        return bedrockSafeText;
    }

    public double getMaxWidthBlocks() {
        return maxWidthBlocks;
    }

    public int getModelBackground() {
        return modelBackground;
    }

    public int getModelButtonIdle() {
        return modelButtonIdle;
    }

    public int getModelButtonHover() {
        return modelButtonHover;
    }

    public int getModelButtonActive() {
        return modelButtonActive;
    }

    public String getModelBackgroundKey() {
        return modelBackgroundKey;
    }

    public String getModelButtonIdleKey() {
        return modelButtonIdleKey;
    }

    public String getModelButtonHoverKey() {
        return modelButtonHoverKey;
    }

    public String getModelButtonActiveKey() {
        return modelButtonActiveKey;
    }

    public int getModelBgTop() {
        return modelBgTop;
    }

    public int getModelBgRow() {
        return modelBgRow;
    }

    public int getModelBgFiller() {
        return modelBgFiller;
    }

    public int getModelBgFooter() {
        return modelBgFooter;
    }

    public String getModelBgTopKey() {
        return modelBgTopKey;
    }

    public String getModelBgRowKey() {
        return modelBgRowKey;
    }

    public String getModelBgFillerKey() {
        return modelBgFillerKey;
    }

    public String getModelBgFooterKey() {
        return modelBgFooterKey;
    }
}
