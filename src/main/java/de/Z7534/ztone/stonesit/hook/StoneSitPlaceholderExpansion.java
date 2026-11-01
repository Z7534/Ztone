package de.Z7534.ztone.stonesit.hook;

import de.Z7534.ztone.stonesit.manager.SitManager;
import de.Z7534.ztone.stonesit.model.StoneSitPose;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class StoneSitPlaceholderExpansion extends PlaceholderExpansion {

    private final SitManager sitManager;
    private final String identifier;

    public StoneSitPlaceholderExpansion(SitManager sitManager, String identifier) {
        this.sitManager = sitManager;
        this.identifier = identifier;
    }

    @Override
    public @NotNull String getIdentifier() {
        return identifier;
    }

    @Override
    public @NotNull String getAuthor() {
        return "Z7534";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.1.3";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return null;
        }
        return switch (params.toLowerCase()) {
            case "sitting" -> String.valueOf(sitManager.isEntitySitting(player) || sitManager.isPlayerInPlayerSitStack(player));
            case "blocksitting" -> String.valueOf(sitManager.isEntitySitting(player));
            case "playersitting" -> String.valueOf(sitManager.isPlayerInPlayerSitStack(player));
            case "spinning" -> String.valueOf(sitManager.isPlayerSpinning(player));
            case "posing" -> String.valueOf(sitManager.isPlayerPosing(player) || sitManager.isPlayerSpinning(player));
            case "pose" -> {
                if (sitManager.isPlayerSpinning(player)) {
                    yield "spin";
                }
                StoneSitPose pose = sitManager.getPoseByPlayer(player);
                yield pose != null ? pose.getPoseType().getName() : "none";
            }
            default -> null;
        };
    }
}
