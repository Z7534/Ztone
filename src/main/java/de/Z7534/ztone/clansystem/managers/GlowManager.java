package de.Z7534.ztone.clansystem.managers;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.models.ClanMember;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GlowManager {

    private final Ztone plugin;
    private final boolean packetFilteringAvailable;
    private GlowPacketHook packetHook;
    private BukkitTask enforceTask;

    // Schneller thread-sicherer Lookup: entityId -> Player (verhindert asynchrone World-Lookups auf Netty-Threads)
    private final Map<Integer, Player> entityIdToPlayer = new ConcurrentHashMap<>();
    // Trackt Spieler, die aktiv durch das Clan-System zum Leuchten gebracht wurden
    private final Set<UUID> clanGlowingPlayers = ConcurrentHashMap.newKeySet();

    public GlowManager(Ztone plugin) {
        this.plugin = plugin;

        boolean protocolLibPresent = Bukkit.getPluginManager().getPlugin("ProtocolLib") != null
                && Bukkit.getPluginManager().isPluginEnabled("ProtocolLib");

        boolean hookRegistered = false;
        if (protocolLibPresent) {
            try {
                this.packetHook = new GlowPacketHook(plugin, this);
                hookRegistered = true;
                plugin.getLogger().info("ProtocolLib gefunden - Clan-Glow ist nur fuer das eigene Team sichtbar.");
            } catch (Throwable t) {
                plugin.getLogger().fine("Konnte den ProtocolLib-Hook fuer teambasierten Glow nicht laden: " + t);
            }
        } else {
            plugin.getLogger().severe("ProtocolLib nicht gefunden - Clan-Glow-Effekt bleibt deaktiviert!");
        }
        this.packetFilteringAvailable = hookRegistered;

        // Initiale Online-Spieler registrieren
        for (Player player : Bukkit.getOnlinePlayers()) {
            entityIdToPlayer.put(player.getEntityId(), player);
        }

        // Periodischer Check alle 5 Sekunden (100 Ticks)
        this.enforceTask = Bukkit.getScheduler().runTaskTimer(plugin, this::enforceAll, 100L, 100L);
    }

    public boolean isPacketFilteringAvailable() {
        return packetFilteringAvailable;
    }

    public void setupAll() {
        for (Clan clan : plugin.getClanManager().getAllClans()) {
            refresh(clan);
        }
    }

    public void shutdown() {
        if (enforceTask != null) {
            enforceTask.cancel();
            enforceTask = null;
        }
        if (packetHook != null) {
            packetHook.unregister();
            packetHook = null;
        }
        // Clan-Glow sauber zuruecksetzen, sofern nicht extern aktiv
        for (UUID uuid : clanGlowingPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && !isExternalGlowActive(player)) {
                player.setGlowing(false);
            }
        }
        clanGlowingPlayers.clear();
        entityIdToPlayer.clear();
    }

    public Player getPlayerByEntityId(int entityId) {
        Player player = entityIdToPlayer.get(entityId);
        if (player != null && player.isOnline() && player.getEntityId() == entityId) {
            return player;
        }
        // Fallback: Online-Spieler durchsuchen und Cache aktualisieren
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getEntityId() == entityId) {
                entityIdToPlayer.put(entityId, p);
                return p;
            }
        }
        return null;
    }

    public void onJoin(Player player) {
        if (player != null) {
            entityIdToPlayer.put(player.getEntityId(), player);
        }
    }

    public void onQuit(Player player) {
        if (player != null) {
            entityIdToPlayer.remove(player.getEntityId());
            clanGlowingPlayers.remove(player.getUniqueId());
        }
    }

    public boolean isClanGlowActive(Player player) {
        if (player == null || !packetFilteringAvailable) return false;
        Clan clan = plugin.getClanManager().getClanByPlayer(player);
        if (clan == null || !clan.isGlowEnabled()) return false;
        if (plugin.getLevelManager() != null && !plugin.getLevelManager().isGlowUnlocked(clan.getLevel())) {
            return false;
        }
        return true;
    }

    public boolean isExternalGlowActive(Player player) {
        if (player == null || !player.isOnline()) return false;
        if (player.hasPotionEffect(PotionEffectType.GLOWING)) {
            return true;
        }
        try {
            if (plugin.getPvpManager() != null && plugin.getPvpManager().getGlowManager() != null) {
                if (plugin.getPvpManager().getGlowManager().isGlowing(player.getUniqueId())) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public void disband(Clan clan) {
        if (clan == null) return;
        for (ClanMember member : clan.getMembers().values()) {
            Player p = Bukkit.getPlayer(member.getUuid());
            if (p != null && p.isOnline()) {
                removeFromPlayer(p);
            }
        }
    }

    public void applyToPlayer(Player player) {
        if (player == null || !player.isOnline()) return;
        entityIdToPlayer.put(player.getEntityId(), player);

        boolean shouldGlow = isClanGlowActive(player);
        if (shouldGlow) {
            clanGlowingPlayers.add(player.getUniqueId());
            if (!player.isGlowing()) {
                player.setGlowing(true);
            }
        } else {
            clanGlowingPlayers.remove(player.getUniqueId());
            // Nur ausschalten, wenn kein anderes System (Combat Tag oder Potion) Leuchten verlangt
            if (!isExternalGlowActive(player)) {
                if (player.isGlowing()) {
                    player.setGlowing(false);
                }
            }
        }
    }

    public void removeFromPlayer(Player player) {
        if (player == null) return;
        clanGlowingPlayers.remove(player.getUniqueId());
        if (!isExternalGlowActive(player)) {
            if (player.isGlowing()) {
                player.setGlowing(false);
            }
        }
    }

    public void refreshVisibilityForNewMember(Clan clan, Player player) {
        refresh(clan);
    }

    public void refreshVisibilityForLeavingMember(Clan clan, Player player) {
        removeFromPlayer(player);
        refresh(clan);
    }

    private void enforceAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyToPlayer(player);
        }
    }

    public void refresh(Clan clan) {
        if (clan == null) return;
        for (ClanMember member : clan.getMembers().values()) {
            Player player = Bukkit.getPlayer(member.getUuid());
            if (player != null && player.isOnline()) {
                applyToPlayer(player);
            }
        }
    }
}
