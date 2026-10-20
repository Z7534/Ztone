package de.Z7534.ztone.stoneattack.modules.leaderboard.hologram;

import java.util.ArrayList;
import java.util.List;

/**
 * Zerlegt ein Rechteck mit abgerundeten Ecken in einfache Rechtecke.
 *
 * Text-Displays koennen nur Rechtecke zeichnen - die Rundung entsteht aus uebereinander gesetzten,
 * nach aussen hin schmaler werdenden Streifen (Viertelkreis in {@code steps} Stufen). Diese Klasse
 * liefert nur die Geometrie; das Spawnen der Entities macht der Aufrufer (geteilte Board-Flaechen
 * im {@link HologramManager}, pro Spieler sichtbare Hover-Flaechen im {@link HoverAnimator}).
 */
final class RoundedShape {

    private RoundedShape() {
    }

    /**
     * @param u0      linke Kante
     * @param v0      untere Kante
     * @param outline 0 = gefuellt. Groesser 0 = nur ein Rahmen dieser Staerke. Der Rahmen-Modus
     *                ist bei durchsichtigen Flaechen wichtig: zwei gefuellte Flaechen
     *                uebereinander addieren ihre Deckkraft, die Mitte waere sonst sichtbar
     *                weniger durchsichtig als der Rand.
     * @return Rechtecke als {@code {u0, v0, breite, hoehe}}
     */
    static List<double[]> rects(double u0, double v0, double width, double height, double radius, int steps,
                                double outline) {
        List<double[]> out = new ArrayList<>();
        double r = Math.min(radius, Math.min(width, height) / 2.0);
        if (r < 0.01) {
            out.add(new double[]{u0, v0, width, height});
            return out;
        }
        double step = r / steps;
        double overlap = 0.002; // Streifen ueberlappen minimal -> keine hellen Fugen dazwischen

        double middleHeight = height - 2 * r;
        if (middleHeight > 0.001) {
            if (outline > 0) {
                // Im Rahmen-Modus bleibt der gerade Mittelteil hohl: nur links und rechts ein Steg.
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

            // Im Rahmen-Modus ist nur der aeusserste Streifen durchgehend (das ist die
            // Ober-/Unterkante des Rahmens); die restlichen Streifen bekommen nur noch
            // zwei Seitenstege. Die Stege werden um den Versatz zum vorherigen Streifen
            // verbreitert, damit in der Rundung keine Treppenluecken entstehen.
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
