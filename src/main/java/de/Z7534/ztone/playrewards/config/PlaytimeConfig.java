package de.Z7534.ztone.playrewards.config;

import de.Z7534.ztone.playrewards.model.RewardItem;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class PlaytimeConfig {

    private final File file;
    private FileConfiguration config;
    private String title;
    private Material frameGlass;
    private final List<RewardItem> playtimeRewards = new ArrayList<>();

    public PlaytimeConfig(File folder) {
        this.file = new File(folder, "rewards/playtime.yml");
    }

    public void load() {
        playtimeRewards.clear();
        if (!file.exists()) {
            return;
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        this.title = config.getString("title", "<gradient:#33AAFF:#0055FF><bold>Spielzeit-Belohnungen</bold></gradient> <gray>(Seite %page%/%max_page%)");
        String glassName = config.getString("frame_glass", "LIGHT_BLUE_STAINED_GLASS_PANE");
        Material glassMat = Material.matchMaterial(glassName);
        this.frameGlass = glassMat != null ? glassMat : Material.LIGHT_BLUE_STAINED_GLASS_PANE;

        ConfigurationSection section = config.getConfigurationSection("rewards");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection itemSec = section.getConfigurationSection(key);
                if (itemSec == null) continue;

                long hours = itemSec.getLong("required_playtime_hours", 0);
                long minutes = itemSec.getLong("required_playtime_minutes", 0);
                long totalSec = (hours * 3600L) + (minutes * 60L);

                String name = itemSec.getString("name", key);
                String matName = itemSec.getString("material", "DIAMOND");
                Material mat = Material.matchMaterial(matName);
                if (mat == null) mat = Material.DIAMOND;

                String headTexture = itemSec.getString("custom_head_texture", "");
                List<String> lore = itemSec.getStringList("lore_preview");
                List<String> commands = itemSec.getStringList("commands");

                RewardItem item = new RewardItem(key, name, mat, headTexture, -1, totalSec, 0, lore, commands);
                playtimeRewards.add(item);
            }
        }
        // Nach benötigter Spielzeit aufsteigend sortieren
        playtimeRewards.sort(Comparator.comparingLong(RewardItem::getRequiredPlaytimeSeconds));
    }

    public String getTitle() {
        return title;
    }

    public Material getFrameGlass() {
        return frameGlass;
    }

    public List<RewardItem> getPlaytimeRewards() {
        return playtimeRewards;
    }
}
