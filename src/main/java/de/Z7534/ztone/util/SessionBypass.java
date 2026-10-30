package de.Z7534.ztone.util;

import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wiederverwendbarer, rein sessionbasierter Bypass (wie der Claim-Bypass).
 * <p>
 * Die Basis-Permission erlaubt nur, den Bypass selbst ein- und auszuschalten. Aktiv ist er erst
 * nach dem Befehl. Der Zustand liegt bewusst nur im Arbeitsspeicher und wird beim Beitritt und beim
 * Verlassen des Servers zurückgesetzt. Mit der Auto-Join-Permission wird er beim Beitritt automatisch
 * aktiviert (diese Permission ist standardmäßig {@code false}, auch nicht für OPs).
 * <p>
 * Wird der Bypass von einem Admin explizit für einen anderen Spieler aktiviert, gilt er auch ohne
 * die Basis-Permission bis zum Verlassen des Servers.
 */
public class SessionBypass {

    private final String permission;
    private final String autoJoinPermission;

    private final Set<UUID> active = ConcurrentHashMap.newKeySet();
    private final Set<UUID> explicit = ConcurrentHashMap.newKeySet();

    public SessionBypass(String permission, String autoJoinPermission) {
        this.permission = permission;
        this.autoJoinPermission = autoJoinPermission;
    }

    public String getPermission() {
        return permission;
    }

    public String getAutoJoinPermission() {
        return autoJoinPermission;
    }

    public boolean isActive(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        if (!active.contains(uuid)) {
            return false;
        }
        if (explicit.contains(uuid)) {
            return true;
        }
        if (!player.hasPermission(permission)) {
            active.remove(uuid);
            return false;
        }
        return true;
    }

    public void setActive(UUID uuid, boolean value) {
        if (value) {
            active.add(uuid);
        } else {
            active.remove(uuid);
            explicit.remove(uuid);
        }
    }

    public void setExplicitActive(UUID uuid, boolean value) {
        if (value) {
            active.add(uuid);
            explicit.add(uuid);
        } else {
            active.remove(uuid);
            explicit.remove(uuid);
        }
    }

    /**
     * Schaltet den Bypass um und liefert den neuen Zustand zurück.
     */
    public boolean toggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (active.remove(uuid)) {
            explicit.remove(uuid);
            return false;
        }
        active.add(uuid);
        return true;
    }

    /**
     * Beim Beitritt: Zustand zurücksetzen und ggf. per Auto-Join-Permission aktivieren.
     *
     * @return true, wenn der Bypass automatisch aktiviert wurde
     */
    public boolean handleJoin(Player player) {
        reset(player.getUniqueId());
        if (autoJoinPermission != null && player.hasPermission(autoJoinPermission)) {
            active.add(player.getUniqueId());
            return true;
        }
        return false;
    }

    public void reset(UUID uuid) {
        active.remove(uuid);
        explicit.remove(uuid);
    }

    public void clear() {
        active.clear();
        explicit.clear();
    }
}
