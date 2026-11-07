package de.Z7534.ztone.stoneattack.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;

import java.text.DecimalFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Zahlenformatierungs- und Minecraft-Pixelbreite-Berechnungen.
 */
public final class FormatUtil {

    private static final Pattern COLOR_TAG_PATTERN = Pattern.compile("(?i)(§[0-9a-fk-or]|<[^>]+>)");

    private FormatUtil() {
    }

    public static String round2(double value) {
        DecimalFormat df = new DecimalFormat("0.00");
        return df.format(value);
    }

    public static String applyMoneyFormat(double amount, String pattern) {
        DecimalFormat df = new DecimalFormat(pattern);
        return df.format(amount);
    }

    /**
     * Berechnet die visuelle Pixelbreite eines einzelnen Zeichens im Minecraft-Standard-Font.
     * Berücksichtigt Standard-Zeichen, Ziffern, Sonderzeichen, SmallCaps und Bold-Stil (+1px).
     */
    public static int getCharPixelWidth(char c, boolean bold) {
        int width;
        switch (c) {
            case 'i', '!', '|', ':', ';', '.', ',', '\'', '`' -> width = 2;
            case 'l', '[', ']', '(', ')' -> width = 3;
            case 't', 'I', ' ', '{', '}', '*', '"' -> width = 4;
            case 'f', 'k', '<', '>', '«', '»', '●' -> width = 5;
            case '@', '~' -> width = 7;
            case '➊', '➋', '➌' -> width = 8;
            // SmallCaps Unicode Glyphen (exakte Minecraft-Advance-Werte im Default-Font)
            case '\u026A' -> width = 4; // ɪ (3px + 1px gap)
            case '\u029F', '\u1D0A' -> width = 5; // ʟ, ᴊ
            case '\u1D00', '\u0299', '\u1D04', '\u1D05', '\u1D07', '\uA730', '\u0262',
                 '\u029C', '\u1D0B', '\u0274', '\u1D0F', '\u1D18', '\u01EB', '\u0280',
                 '\uA731', '\u1D1B', '\u1D1C', '\u1D20', '\u028F', '\u1D22' -> width = 6; // ᴀ, ʙ, etc. (5px + 1px gap)
            case '\u1D0D', '\u1D21' -> width = 6; // ᴍ, ᴡ
            default -> {
                if (c >= 'A' && c <= 'Z') {
                    if (c == 'I') width = 4;
                    else if (c == 'M' || c == 'W') width = 7;
                    else width = 6;
                } else {
                    width = 6;
                }
            }
        }
        return width + (bold ? 1 : 0);
    }

    /**
     * Berechnet die visuelle Pixelbreite eines formatfreien oder Farbcodes enthaltenden Strings.
     */
    public static int getStringPixelWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int total = 0;
        boolean bold = false;
        int len = text.length();

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);

            // Legacy Farbcodes
            if (c == '§' && i + 1 < len) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (code == 'l') {
                    bold = true;
                } else if (code == 'r' || (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                    bold = false;
                }
                i++;
                continue;
            }

            // MiniMessage-Tags wie <b> oder </b>
            if (c == '<') {
                int close = text.indexOf('>', i);
                if (close > i) {
                    String tag = text.substring(i + 1, close).trim().toLowerCase();
                    if (tag.equals("b") || tag.equals("bold")) {
                        bold = true;
                    } else if (tag.equals("/b") || tag.equals("/bold") || tag.equals("reset")) {
                        bold = false;
                    }
                    i = close;
                    continue;
                }
            }

            total += getCharPixelWidth(c, bold);
        }

        return total;
    }

    /**
     * Berechnet die visuelle Pixelbreite eines Adventure Component-Baums.
     */
    public static int getComponentPixelWidth(Component component) {
        if (component == null) {
            return 0;
        }

        int total = 0;
        boolean bold = component.hasDecoration(TextDecoration.BOLD);

        if (component instanceof TextComponent textComp) {
            String content = textComp.content();
            if (content != null && !content.isEmpty()) {
                for (int i = 0; i < content.length(); i++) {
                    total += getCharPixelWidth(content.charAt(i), bold);
                }
            }
        }

        for (Component child : component.children()) {
            total += getComponentPixelWidth(child);
        }

        return total;
    }

    /**
     * Kürzt einen Text exakt auf die maximale Pixelbreite und hängt einen Suffix an (z. B. "...").
     */
    public static String truncateToPixelWidth(String text, int maxPixels, String suffix, boolean bold) {
        if (text == null) {
            return "";
        }
        if (getStringPixelWidth(text) <= maxPixels) {
            return text;
        }

        int suffixWidth = getStringPixelWidth(suffix);
        int available = maxPixels - suffixWidth;
        if (available <= 0) {
            return suffix;
        }

        StringBuilder sb = new StringBuilder();
        int current = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int charWidth = getCharPixelWidth(c, bold);
            if (current + charWidth > available) {
                break;
            }
            sb.append(c);
            current += charWidth;
        }

        return sb.append(suffix).toString();
    }

    /**
     * Kürzt einen sehr großen Betrag/Wert in lesbare Einheiten (z. B. 99.99T, 12.50B, 1.20M),
     * wenn der voll ausgeschriebene Wert sonst die Spaltenbreite des Leaderboards sprengen würde.
     * Behält den Währungs-Suffix / das Icon (z. B. " 🪙", " $", " Kills") am Ende bei.
     */
    public static String formatCompactValue(String originalFormatted, double rawValue) {
        if (originalFormatted == null || originalFormatted.isEmpty()) {
            return "";
        }
        double abs = Math.abs(rawValue);
        if (abs < 1_000_000.0) {
            return originalFormatted;
        }

        // Suffix/Einheit aus originalFormatted extrahieren (alles nach den führenden Ziffern/Kommas)
        String suffix = "";
        for (int i = originalFormatted.length() - 1; i >= 0; i--) {
            char c = originalFormatted.charAt(i);
            if (Character.isDigit(c) || c == ',' || c == '.') {
                suffix = originalFormatted.substring(i + 1);
                break;
            }
        }

        String compact;
        if (abs >= 1_000_000_000_000.0) {
            compact = String.format(Locale.US, "%.2fT", rawValue / 1_000_000_000_000.0);
        } else if (abs >= 1_000_000_000.0) {
            compact = String.format(Locale.US, "%.2fB", rawValue / 1_000_000_000.0);
        } else {
            compact = String.format(Locale.US, "%.2fM", rawValue / 1_000_000.0);
        }

        if (compact.contains(".00")) {
            compact = compact.replace(".00", "");
        }

        return compact + suffix;
    }
}
