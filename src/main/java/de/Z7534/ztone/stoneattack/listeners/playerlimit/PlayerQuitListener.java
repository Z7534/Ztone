package de.Z7534.ztone.stoneattack.listeners.playerlimit;

import de.Z7534.ztone.stoneattack.Stoneattack;
import de.Z7534.ztone.stoneattack.modules.playerlimit.PlayerLimitModule;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final Stoneattack plugin;

    public PlayerQuitListener(Stoneattack plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PlayerLimitModule module = plugin.getPlayerLimitModule();
        if (module == null) {
            return;
        }
        boolean wasTracked = module.getJoinOrder().contains(event.getPlayer().getUniqueId());
        module.onQuit(event.getPlayer());

        if (wasTracked && plugin.getConfigManager().get().getBoolean("playerlimit.join-order-logging.enabled", true)) {
            module.getLogger().logLeave(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        }
    }
}
