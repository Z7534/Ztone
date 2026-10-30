package de.Z7534.ztone.stonesit.util;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.claims.Claims;
import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.stonesit.config.StoneSitConfig;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class EnvironmentUtil {

    private final Ztone plugin;
    private final StoneSitConfig config;

    public EnvironmentUtil(Ztone plugin, StoneSitConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean isEntityInAllowedWorld(Entity entity) {
        if (hasPermission(entity, "stonesit.bypass.world", "stonesit.bypass.*", "GSit.ByPass.World", "GSit.ByPass.*")) {
            return true;
        }
        String worldName = entity.getWorld().getName().toLowerCase(Locale.ROOT);
        if (config.getWorldBlacklist().contains(worldName)) {
            return false;
        }
        if (!config.getWorldWhitelist().isEmpty() && !config.getWorldWhitelist().contains(worldName)) {
            return false;
        }
        return true;
    }

    public boolean canUseInLocation(Location location, Player player) {
        if (hasPermission(player, "stonesit.bypass.region", "stonesit.bypass.*", "GSit.ByPass.Region", "GSit.ByPass.*")) {
            return true;
        }
        Claims claimsModule = plugin.getClaims();
        if (claimsModule != null && plugin.getConfigManager() != null && plugin.getConfigManager().isClaimsEnabled()) {
            var claimManager = claimsModule.getClaimManager();
            if (claimManager != null) {
                Claim claim = claimManager.getClaimAt(location);
                if (claim != null) {
                    if (claim.isBanned(player.getUniqueId())) {
                        return false;
                    }
                    if (config.isTrustedRegionOnly()) {
                        return claim.isOwnerOrManager(player.getUniqueId())
                                || claim.isTrusted(player.getUniqueId())
                                || (claim.isClanTrustEnabled() && plugin.isSameClan(claim.owner(), player.getUniqueId()))
                                || (claim.isAllyTrustEnabled() && plugin.isAlly(claim.owner(), player.getUniqueId()));
                    }
                }
            }
        }
        return true;
    }

    public static boolean hasPermission(org.bukkit.permissions.Permissible permissible, String... permissions) {
        if (permissible.hasPermission("stonesit.*") || permissible.hasPermission("stonesit.admin") || permissible.hasPermission("GSit.*")) {
            return true;
        }
        for (String perm : permissions) {
            if (permissible.hasPermission(perm)) {
                return true;
            }
        }
        return false;
    }
}
