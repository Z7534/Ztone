package de.Z7534.ztone.clearlag.config;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.claims.util.Text;
import de.Z7534.ztone.util.ConfigUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.Map;

public class ClearLagConfig {

    private final Ztone plugin;
    private File configFile;
    private FileConfiguration config;

    private int intervalSeconds;
    private int warning5mSeconds;
    private int warning1mSeconds;
    private int minItemAgeTicks;

    private String prefix;
    private String messageWarning5m;
    private String messageWarning1m;
    private String messageCleared;

    public ClearLagConfig(Ztone plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "clearlag/config.yml");
    }

    public void load() {
        this.config = ConfigUtil.loadAndSync(plugin, configFile, "clearlag/config.yml");

        this.intervalSeconds = config.getInt("interval-seconds", 600);
        this.warning5mSeconds = config.getInt("warning-5m-seconds", 300);
        this.warning1mSeconds = config.getInt("warning-1m-seconds", 60);
        this.minItemAgeTicks = config.getInt("min-item-age-ticks", 200); // 10 Sekunden = 200 Ticks

        this.prefix = config.getString("messages.prefix",
                "<gradient:#00C9FF:#92FE9D><bold>ᴄʟᴇᴀʀʟᴀɢ</bold></gradient> <dark_gray>»</dark_gray>");
        this.messageWarning5m = config.getString("messages.warning-5m",
                "<prefix> <gray>Alle auf dem Boden liegenden Items werden in <yellow><bold>5 Minuten</bold></yellow> gelöscht!</gray>");
        this.messageWarning1m = config.getString("messages.warning-1m",
                "<prefix> <gray>Alle auf dem Boden liegenden Items werden in <yellow><bold>1 Minute</bold></yellow> gelöscht!</gray>");
        this.messageCleared = config.getString("messages.cleared",
                "<prefix> <gray>Es wurden <gradient:#00C9FF:#92FE9D><bold>{count}</bold></gradient> auf dem Boden liegende Items entfernt!</gray>");
    }

    public Component getMessage(String key, Map<String, String> placeholders) {
        String template = switch (key) {
            case "warning-5m" -> messageWarning5m;
            case "warning-1m" -> messageWarning1m;
            case "cleared" -> messageCleared;
            default -> config.getString("messages." + key, "");
        };
        template = template.replace("<prefix>", prefix);
        return Text.line(template, placeholders);
    }

    public Component getMessage(String key) {
        return getMessage(key, Map.of());
    }

    public int getIntervalSeconds() { return intervalSeconds; }
    public int getWarning5mSeconds() { return warning5mSeconds; }
    public int getWarning1mSeconds() { return warning1mSeconds; }
    public int getMinItemAgeTicks() { return minItemAgeTicks; }
    public String getPrefix() { return prefix; }
}
