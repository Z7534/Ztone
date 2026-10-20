package de.Z7534.ztone.claims.listener;

import de.Z7534.ztone.claims.gui.Gui;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class GuiListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Gui gui)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;

        int topSize = e.getView().getTopInventory().getSize();
        if (e.getRawSlot() >= 0 && e.getRawSlot() < topSize) {
            gui.onClick(player, e.getRawSlot(), e.getClick());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Gui) {
            e.setCancelled(true);
        }
    }
}
