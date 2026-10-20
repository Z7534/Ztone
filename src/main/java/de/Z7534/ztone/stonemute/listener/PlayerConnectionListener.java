package de.Z7534.ztone.stonemute.listener;

import de.Z7534.ztone.stonemute.mute.MuteService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerConnectionListener implements Listener {

    private final MuteService muteService;

    public PlayerConnectionListener(MuteService muteService) {
        this.muteService = muteService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        muteService.refresh(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        muteService.forget(event.getPlayer().getUniqueId());
    }
}
