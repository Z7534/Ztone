package de.Z7534.ztone.pvpmanager.listeners;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import org.bukkit.Bukkit;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EntityDeathListener implements Listener {

    private final Pvpmanager plugin;

    public EntityDeathListener(Pvpmanager plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Check if this is a combat log punishment death
        boolean isCombatLogDeath = victim.hasMetadata("combatlog_punishment") &&
                !victim.getMetadata("combatlog_punishment").isEmpty() &&
                victim.getMetadata("combatlog_punishment").get(0).value() instanceof Boolean &&
                (Boolean) victim.getMetadata("combatlog_punishment").get(0).value();

        // Untag the victim since they're dead and can't be in combat anymore
        plugin.getCombatManager().untag(victim);
        plugin.getCombatManager().onOpponentDeath(victim.getUniqueId());

        boolean isPvpKill = killer != null && !killer.equals(victim);
        boolean deathchestActive = plugin.getPlugin().getConfigManager().isDeathchestEnabled();

        if (isPvpKill) {
            if (!deathchestActive) {
                handleProtectedDrops(event, killer, victim);
            }
        } else {
            if (!deathchestActive) {
                applyDropMode(event);
            }
        }

        // Suppress default death message for combat log punishments
        if (isCombatLogDeath) {
            event.setDeathMessage(null);
        }

        if (!isPvpKill) {
            return;
        }

        boolean excluded = plugin.getConfigManager().config()
                .getStringList("Player Kills.WorldGuard Exclusions").contains(victim.getWorld().getName());

        if (!excluded) {
            handleRewardsAndPenalties(killer, victim);
            handleCommandsOnKill(killer, victim, event);
            handleDeathEffects(victim);
        }

        for (String command : plugin.getConfigManager().config().getStringList("Player Kills.Commands On Respawn")) {
            Bukkit.getScheduler().runTask(plugin.getPlugin(), () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    command.replace("{player}", victim.getName())));
        }

        plugin.getAntiKillAbuseManager().registerKillAndCheckAbuse(killer, victim);

        // Remove the metadata after processing
        if (isCombatLogDeath) {
            victim.removeMetadata("combatlog_punishment", plugin.getPlugin());
        }
    }

    private void handleProtectedDrops(PlayerDeathEvent event, Player killer, Player victim) {
        long protectSeconds = plugin.getConfigManager().config().getLong("Player Kills.Loot Protection.Time",
                plugin.getConfigManager().config().getLong("Loot Protection.Time", 0));
        boolean lootProtectionEnabled = plugin.getConfigManager().config().getBoolean("Player Kills.Loot Protection.Enabled",
                plugin.getConfigManager().config().getBoolean("Loot Protection.Enabled", false));

        if (!lootProtectionEnabled || protectSeconds <= 0 || victim.getLocation().getWorld() == null) {
            applyDropMode(event);
            return;
        }

        // Drop all items and register them for killer-only pickup
        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        event.getDrops().clear();

        for (ItemStack item : drops) {
            if (item != null && !item.getType().isAir()) {
                Item droppedItem = victim.getWorld().dropItemNaturally(victim.getLocation(), item);
                plugin.getLootProtectionManager().registerItem(droppedItem, killer.getUniqueId(), protectSeconds);
            }
        }

        // Drop all experience and register for killer-only pickup
        int droppedExp = event.getDroppedExp();
        if (droppedExp > 0) {
            ExperienceOrb orb = victim.getWorld().spawn(victim.getLocation(), ExperienceOrb.class);
            orb.setExperience(droppedExp);
            plugin.getLootProtectionManager().registerOrb(orb, killer.getUniqueId(), protectSeconds);
            event.setDroppedExp(0);
        }

        plugin.getMessageDispatcher().send(killer, "Loot_Protection_Winner_Notice",
                Map.of("time", String.valueOf(protectSeconds), "victim", victim.getName()));
    }

    private void applyDropMode(PlayerDeathEvent event) {
        String mode = plugin.getConfigManager().config().getString("Other Settings.Player Drop Mode", "ALWAYS");
        Player killer = event.getEntity().getKiller();
        switch (mode.toUpperCase()) {
            case "KEEP" -> {
                event.getDrops().clear();
                event.setKeepInventory(true);
                event.setDroppedExp(0);
            }
            case "CLEAR" -> {
                event.getDrops().clear();
                event.setDroppedExp(0);
            }
            case "TRANSFER" -> {
                if (killer != null) {
                    List<ItemStack> drops = new ArrayList<>(event.getDrops());
                    event.getDrops().clear();
                    for (ItemStack item : drops) {
                        Map<Integer, ItemStack> overflow = killer.getInventory().addItem(item);
                        overflow.values().forEach(remaining -> killer.getWorld().dropItemNaturally(killer.getLocation(), remaining));
                    }
                }
            }
            case "DROP", "ALWAYS" -> {
            }
            default -> {
            }
        }
    }

    private void handleRewardsAndPenalties(Player killer, Player victim) {
        double expSteal = plugin.getConfigManager().config().getDouble("Player Kills.Exp Steal", 0);
        if (expSteal > 0) {
            int stolen = (int) (victim.getTotalExperience() * expSteal);
            if (stolen > 0) {
                killer.giveExp(stolen);
                plugin.getMessageDispatcher().send(killer, "Exp_Won", Map.of("victim", victim.getName(), "exp", String.valueOf(stolen)));
                plugin.getMessageDispatcher().send(victim, "Exp_Stolen", Map.of("player", killer.getName(), "exp", String.valueOf(stolen)));
            }
        }
    }

    private void handleCommandsOnKill(Player killer, Player victim, PlayerDeathEvent event) {
        String itemName = killer.getInventory().getItemInMainHand().getType().name();
        for (String command : plugin.getConfigManager().config().getStringList("Player Kills.Commands On Kill.Commands")) {
            String parsed = command.replace("{player}", killer.getName())
                    .replace("{victim}", victim.getName())
                    .replace("{item}", itemName);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }

    private void handleDeathEffects(Player victim) {
        if (plugin.getConfigManager().config().getBoolean("Player Kills.Death Effects.Lightning", false)) {
            victim.getWorld().strikeLightningEffect(victim.getLocation());
        }
    }
}
