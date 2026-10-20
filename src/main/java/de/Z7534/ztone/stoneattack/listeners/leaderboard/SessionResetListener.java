package de.Z7534.ztone.stoneattack.listeners.leaderboard;

import de.Z7534.ztone.stoneattack.Stoneattack;
import de.Z7534.ztone.stoneattack.modules.leaderboard.LeaderboardModule;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Setzt die Session-Auswahl (Zeitraum + Seite) eines Spielers auf
 * Global/Seite 1 zurueck, sobald er die Welt wechselt oder den
 * Server verlaesst (was bei einem Proxy-Serverwechsel aus Sicht
 * dieses Backend-Servers ebenfalls ein PlayerQuitEvent ausloest).
 */
public class SessionResetListener implements Listener {

    private final Stoneattack plugin;

    public SessionResetListener(Stoneattack plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        LeaderboardModule module = plugin.getLeaderboardModule();
        if (module != null) {
            module.getSessionManager().resetPlayer(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        LeaderboardModule module = plugin.getLeaderboardModule();
        if (module != null) {
            module.getSessionManager().forget(event.getPlayer().getUniqueId());
            module.getHologramManager().despawnAllForPlayer(event.getPlayer());
        }
    }
}
