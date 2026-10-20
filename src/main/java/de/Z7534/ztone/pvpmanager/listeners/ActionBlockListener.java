package de.Z7534.ztone.pvpmanager.listeners;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.permissions.Permissions;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.Set;

public class ActionBlockListener implements Listener {

    private static final Set<InventoryType> CONTAINER_TYPES = Set.of(
            InventoryType.CHEST,
            InventoryType.DISPENSER,
            InventoryType.DROPPER,
            InventoryType.FURNACE,
            InventoryType.BREWING,
            InventoryType.HOPPER,
            InventoryType.SHULKER_BOX,
            InventoryType.BARREL,
            InventoryType.BLAST_FURNACE,
            InventoryType.SMOKER,
            InventoryType.ENDER_CHEST,
            InventoryType.CRAFTER
    );

    private final Pvpmanager plugin;

    public ActionBlockListener(Pvpmanager plugin) {
        this.plugin = plugin;
    }

    private boolean isTagged(Player player) {
        return plugin.getCombatManager().isTagged(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!isTagged(player) || plugin.getCombatManager().hasTeleportBypass(player.getUniqueId())) {
            return;
        }

        // Only command-based / plugin-based teleports are blocked (e.g. /tp, /tpa, /home, /spawn)
        // Enderpearls, chorus fruits, nether/end portals etc. remain explicitly allowed!
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.COMMAND || cause == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            event.setCancelled(true);
            plugin.getMessageDispatcher().send(player, "Teleport_Blocked_InCombat");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractContainer(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!isTagged(player)) {
            return;
        }

        Material mat = event.getClickedBlock().getType();
        if (mat == Material.ENDER_CHEST || event.getClickedBlock().getState() instanceof Container) {
            event.setCancelled(true);
            plugin.getMessageDispatcher().send(player, "Container_Blocked_InCombat");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!isTagged(player)) {
            return;
        }

        InventoryType type = event.getInventory().getType();
        boolean isContainer = CONTAINER_TYPES.contains(type)
                || (event.getInventory().getHolder() instanceof Container);

        if (isContainer) {
            event.setCancelled(true);
            plugin.getMessageDispatcher().send(player, "Container_Blocked_InCombat");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        Material type = event.getItem().getType();

        if (type == Material.ENCHANTED_GOLDEN_APPLE) {
            if (plugin.getCooldownManager().isEgappleOnCooldown(player)) {
                event.setCancelled(true);
                long remaining = plugin.getCooldownManager().getEgappleRemaining(player);
                plugin.getMessageDispatcher().send(player, "Egapple_Cooldown",
                        Map.of("time", String.valueOf(remaining)));
                return;
            }
            plugin.getCooldownManager().triggerEgapple(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (plugin.getCombatManager().isTagged(player.getUniqueId())) {
            // Alle Befehle im Kampf blockieren!
            event.setCancelled(true);
            plugin.getMessageDispatcher().send(player, "Command_Denied_InCombat");
        }
    }
}
