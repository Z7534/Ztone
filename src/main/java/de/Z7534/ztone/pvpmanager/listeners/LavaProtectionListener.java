package de.Z7534.ztone.pvpmanager.listeners;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.utils.MessageUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LavaProtectionListener implements Listener {

    private final Pvpmanager plugin;
    private final Map<Location, PlacedLava> trackedLava = new ConcurrentHashMap<>();

    public LavaProtectionListener(Pvpmanager plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.getBucket() != Material.LAVA_BUCKET) {
            return;
        }

        Player player = event.getPlayer();
        Block targetBlock = event.getBlockClicked().getRelative(event.getBlockFace());
        Location loc = targetBlock.getLocation();

        // 1. Check if any player within 6 blocks has PvP disabled (or if placer has PvP disabled)
        for (Entity entity : loc.getWorld().getNearbyEntities(loc, 6.0, 6.0, 6.0)) {
            if (entity instanceof Player nearby && !nearby.equals(player)) {
                if (!plugin.canPvP(nearby) || !plugin.canPvP(player)) {
                    event.setCancelled(true);
                    MessageUtils.send(player, "&c[PvPManager] Du kannst hier keine Lava platzieren, da sich ein Spieler mit deaktiviertem PvP in der Nähe befindet!");
                    return;
                }
            }
        }

        // 2. Track this placed lava
        cleanOldLava();
        trackedLava.put(loc, new PlacedLava(player.getUniqueId(), System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLavaFlow(BlockFromToEvent event) {
        Location fromLoc = event.getBlock().getLocation();
        PlacedLava origin = trackedLava.get(fromLoc);
        if (origin != null && (System.currentTimeMillis() - origin.placedAt()) < 180_000L) {
            trackedLava.put(event.getToBlock().getLocation(), origin);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractFire(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getItem() == null || event.getClickedBlock() == null) {
            return;
        }

        Material mat = event.getItem().getType();
        if (mat == Material.FLINT_AND_STEEL || mat == Material.FIRE_CHARGE) {
            Player player = event.getPlayer();
            Location loc = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
            for (Entity entity : loc.getWorld().getNearbyEntities(loc, 5.0, 5.0, 5.0)) {
                if (entity instanceof Player nearby && !nearby.equals(player)) {
                    if (!plugin.canPvP(nearby) || !plugin.canPvP(player)) {
                        event.setCancelled(true);
                        MessageUtils.send(player, "&c[PvPManager] Du kannst hier kein Feuer entzünden, da sich ein Spieler mit deaktiviertem PvP in der Nähe befindet!");
                        return;
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLavaOrFireDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (plugin.canPvP(victim)) {
            return;
        }

        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK) {

            Location loc = victim.getLocation();
            long now = System.currentTimeMillis();
            boolean isPlayerLava = false;

            for (int x = -1; x <= 1 && !isPlayerLava; x++) {
                for (int y = -1; y <= 2 && !isPlayerLava; y++) {
                    for (int z = -1; z <= 1 && !isPlayerLava; z++) {
                        Location check = loc.clone().add(x, y, z).getBlock().getLocation();
                        PlacedLava pl = trackedLava.get(check);
                        if (pl != null && now - pl.placedAt() < 180_000L && !pl.placer().equals(victim.getUniqueId())) {
                            isPlayerLava = true;
                        }
                    }
                }
            }

            boolean hostileNearby = false;
            for (Entity entity : loc.getWorld().getNearbyEntities(loc, 8.0, 8.0, 8.0)) {
                if (entity instanceof Player other && !other.equals(victim)) {
                    hostileNearby = true;
                    break;
                }
            }

            if (isPlayerLava || (hostileNearby && cause == EntityDamageEvent.DamageCause.FIRE_TICK)) {
                event.setCancelled(true);
                victim.setFireTicks(0);
            }
        }
    }

    private void cleanOldLava() {
        long now = System.currentTimeMillis();
        trackedLava.entrySet().removeIf(entry -> now - entry.getValue().placedAt() > 180_000L);
    }
}
