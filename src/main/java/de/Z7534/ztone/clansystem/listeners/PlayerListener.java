package de.Z7534.ztone.clansystem.listeners;

import de.Z7534.ztone.Ztone;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerListener implements Listener {

    private final Ztone plugin;

    public PlayerListener(Ztone plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        plugin.getDatabaseManager().clearExpiredCooldowns();

        if (plugin.getGlowManager() != null) {
            plugin.getGlowManager().onJoin(player);
            // 2 Ticks Verzoegerung, damit der Client vollstaendig verbunden und das Entity-Tracking aktiv ist
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.getGlowManager().applyToPlayer(player);
                }
            }, 2L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (plugin.getGlowManager() != null) {
            plugin.getGlowManager().onQuit(player);
        }

        ChatListener.clearToggles(player.getUniqueId());
        plugin.getChatInputManager().cancel(player.getUniqueId());

        de.Z7534.ztone.clansystem.gui.AbstractGUI.removeOpenGui(player);
        de.Z7534.ztone.clansystem.gui.ClanChestGUI.removeOpenChest(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // Beim Respawn erhaelt der Spieler in Minecraft eine neue Entity-ID, Cache aktualisieren und verzoegert setzen
        if (plugin.getGlowManager() != null) {
            plugin.getGlowManager().onJoin(player);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.getGlowManager().applyToPlayer(player);
                }
            }, 2L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();

        if (plugin.getGlowManager() != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.getGlowManager().applyToPlayer(player);
                }
            }, 2L);
        }
    }
}
