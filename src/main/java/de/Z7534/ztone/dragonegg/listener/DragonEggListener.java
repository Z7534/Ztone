package de.Z7534.ztone.dragonegg.listener;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.dragonegg.config.DragonEggConfig;
import de.Z7534.ztone.dragonegg.manager.DragonEggBypassManager;
import de.Z7534.ztone.dragonegg.manager.DragonEggManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;

public class DragonEggListener implements Listener {

    private final Ztone plugin;
    private final DragonEggConfig config;
    private final DragonEggManager manager;
    private final DragonEggBypassManager bypassManager;

    public DragonEggListener(Ztone plugin, DragonEggConfig config, DragonEggManager manager,
                             DragonEggBypassManager bypassManager) {
        this.plugin = plugin;
        this.config = config;
        this.manager = manager;
        this.bypassManager = bypassManager;
    }

    private void scheduleCheck(Player player) {
        if (player == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                manager.updatePlayer(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (bypassManager.handleJoin(player)) {
            player.sendMessage(config.getMessage("bypass-auto-join"));
        }
        scheduleCheck(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        bypassManager.reset(player.getUniqueId());
        // Clean up glowing
        player.setGlowing(false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        scheduleCheck(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        scheduleCheck(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            scheduleCheck(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        scheduleCheck(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleCheck(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleCheck(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            scheduleCheck(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        scheduleCheck(event.getPlayer());
    }
}
