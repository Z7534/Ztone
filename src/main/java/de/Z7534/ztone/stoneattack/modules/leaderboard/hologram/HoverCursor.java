package de.Z7534.ztone.stoneattack.modules.leaderboard.hologram;

import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.List;

/**
 * Der "Cursor", der einem Spieler zeigt, auf welchen Button er gerade schaut: eine leuchtende
 * Flaeche mit Rahmen und einem kurzen Glanz-Streifen. Er gehoert genau einem Spieler und einem
 * Board, deshalb kann jeder Spieler seine eigene Animation (Einblenden, Gleiten, Druecken) sehen.
 *
 * Es gibt je einen Cursor fuer die drei Modus-Buttons (gleiten dazwischen) und einen fuer die
 * beiden Pfeile. Die Teile werden einmal am "Ausgangs-Button" gespawnt; zu einem anderen Button
 * gleitet der Cursor ueber die Verschiebung ({@code offU/offV}) der Transformation.
 */
final class HoverCursor {

    /** Ein einzelnes Rechteck des Cursors (Fuellung, Rahmenteil oder Glanz). */
    static final class Part {
        final TextDisplay display;
        /** Drehpunkt relativ zur Mitte des Ausgangs-Buttons (u = rechts, v = hoch). */
        final double pivotU;
        final double pivotV;
        /** Skalierung im Ruhezustand (8 * Breite, 4 * Hoehe - siehe DisplayFactory#spawnRect). */
        final float scaleX;
        final float scaleY;
        final int rgb;
        /** Alpha bei voller Sichtbarkeit. */
        final int maxAlpha;
        /** Zuletzt an den Client gesendetes Alpha (spart doppelte Pakete). */
        int shownAlpha = 0;

        Part(TextDisplay display, double pivotU, double pivotV, float scaleX, float scaleY, int rgb, int maxAlpha) {
            this.display = display;
            this.pivotU = pivotU;
            this.pivotV = pivotV;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.rgb = rgb;
            this.maxAlpha = maxAlpha;
        }
    }

    final boolean modeKind;
    /** Mitte des Ausgangs-Buttons im Board-Koordinatensystem. */
    final double centerU;
    final double centerV;

    /** Fuellung + Rahmen (werden gemeinsam skaliert und verschoben). */
    final List<Part> body = new ArrayList<>();
    /** Glanz-Streifen, null wenn abgeschaltet. */
    Part shine;
    /** Wie weit der Glanz maximal nach rechts wandert (in Bloecken). */
    double shineTravel;

    // ---- Zustand ----
    /** Button, an dem der Cursor gerade sitzt. null = unsichtbar/ruhend. */
    ClickBinding.Action at;
    /** Aktuelle Verschiebung gegenueber dem Ausgangs-Button. */
    double offU;
    double offV;
    /** Aktuell angewandter Groessenfaktor der ganzen Gruppe. */
    float scale = 1f;
    /** Sichtbarkeitsstufe 0 (unsichtbar) bis fadeTicks (voll sichtbar). */
    int level;
    /** Ticks, seit der Spieler den Button nicht mehr ansieht. */
    int lost;
    /** Fortschritt der Glanz-Animation, -1 = ruht. */
    int shineTick = -1;

    HoverCursor(boolean modeKind, double centerU, double centerV) {
        this.modeKind = modeKind;
        this.centerU = centerU;
        this.centerV = centerV;
    }

    List<Entity> entities() {
        List<Entity> all = new ArrayList<>();
        for (Part p : body) {
            all.add(p.display);
        }
        if (shine != null) {
            all.add(shine.display);
        }
        return all;
    }
}
