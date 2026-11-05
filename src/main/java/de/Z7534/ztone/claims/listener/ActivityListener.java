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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ActivityListener implements Listener {

    /** Mindestabstand zwischen zwei Hinweisen pro Spieler. */
    private static final long MESSAGE_COOLDOWN_MS = 2000L;

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
    private final Map<UUID, Long> lastEggMessage = new HashMap<>();

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
        bypass.reset(player.getUniqueId());
        if (player.hasPermission(BypassManager.PERMISSION_AUTO_JOIN)) {
            bypass.setActive(player.getUniqueId(), true);
        }
        claimManager.getOrCreatePlayerData(player.getUniqueId(), player.getName());
        activityManager.recordActivity(player.getUniqueId());
        activityManager.checkEntryHint(player);
        // Prüfen, ob der Spieler in einem Claim steht, aus dem er gebannt/ausgeschlossen ist
        Bukkit.getScheduler().runTask(plugin, () -> evictIfRestricted(player));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        // Nach dem Respawn direkt prüfen (1 Tick später)
        Player player = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                evictIfRestricted(player);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();
        bypass.reset(uuid);
        lastBanMessage.remove(uuid);
        lastCombatMessage.remove(uuid);
        lastEggMessage.remove(uuid);
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
                return;
            }
            if (enforceCombat(e)) {
                return;
            }
            if (enforceDragonEgg(e)) {
                return;
            }
        }

        if ((from.getBlockX() >> 4) != (to.getBlockX() >> 4)
                || (from.getBlockZ() >> 4) != (to.getBlockZ() >> 4)) {
            activityManager.checkEntryHint(e.getPlayer(), to);
        }
    }

    /**
     * Fängt jeden Teleport ab (Befehle, Homes, Warps, Enderperlen, Chorus-Früchte etc.).
     * Wenn das Ziel in einem für den Spieler blockierten Claim liegt (Bann, Combat Tag, Drachenei),
     * wird der Teleport nicht stur abgebrochen oder der Spieler im Claim gelassen,
     * sondern direkt auf einen sicheren Platz per SafeSpotFinder umgeleitet.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onTeleport(PlayerTeleportEvent e) {
        Player player = e.getPlayer();
        Location to = e.getTo();
        if (to == null) return;

        if (bypass.isActive(player)) {
            activityManager.checkEntryHint(player, to);
            return;
        }

        Claim claim = claimManager.getClaimAt(to);
        if (claim == null) {
            activityManager.checkEntryHint(player, to);
            return;
        }

        boolean banned = claim.isBanned(player.getUniqueId());
        boolean combatBlocked = isCombatBlocked(player, claim);
        boolean eggBlocked = isDragonEggBlocked(player);

        if (banned || combatBlocked || eggBlocked) {
            // Umleiten an einen sicheren Platz außerhalb des Ziel-Claims
            Optional<Location> safeSpot = banEnforcer.findSafeSpotOutside(to, claim);
            if (safeSpot.isPresent()) {
                e.setTo(safeSpot.get());
            } else {
                e.setTo(to.getWorld().getSpawnLocation());
            }

            if (banned) {
                sendBanMessage(player);
                player.sendMessage(Text.line(config.prefix() + config.message("banned-teleported")));
            } else if (combatBlocked) {
                sendCombatMessage(player);
            } else {
                sendEggMessage(player);
            }
            return;
        }

        activityManager.checkEntryHint(player, e.getTo());
    }

    private void stopElytraGliding(Player player, Location from, Location to) {
        if (player.isGliding()) {
            player.setGliding(false);
        }
        if (from != null && to != null) {
            org.bukkit.util.Vector diff = from.toVector().subtract(to.toVector());
            if (diff.lengthSquared() > 0.0001) {
                org.bukkit.util.Vector push = diff.normalize().multiply(0.8);
                push.setY(Math.max(0.1, push.getY()));
                player.setVelocity(push);
                return;
            }
        }
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
    }

    private boolean enforceBan(PlayerMoveEvent e) {
        Player player = e.getPlayer();
        Claim target = claimManager.getClaimAt(e.getTo());
        if (target == null || !target.isBanned(player.getUniqueId())) return false;
        if (bypass.isActive(player)) return false;

        Claim origin = claimManager.getClaimAt(e.getFrom());
        if (origin == target) {
            stopElytraGliding(player, null, null);
            e.setCancelled(true);
            evictIfRestricted(player);
            sendBanMessage(player);
            return true;
        }

        stopElytraGliding(player, e.getFrom(), e.getTo());
        e.setCancelled(true);
        sendBanMessage(player);
        return true;
    }

    private boolean enforceCombat(PlayerMoveEvent e) {
        Player player = e.getPlayer();
        if (bypass.isActive(player)) return false;

        Claim target = claimManager.getClaimAt(e.getTo());
        if (target == null || !isCombatBlocked(player, target)) return false;

        Claim origin = claimManager.getClaimAt(e.getFrom());
        if (origin == target) {
            stopElytraGliding(player, null, null);
            e.setCancelled(true);
            evictIfRestricted(player);
            sendCombatMessage(player);
            return true;
        }

        stopElytraGliding(player, e.getFrom(), e.getTo());
        e.setCancelled(true);
        sendCombatMessage(player);
        return true;
    }

    private boolean enforceDragonEgg(PlayerMoveEvent e) {
        Player player = e.getPlayer();
        if (bypass.isActive(player)) return false;
        if (!isDragonEggBlocked(player)) return false;

        Claim target = claimManager.getClaimAt(e.getTo());
        if (target == null) return false;

        Claim origin = claimManager.getClaimAt(e.getFrom());
        if (origin == target) {
            stopElytraGliding(player, null, null);
            e.setCancelled(true);
            evictIfRestricted(player);
            sendEggMessage(player);
            return true;
        }

        stopElytraGliding(player, e.getFrom(), e.getTo());
        e.setCancelled(true);
        sendEggMessage(player);
        return true;
    }

    private boolean isCombatBlocked(Player player, Claim claim) {
        de.Z7534.ztone.Ztone ztone = de.Z7534.ztone.Ztone.getInstance();
        if (ztone == null || ztone.getPvpManager() == null) return false;
        var pvpMgr = ztone.getPvpManager();
        if (pvpMgr.getOverrideManager().isOverriding(player.getUniqueId())) return false;
        if (pvpClaimBypass != null && pvpClaimBypass.isPlayerBypassed(player)) return false;
        if (!pvpMgr.getCombatManager().isTagged(player.getUniqueId())) return false;
        return (pvpClaimBypass == null || !pvpClaimBypass.isClaimBypassed(claim.id()));
    }

    private boolean isDragonEggBlocked(Player player) {
        de.Z7534.ztone.Ztone ztone = de.Z7534.ztone.Ztone.getInstance();
        if (ztone == null) return false;
        return ztone.isDragonEggBlockingClaim(player);
    }

    public void evictIfRestricted(Player player) {
        if (!player.isOnline() || bypass.isActive(player)) return;
        Claim claim = claimManager.getClaimAt(player.getLocation());
        if (claim == null) return;

        boolean banned = claim.isBanned(player.getUniqueId());
        boolean combatBlocked = isCombatBlocked(player, claim);
        boolean eggBlocked = isDragonEggBlocked(player);

        if (banned) {
            banEnforcer.evict(player, claim);
        } else if (combatBlocked) {
            banEnforcer.evict(player, claim);
            sendCombatMessage(player);
        } else if (eggBlocked) {
            banEnforcer.evict(player, claim);
            sendEggMessage(player);
        }
    }

    private void sendBanMessage(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastBanMessage.get(player.getUniqueId());
        if (last != null && now - last < MESSAGE_COOLDOWN_MS) return;
        lastBanMessage.put(player.getUniqueId(), now);
        player.sendMessage(Text.line(config.prefix() + config.message("cannot-enter-banned")));
    }

    private void sendCombatMessage(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastCombatMessage.get(player.getUniqueId());
        if (last != null && now - last < MESSAGE_COOLDOWN_MS) return;
        lastCombatMessage.put(player.getUniqueId(), now);
        String msg = config.message("cannot-enter-combat");
        if (msg == null || msg.isBlank() || msg.equals("cannot-enter-combat")) {
            msg = "<red>Du bist im Kampf! Du kannst keine Claims betreten.</red>";
        }
        player.sendMessage(Text.line(config.prefix() + msg));
    }

    private void sendEggMessage(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastEggMessage.get(player.getUniqueId());
        if (last != null && now - last < MESSAGE_COOLDOWN_MS) return;
        lastEggMessage.put(player.getUniqueId(), now);
        player.sendMessage(Text.line(config.prefix() + "<red>Du trägst das gesicherte Drachenei und darfst keine Claims betreten!</red>"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onToggleGlide(EntityToggleGlideEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        if (!e.isGliding()) return;
        if (bypass.isActive(player)) return;

        Claim claim = claimManager.getClaimAt(player.getLocation());
        if (claim == null) return;

        if (claim.isBanned(player.getUniqueId()) || isCombatBlocked(player, claim) || isDragonEggBlocked(player)) {
            e.setCancelled(true);
            stopElytraGliding(player, null, null);
        }
    }
}
