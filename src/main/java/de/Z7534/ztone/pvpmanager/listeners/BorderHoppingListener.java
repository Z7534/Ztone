package de.Z7534.ztone.pvpmanager.listeners;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class BorderHoppingListener implements Listener {

    private final Pvpmanager plugin;

    public BorderHoppingListener(Pvpmanager plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getBorderHoppingManager().isEnabled()) {
            return;
        }
        if (!plugin.getCombatManager().isTagged(event.getPlayer().getUniqueId())) {
            return;
        }
        if (!plugin.getHookManager().worldGuard().isAvailable()) {
            return;
        }

        boolean wasProtected = isProtectedZone(event.getFrom());
        boolean isProtectedNow = isProtectedZone(event.getTo());

        if (!wasProtected && isProtectedNow) {
            boolean handled = plugin.getBorderHoppingManager().handleZoneEntry(event.getPlayer(), event.getFrom(), event.getTo());
            if (handled) {
                event.setTo(event.getFrom());
            }
        }
    }

    private boolean isProtectedZone(org.bukkit.Location location) {
        return plugin.getHookManager().worldGuard().isPvpExplicitlyDenied(location);
    }
}
