package de.Z7534.ztone.claims.listener;

import de.Z7534.ztone.claims.ClaimsContext;
import de.Z7534.ztone.claims.manager.ClaimMapManager;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

public final class ClaimMapListener implements Listener {

    private final ClaimsContext ctx;
    private final ClaimMapManager mapManager;

    public ClaimMapListener(ClaimsContext ctx, ClaimMapManager mapManager) {
        this.ctx = ctx;
        this.mapManager = mapManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (mapManager.isClaimMap(e.getMainHandItem()) || mapManager.isClaimMap(e.getOffHandItem())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (mapManager.isClaimMap(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (mapManager.isClaimMap(e.getCurrentItem()) || mapManager.isClaimMap(e.getCursor())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (mapManager.isClaimMap(e.getOldCursor())) {
            e.setCancelled(true);
            return;
        }
        for (ItemStack item : e.getNewItems().values()) {
            if (mapManager.isClaimMap(item)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        if (mapManager.isClaimMap(e.getItem())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    @SuppressWarnings("removal")
    public void onDeath(PlayerDeathEvent e) {
        Player player = e.getEntity();
        if (mapManager.hasSavedOffHand(player.getUniqueId()) || mapManager.isClaimMap(player.getInventory().getItemInOffHand())) {
            // Claim-Karte niemals droppen
            e.getDrops().removeIf(mapManager::isClaimMap);

            ItemStack saved = mapManager.removeSavedOffHand(player.getUniqueId());
            if (saved != null && saved.getType() != Material.AIR) {
                Boolean keepInv = player.getWorld().getGameRuleValue(GameRule.KEEP_INVENTORY);
                if (Boolean.TRUE.equals(keepInv)) {
                    player.getInventory().setItemInOffHand(saved);
                } else {
                    e.getDrops().add(saved);
                }
            }

            ctx.selection().end(player.getUniqueId());
        }
    }
}
