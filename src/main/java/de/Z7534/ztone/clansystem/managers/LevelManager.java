package de.Z7534.ztone.clansystem.managers;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.models.Clan;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class LevelManager {

    private final Ztone plugin;
    private FileConfiguration levelConfig;

    private int maxLevel;
    private Map<Integer, Integer> pointsRequired;
    private Map<Integer, LevelRewards> rewards;

    public LevelManager(Ztone plugin) {
        this.plugin = plugin;
        this.pointsRequired = new HashMap<>();
        this.rewards = new HashMap<>();
        reload();
    }

    public void reload() {
        File clanFolder = new File(plugin.getDataFolder(), "clansystem");
        File levelFile = new File(clanFolder, "level.yml");

        if (!levelFile.exists()) {
            File legacyFile = new File(plugin.getDataFolder(), "level.yml");
            if (legacyFile.exists()) {
                levelFile = legacyFile;
            } else {
                plugin.saveResource("clansystem/level.yml", false);
            }
        }
        levelConfig = YamlConfiguration.loadConfiguration(levelFile);

        InputStream bundled = plugin.getResource("clansystem/level.yml");
        if (bundled == null) {
            bundled = plugin.getResource("level.yml");
        }
        if (bundled != null) {
            try {
                levelConfig.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(bundled, StandardCharsets.UTF_8)));
            } finally {
                try {
                    bundled.close();
                } catch (Exception ignored) {
                }
            }
        }

        loadLevels();
    }

    private void loadLevels() {
        pointsRequired.clear();
        rewards.clear();
        maxLevel = 0;

        ConfigurationSection levels = levelConfig.getConfigurationSection("levels");
        if (levels != null) {
            for (String key : levels.getKeys(false)) {
                try {
                    int level = Integer.parseInt(key);
                    ConfigurationSection levelSection = levels.getConfigurationSection(key);

                    if (levelSection != null) {
                        int points = levelSection.getInt("points-required", level * 100);
                        pointsRequired.put(level, points);

                        ConfigurationSection rewardsSection = levelSection.getConfigurationSection("rewards");
                        if (rewardsSection != null) {
                            java.util.List<String> colors = new java.util.ArrayList<>();
                            for (String colorCode : rewardsSection.getStringList("colors")) {
                                if (colorCode != null && !colorCode.isBlank()) {
                                    colors.add(colorCode.toLowerCase());
                                }
                            }

                            LevelRewards levelRewards = new LevelRewards(
                                    rewardsSection.getInt("max-members", 0),
                                    rewardsSection.getInt("max-homes", 0),
                                    rewardsSection.getInt("max-warps", 0),
                                    rewardsSection.getInt("chest-rows", 0),
                                    rewardsSection.getString("icon", null),
                                    colors,
                                    rewardsSection.getBoolean("gradient-unlocked", false),
                                    rewardsSection.getBoolean("strikethrough-unlocked", false),
                                    rewardsSection.getBoolean("glow-unlocked", false)
                            );
                            rewards.put(level, levelRewards);
                        }

                        if (level > maxLevel) {
                            maxLevel = level;
                        }
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (maxLevel == 0) {
            // Fallback, falls die level.yml fehlt oder leer/ungültig ist.
            // Level 1 enthält hier bereits die Grundausstattung (siehe level.yml).
            maxLevel = 5;
            pointsRequired.put(1, 0);
            pointsRequired.put(2, 300);
            pointsRequired.put(3, 600);
            pointsRequired.put(4, 1000);
            pointsRequired.put(5, 1500);

            rewards.put(1, new LevelRewards(15, 1, 3, 3, "NETHER_STAR",
                    java.util.List.of("&f", "&5", "&7", "&9", "&a"), false, false, false));
            rewards.put(2, new LevelRewards(5, 0, 1, 1, "DIAMOND",
                    java.util.List.of("&b", "&e"), true, false, false));
            rewards.put(3, new LevelRewards(5, 0, 1, 1, "EMERALD",
                    java.util.List.of("&1", "&3"), false, false, true));
            rewards.put(4, new LevelRewards(5, 0, 2, 1, "TOTEM_OF_UNDYING",
                    java.util.List.of("&0", "&8"), false, true, false));
            rewards.put(5, new LevelRewards(10, 0, 3, 0, "NETHERITE_INGOT",
                    java.util.List.of("&c", "&4", "&2", "&6"), false, false, false));
        }
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    /**
     * Das Icon, mit dem jeder neue Clan startet - das ist das Icon, das in
     * Level 1 (der Grundausstattung) konfiguriert ist.
     */
    public String getBaseIcon() {
        String icon = getRewards(1).icon();
        return icon != null ? icon : "SHIELD";
    }

    public boolean isMaxLevel(int level) {
        return level >= maxLevel;
    }

    public int getPointsRequired(int level) {
        return pointsRequired.getOrDefault(level, level * 100);
    }

    public LevelRewards getRewards(int level) {
        return rewards.getOrDefault(level, new LevelRewards(0, 0, 0, 0, null, java.util.List.of(), false, false, false));
    }

    public int calculateLevel(int points) {
        int level = 1;
        for (int i = 1; i <= maxLevel; i++) {
            if (points >= getPointsRequired(i)) {
                level = i;
            } else {
                break;
            }
        }
        return level;
    }

    public int getMaxMembers(int level) {
        int total = 0;
        for (int i = 1; i <= level; i++) {
            LevelRewards r = getRewards(i);
            total += r.maxMembers();
        }
        return total;
    }

    public int getMaxHomes(int level) {

        return 1;
    }

    public int getMaxWarps(int level) {
        int total = 0;
        for (int i = 1; i <= level; i++) {
            LevelRewards r = getRewards(i);
            total += r.maxWarps();
        }
        return total;
    }

    public int getChestSize(int level) {
        int totalRows = 0;
        for (int i = 1; i <= level; i++) {
            LevelRewards r = getRewards(i);
            totalRows += r.chestRows();
        }

        totalRows = Math.min(totalRows, 6);
        return totalRows * 9;
    }

    public boolean checkLevelUp(Clan clan) {
        int currentLevel = clan.getLevel();
        if (currentLevel >= maxLevel) {
            return false;
        }

        int nextLevel = currentLevel + 1;
        int required = getPointsRequired(nextLevel);

        if (clan.getPoints() >= required) {
            clan.setLevel(nextLevel);

            int newChestSize = getChestSize(nextLevel);
            if (clan.getChestSize() < newChestSize) {
                clan.resizeChest(newChestSize);
                // Offene Truhe schließen, damit sie beim erneuten Öffnen mit der neuen Größe aufgebaut wird.
                // Clan chest closed
            }

            return true;
        }

        return false;
    }

    public String getRewardsDescription(int level) {
        LevelRewards r = getRewards(level);
        StringBuilder sb = new StringBuilder();

        if (r.maxMembers() > 0) {
            sb.append("&a+").append(r.maxMembers()).append(" Mitglieder ");
        }
        if (r.maxHomes() > 0) {
            sb.append("&a+").append(r.maxHomes()).append(" Homes ");
        }
        if (r.maxWarps() > 0) {
            sb.append("&a+").append(r.maxWarps()).append(" Warps ");
        }
        if (r.chestRows() > 0) {
            sb.append("&a+").append(r.chestRows() * 9).append(" Truhen-Slots ");
        }
        if (r.icon() != null && !r.icon().isBlank()) {
            sb.append("&a+1 neues Clan-Icon ");
        }
        if (!r.colors().isEmpty()) {
            sb.append("&a+").append(r.colors().size())
                    .append(r.colors().size() == 1 ? " neue Farbe " : " neue Farben ");
        }
        if (r.gradientUnlocked()) {
            sb.append("&a+Farbverlauf ");
        }
        if (r.strikethroughUnlocked()) {
            sb.append("&a+Durchgestrichen-Formatierung ");
        }
        if (r.glowUnlocked()) {
            sb.append("&a+Glow-Effekt ");
        }

        return sb.toString().trim();
    }

    public java.util.List<String> getUnlockedIcons(int level) {
        java.util.List<String> icons = new java.util.ArrayList<>();
        for (int i = 1; i <= level; i++) {
            String icon = getRewards(i).icon();
            if (icon != null && !icon.isBlank() && !icons.contains(icon)) {
                icons.add(icon);
            }
        }
        return icons;
    }

    public java.util.List<String> getAllConfiguredIcons() {
        java.util.List<String> icons = new java.util.ArrayList<>();
        for (int i = 1; i <= maxLevel; i++) {
            String icon = getRewards(i).icon();
            if (icon != null && !icon.isBlank() && !icons.contains(icon)) {
                icons.add(icon);
            }
        }
        return icons;
    }

    public int getRequiredLevelForIcon(String iconName) {
        for (int i = 1; i <= maxLevel; i++) {
            String icon = getRewards(i).icon();
            if (icon != null && icon.equalsIgnoreCase(iconName)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Liefert das Level, ab dem eine bestimmte Farbe (z.B. "&c") laut level.yml
     * freigeschaltet ist. Ist die Farbe in keinem Level konfiguriert, wird
     * sicherheitshalber Level 1 zurückgegeben (also von Anfang an freigeschaltet),
     * damit ein vergessener Eintrag in der level.yml niemanden dauerhaft aussperrt.
     */
    public int getRequiredLevelForColor(String colorCode) {
        if (colorCode == null) {
            return 1;
        }
        String code = colorCode.toLowerCase();
        for (int i = 1; i <= maxLevel; i++) {
            if (getRewards(i).colors().contains(code)) {
                return i;
            }
        }
        return 1;
    }

    public boolean isColorUnlocked(String colorCode, int level) {
        return level >= getRequiredLevelForColor(colorCode);
    }

    /**
     * Level, ab dem der Farbverlauf (Gradient) freigeschaltet wird (0, wenn in
     * der level.yml nirgends "gradient-unlocked: true" gesetzt ist - dann gilt
     * der Farbverlauf als von Anfang an freigeschaltet).
     */
    public int getGradientUnlockLevel() {
        for (int i = 1; i <= maxLevel; i++) {
            if (getRewards(i).gradientUnlocked()) {
                return i;
            }
        }
        return 0;
    }

    public boolean isGradientUnlocked(int level) {
        int required = getGradientUnlockLevel();
        return required <= 0 || level >= required;
    }

    /**
     * Level, ab dem die durchgestrichene Formatierung freigeschaltet wird
     * (0 = von Anfang an freigeschaltet, siehe getGradientUnlockLevel()).
     */
    public int getStrikethroughUnlockLevel() {
        for (int i = 1; i <= maxLevel; i++) {
            if (getRewards(i).strikethroughUnlocked()) {
                return i;
            }
        }
        return 0;
    }

    public boolean isStrikethroughUnlocked(int level) {
        int required = getStrikethroughUnlockLevel();
        return required <= 0 || level >= required;
    }

    /**
     * Level, ab dem der Glow-Effekt freigeschaltet wird
     * (0 = von Anfang an freigeschaltet, siehe getGradientUnlockLevel()).
     */
    public int getGlowUnlockLevel() {
        for (int i = 1; i <= maxLevel; i++) {
            if (getRewards(i).glowUnlocked()) {
                return i;
            }
        }
        return 0;
    }

    public boolean isGlowUnlocked(int level) {
        int required = getGlowUnlockLevel();
        return required <= 0 || level >= required;
    }

    public record LevelRewards(int maxMembers, int maxHomes, int maxWarps, int chestRows, String icon,
                                java.util.List<String> colors, boolean gradientUnlocked,
                                boolean strikethroughUnlocked, boolean glowUnlocked) {
    }
}
