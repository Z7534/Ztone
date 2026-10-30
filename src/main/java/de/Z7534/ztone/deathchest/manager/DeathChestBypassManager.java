package de.Z7534.ztone.deathchest.manager;

import de.Z7534.ztone.util.SessionBypass;

/**
 * Bypass für die Killer-Sperre von Todeskisten.
 * <p>
 * Mit aktivem Bypass kann man gesperrte Todeskisten trotzdem öffnen, hineinschauen und Items entnehmen.
 * Ein-/Ausschalten per {@code /deathchest bypass [on|off] [spieler]}.
 * <ul>
 *     <li>{@value #PERMISSION} – eigenen Bypass schalten (Standard: OP)</li>
 *     <li>{@value #PERMISSION_OTHER} – Bypass anderer Spieler schalten (Standard: OP)</li>
 *     <li>{@value #PERMISSION_AUTO_JOIN} – Bypass beim Beitritt automatisch an (Standard: false, auch nicht für OPs)</li>
 * </ul>
 */
public final class DeathChestBypassManager extends SessionBypass {

    public static final String PERMISSION = "deathchest.bypass";
    public static final String PERMISSION_OTHER = "deathchest.bypass.other";
    public static final String PERMISSION_AUTO_JOIN = "deathchest.bypass.join";

    public DeathChestBypassManager() {
        super(PERMISSION, PERMISSION_AUTO_JOIN);
    }
}
