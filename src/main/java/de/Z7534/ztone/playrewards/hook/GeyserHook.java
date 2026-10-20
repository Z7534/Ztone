package de.Z7534.ztone.playrewards.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.UUID;

public class GeyserHook {

    private final boolean floodgateInstalled;

    public GeyserHook() {
        this.floodgateInstalled = Bukkit.getPluginManager().isPluginEnabled("floodgate");
    }

    /**
     * Prüft, ob der übergebene Spieler ein Bedrock-Spieler ist.
     */
    public boolean isBedrockPlayer(Player player) {
        if (player == null) return false;
        return isBedrockPlayer(player.getUniqueId());
    }

    /**
     * Prüft anhand der UUID, ob es sich um einen Bedrock-Spieler handelt.
     */
    public boolean isBedrockPlayer(UUID uuid) {
        if (uuid == null) return false;
        if (floodgateInstalled) {
            try {
                return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
            } catch (Throwable ignored) {}
        }
        // Fallback: Bedrock-UUIDs via Floodgate starten typischerweise mit 00000000-0000-0000
        return uuid.getMostSignificantBits() == 0L;
    }
}
