package de.Z7534.ztone.claims.manager;

import de.Z7534.ztone.claims.config.ConfigManager;
import de.Z7534.ztone.claims.model.ChunkPos;
import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.claims.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class BanEnforcer {

    private final ConfigManager config;
    private final SafeSpotFinder safeSpotFinder;
    private final BypassManager bypass;

    public BanEnforcer(ConfigManager config, SafeSpotFinder safeSpotFinder, BypassManager bypass) {
        this.config = config;
        this.safeSpotFinder = safeSpotFinder;
        this.bypass = bypass;
    }

    public SafeSpotFinder getSafeSpotFinder() {
        return safeSpotFinder;
    }

    public void handleBan(Claim claim, UUID target, String issuerName) {
        Player online = Bukkit.getPlayer(target);
        if (online == null) return;

        if (bypass.isActive(online)) {
            online.sendMessage(Text.line(config.prefix() + config.message("banned-bypass-notice"),
                    Map.of("spieler", issuerName)));
            return;
        }

        ChunkPos pos = new ChunkPos(online.getLocation().getWorld().getName(),
                online.getLocation().getBlockX() >> 4, online.getLocation().getBlockZ() >> 4);
        if (!claim.chunks().contains(pos)) return;

        evict(online, claim);
    }

    /**
     * Schließt alle fremden Spieler aus dem Claim aus (wenn /claim ban * gesetzt wurde).
     */
    public void handleBanAll(Claim claim, String issuerName) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (claim.isBanned(online.getUniqueId())) {
                if (bypass.isActive(online)) {
                    online.sendMessage(Text.line(config.prefix() + config.message("banned-bypass-notice"),
                            Map.of("spieler", issuerName)));
                    continue;
                }
                ChunkPos pos = new ChunkPos(online.getLocation().getWorld().getName(),
                        online.getLocation().getBlockX() >> 4, online.getLocation().getBlockZ() >> 4);
                if (claim.chunks().contains(pos)) {
                    evict(online, claim);
                }
            }
        }
    }

    /**
     * Findet einen sicheren Platz außerhalb des übergebenen Claims.
     */
    public Optional<Location> findSafeSpotOutside(Location loc, Claim claim) {
        return safeSpotFinder.findSafeSpot(loc, claim, config.banSafeSpotRadius());
    }

    /**
     * Teleportiert einen (gebannten) Spieler aus dem Claim hinaus. Wird auch benutzt, wenn
     * ein Spieler bereits im Claim steht, z.B. weil er gebannt wurde, während er offline war.
     */
    public void evict(Player online, Claim claim) {
        if (online.isInsideVehicle()) {
            online.leaveVehicle();
        }
        if (online.isGliding()) {
            online.setGliding(false);
        }
        online.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        try {
            var stoneSit = de.Z7534.ztone.Ztone.getInstance().getStoneSit();
            if (stoneSit != null && stoneSit.getSitManager() != null) {
                stoneSit.getSitManager().stopAllForPlayer(online, de.Z7534.ztone.stonesit.model.StopReason.PLUGIN);
            }
        } catch (Throwable ignored) {}

        safeSpotFinder.findSafeSpot(online.getLocation(), claim, config.banSafeSpotRadius())
                .ifPresentOrElse(loc -> {
                    online.teleport(loc);
                    online.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                    online.setGliding(false);
                }, () -> {
                    online.teleport(online.getWorld().getSpawnLocation());
                    online.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                    online.setGliding(false);
                });
        online.sendMessage(Text.line(config.prefix() + config.message("banned-teleported")));
    }
}
