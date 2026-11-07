package de.Z7534.ztone.stoneattack.modules.leaderboard.hologram;

import de.Z7534.ztone.playrewards.hook.GeyserHook;
import de.Z7534.ztone.stoneattack.Stoneattack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verfolgt den Status des Resourcepacks pro Spieler.
 *
 * Verhindert Missing-Texture (violett-schwarze Würfel) und schwebendes Vanilla-Papier:
 * - Modus A (Resourcepack aktiv mit Custom-Item-Displays) wird standardmäßig für Java-Spieler gerendert.
 * - Bei {@code DECLINED}, {@code FAILED_DOWNLOAD} oder {@code FAILED_RELOAD} greift für diesen Spieler
 *   automatisch Modus B (Fallback-Modus mit prozeduralen Boxen).
 * - Bedrock-/Geyser-Spieler werden standardmäßig automatisch im Fallback-Modus behandelt.
 */
public class ResourcePackStatusTracker implements Listener {

    private final Stoneattack plugin;
    private final HologramManager hologramManager;
    private final GeyserHook geyserHook;
    private final Map<UUID, Boolean> packStatus = new ConcurrentHashMap<>();

    public ResourcePackStatusTracker(Stoneattack plugin, HologramManager hologramManager) {
        this.plugin = plugin;
        this.hologramManager = hologramManager;
        this.geyserHook = new GeyserHook();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        boolean loaded = event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED;
        boolean failed = event.getStatus() == PlayerResourcePackStatusEvent.Status.DECLINED
                || event.getStatus() == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD
                || event.getStatus() == PlayerResourcePackStatusEvent.Status.FAILED_RELOAD;

        if (loaded) {
            Boolean previous = packStatus.put(player.getUniqueId(), Boolean.TRUE);
            if (previous == null || !previous) {
                if (hologramManager != null) {
                    hologramManager.onResourcePackStatusChanged(player);
                }
            }
        } else if (failed) {
            Boolean previous = packStatus.put(player.getUniqueId(), Boolean.FALSE);
            if (previous == null || previous) {
                if (hologramManager != null) {
                    hologramManager.onResourcePackStatusChanged(player);
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        packStatus.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Prüft, ob das Resourcepack für den Spieler aktiv genutzt werden soll.
     */
    public boolean hasResourcePack(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        HologramPositionStore store = hologramManager != null ? hologramManager.getPositionStore() : null;
        if (store != null && !store.isResourcePackEnabled()) {
            return false;
        }

        // Bedrock-Prüfung: Bedrock-Clients nutzen immer das Fallback (Modus B)
        if (isBedrockPlayer(player)) {
            String bedrockMode = store != null ? store.getBedrockMode() : "AUTO";
            if (!"DISABLED".equalsIgnoreCase(bedrockMode)) {
                return false;
            }
        }

        Boolean status = packStatus.get(player.getUniqueId());
        // Fallback greift NUR, wenn der Spieler das Pack explizit abgelehnt hat oder der Download fehlschlug
        if (Boolean.FALSE.equals(status)) {
            return false;
        }

        return true;
    }

    /**
     * Prüft, ob der Spieler über Bedrock/Geyser verbunden ist.
     */
    public boolean isBedrockPlayer(Player player) {
        if (player == null) {
            return false;
        }
        return geyserHook.isBedrockPlayer(player);
    }

    public void clear() {
        packStatus.clear();
    }
}
