package de.Z7534.ztone.stonevoice.listener;

import de.Z7534.ztone.stonevoice.StoneVoice;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles Bukkit-side events.
 */
public class BukkitEventListener implements Listener {

    private final StoneVoice plugin;

    public BukkitEventListener(StoneVoice plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getAdminVoiceManager().onPlayerQuit(event.getPlayer().getUniqueId());
    }
}
