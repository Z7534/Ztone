package de.Z7534.ztone.pvpmanager.listeners;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class CombatLogListener implements Listener {

    private final Pvpmanager plugin;

    public CombatLogListener(Pvpmanager plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getCombatManager().isTagged(player.getUniqueId())) {
            // Suppress the default left/quit message
            event.setQuitMessage(null);
            // Mark player as combat logging for death message suppression
            player.setMetadata("combatlog_punishment", new org.bukkit.metadata.FixedMetadataValue(plugin.getPlugin(), true));
            plugin.getCombatLogPunishmentManager().handleLogout(player);
        }
        // Nametag/BossBar aufraeumen, damit kein Team-Eintrag zurueckbleibt
        plugin.getDisplayManager().clearCombatDisplay(player);
    }
}
