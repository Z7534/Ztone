package de.Z7534.ztone.clearlag.hook;

import de.Z7534.ztone.clearlag.manager.ClearLagManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
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
        return "1.0.2";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        String p = params.toLowerCase();
        return switch (p) {
            case "countdown", "smallcaps", "unformatted" -> manager.getCountdownSmallCaps();
            case "time", "digital" -> manager.getCountdownDigital();
            case "seconds", "sec" -> String.valueOf(manager.getRemainingSeconds());
            case "minutes", "min" -> String.valueOf(manager.getRemainingSeconds() / 60);
            default -> null;
        };
    }
}
