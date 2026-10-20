package de.Z7534.ztone.pvpmanager.managers;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.utils.MessageUtils;
import de.Z7534.ztone.pvpmanager.utils.TimeFormatter;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class MessageDispatcher {

    private final Pvpmanager plugin;

    public MessageDispatcher(Pvpmanager plugin) {
        this.plugin = plugin;
    }

    public void send(Player player, String key, Map<String, String> placeholders) {
        String raw = plugin.getLocaleManager().raw(key);
        if (raw == null || raw.isEmpty()) {
            return;
        }
        Map<String, String> merged = new HashMap<>();
        merged.put("prefix", plugin.getLocaleManager().raw("Prefix"));
        if (placeholders != null) {
            merged.putAll(placeholders);
        }
        MessageUtils.send(player, raw, merged);
    }

    public void send(Player player, String key) {
        send(player, key, null);
    }

    public void sendWithTime(Player player, String key, long seconds) {
        send(player, key, Map.of("time", TimeFormatter.format(plugin, seconds)));
    }

    public void sendWithTime(Player player, String key, long seconds, Map<String, String> extraPlaceholders) {
        Map<String, String> merged = new HashMap<>(extraPlaceholders);
        merged.put("time", TimeFormatter.format(plugin, seconds));
        send(player, key, merged);
    }

    public String get(String key) {
        return plugin.getLocaleManager().raw(key);
    }
}
