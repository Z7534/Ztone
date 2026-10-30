package de.Z7534.ztone.dragonegg.manager;

import de.Z7534.ztone.util.SessionBypass;

/**
 * Verwaltet den Bypass für das DragonEgg-Modul.
 * <p>
 * Spieler mit aktivem Bypass leuchten nicht permanent, wenn sie das Ei tragen,
 * haben kein erzwungenes PvP und dürfen Claims betreten.
 * <ul>
 *     <li>{@value #PERMISSION} – eigenen Bypass schalten (Standard: OP)</li>
 *     <li>{@value #PERMISSION_OTHER} – Bypass anderer Spieler schalten (Standard: OP)</li>
 *     <li>{@value #PERMISSION_AUTO_JOIN} – Bypass beim Beitritt automatisch an (Standard: false, auch nicht für OPs)</li>
 * </ul>
 */
public final class DragonEggBypassManager extends SessionBypass {

    public static final String PERMISSION = "dragonegg.bypass";
    public static final String PERMISSION_OTHER = "dragonegg.bypass.other";
    public static final String PERMISSION_AUTO_JOIN = "dragonegg.bypass.join";

    public DragonEggBypassManager() {
        super(PERMISSION, PERMISSION_AUTO_JOIN);
    }
}
