package de.Z7534.ztone.stoneattack.modules.leaderboard.hologram;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Faengt Rechts- und Linksklicks auf die (unsichtbaren) Interaction-Entities
 * der Hologramm-Buttons/-Pfeile ab. Normale Bukkit-Events statt
 * Paket-Abfangen - laeuft immer auf dem Main-Thread.
 */
public class HologramClickListener implements Listener {

    /** Ein einzelner Klick kann mehrere Events ausloesen (Interact + InteractAt, beide Haende) -> entprellen. */
    private static final long DEBOUNCE_MS = 150L;

    private final HologramManager hologramManager;
    private final Map<UUID, Long> lastClick = new HashMap<>();

    public HologramClickListener(HologramManager hologramManager) {
        this.hologramManager = hologramManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        handle(event.getPlayer(), event.getRightClicked().getEntityId(), event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        handle(event.getPlayer(), event.getRightClicked().getEntityId(), event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        handle(event.getPlayer(), event.getAttacked().getEntityId(), event);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastClick.remove(event.getPlayer().getUniqueId());
    }

    private void handle(Player player, int entityId, org.bukkit.event.Cancellable event) {
        if (!hologramManager.isTrackedEntity(entityId)) {
            return;
        }
        // Klicks auf Hologramm-Elemente duerfen keine normalen Spielaktionen ausloesen.
        event.setCancelled(true);

        long now = System.currentTimeMillis();
        Long last = lastClick.get(player.getUniqueId());
        if (last != null && now - last < DEBOUNCE_MS) {
            return;
        }
        lastClick.put(player.getUniqueId(), now);

        hologramManager.handleClick(player, entityId);
    }
}
