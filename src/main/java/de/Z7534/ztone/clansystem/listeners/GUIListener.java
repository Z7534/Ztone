package de.Z7534.ztone.clansystem.listeners;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.gui.AbstractGUI;
import de.Z7534.ztone.clansystem.gui.ClanChestGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public class GUIListener implements Listener {

    private final Ztone plugin;

    public GUIListener(Ztone plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();

        if (holder instanceof AbstractGUI gui) {
            gui.handleClick(event);
            return;
        }

        if (holder instanceof ClanChestGUI chestGui) {
            chestGui.handleClick(event);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();

        if (holder instanceof AbstractGUI) {
            event.setCancelled(true);
        }

    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        InventoryHolder holder = event.getInventory().getHolder();

        if (holder instanceof AbstractGUI) {
            AbstractGUI.removeOpenGui(player);
            return;
        }

        if (holder instanceof ClanChestGUI chestGui) {
            chestGui.handleClose(event);
        }
    }
}
