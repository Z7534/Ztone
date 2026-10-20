package de.Z7534.ztone.claims.manager;

import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwaltet den Claim-Bypass von Teammitgliedern.
 * <p>
 * Die Berechtigung {@value #PERMISSION} erlaubt nur, den Bypass mit
 * {@code /staffclaim bypass} selbst ein- und auszuschalten. Aktiv ist er erst
 * nach dem Befehl. Der Zustand liegt bewusst nur im Arbeitsspeicher und wird beim
 * Beitritt und beim Verlassen des Servers zurückgesetzt, ein Neustart oder
 * Rejoin deaktiviert ihn also immer wieder.
 */
public final class BypassManager {

    public static final String PERMISSION = "claims.admin.bypass";
    public static final String PERMISSION_AUTO_JOIN = "claims.admin.bypass.join";

    private final Set<UUID> active = ConcurrentHashMap.newKeySet();
    private final Set<UUID> explicit = ConcurrentHashMap.newKeySet();

    /**
     * Ob der Spieler gerade Bypass-Rechte hat.
     * Wenn der Bypass explizit für den Spieler aktiviert wurde (z.B. per Admin-Befehl),
     * gilt er auch ohne die LuckPerms-Permission!
     */
    public boolean isActive(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        if (!active.contains(uuid)) {
            return false;
        }
        if (explicit.contains(uuid)) {
            return true;
        }
        if (!player.hasPermission(PERMISSION)) {
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

    public void reset(UUID uuid) {
        active.remove(uuid);
        explicit.remove(uuid);
    }

    public void clear() {
        active.clear();
        explicit.clear();
    }
}
