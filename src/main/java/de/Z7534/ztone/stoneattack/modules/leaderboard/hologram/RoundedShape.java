package de.Z7534.ztone.stoneattack.modules.leaderboard.hologram;

import java.util.ArrayList;
import java.util.List;

/**
 * Zerlegt ein Rechteck mit abgerundeten Ecken in einfache Rechtecke für das
 * prozedurale Fallback-Rendering (Modus B ohne Resourcepack).
 *
 * Liefert für Modus B saubere, nicht überlappende Streifen für Hintergrund
 * und Akzentrahmen (#00e5ff), die für Spieler ohne Resourcepack angezeigt
 * und für Spieler mit geladenem Pack per {@code player.hideEntity} ausgeblendet werden.
 */
public final class RoundedShape {

    private RoundedShape() {
    }

    /**
     * @param u0      linke Kante
     * @param v0      untere Kante
     * @param width   Gesamtbreite
     * @param height  Gesamthöhe
     * @param radius  Eckenradius
     * @param steps   Stufen des Viertelkreises
     * @param outline 0 = gefüllt. Größer 0 = nur ein Rahmen dieser Stärke.
     * @return Rechtecke als {@code {u0, v0, breite, hoehe}}
     */
    public static List<double[]> rects(double u0, double v0, double width, double height, double radius, int steps,
                                       double outline) {
        List<double[]> out = new ArrayList<>();
        double r = Math.min(radius, Math.min(width, height) / 2.0);
        if (r < 0.01) {
            out.add(new double[]{u0, v0, width, height});
            return out;
        }
        double step = r / steps;
        double overlap = 0.002;

        double middleHeight = height - 2 * r;
        if (middleHeight > 0.001) {
            if (outline > 0) {
                out.add(new double[]{u0, v0 + r, outline, middleHeight});
                out.add(new double[]{u0 + width - outline, v0 + r, outline, middleHeight});
            } else {
                out.add(new double[]{u0, v0 + r, width, middleHeight});
            }
        }
        double previousInset = -1.0;
        for (int j = 0; j < steps; j++) {
            double centerY = (j + 0.5) * step;
            double dy = r - centerY;
            double inset = r - Math.sqrt(Math.max(0.0, r * r - dy * dy));
            double bandWidth = width - 2 * inset;
            double bandHeight = step + overlap;

            boolean solidBand = outline <= 0 || j * step < outline;
            if (solidBand) {
                out.add(new double[]{u0 + inset, v0 + j * step, bandWidth, bandHeight});
                out.add(new double[]{u0 + inset, v0 + height - (j + 1) * step - overlap, bandWidth, bandHeight});
            } else {
                double segment = outline + Math.max(0.0, previousInset < 0 ? 0.0 : inset - previousInset);
                segment = Math.min(segment, bandWidth / 2.0);
                double rightU = u0 + width - inset - segment;
                out.add(new double[]{u0 + inset, v0 + j * step, segment, bandHeight});
                out.add(new double[]{rightU, v0 + j * step, segment, bandHeight});
                out.add(new double[]{u0 + inset, v0 + height - (j + 1) * step - overlap, segment, bandHeight});
                out.add(new double[]{rightU, v0 + height - (j + 1) * step - overlap, segment, bandHeight});
            }
            previousInset = inset;
        }
        return out;
    }
}
