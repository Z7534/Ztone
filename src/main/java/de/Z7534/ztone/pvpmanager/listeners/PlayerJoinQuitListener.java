package de.Z7534.ztone.pvpmanager.listeners;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.permissions.Permissions;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.WorldLoadEvent;

public class PlayerJoinQuitListener implements Listener {

    private final Pvpmanager plugin;

    public PlayerJoinQuitListener(Pvpmanager plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getPlayerDataManager().loadOnJoin(player);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        // Dimensionswechsel veraendert nicht mehr ungefragt den persoenlichen PvP-Status des Spielers
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (plugin.getOverrideManager() != null) {
            plugin.getOverrideManager().updateWorldMetadata(event.getWorld());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPlayerDataManager().saveAndUnload(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(Permissions.EXEMPT_RESPAWNPROTECTION)
                || player.hasPermission(Permissions.EXEMPT)) {
            return;
        }
        // Grant spawn protection (default 15 seconds)
        plugin.getRespawnProtectionManager().giveProtection(player);
    }
}
