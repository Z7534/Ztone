package de.Z7534.ztone.stonemute.mute;

import de.Z7534.ztone.stonemute.Stonemute;
import de.Z7534.ztone.stonemute.config.StonemuteConfig;
import litebans.api.Database;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Haelt den Mute-Status aller Online-Spieler in einem Cache.
 * Der Voicechat-Thread liest nur aus diesem Cache und fragt nie die Datenbank ab.
 */
public final class MuteService {

    private final Stonemute plugin;
    private final StonemuteConfig settings;

    private final Map<UUID, Boolean> muted = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastNotified = new ConcurrentHashMap<>();

    private BukkitTask refreshTask;

    public MuteService(Stonemute plugin, StonemuteConfig settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public void start() {
        long ticks = settings.refreshIntervalSeconds() * 20L;
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin.getPlugin(), this::refreshAll, ticks, ticks);
        refreshAll();
    }

    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        muted.clear();
        lastNotified.clear();
    }

    public boolean isMuted(UUID uuid) {
        return muted.getOrDefault(uuid, false);
    }

    /** Begrenzt, wie oft ein gemuteter Spieler den Hinweis bekommt. */
    public boolean shouldNotify(UUID uuid) {
        long now = System.currentTimeMillis();
        long cooldownMillis = settings.notifyCooldownSeconds() * 1000L;
        Long previous = lastNotified.get(uuid);
        if (previous != null && now - previous < cooldownMillis) {
            return false;
        }
        lastNotified.put(uuid, now);
        return true;
    }

    public void forget(UUID uuid) {
        muted.remove(uuid);
        lastNotified.remove(uuid);
    }

    /** Muss auf dem Main-Thread aufgerufen werden (liest Bukkit-Spielerdaten). */
    public void refreshAll() {
        if (!plugin.isEnabled()) {
            return;
        }
        Map<UUID, String> snapshot = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            snapshot.put(player.getUniqueId(), ipOf(player));
        }
        if (snapshot.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin.getPlugin(), () -> snapshot.forEach(this::check));
    }

    /** Muss auf dem Main-Thread aufgerufen werden. */
    public void refresh(Player player) {
        if (!plugin.isEnabled()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        String ip = ipOf(player);
        Bukkit.getScheduler().runTaskAsynchronously(plugin.getPlugin(), () -> check(uuid, ip));
    }

    private void check(UUID uuid, String ip) {
        try {
            Database database = Database.get();
            boolean isMuted = settings.checkAllServerScopes()
                    ? database.isPlayerMuted(uuid, ip, Database.ANY_SERVER_SCOPE)
                    : database.isPlayerMuted(uuid, ip);
            muted.put(uuid, isMuted);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Mute-Abfrage bei LiteBans fehlgeschlagen fuer " + uuid, e);
        }
    }

    private static String ipOf(Player player) {
        InetSocketAddress address = player.getAddress();
        if (address == null || address.getAddress() == null) {
            return null;
        }
        return address.getAddress().getHostAddress();
    }
}
