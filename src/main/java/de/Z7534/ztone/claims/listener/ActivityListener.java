package de.Z7534.ztone.claims.listener;

import de.Z7534.ztone.claims.config.ConfigManager;
import de.Z7534.ztone.claims.manager.ActivityManager;
import de.Z7534.ztone.claims.manager.BanEnforcer;
import de.Z7534.ztone.claims.manager.BypassManager;
import de.Z7534.ztone.claims.manager.ChatInputManager;
import de.Z7534.ztone.claims.manager.ClaimManager;
import de.Z7534.ztone.claims.manager.ClaimMapManager;
import de.Z7534.ztone.claims.manager.SelectionManager;
import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.claims.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ActivityListener implements Listener {

    /** Mindestabstand zwischen zwei "Du bist gebannt"-Nachrichten pro Spieler. */
    private static final long BAN_MESSAGE_COOLDOWN_MS = 2000L;

    private final JavaPlugin plugin;
    private final ActivityManager activityManager;
    private final ClaimManager claimManager;
    private final SelectionManager selectionManager;
    private final ChatInputManager chatInputManager;
    private final ConfigManager config;
    private final BypassManager bypass;
    private final BanEnforcer banEnforcer;
    private final ClaimMapManager claimMapManager;

    private final Map<UUID, Long> lastBanMessage = new HashMap<>();

    public ActivityListener(JavaPlugin plugin, ActivityManager activityManager, ClaimManager claimManager,
                             SelectionManager selectionManager, ChatInputManager chatInputManager,
                             ConfigManager config, BypassManager bypass, BanEnforcer banEnforcer,
                             ClaimMapManager claimMapManager) {
        this.plugin = plugin;
        this.activityManager = activityManager;
        this.claimManager = claimManager;
        this.selectionManager = selectionManager;
        this.chatInputManager = chatInputManager;
        this.config = config;
        this.bypass = bypass;
        this.banEnforcer = banEnforcer;
        this.claimMapManager = claimMapManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        // Der Bypass gilt nie über eine Session hinaus: bei jedem Beitritt ist er deaktiviert,
        // außer der Spieler besitzt die Auto-Bypass-on-Join-Permission.
        bypass.reset(player.getUniqueId());
        if (player.hasPermission(BypassManager.PERMISSION_AUTO_JOIN)) {
            bypass.setActive(player.getUniqueId(), true);
        }
        claimManager.getOrCreatePlayerData(player.getUniqueId(), player.getName());
        activityManager.recordActivity(player.getUniqueId());
        activityManager.checkEntryHint(player);
        // Wurde der Spieler gebannt, während er offline war, und steht er noch im Claim, holen wir ihn raus.
        Bukkit.getScheduler().runTask(plugin, () -> evictIfBanned(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();
        bypass.reset(uuid);
        lastBanMessage.remove(uuid);
        activityManager.forgetPlayer(uuid);
        if (claimMapManager != null) {
            claimMapManager.restoreOffHand(player);
        }
        selectionManager.end(uuid);
        chatInputManager.cancel(uuid);
        claimManager.findPlayerData(uuid).ifPresent(pd -> {
            pd.setLastSeen(System.currentTimeMillis());
            claimManager.savePlayerAsync(pd);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        activityManager.recordActivity(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;

        if (from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()) {
            activityManager.recordActivity(e.getPlayer().getUniqueId());
            if (enforceBan(e)) {
                // Bewegung wurde abgebrochen: keinen Eintrittshinweis für den Claim zeigen, den er nicht betritt.
                return;
            }
        }

        if ((from.getBlockX() >> 4) != (to.getBlockX() >> 4)
                || (from.getBlockZ() >> 4) != (to.getBlockZ() >> 4)) {
            activityManager.checkEntryHint(e.getPlayer(), to);
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        Player player = e.getPlayer();
        if (e.getTo() == null) return;
        if (!bypass.isActive(player)) {
            Claim claim = claimManager.getClaimAt(e.getTo());
            if (claim != null && claim.isBanned(player.getUniqueId())) {
                e.setCancelled(true);
                sendBanMessage(player);
                return;
            }
        }

        activityManager.checkEntryHint(player, e.getTo());
    }

    /**
     * @return true, wenn die Bewegung abgebrochen wurde
     */
    private boolean enforceBan(PlayerMoveEvent e) {
        Player player = e.getPlayer();
        Claim target = claimManager.getClaimAt(e.getTo());
        if (target == null || !target.isBanned(player.getUniqueId())) return false;
        if (bypass.isActive(player)) return false;

        Claim origin = claimManager.getClaimAt(e.getFrom());
        if (origin == target) {
            // Der Spieler steht bereits im Claim (z.B. nachträglich gebannt). Würden wir jede Bewegung
            // abbrechen, käme er nie wieder heraus - stattdessen wird er hinausbefördert.
            Bukkit.getScheduler().runTask(plugin, () -> evictIfBanned(player));
            return false;
        }

        e.setCancelled(true);
        sendBanMessage(player);
        return true;
    }

    private void evictIfBanned(Player player) {
        if (!player.isOnline() || bypass.isActive(player)) return;
        Claim claim = claimManager.getClaimAt(player.getLocation());
        if (claim != null && claim.isBanned(player.getUniqueId())) {
            banEnforcer.evict(player, claim);
        }
    }

    private void sendBanMessage(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastBanMessage.get(player.getUniqueId());
        if (last != null && now - last < BAN_MESSAGE_COOLDOWN_MS) return;
        lastBanMessage.put(player.getUniqueId(), now);
        player.sendMessage(Text.line(config.prefix() + config.message("cannot-enter-banned")));
    }
}
