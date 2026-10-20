package de.Z7534.ztone.stonelog.listener;

import de.Z7534.ztone.stonelog.Stonelog;
import de.Z7534.ztone.stonelog.config.ConfigManager;
import de.Z7534.ztone.stonelog.discord.Embed;
import de.Z7534.ztone.stonelog.util.AvatarUtil;
import de.Z7534.ztone.stonelog.util.CodeBlockUtil;
import de.Z7534.ztone.stonelog.util.DimensionUtil;
import de.Z7534.ztone.stonelog.util.PlaceholderUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;

/**
 * Loggt Server-Beitritte (gruen) und Verlassen (rot) im selben Webhook (Bild 4).
 */
public class JoinLeaveListener implements Listener {

    private final Stonelog plugin;

    public JoinLeaveListener(Stonelog plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        send(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        send(event.getPlayer(), false);
    }

    private void send(Player player, boolean joined) {
        ConfigManager.WebhookEntry entry = plugin.getConfigManager().getJoinLeave();
        if (!entry.isEnabled()) return;

        String dimension = DimensionUtil.of(player.getWorld());
        Location loc = player.getLocation();
        String position = loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();

        Map<String, String> placeholders = Map.of(
                "player", player.getName(),
                "uuid", player.getUniqueId().toString(),
                "world", dimension,
                "position", position
        );

        String avatarUrl = AvatarUtil.headUrl(plugin.getConfigManager().getAvatarBaseUrl(), player.getUniqueId());

        String key = joined ? "author-join" : "author-leave";
        String def = joined ? "{player} ist den Server beigetreten" : "{player} hat den Server verlassen";

        String webhookUsername = PlaceholderUtil.apply(entry.msg(key, def), placeholders);

        Embed embed = new Embed();
        embed.footerText = PlaceholderUtil.apply(entry.msg("footer", "{uuid}"), placeholders);
        embed.color = joined ? 0x2ecc71 : 0xe74c3c;

        embed.addField(entry.msg("field-world", "Welt"), CodeBlockUtil.wrap(dimension), false);
        embed.addField(entry.msg("field-position", "Position"), CodeBlockUtil.wrap(position), false);

        plugin.getWebhookSender().send(entry.getUrls(), webhookUsername, avatarUrl, embed);
    }
}
