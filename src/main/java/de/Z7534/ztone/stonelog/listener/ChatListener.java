package de.Z7534.ztone.stonelog.listener;

import de.Z7534.ztone.stonelog.Stonelog;
import de.Z7534.ztone.stonelog.config.ConfigManager;
import de.Z7534.ztone.stonelog.discord.Embed;
import de.Z7534.ztone.stonelog.util.AvatarUtil;
import de.Z7534.ztone.stonelog.util.PlaceholderUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loggt alle Chat-Nachrichten (Bild 2).
 */
public class ChatListener implements Listener {

    /** Gleiche Nachricht vom selben Spieler innerhalb dieser Zeit = Duplikat. */
    private static final long DEDUPE_MS = 1500L;

    private final Stonelog plugin;
    private final Map<UUID, Recent> recent = new ConcurrentHashMap<>();

    public ChatListener(Stonelog plugin) {
        this.plugin = plugin;
    }

    /** Registriert modernes und (falls vorhanden) altes Chat-Event. */
    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin.getPlugin());
        registerLegacyChatEvent();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onChat(AsyncChatEvent event) {
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        handle(event.getPlayer(), message);
    }

    @SuppressWarnings("unchecked")
    private void registerLegacyChatEvent() {
        Class<? extends Event> eventClass;
        Method getPlayer;
        Method getMessage;
        try {
            Class<?> raw = Class.forName("org.bukkit.event.player.AsyncPlayerChatEvent");
            eventClass = (Class<? extends Event>) raw;
            getPlayer = raw.getMethod("getPlayer");
            getMessage = raw.getMethod("getMessage");
        } catch (ClassNotFoundException e) {
            return;
        } catch (Throwable t) {
            plugin.getLogger().warning("Legacy-Chat-Event konnte nicht angebunden werden: " + t.getMessage());
            return;
        }

        try {
            plugin.getServer().getPluginManager().registerEvent(
                    eventClass,
                    this,
                    EventPriority.LOWEST,
                    (listener, event) -> {
                        if (!eventClass.isInstance(event)) return;
                        try {
                            Player player = (Player) getPlayer.invoke(event);
                            String message = (String) getMessage.invoke(event);
                            handle(player, message);
                        } catch (Throwable ignored) {
                        }
                    },
                    plugin.getPlugin(),
                    false);
        } catch (Throwable t) {
            plugin.getLogger().warning("Legacy-Chat-Event konnte nicht registriert werden: " + t.getMessage());
        }
    }

    private void handle(Player player, String message) {
        if (player == null || message == null) return;
        if (isDuplicate(player.getUniqueId(), message)) return;

        ConfigManager.WebhookEntry entry = plugin.getConfigManager().getChat();
        if (!entry.isEnabled()) return;

        Map<String, String> placeholders = Map.of(
                "player", player.getName(),
                "uuid", player.getUniqueId().toString(),
                "message", message
        );

        String avatarUrl = AvatarUtil.headUrl(plugin.getConfigManager().getAvatarBaseUrl(), player.getUniqueId());

        String webhookUsername = PlaceholderUtil.apply(entry.msg("author", "{player} hat in den Chat geschrieben"), placeholders);

        Embed embed = new Embed();
        embed.description = PlaceholderUtil.apply(entry.msg("description", "```{message}```"), placeholders);
        embed.footerText = PlaceholderUtil.apply(entry.msg("footer", "{uuid}"), placeholders);
        embed.color = 0x2b2d31;

        plugin.getWebhookSender().send(entry.getUrls(), webhookUsername, avatarUrl, embed);
    }

    private boolean isDuplicate(UUID uuid, String message) {
        long now = System.currentTimeMillis();
        if (recent.size() > 500) {
            recent.values().removeIf(r -> (now - r.timestamp()) > DEDUPE_MS);
        }
        Recent previous = recent.put(uuid, new Recent(message, now));
        return previous != null
                && previous.message().equals(message)
                && (now - previous.timestamp()) < DEDUPE_MS;
    }

    private record Recent(String message, long timestamp) {
    }
}
