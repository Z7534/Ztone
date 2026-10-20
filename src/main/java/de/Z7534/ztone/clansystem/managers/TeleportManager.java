package de.Z7534.ztone.clansystem.managers;

import de.Z7534.ztone.Ztone;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportManager implements Listener {

    private record PendingTeleport(Location startLocation, Runnable onCancelled) {
    }

    private final Ztone plugin;
    private final Map<UUID, PendingTeleport> pending = new ConcurrentHashMap<>();

    public TeleportManager(Ztone plugin) {
        this.plugin = plugin;
    }

    public void startWarmup(Player player, int warmupSeconds, boolean cancelOnMove, Runnable onComplete, Runnable onCancelled) {
        UUID uuid = player.getUniqueId();

        if (warmupSeconds <= 0) {
            onComplete.run();
            return;
        }

        if (cancelOnMove) {
            pending.put(uuid, new PendingTeleport(player.getLocation().clone(), onCancelled));
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (cancelOnMove) {

                if (pending.remove(uuid) == null) {
                    return;
                }
            }
            onComplete.run();
        }, warmupSeconds * 20L);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (pending.isEmpty()) {
            return;
        }
        UUID uuid = event.getPlayer().getUniqueId();
        PendingTeleport p = pending.get(uuid);
        if (p == null) {
            return;
        }

        Location to = event.getTo();
        if (to == null) {
            return;
        }
        Location start = p.startLocation();

        if (to.getBlockX() != start.getBlockX()
                || to.getBlockY() != start.getBlockY()
                || to.getBlockZ() != start.getBlockZ()) {
            pending.remove(uuid);
            p.onCancelled().run();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
