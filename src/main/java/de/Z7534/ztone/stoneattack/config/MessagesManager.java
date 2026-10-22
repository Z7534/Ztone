package de.Z7534.ztone.stoneattack.config;

import de.Z7534.ztone.stoneattack.Stoneattack;
import de.Z7534.ztone.stoneattack.util.ColorUtil;
import de.Z7534.ztone.util.ConfigUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.Map;

/**
 * Laedt/haelt die messages.yml und stellt Hilfsmethoden zum direkten
 * Versenden fertig formatierter Nachrichten bereit. Jede Nachricht
 * kann optional den globalen "prefix" enthalten (%prefix%).
 */
public class MessagesManager {

    private final Stoneattack plugin;
    private File file;
    private FileConfiguration messages;

    public MessagesManager(Stoneattack plugin) {
        this.plugin = plugin;
    }

    public void load() {
        file = new File(plugin.getDataFolder(), "messages.yml");
        messages = ConfigUtil.loadAndSync(plugin.getPlugin(), file, "stoneattack/messages.yml");
    }

    public void reload() {
        load();
    }

    private String prefix() {
        return messages.getString("prefix", "");
    }

    public String raw(String path) {
        return messages.getString(path, "<red>Fehlende Nachricht: " + path + "</red>");
    }

    public Component get(String path) {
        return get(path, Map.of());
    }

    public Component get(String path, Map<String, String> placeholders) {
        String template = raw(path).replace("%prefix%", prefix());
        return ColorUtil.parse(template, placeholders);
    }

    public void send(CommandSender sender, String path) {
        sender.sendMessage(get(path));
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        sender.sendMessage(get(path, placeholders));
    }
}
