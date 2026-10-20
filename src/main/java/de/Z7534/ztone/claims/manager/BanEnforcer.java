package de.Z7534.ztone.claims.manager;

import de.Z7534.ztone.claims.config.ConfigManager;
import de.Z7534.ztone.claims.model.ChunkPos;
import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.claims.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
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
     * Teleportiert einen (gebannten) Spieler aus dem Claim hinaus. Wird auch benutzt, wenn
     * ein Spieler bereits im Claim steht, z.B. weil er gebannt wurde, während er offline war.
     */
    public void evict(Player online, Claim claim) {
        safeSpotFinder.findSafeSpot(online.getLocation(), claim, config.banSafeSpotRadius())
                .ifPresentOrElse(online::teleport, () -> online.teleport(online.getWorld().getSpawnLocation()));
        online.sendMessage(Text.line(config.prefix() + config.message("banned-teleported")));
    }
}
