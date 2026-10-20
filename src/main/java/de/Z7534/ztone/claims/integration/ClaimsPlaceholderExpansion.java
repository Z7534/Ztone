package de.Z7534.ztone.claims.integration;

import de.Z7534.ztone.claims.config.ConfigManager;
import de.Z7534.ztone.claims.manager.ClaimManager;
import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.claims.model.PlayerData;
import de.Z7534.ztone.claims.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**\
 * PlaceholderAPI-Erweiterung für das Claims-Modul.\
 * Identifier: {@code %claims_...%}\
 */
public final class ClaimsPlaceholderExpansion extends PlaceholderExpansion {

    private final ClaimManager claimManager;
    private final ConfigManager config;

    public ClaimsPlaceholderExpansion(ClaimManager claimManager, ConfigManager config) {
        this.claimManager = claimManager;
        this.config = config;
    }

    @Override
    public String getIdentifier() {
        return "claims";
    }

    @Override
    public String getAuthor() {
        return "Z7534";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) {
            return "";
        }

        switch (params.toLowerCase()) {
            case "claim", "current_claim", "current_owner" -> {
                Claim claim = claimManager.getClaimAt(player.getLocation());
                if (claim == null) {
                    return legacy(config.message("placeholder-not-claimed"));
                }
                return ownerName(claim.owner());
            }
            case "current_name", "current_claim_name" -> {
                Claim claim = claimManager.getClaimAt(player.getLocation());
                if (claim == null) {
                    return legacy(config.message("placeholder-not-claimed"));
                }
                return claim.name() != null ? claim.name() : ownerName(claim.owner());
            }
            case "claims", "used", "slots_used", "count" -> {
                return String.valueOf(claimManager.getUsedClaimSlots(player.getUniqueId()));
            }
            case "max", "slots_max", "max_claims" -> {
                int max = claimManager.getMaxClaimSlots(player);
                return max == Integer.MAX_VALUE ? "∞" : String.valueOf(max);
            }
            case "free", "slots_free" -> {
                int max = claimManager.getMaxClaimSlots(player);
                if (max == Integer.MAX_VALUE) {
                    return "∞";
                }
                int used = claimManager.getUsedClaimSlots(player.getUniqueId());
                return String.valueOf(Math.max(0, max - used));
            }
            case "bonus", "bonus_slots", "bonus_claims" -> {
                return claimManager.findPlayerData(player.getUniqueId())
                        .map(pd -> String.valueOf(pd.bonusClaimSlots()))
                        .orElse("0");
            }
            case "is_in_claim" -> {
                return claimManager.getClaimAt(player.getLocation()) != null ? "true" : "false";
            }
            case "is_owner" -> {
                Claim claim = claimManager.getClaimAt(player.getLocation());
                return (claim != null && claim.owner().equals(player.getUniqueId())) ? "true" : "false";
            }
            case "is_trusted" -> {
                Claim claim = claimManager.getClaimAt(player.getLocation());
                if (claim == null) {
                    return "false";
                }
                return (claim.owner().equals(player.getUniqueId()) || claim.isTrusted(player.getUniqueId())) ? "true" : "false";
            }
            default -> {
                return null;
            }
        }
    }

    private String ownerName(UUID owner) {
        String name = claimManager.findPlayerData(owner)
                .map(PlayerData::name)
                .orElse(null);
        if (name == null) {
            name = Bukkit.getOfflinePlayer(owner).getName();
        }
        return name != null ? name : "?";
    }

    private String legacy(String rawMessage) {
        return LegacyComponentSerializer.legacySection().serialize(Text.line(rawMessage));
    }
}
