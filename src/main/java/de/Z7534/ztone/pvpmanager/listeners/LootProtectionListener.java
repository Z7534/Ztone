package de.Z7534.ztone.pvpmanager.listeners;

import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import de.Z7534.ztone.pvpmanager.Pvpmanager;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;

import java.util.Map;

public class LootProtectionListener implements Listener {

    private final Pvpmanager plugin;

    public LootProtectionListener(Pvpmanager plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Item item = event.getItem();
        if (!plugin.getLootProtectionManager().canPickupItem(item, player)) {
            event.setCancelled(true);
            long left = plugin.getLootProtectionManager().remainingSeconds(item);
            plugin.getMessageDispatcher().send(player, "Loot_Protection_Pickup_Denied",
                    Map.of("time", String.valueOf(left)));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickupExp(PlayerPickupExperienceEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getLootProtectionManager().canPickupOrb(event.getExperienceOrb(), player)) {
            event.setCancelled(true);
        }
    }
}
