package de.Z7534.ztone.mapcontrol.listeners;

import de.Z7534.ztone.mapcontrol.MapControl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerListener implements Listener {

    private final MapControl plugin;

    public PlayerListener(MapControl plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        scheduleRestrictions(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        scheduleRestrictions(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        scheduleRestrictions(event.getPlayer());
    }

    private void scheduleRestrictions(Player player) {
        int delayTicks = Math.max(1, plugin.getPluginConfig().getSendDelayTicks());
        plugin.getServer().getScheduler().runTaskLater(plugin.getPlugin(), () -> {
            if (!player.isOnline()) return;
            plugin.applyAllRestrictions(player);
        }, delayTicks);
    }
}
