package de.Z7534.ztone.clansystem.managers;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.utils.ColorUtils;
import de.Z7534.ztone.util.ConfigUtil;
import de.Z7534.ztone.util.SmallCapsUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class MessageManager {

    private final Ztone plugin;
    private FileConfiguration messagesConfig;
    private FileConfiguration defaultMessages;
    private String prefix;

    public MessageManager(Ztone plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File clanFolder = new File(plugin.getDataFolder(), "clansystem");
        File messagesFile = new File(clanFolder, "messages.yml");

        if (!messagesFile.exists()) {
            File legacyFile = new File(plugin.getDataFolder(), "messages.yml");
            if (legacyFile.exists()) {
                messagesFile = legacyFile;
            }
        }
        messagesConfig = ConfigUtil.loadAndSync(plugin, messagesFile, "clansystem/messages.yml");

        defaultMessages = null;
        InputStream bundled = plugin.getResource("clansystem/messages.yml");
        if (bundled == null) {
            bundled = plugin.getResource("messages.yml");
        }
        if (bundled != null) {
            defaultMessages = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(bundled, StandardCharsets.UTF_8));
        }
        prefix = messagesConfig.getString("prefix", "&8[&6Clan&8] &7");
    }

    public String getPrefix() {
        return prefix;
    }

    public String getRawOrNull(String path) {
        if (messagesConfig != null && messagesConfig.contains(path)) {
            return messagesConfig.getString(path);
        }
        if (defaultMessages != null && defaultMessages.contains(path)) {
            return defaultMessages.getString(path);
        }
        return null;
    }

    public String getRaw(String path) {
        String missing = "&cNachricht nicht gefunden: " + path;
        String fallback = defaultMessages != null ? defaultMessages.getString(path, missing) : missing;
        return messagesConfig.getString(path, fallback);
    }

    public String get(String path) {
        String full = prefix + getRaw(path);
        return SmallCapsUtil.toSmallCaps(full);
    }

    public String get(String path, Map<String, String> placeholders) {
        String message = applyPlaceholders(getRaw(path), placeholders);
        String full = prefix + message;
        return SmallCapsUtil.toSmallCaps(full);
    }

    public String applyPlaceholders(String text, Map<String, String> placeholders) {
        if (text == null) return "";
        if (placeholders == null || placeholders.isEmpty()) return text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue() != null ? entry.getValue() : "";
            if (text.contains("%" + key + "%")) {
                text = text.replace("%" + key + "%", val);
            }
            if (text.contains("{" + key + "}")) {
                text = text.replace("{" + key + "}", val);
            }
            if (!text.contains("%" + key + "%") && !text.contains("{" + key + "}") && text.contains(key)) {
                text = text.replace(key, val);
            }
        }
        return text;
    }

    public String getRawWithPlaceholders(String path, Map<String, String> placeholders) {
        return applyPlaceholders(getRaw(path), placeholders);
    }

    public Component getComponent(String path) {
        return ColorUtils.colorize(get(path));
    }

    public Component getComponent(String path, Map<String, String> placeholders) {
        return ColorUtils.colorize(get(path, placeholders));
    }

    public void send(CommandSender sender, String path) {
        if (sender != null) {
            sender.sendMessage(getComponent(path));
        }
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        if (sender != null) {
            sender.sendMessage(getComponent(path, placeholders));
        }
    }

    public void sendRaw(CommandSender sender, String path) {
        if (sender != null) {
            sender.sendMessage(ColorUtils.colorize(SmallCapsUtil.toSmallCaps(getRaw(path))));
        }
    }

    public void sendRaw(CommandSender sender, String path, Map<String, String> placeholders) {
        if (sender != null) {
            sender.sendMessage(ColorUtils.colorize(SmallCapsUtil.toSmallCaps(getRawWithPlaceholders(path, placeholders))));
        }
    }

    public void broadcast(String path) {
        Bukkit.broadcast(getComponent(path));
    }

    public void broadcast(String path, Map<String, String> placeholders) {
        Bukkit.broadcast(getComponent(path, placeholders));
    }

    public void broadcastExcept(String path, Player exceptPlayer, Map<String, String> placeholders) {
        Component comp = getComponent(path, placeholders);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (exceptPlayer != null && online.getUniqueId().equals(exceptPlayer.getUniqueId())) {
                continue;
            }
            online.sendMessage(comp);
        }
    }

    public void broadcastExcept(String path, Player exceptPlayer) {
        broadcastExcept(path, exceptPlayer, Map.of());
    }

    public static Map<String, String> of(String... pairs) {
        Map<String, String> map = new HashMap<>();
        if (pairs == null) return map;
        for (int i = 0; i < pairs.length - 1; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    public static String formatTime(long seconds) {
        if (seconds <= 0) return "0s";
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (secs > 0 || sb.length() == 0) sb.append(secs).append("s");
        return sb.toString().trim();
    }
}
