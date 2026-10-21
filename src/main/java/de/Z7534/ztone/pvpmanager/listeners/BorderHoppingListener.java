package de.Z7534.ztone.pvpmanager.listeners;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import org.bukkit.Location;
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

        boolean wasProtected = isProtectedZone(event.getFrom());
        boolean isProtectedNow = isProtectedZone(event.getTo());

        if (!wasProtected && isProtectedNow) {
            boolean handled = plugin.getBorderHoppingManager().handleZoneEntry(event.getPlayer(), event.getFrom(), event.getTo());
            if (handled) {
                event.setTo(event.getFrom());
            }
        }
    }

    private boolean isProtectedZone(Location location) {
        if (location == null) return false;
        if (plugin.getHookManager().worldGuard().isAvailable()
                && plugin.getHookManager().worldGuard().isPvpExplicitlyDenied(location)) {
            return true;
        }
        if (plugin.getPlugin().getConfigManager().isClaimsEnabled() && plugin.getPlugin().getClaims() != null) {
            var claims = plugin.getPlugin().getClaims();
            var claim = claims.getClaimManager().getClaimAt(location);
            if (claim != null) {
                var pvpBypass = claims.getPvPClaimBypass();
                // In Claims ist PvP standardmaessig deaktiviert (Safezone), ausser per /claimspvpoverride freigegeben
                return pvpBypass == null || !pvpBypass.isClaimBypassed(claim.id());
            }
        }
        return false;
    }
}
