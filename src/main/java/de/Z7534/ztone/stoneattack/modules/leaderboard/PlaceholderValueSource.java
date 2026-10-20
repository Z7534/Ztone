package de.Z7534.ztone.stoneattack.modules.leaderboard;

import de.Z7534.ztone.stoneattack.util.ColorUtil;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

/**
 * Liest den Wert eines Leaderboards aus einem beliebigen PlaceholderAPI-Platzhalter
 * (z. B. von einem eigenen Playtime-Plugin) statt aus der Vanilla-Statistik.
 *
 * Der Platzhalter-Text wird dabei IMMER erst von jeder Formatierung befreit
 * (Farbcodes, Hex-Codes, MiniMessage-Tags) und dann als Zahl gelesen. Die Ausgabe
 * auf dem Board laeuft danach ganz normal durch die Formatierung aus
 * {@code leaderboard.format.*} und die Small-Caps-Umwandlung.
 *
 * Diese Klasse referenziert PlaceholderAPI direkt und darf deshalb nur benutzt
 * werden, wenn das Plugin vorhanden ist (das prueft {@link StatisticsService}).
 */
final class PlaceholderValueSource {

    private PlaceholderValueSource() {
    }

    /**
     * @return die Zahl hinter dem Platzhalter, oder {@code NaN}, wenn der Platzhalter
     *         nicht aufgeloest wurde bzw. keine Zahl enthaelt.
     */
    static double read(OfflinePlayer player, String placeholder) {
        String resolved = PlaceholderAPI.setPlaceholders(player, placeholder);
        if (resolved == null || resolved.equals(placeholder)) {
            return Double.NaN; // Platzhalter unbekannt -> PAPI laesst den Text unveraendert
        }
        return parseNumber(ColorUtil.stripFormatting(resolved));
    }

    /**
     * Liest eine Zahl aus bereits bereinigtem Text. Versteht "1234", "1234.5",
     * "1,234.50" (englisch) und "1.234,50" (deutsch). Alles, was keine Ziffer,
     * Trenner oder Minus ist (z. B. Einheiten wie "h"), wird ignoriert.
     */
    static double parseNumber(String text) {
        if (text == null) {
            return Double.NaN;
        }
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if ((c >= '0' && c <= '9') || c == '.' || c == ',' || (c == '-' && sb.isEmpty())) {
                sb.append(c);
            }
        }
        String s = sb.toString();
        if (s.isEmpty() || s.equals("-")) {
            return Double.NaN;
        }

        int lastDot = s.lastIndexOf('.');
        int lastComma = s.lastIndexOf(',');
        String normalized;
        if (lastDot >= 0 && lastComma >= 0) {
            // Beide da: das LETZTE Zeichen ist das Dezimalzeichen, das andere trennt Tausender.
            if (lastDot > lastComma) {
                normalized = s.replace(",", "");
            } else {
                normalized = s.replace(".", "").replace(',', '.');
            }
        } else if (lastComma >= 0) {
            boolean single = s.indexOf(',') == lastComma;
            boolean thousands = !single || s.length() - lastComma - 1 == 3;
            normalized = thousands ? s.replace(",", "") : s.replace(',', '.');
        } else if (lastDot >= 0) {
            // Mehrere Punkte = Tausendertrenner; ein einzelner Punkt ist ein Dezimalpunkt.
            boolean single = s.indexOf('.') == lastDot;
            normalized = single ? s : s.replace(".", "");
        } else {
            normalized = s;
        }

        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** Rechnet den Platzhalter-Wert der Spielzeit anhand der konfigurierten Einheit in Ticks um. */
    static double playtimeToTicks(double value, String unit) {
        return switch (unit == null ? "seconds" : unit.toLowerCase(Locale.ROOT)) {
            case "ticks", "tick" -> value;
            case "minutes", "minute", "min" -> value * 20.0 * 60.0;
            case "hours", "hour", "h" -> value * 20.0 * 60.0 * 60.0;
            case "milliseconds", "millis", "ms" -> value / 1000.0 * 20.0;
            default -> value * 20.0; // seconds
        };
    }
}
