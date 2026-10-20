package de.Z7534.ztone.stoneattack.modules.leaderboard.hologram;

import org.bukkit.entity.TextDisplay;

/**
 * Eine Beschriftung (Modus-Button oder Pfeil), die beim Hover "angehoben" wird: sie waechst
 * weich um die eigene Textmitte und rutscht bei den Pfeilen ein Stueck nach aussen.
 * Gehoert genau einem Spieler (siehe {@link HologramInstance}), damit jeder Spieler seine
 * eigene Animation sieht.
 */
final class HoverLabel {

    final TextDisplay display;
    /** Normale Textgroesse (Skalierung im Ruhezustand). */
    final float baseScale;
    /** Hoehe der Textmitte ueber dem Ankerpunkt bei Skalierung 1 (siehe HologramLayout). */
    final double centerOffset;
    /** -1 = beim Hover nach links schieben, +1 = nach rechts, 0 = gar nicht. */
    final double nudgeDirection;

    /** Aktuell angewandter Groessenfaktor (1 = normal). */
    float factor = 1f;
    /** Aktuell angewandte seitliche Verschiebung in Bloecken. */
    double nudge = 0.0;

    HoverLabel(TextDisplay display, float baseScale, double centerOffset, double nudgeDirection) {
        this.display = display;
        this.baseScale = baseScale;
        this.centerOffset = centerOffset;
        this.nudgeDirection = nudgeDirection;
    }
}
