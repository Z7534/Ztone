package de.Z7534.ztone.stoneattack.modules.leaderboard.hologram;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Faengt Rechts- und Linksklicks auf die Interaction-Entities
 * der Hologramm-Buttons/-Pfeile ab (inkl. Geyser/Bedrock Touch- und Controller-Support).
 */
public class HologramClickListener implements Listener {

    /** Ein einzelner Klick kann mehrere Events ausloesen -> entprellen. */
    private static final long DEBOUNCE_MS = 60L;

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

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            handle(player, event.getEntity().getEntityId(), event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractAirOrBlock(PlayerInteractEvent event) {
        Action a = event.getAction();
        if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK
                || a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) {
            Player player = event.getPlayer();
            long now = System.currentTimeMillis();
            Long last = lastClick.get(player.getUniqueId());
            if (last != null && now - last < DEBOUNCE_MS) {
                return;
            }
            if (hologramManager.tryRaycastClick(player)) {
                lastClick.put(player.getUniqueId(), now);
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwing(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Long last = lastClick.get(player.getUniqueId());
        if (last != null && now - last < DEBOUNCE_MS) {
            return;
        }
        if (hologramManager.tryRaycastClick(player)) {
            lastClick.put(player.getUniqueId(), now);
            event.setCancelled(true);
        }
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
