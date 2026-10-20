package de.Z7534.ztone.playrewards.listener;

import de.Z7534.ztone.playrewards.PlayReward;
import de.Z7534.ztone.playrewards.model.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class AfkDetectionListener implements Listener {

    private final PlayReward plugin;

    public AfkDetectionListener(PlayReward plugin) {
        this.plugin = plugin;
    }

    private void registerActivity(Player player) {
        PlayerData data = plugin.getDatabaseManager().getCachedData(player.getUniqueId());
        if (data != null) {
            data.setLastActiveTime(System.currentTimeMillis());
            if (data.isAfk()) {
                data.setAfk(false);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Prüfen, ob sich der Spieler tatsächlich von einem Block wegbewegt hat (nicht nur Sichtfeld)
        if (from.getBlockX() != to.getBlockX() ||
            from.getBlockY() != to.getBlockY() ||
            from.getBlockZ() != to.getBlockZ()) {
            registerActivity(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        registerActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        registerActivity(event.getPlayer());
    }
}
