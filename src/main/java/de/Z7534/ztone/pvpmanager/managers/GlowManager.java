package de.Z7534.ztone.pvpmanager.managers;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Setzt den Combat-Glow.
 * <p>
 * Falls ProtocolLib vorhanden ist, wird die Paket-Interception aktiviert,
 * um Konflikte mit anderen Glow-Plugins zu vermeiden.
 * Ohne ProtocolLib laeuft der normale Bukkit Player#setGlowing(boolean) Mechanismus.
 */
public class GlowManager {

    private final Pvpmanager plugin;
    private final Set<UUID> glowing = ConcurrentHashMap.newKeySet();
    private Object packetHook;

    public GlowManager(Pvpmanager plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        if (!plugin.getHookManager().protocolLib().isAvailable()) {
            return;
        }
        try {
            this.packetHook = new PvpGlowPacketHook(plugin, this);
            plugin.getLogger().info("ProtocolLib gefunden - Combat-Glow laeuft ueber Paket-Interception.");
        } catch (Throwable throwable) {
            plugin.getLogger().fine("ProtocolLib gefunden, aber Paket-Listener konnte nicht geladen werden: " + throwable.getMessage());
        }
    }

    public void shutdown() {
        glowing.clear();
        if (packetHook instanceof PvpGlowPacketHook hook) {
            hook.unregister();
        }
        packetHook = null;
    }

    public void setGlowing(Player player, boolean glow) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        if (glow) {
            glowing.add(uuid);
            if (!player.isGlowing()) {
                player.setGlowing(true);
            }
        } else {
            glowing.remove(uuid);
            // Wenn Clan-Glow oder ein Trank-Effekt noch aktiv ist, Glow auf dem Server keinesfalls abschalten!
            boolean keepGlowing = false;
            try {
                if (plugin.getPlugin().getGlowManager() != null
                        && plugin.getPlugin().getGlowManager().isClanGlowActive(player)) {
                    keepGlowing = true;
                }
            } catch (Throwable ignored) {}
            if (player.hasPotionEffect(PotionEffectType.GLOWING)) {
                keepGlowing = true;
            }
            if (!keepGlowing) {
                if (player.isGlowing()) {
                    player.setGlowing(false);
                }
            }
        }
    }

    public void reassert(Player player) {
        if (glowing.contains(player.getUniqueId()) && !player.isGlowing()) {
            player.setGlowing(true);
        }
    }

    public boolean isGlowing(UUID uuid) {
        return glowing.contains(uuid);
    }

    public Set<UUID> getGlowing() {
        return Collections.unmodifiableSet(glowing);
    }
}
