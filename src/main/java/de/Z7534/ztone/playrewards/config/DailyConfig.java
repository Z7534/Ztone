package de.Z7534.ztone.playrewards.config;

import de.Z7534.ztone.playrewards.model.RewardItem;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class DailyConfig {

    private final File file;
    private FileConfiguration config;
    private String title;
    private Material frameGlass;
    private final Map<String, RewardItem> rewards = new LinkedHashMap<>();

    public DailyConfig(File folder) {
        this.file = new File(folder, "rewards/daily.yml");
    }

    public void load() {
        rewards.clear();
        if (!file.exists()) {
            return;
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        this.title = config.getString("title", "<gradient:#FFAA00:#FF5500><bold>Tägliche Belohnungen</bold></gradient>");
        String glassName = config.getString("frame_glass", "ORANGE_STAINED_GLASS_PANE");
        Material glassMat = Material.matchMaterial(glassName);
        this.frameGlass = glassMat != null ? glassMat : Material.ORANGE_STAINED_GLASS_PANE;

        ConfigurationSection section = config.getConfigurationSection("rewards");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection itemSec = section.getConfigurationSection(key);
                if (itemSec == null) continue;

                int slot = itemSec.getInt("slot", -1);
                long reqPlaytimeSec = itemSec.getLong("required_playtime_minutes", 0) * 60L;
                String name = itemSec.getString("name", key);
                String matName = itemSec.getString("material", "CHEST");
                Material mat = Material.matchMaterial(matName);
                if (mat == null) mat = Material.CHEST;

                String headTexture = itemSec.getString("custom_head_texture", "");
                List<String> lore = itemSec.getStringList("lore_preview");
                List<String> commands = itemSec.getStringList("commands");

                RewardItem item = new RewardItem(key, name, mat, headTexture, slot, reqPlaytimeSec, 0, lore, commands);
                rewards.put(key, item);
            }
        }
    }

    public String getTitle() {
        return title;
    }

    public Material getFrameGlass() {
        return frameGlass;
    }

    public Map<String, RewardItem> getRewards() {
        return rewards;
    }

    public RewardItem getReward(String id) {
        return rewards.get(id);
    }
}
