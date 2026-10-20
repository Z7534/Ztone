package de.Z7534.ztone.deathchest.config;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.claims.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class DeathChestConfig {

    private final Ztone plugin;
    private File configFile;
    private FileConfiguration config;

    private int killerLockSeconds;
    private int totalDespawnSeconds;
    private boolean persistenceEnabled;

    private String hologramLine1;
    private String hologramLine2Locked;
    private String hologramLine2Unlocked;
    private String hologramLine3;

    private String prefix;
    private String messageLockedForKiller;
    private String messageTakeOnly;
    private String messageChestCreated;
    private String messageChestDespawned;
    private String messageAllChestsCleared;

    public DeathChestConfig(Ztone plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "deathchest/config.yml");
    }

    public void load() {
        if (!configFile.exists()) {
            plugin.saveResource("deathchest/config.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(configFile);

        try (InputStream defStream = plugin.getResource("deathchest/config.yml")) {
            if (defStream != null) {
                this.config.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defStream, StandardCharsets.UTF_8)));
            }
        } catch (Exception ignored) {
        }

        this.killerLockSeconds = config.getInt("killer-lock-seconds", 30);
        this.totalDespawnSeconds = config.getInt("total-despawn-seconds", 300);
        this.persistenceEnabled = config.getBoolean("persistence", true);

        this.hologramLine1 = config.getString("hologram.line-1",
                "<gradient:#FF0844:#FFB199><bold>☠ TODESBEUTE</bold></gradient> <dark_gray>•</dark_gray> <gradient:#FFAA00:#FFD700><bold>{victim}</bold></gradient> <gradient:#FF0844:#FFB199><bold>☠</bold></gradient>");
        this.hologramLine2Locked = config.getString("hologram.line-2-locked",
                "<red>🔒</red> <gradient:#FF416C:#FF4B2B><bold>GESPERRT</bold></gradient> <dark_gray>»</dark_gray> <yellow><bold>{killer}</bold></yellow> <dark_gray>(</dark_gray><gold><bold>{seconds}s</bold></gold><dark_gray>)</dark_gray>");
        this.hologramLine2Unlocked = config.getString("hologram.line-2-unlocked",
                "<green>🔓</green> <gradient:#00B4DB:#0083B0><bold>FREIGEGEBEN</bold></gradient> <dark_gray>»</dark_gray> <gradient:#56AB2F:#A8E063>Jeder darf plündern!</gradient>");
        this.hologramLine3 = config.getString("hologram.line-3",
                "<gold>⌛</gold> <gray>Despawn in</gray> <dark_gray>»</dark_gray> <gradient:#F7971E:#FFD200><bold>{minutes}m {seconds}s</bold></gradient>");

        this.prefix = config.getString("messages.prefix",
                "<gradient:#FF5555:#FFAA00><bold>ᴅᴇᴀᴛʜᴄʜᴇꜱᴛ</bold></gradient> <dark_gray>»</dark_gray>");
        this.messageLockedForKiller = config.getString("messages.locked-for-killer",
                "<prefix> <red>Diese Todeskiste ist noch für <yellow>{killer}</yellow> gesperrt! (<gold>{seconds}s</gold>)</red>");
        this.messageTakeOnly = config.getString("messages.take-only",
                "<prefix> <red>Du kannst keine Items in die Todeskiste legen!</red>");
        this.messageChestCreated = config.getString("messages.chest-created",
                "<prefix> <gray>Deine Todeskiste wurde bei <yellow>{x}, {y}, {z}</yellow> erstellt.</gray>");
        this.messageChestDespawned = config.getString("messages.chest-despawned",
                "<prefix> <gray>Deine Todeskiste ist verfallen.</gray>");
        this.messageAllChestsCleared = config.getString("messages.all-chests-cleared",
                "<prefix> <green>Es wurden <yellow>{count}</yellow> aktive Todeskisten entfernt.</green>");
    }

    public Component getMessage(String key, Map<String, String> placeholders) {
        String template = switch (key) {
            case "locked-for-killer" -> messageLockedForKiller;
            case "take-only" -> messageTakeOnly;
            case "chest-created" -> messageChestCreated;
            case "chest-despawned" -> messageChestDespawned;
            case "all-chests-cleared" -> messageAllChestsCleared;
            default -> config.getString("messages." + key, "");
        };
        template = template.replace("<prefix>", prefix);
        return Text.line(template, placeholders);
    }

    public Component getMessage(String key) {
        return getMessage(key, Map.of());
    }

    public int getKillerLockSeconds() { return killerLockSeconds; }
    public int getTotalDespawnSeconds() { return totalDespawnSeconds; }
    public boolean isPersistenceEnabled() { return persistenceEnabled; }
    public String getHologramLine1() { return hologramLine1; }
    public String getHologramLine2Locked() { return hologramLine2Locked; }
    public String getHologramLine2Unlocked() { return hologramLine2Unlocked; }
    public String getHologramLine3() { return hologramLine3; }
    public String getPrefix() { return prefix; }
}
