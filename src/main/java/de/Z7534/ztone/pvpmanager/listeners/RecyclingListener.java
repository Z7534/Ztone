package de.Z7534.ztone.pvpmanager.listeners;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.permissions.Permissions;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;

import java.util.List;
import java.util.Map;

public class RecyclingListener implements Listener {

    private final Pvpmanager plugin;

    public RecyclingListener(Pvpmanager plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        Material consumed = event.getItem().getType();

        if (isStew(consumed)) {
            double healAmount = plugin.getConfigManager().config().getDouble("Other Settings.Auto Soup.Health", 0);
            if (healAmount > 0) {
                org.bukkit.Bukkit.getScheduler().runTask(plugin.getPlugin(), () -> {
                    double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                    player.setHealth(Math.min(maxHealth, player.getHealth() + healAmount));
                });
            }
        }

        if (!plugin.getConfigManager().config().getBoolean("Other Settings.Recycling.Enabled", false)) {
            return;
        }
        if (!player.hasPermission(Permissions.FEATURE_RECYCLING)) {
            return;
        }

        Material leftover = switch (consumed) {
            case POTION -> Material.GLASS_BOTTLE;
            case MILK_BUCKET -> Material.BUCKET;
            case MUSHROOM_STEW, RABBIT_STEW, BEETROOT_SOUP, SUSPICIOUS_STEW -> Material.BOWL;
            default -> null;
        };

        if (leftover == null) {
            return;
        }

        List<String> recyclable = plugin.getConfigManager().config().getStringList("Other Settings.Recycling.List");
        if (!recyclable.contains(leftover.name())) {
            return;
        }

        org.bukkit.Bukkit.getScheduler().runTask(plugin.getPlugin(), () -> {
            org.bukkit.inventory.PlayerInventory inventory = player.getInventory();
            for (int i = 0; i < inventory.getSize(); i++) {
                org.bukkit.inventory.ItemStack item = inventory.getItem(i);
                if (item != null && item.getType() == leftover) {
                    if (item.getAmount() <= 1) {
                        inventory.setItem(i, null);
                    } else {
                        item.setAmount(item.getAmount() - 1);
                    }
                    plugin.getMessageDispatcher().send(player, "Recycled_Item", Map.of("item", leftover.name()));
                    break;
                }
            }
        });
    }

    private boolean isStew(Material material) {
        return material == Material.MUSHROOM_STEW || material == Material.RABBIT_STEW
                || material == Material.BEETROOT_SOUP || material == Material.SUSPICIOUS_STEW;
    }
}
