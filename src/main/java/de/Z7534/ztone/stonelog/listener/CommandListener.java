package de.Z7534.ztone.stonelog.listener;

import de.Z7534.ztone.stonelog.Stonelog;
import de.Z7534.ztone.stonelog.config.ConfigManager;
import de.Z7534.ztone.stonelog.discord.Embed;
import de.Z7534.ztone.stonelog.util.AvatarUtil;
import de.Z7534.ztone.stonelog.util.PlaceholderUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Map;

/**
 * Loggt JEDEN von einem Spieler ausgefuehrten Befehl (Bild 1).
 */
public class CommandListener implements Listener {

    private final Stonelog plugin;

    public CommandListener(Stonelog plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        ConfigManager.WebhookEntry entry = plugin.getConfigManager().getCommands();
        if (!entry.isEnabled()) return;

        Player player = event.getPlayer();
        String command = event.getMessage();

        Map<String, String> placeholders = Map.of(
                "player", player.getName(),
                "uuid", player.getUniqueId().toString(),
                "command", command
        );

        String avatarUrl = AvatarUtil.headUrl(plugin.getConfigManager().getAvatarBaseUrl(), player.getUniqueId());

        String webhookUsername = PlaceholderUtil.apply(entry.msg("author", "{player} hat einen Befehl ausgeführt"), placeholders);

        Embed embed = new Embed();
        embed.description = PlaceholderUtil.apply(entry.msg("description", "```{command}```"), placeholders);
        embed.footerText = PlaceholderUtil.apply(entry.msg("footer", "{uuid}"), placeholders);
        embed.color = 0x2b2d31;

        plugin.getWebhookSender().send(entry.getUrls(), webhookUsername, avatarUrl, embed);
    }
}
