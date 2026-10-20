package de.Z7534.ztone.pvpmanager.managers;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.permissions.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.Set;
import java.util.UUID;

public class CombatLogPunishmentManager {

    private final Pvpmanager plugin;

    public CombatLogPunishmentManager(Pvpmanager plugin) {
        this.plugin = plugin;
    }

    public void handleLogout(Player player) {
        if (!plugin.getCombatManager().isTagged(player.getUniqueId())) {
            return;
        }
        if (player.hasPermission(Permissions.EXEMPT_COMBATLOG) || player.hasPermission(Permissions.EXEMPT)) {
            return;
        }

        UUID opponentId = plugin.getCombatManager().getLastAttacker(player.getUniqueId());
        if (opponentId == null) {
            Set<UUID> opps = plugin.getCombatManager().getOpponents(player.getUniqueId());
            if (opps != null && !opps.isEmpty()) {
                opponentId = opps.iterator().next();
            }
        }
        if (opponentId != null) {
            player.setMetadata("combatlog_attacker", new FixedMetadataValue(plugin.getPlugin(), opponentId.toString()));
        }

        // Run punishment commands (e.g. broadcast warning)
        for (String command : plugin.getConfigManager().config().getStringList("Combat Log Punishments.Commands On Combat Log")) {
            String parsed = command.replace("{player}", player.getName());
            Bukkit.getScheduler().runTask(plugin.getPlugin(), () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed));
        }

        boolean killEnabled = plugin.getConfigManager().config().getBoolean("Combat Log Punishments.Kill on Logout.Enabled", true);
        boolean dropInventory = plugin.getConfigManager().config().getBoolean("Combat Log Punishments.Kill on Logout.Player Drops.Inventory", true);
        boolean dropExperience = plugin.getConfigManager().config().getBoolean("Combat Log Punishments.Kill on Logout.Player Drops.Experience", true);
        boolean dropArmor = plugin.getConfigManager().config().getBoolean("Combat Log Punishments.Kill on Logout.Player Drops.Armor", true);

        // Handle lightning strikes for combat log punishment
        boolean lightningEnabled = plugin.getConfigManager().config().getBoolean("Combat Log Punishments.Lightning Strike.Enabled", true);
        int lightningCount = plugin.getConfigManager().config().getInt("Combat Log Punishments.Lightning Strike.Count", 10);
        // Note: Lightning color in Minecraft is always white, but we keep the config for future compatibility
        String lightningColor = plugin.getConfigManager().config().getString("Combat Log Punishments.Lightning Strike.Color", "WHITE");

        if (lightningEnabled) {
            Location location = player.getLocation();
            if (location.getWorld() != null) {
                // Spawn multiple lightning bolts
                for (int i = 0; i < lightningCount; i++) {
                    // Small random offset to prevent all lightning from stacking exactly
                    double offsetX = (Math.random() - 0.5) * 2.0; // -1 to 1
                    double offsetZ = (Math.random() - 0.5) * 2.0; // -1 to 1
                    Location strikeLocation = location.clone().add(offsetX, 0, offsetZ);
                    location.getWorld().strikeLightningEffect(strikeLocation);
                }
            }
        }

        boolean deathchestActive = plugin.getPlugin().getConfigManager().isDeathchestEnabled();
        if (killEnabled) {
            if (!deathchestActive) {
                // Drop items based on config
                Location location = player.getLocation();
                if (location.getWorld() != null) {
                    if (dropInventory) {
                        for (ItemStack item : player.getInventory().getStorageContents()) {
                            if (item != null && !item.getType().isAir()) {
                                location.getWorld().dropItemNaturally(location, item);
                            }
                        }
                    }
                    if (dropArmor) {
                        for (ItemStack item : player.getInventory().getArmorContents()) {
                            if (item != null && !item.getType().isAir()) {
                                location.getWorld().dropItemNaturally(location, item);
                            }
                        }
                    }
                    ItemStack offHand = player.getInventory().getItemInOffHand();
                    if (offHand != null && !offHand.getType().isAir() && dropInventory) { // offhand considered part of inventory drop
                        location.getWorld().dropItemNaturally(location, offHand);
                    }

                    if (dropExperience) {
                        int exp = player.getTotalExperience();
                        if (exp > 0) {
                            ExperienceOrb orb = location.getWorld().spawn(location, ExperienceOrb.class);
                            orb.setExperience(exp);
                        }
                    }
                }

                player.getInventory().clear();
                player.setExp(0);
                player.setLevel(0);
                player.setTotalExperience(0);
            }
            player.setHealth(0.0); // This will trigger death event
        }
    }
}
