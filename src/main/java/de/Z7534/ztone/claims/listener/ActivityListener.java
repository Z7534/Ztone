package de.Z7534.ztone.claims.listener;

import de.Z7534.ztone.claims.config.ConfigManager;
import de.Z7534.ztone.claims.manager.ActivityManager;
import de.Z7534.ztone.claims.manager.BanEnforcer;
import de.Z7534.ztone.claims.manager.BypassManager;
import de.Z7534.ztone.claims.manager.ChatInputManager;
import de.Z7534.ztone.claims.manager.ClaimManager;
import de.Z7534.ztone.claims.manager.ClaimMapManager;
import de.Z7534.ztone.claims.manager.PvPClaimBypassManager;
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

    /** Mindestabstand zwischen zwei "Du bist gebannt"- bzw. Kampf-Nachrichten pro Spieler. */
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
    private final PvPClaimBypassManager pvpClaimBypass;

    private final Map<UUID, Long> lastBanMessage = new HashMap<>();
    private final Map<UUID, Long> lastCombatMessage = new HashMap<>();

    public ActivityListener(JavaPlugin plugin, ActivityManager activityManager, ClaimManager claimManager,
                             SelectionManager selectionManager, ChatInputManager chatInputManager,
                             ConfigManager config, BypassManager bypass, BanEnforcer banEnforcer,
                             ClaimMapManager claimMapManager, PvPClaimBypassManager pvpClaimBypass) {
        this.plugin = plugin;
        this.activityManager = activityManager;
        this.claimManager = claimManager;
        this.selectionManager = selectionManager;
        this.chatInputManager = chatInputManager;
        this.config = config;
        this.bypass = bypass;
        this.banEnforcer = banEnforcer;
        this.claimMapManager = claimMapManager;
        this.pvpClaimBypass = pvpClaimBypass;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        // Der Bypass gilt nie ueber eine Session hinaus: bei jedem Beitritt ist er deaktiviert,
        // ausser der Spieler besitzt die Auto-Bypass-on-Join-Permission.
        bypass.reset(player.getUniqueId());
        if (player.hasPermission(BypassManager.PERMISSION_AUTO_JOIN)) {
            bypass.setActive(player.getUniqueId(), true);
        }
        claimManager.getOrCreatePlayerData(player.getUniqueId(), player.getName());
        activityManager.recordActivity(player.getUniqueId());
        activityManager.checkEntryHint(player);
        // Wurde der Spieler gebannt, waehrend er offline war, und steht er noch im Claim, holen wir ihn raus.
        Bukkit.getScheduler().runTask(plugin, () -> evictIfBanned(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();
        bypass.reset(uuid);
        lastBanMessage.remove(uuid);
        lastCombatMessage.remove(uuid);
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
                // Bewegung wurde abgebrochen: keinen Eintrittshinweis fuer den Claim zeigen, den er nicht betritt.
                return;
            }
            if (enforceCombat(e)) {
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

            de.Z7534.ztone.Ztone ztone = de.Z7534.ztone.Ztone.getInstance();
            if (ztone != null && ztone.getPvpManager() != null) {
                var pvpMgr = ztone.getPvpManager();
                if (!pvpMgr.getOverrideManager().isOverriding(player.getUniqueId())
                        && (pvpClaimBypass == null || !pvpClaimBypass.isPlayerBypassed(player))) {
                    if (pvpMgr.getCombatManager().isTagged(player.getUniqueId())) {
                        if (claim != null && (pvpClaimBypass == null || !pvpClaimBypass.isClaimBypassed(claim.id()))) {
                            e.setCancelled(true);
                            sendCombatMessage(player);
                            return;
                        }
                    }
                }
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
            // Der Spieler steht bereits im Claim (z.B. nachtraeglich gebannt). Wuerden wir jede Bewegung
            // abbrechen, kaeme er nie wieder heraus - stattdessen wird er hinausbefoerdert.
            Bukkit.getScheduler().runTask(plugin, () -> evictIfBanned(player));
            return false;
        }

        e.setCancelled(true);
        sendBanMessage(player);
        return true;
    }

    /**
     * Verhindert das Betreten von geschuetzten Claims waehrend des Kampfes (wie ein temporaerer Claim-Bann).
     * @return true, wenn die Bewegung abgebrochen wurde
     */
    private boolean enforceCombat(PlayerMoveEvent e) {
        Player player = e.getPlayer();
        if (bypass.isActive(player)) return false;

        de.Z7534.ztone.Ztone ztone = de.Z7534.ztone.Ztone.getInstance();
        if (ztone == null) return false;
        var pvpMgr = ztone.getPvpManager();
        if (pvpMgr == null) return false;

        if (pvpMgr.getOverrideManager().isOverriding(player.getUniqueId())) return false;
        if (pvpClaimBypass != null && pvpClaimBypass.isPlayerBypassed(player)) return false;

        if (!pvpMgr.getCombatManager().isTagged(player.getUniqueId())) return false;

        Claim target = claimManager.getClaimAt(e.getTo());
        if (target == null) return false;

        // Wenn der Ziel-Claim von Admins fuer PvP freigegeben wurde (/claimspvpoverride),
        // verhaelt er sich wie Unclaimed Terrain und darf betreten werden.
        if (pvpClaimBypass != null && pvpClaimBypass.isClaimBypassed(target.id())) return false;

        Claim origin = claimManager.getClaimAt(e.getFrom());
        if (origin == target) {
            // Bereits innerhalb des Claims (z.B. Kampf innerhalb begonnen)
            return false;
        }

        e.setCancelled(true);
        sendCombatMessage(player);
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

    private void sendCombatMessage(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastCombatMessage.get(player.getUniqueId());
        if (last != null && now - last < BAN_MESSAGE_COOLDOWN_MS) return;
        lastCombatMessage.put(player.getUniqueId(), now);
        String msg = config.message("cannot-enter-combat");
        if (msg == null || msg.isBlank() || msg.equals("cannot-enter-combat")) {
            msg = "<red>\u1d05\u1d1c \u0299\u026a\ua731\u1d1b \u026a\u1d0d \u1d0b\u1d00\u1d0d\u1d18\ua730! \u1d05\u1d1c \u1d0b\u1d00\u0274\u0274\ua731\u1d1b \u1d0b\u1d07\u026a\u0274\u1d07 \u1d04\u029f\u1d00\u026a\u1d0d\ua731 \u0299\u1d07\u1d1b\u0280\u1d07\u1d1b\u1d07\u0274.";
        }
        player.sendMessage(Text.line(config.prefix() + msg));
    }
}
