package de.Z7534.ztone.playrewards.config;

import de.Z7534.ztone.playrewards.model.RewardItem;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class StreakConfig {

    private final File file;
    private FileConfiguration config;
    private String title;
    private Material frameGlass;
    private final Map<Integer, RewardItem> streakRewards = new TreeMap<>();

    public StreakConfig(File folder) {
        this.file = new File(folder, "rewards/streak.yml");
    }

    public void load() {
        streakRewards.clear();
        if (!file.exists()) {
            return;
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        this.title = config.getString("title", "<gradient:#55FF55:#00AA00><bold>Tägliche Streak</bold></gradient>");
        String glassName = config.getString("frame_glass", "LIME_STAINED_GLASS_PANE");
        Material glassMat = Material.matchMaterial(glassName);
        this.frameGlass = glassMat != null ? glassMat : Material.LIME_STAINED_GLASS_PANE;

        ConfigurationSection section = config.getConfigurationSection("streaks");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    int day = Integer.parseInt(key);
                    ConfigurationSection itemSec = section.getConfigurationSection(key);
                    if (itemSec == null) continue;

                    int slot = itemSec.getInt("slot", -1);
                    String name = itemSec.getString("name", "Tag " + day);
                    String matName = itemSec.getString("material", "IRON_SWORD");
                    Material mat = Material.matchMaterial(matName);
                    if (mat == null) mat = Material.IRON_SWORD;

                    String headTexture = itemSec.getString("custom_head_texture", "");
                    List<String> lore = itemSec.getStringList("lore_preview");
                    List<String> commands = itemSec.getStringList("commands");

                    RewardItem item = new RewardItem(String.valueOf(day), name, mat, headTexture, slot, 0, day, lore, commands);
                    streakRewards.put(day, item);
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    public String getTitle() {
        return title;
    }

    public Material getFrameGlass() {
        return frameGlass;
    }

    public Map<Integer, RewardItem> getStreakRewards() {
        return streakRewards;
    }

    public RewardItem getReward(int day) {
        return streakRewards.get(day);
    }
}
