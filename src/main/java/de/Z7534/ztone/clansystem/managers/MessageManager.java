package de.Z7534.ztone.clansystem.managers;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.utils.ColorUtils;
import de.Z7534.ztone.util.SmallCapsUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

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
            } else {
                plugin.saveResource("clansystem/messages.yml", false);
            }
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

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
        String full = prefix + getRaw(path);
        String smallCapsTemplate = SmallCapsUtil.toSmallCaps(full);
        return applyPlaceholders(smallCapsTemplate, placeholders);
    }

    public String getRawWithPlaceholders(String path, Map<String, String> placeholders) {
        return applyPlaceholders(getRaw(path), placeholders);
    }

    public String applyPlaceholders(String text, Map<String, String> placeholders) {
        if (text == null || placeholders == null) {
            return text != null ? text : "";
        }
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue() != null ? entry.getValue() : "";
            if (val.isEmpty()) {
                // Bereinige umschließende eckige Klammern, falls Platzhalter leer ist
                text = text.replace("&7[%" + key + "%&7] ", "");
                text = text.replace("&8[%" + key + "%&8] ", "");
                text = text.replace("[%" + key + "%] ", "");
                text = text.replace("&7[%" + key + "%&7]", "");
                text = text.replace("&8[%" + key + "%&8]", "");
                text = text.replace("[%" + key + "%]", "");
            }
            text = text.replace("%" + key + "%", val);
        }
        return text;
    }

    public Component getComponent(String path) {
        return ColorUtils.colorize(get(path));
    }

    public Component getComponent(String path, Map<String, String> placeholders) {
        return ColorUtils.colorize(get(path, placeholders));
    }

    public void send(CommandSender sender, String path) {
        sender.sendMessage(getComponent(path));
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        sender.sendMessage(getComponent(path, placeholders));
    }

    public void sendRaw(CommandSender sender, String path) {
        sender.sendMessage(ColorUtils.colorize(getRaw(path)));
    }

    public void sendRaw(CommandSender sender, String path, Map<String, String> placeholders) {
        sender.sendMessage(ColorUtils.colorize(getRawWithPlaceholders(path, placeholders)));
    }

    public void broadcast(String path) {
        Component message = getComponent(path);
        Bukkit.broadcast(message);
    }

    public void broadcast(String path, Map<String, String> placeholders) {
        Component message = getComponent(path, placeholders);
        Bukkit.broadcast(message);
    }

    public void broadcastExcept(String path, org.bukkit.entity.Player exclude, Map<String, String> placeholders) {
        broadcastExcept(path, placeholders, exclude);
    }

    public void broadcastExcept(String path, Map<String, String> placeholders, org.bukkit.entity.Player... exclude) {
        Component message = getComponent(path, placeholders);
        java.util.Set<java.util.UUID> excludedIds = new java.util.HashSet<>();
        if (exclude != null) {
            for (org.bukkit.entity.Player p : exclude) {
                if (p != null) {
                    excludedIds.add(p.getUniqueId());
                }
            }
        }
        for (org.bukkit.entity.Player online : Bukkit.getOnlinePlayers()) {
            if (excludedIds.contains(online.getUniqueId())) {
                continue;
            }
            online.sendMessage(message);
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }

    public static Map<String, String> of(String key, String value) {
        Map<String, String> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    public static Map<String, String> of(String k1, String v1, String k2, String v2) {
        Map<String, String> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }

    public static Map<String, String> of(String k1, String v1, String k2, String v2, String k3, String v3) {
        Map<String, String> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        return map;
    }

    public static Map<String, String> of(String k1, String v1, String k2, String v2,
                                         String k3, String v3, String k4, String v4) {
        Map<String, String> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        return map;
    }

    public static Map<String, String> of(String k1, String v1, String k2, String v2,
                                         String k3, String v3, String k4, String v4,
                                         String k5, String v5) {
        Map<String, String> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        map.put(k5, v5);
        return map;
    }

    public static Map<String, String> of(String k1, String v1, String k2, String v2,
                                         String k3, String v3, String k4, String v4,
                                         String k5, String v5, String k6, String v6) {
        Map<String, String> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        map.put(k5, v5);
        map.put(k6, v6);
        return map;
    }

    public static String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + " Sekunden";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long secs = seconds % 60;
            return minutes + " Minuten" + (secs > 0 ? " " + secs + " Sekunden" : "");
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + " Stunden" + (minutes > 0 ? " " + minutes + " Minuten" : "");
        } else {
            long days = seconds / 86400;
            long hours = (seconds % 86400) / 3600;
            return days + " Tage" + (hours > 0 ? " " + hours + " Stunden" : "");
        }
    }

    public static String formatTimeShort(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        } else if (seconds < 86400) {
            return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        } else {
            return (seconds / 86400) + "d " + ((seconds % 86400) / 3600) + "h";
        }
    }
}
