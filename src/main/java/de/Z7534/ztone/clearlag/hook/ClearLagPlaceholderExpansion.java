package de.Z7534.ztone.clearlag.hook;

import de.Z7534.ztone.clearlag.manager.ClearLagManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClearLagPlaceholderExpansion extends PlaceholderExpansion {

    private final ClearLagManager manager;

    public ClearLagPlaceholderExpansion(ClearLagManager manager) {
        this.manager = manager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "clearlag";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Z7534";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.5";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        return switch (params.toLowerCase()) {
            case "countdown" -> String.valueOf(manager.getRemainingSeconds());
            case "countdown_formatted", "countdown_digital" -> manager.getCountdownDigital();
            case "countdown_smallcaps" -> manager.getCountdownSmallCaps();
            default -> null;
        };
    }
}
