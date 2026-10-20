package de.Z7534.ztone.stoneattack.listeners.playerlimit;

import de.Z7534.ztone.stoneattack.Stoneattack;
import de.Z7534.ztone.stoneattack.modules.playerlimit.PlayerLimitModule;
import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Passt die im Multiplayer-Serverbrowser angezeigte Maximalzahl an
 * Spielern an das aktuell gesetzte Limit an (rein kosmetisch, hat
 * keinen Einfluss auf die tatsaechliche Durchsetzung, die passiert
 * ausschliesslich im PlayerLoginEvent).
 */
public class ServerListPingListener implements Listener {

    private final Stoneattack plugin;

    public ServerListPingListener(Stoneattack plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPing(PaperServerListPingEvent event) {
        PlayerLimitModule module = plugin.getPlayerLimitModule();
        if (module == null || !module.isEnabled()) {
            return;
        }
        if (!plugin.getConfigManager().get().getBoolean("playerlimit.ping-shows-limit", true)) {
            return;
        }
        event.setMaxPlayers(module.getLimit());
    }
}
