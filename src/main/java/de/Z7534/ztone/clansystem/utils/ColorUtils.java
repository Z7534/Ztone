package de.Z7534.ztone.clansystem.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtils {

    /**
     * Die 15 auswählbaren, benannten Farben aus dem Farb-Setting-GUI (Code -> Anzeigename).
     * Wird sowohl von der normalen Farbauswahl als auch von der Gradient-Farbauswahl verwendet,
     * damit an beiden Stellen garantiert dieselben Farben zur Auswahl stehen.
     */
    public static final java.util.LinkedHashMap<String, String> NAMED_COLORS = new java.util.LinkedHashMap<>();

    /**
     * Hex-Entsprechung derselben 15 Farben (Code -> Hex), damit sie auch für den
     * Gradient (der intern mit Hex-Werten rechnet) genutzt werden können.
     */
    private static final java.util.LinkedHashMap<String, String> NAMED_COLOR_HEX = new java.util.LinkedHashMap<>();

    static {
        NAMED_COLORS.put("&b", "Aqua");
        NAMED_COLOR_HEX.put("&b", "#55FFFF");
        NAMED_COLORS.put("&0", "Black");
        NAMED_COLOR_HEX.put("&0", "#000000");
        NAMED_COLORS.put("&9", "Blue");
        NAMED_COLOR_HEX.put("&9", "#5555FF");
        NAMED_COLORS.put("&2", "Dark Green");
        NAMED_COLOR_HEX.put("&2", "#00AA00");
        NAMED_COLORS.put("&5", "Dark Purple");
        NAMED_COLOR_HEX.put("&5", "#AA00AA");
        NAMED_COLORS.put("&4", "Dark Red");
        NAMED_COLOR_HEX.put("&4", "#AA0000");
        NAMED_COLORS.put("&c", "Red");
        NAMED_COLOR_HEX.put("&c", "#FF5555");
        NAMED_COLORS.put("&3", "Dark Aqua");
        NAMED_COLOR_HEX.put("&3", "#00AAAA");
        NAMED_COLORS.put("&1", "Dark Blue");
        NAMED_COLOR_HEX.put("&1", "#0000AA");
        NAMED_COLORS.put("&8", "Dark Gray");
        NAMED_COLOR_HEX.put("&8", "#555555");
        NAMED_COLORS.put("&6", "Gold");
        NAMED_COLOR_HEX.put("&6", "#FFAA00");
        NAMED_COLORS.put("&7", "Gray");
        NAMED_COLOR_HEX.put("&7", "#AAAAAA");
        NAMED_COLORS.put("&a", "Green");
        NAMED_COLOR_HEX.put("&a", "#55FF55");
        NAMED_COLORS.put("&e", "Yellow");
        NAMED_COLOR_HEX.put("&e", "#FFFF55");
        NAMED_COLORS.put("&f", "White");
        NAMED_COLOR_HEX.put("&f", "#FFFFFF");
    }

    /**
     * Liefert die Hex-Entsprechung einer der 15 benannten Farben (z.B. "&c" -> "#FF5555").
     * Ist der Code unbekannt, wird Weiß zurückgegeben.
     */
    public static String hexForNamedColor(String code) {
        return NAMED_COLOR_HEX.getOrDefault(code == null ? "" : code.toLowerCase(), "#FFFFFF");
    }

    /**
     * Ermittelt zu einem beliebigen Hex-Farbwert den am nächsten liegenden Code aus den 15
     * benannten Farben (kleinster euklidischer Abstand im RGB-Raum). Wird z.B. genutzt, um
     * im Gradient-Menü das Vorschau-Item farblich an die aktuell gewählte Start-/Endfarbe
     * anzupassen, auch wenn intern mit Hex statt mit Farbcodes gearbeitet wird.
     */
    public static String nearestNamedColorCode(String hex) {
        int[] target = parseHexToRgb(hex);

        String bestCode = "&f";
        double bestDistance = Double.MAX_VALUE;

        for (Map.Entry<String, String> entry : NAMED_COLOR_HEX.entrySet()) {
            int[] candidate = parseHexToRgb(entry.getValue());
            double distance = Math.pow(target[0] - candidate[0], 2)
                    + Math.pow(target[1] - candidate[1], 2)
                    + Math.pow(target[2] - candidate[2], 2);

            if (distance < bestDistance) {
                bestDistance = distance;
                bestCode = entry.getKey();
            }
        }

        return bestCode;
    }

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("&([0-9a-fk-or])");
    private static final Pattern SECTION_COLOR_CODE_PATTERN = Pattern.compile("\\\\u00A7[0-9a-fk-or]");
    private static final Pattern FORMAT_CODE_PATTERN = Pattern.compile("&([klmnoKLMNO])");
    private static final Pattern HEX_VALIDATION_PATTERN = Pattern.compile("^#?[A-Fa-f0-9]{6}$");

    public static Component colorize(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }

        message = translateHexColorCodes(message);

        return LegacyComponentSerializer.legacyAmpersand().deserialize(message);
    }

    public static String translateColors(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        message = translateHexColorCodes(message);

        char[] chars = message.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && "0123456789AaBbCcDdEeFfKkLlMmNnOoRr".indexOf(chars[i + 1]) > -1) {
                chars[i] = '\u00A7';
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }

        return new String(chars);
    }

    private static String translateHexColorCodes(String message) {
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder buffer = new StringBuilder();

        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("&x");
            for (char c : hex.toCharArray()) {
                replacement.append('&').append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);

        return buffer.toString();
    }

    public static String stripColors(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        message = HEX_PATTERN.matcher(message).replaceAll("");

        message = COLOR_CODE_PATTERN.matcher(message).replaceAll("");
        message = SECTION_COLOR_CODE_PATTERN.matcher(message).replaceAll("");

        return message;
    }

    public static boolean isColorAllowed(String colorCode, java.util.List<String> allowedColors) {
        if (allowedColors == null || allowedColors.isEmpty()) {
            return true;
        }
        return allowedColors.contains(colorCode.toLowerCase());
    }

    public static java.util.List<String> extractColorCodes(String message) {
        java.util.List<String> codes = new java.util.ArrayList<>();

        if (message == null || message.isEmpty()) {
            return codes;
        }

        Matcher matcher = COLOR_CODE_PATTERN.matcher(message);
        while (matcher.find()) {
            codes.add("&" + matcher.group(1).toLowerCase());
        }

        return codes;
    }

    public static boolean containsBold(String message) {
        return message != null && (message.contains("&l") || message.contains("&L"));
    }

    public static boolean containsItalic(String message) {
        return message != null && (message.contains("&o") || message.contains("&O"));
    }

    public static boolean containsUnderline(String message) {
        return message != null && (message.contains("&n") || message.contains("&N"));
    }

    public static boolean containsStrikethrough(String message) {
        return message != null && (message.contains("&m") || message.contains("&M"));
    }

    public static boolean containsMagic(String message) {
        return message != null && (message.contains("&k") || message.contains("&K"));
    }

    /**
     * Setzt oder entfernt die Fett-Formatierung (&l) auf einem Text, ohne
     * andere vorhandene Formatierungen (z.B. &n, &m) oder Farbcodes zu verändern.
     */
    public static String setBold(String message, boolean bold) {
        String base = message == null ? "" : message;
        String stripped = base.replace("&l", "").replace("&L", "");

        if (!bold) {
            return stripped;
        }

        return "&l" + stripped;
    }

    /**
     * Extrahiert ausschließlich Formatierungscodes (fett, kursiv, unterstrichen,
     * durchgestrichen, magisch) aus einem Text - keine Farbcodes. Wird benötigt,
     * um Formatierungen zu erhalten, wenn ein Gradient über den Text gelegt wird.
     */
    public static String extractFormatCodes(String message) {
        StringBuilder result = new StringBuilder();
        if (message == null || message.isEmpty()) {
            return "";
        }

        Matcher matcher = FORMAT_CODE_PATTERN.matcher(message);
        while (matcher.find()) {
            result.append('&').append(Character.toLowerCase(matcher.group(1).charAt(0)));
        }

        return result.toString();
    }

    /**
     * Prüft, ob ein String ein gültiger Hex-Farbcode ist (mit oder ohne '#'), z.B. "#FF00AA" oder "ff00aa".
     */
    public static boolean isValidHex(String hex) {
        return hex != null && HEX_VALIDATION_PATTERN.matcher(hex.trim()).matches();
    }

    /**
     * Normalisiert einen Hex-Farbcode auf das Format "#RRGGBB" (Großbuchstaben, mit '#').
     * Ist die Eingabe ungültig, wird Weiß ("#FFFFFF") zurückgegeben.
     */
    public static String normalizeHex(String hex) {
        if (!isValidHex(hex)) {
            return "#FFFFFF";
        }
        String trimmed = hex.trim();
        if (!trimmed.startsWith("#")) {
            trimmed = "#" + trimmed;
        }
        return trimmed.toUpperCase();
    }

    /**
     * Erzeugt einen Farbverlauf (Gradient) zwischen zwei Hex-Farben über den übergebenen,
     * unformatierten Text. Jedes Zeichen bekommt seine eigene interpolierte Hex-Farbe im
     * Minecraft-Format (&x&r&r&g&g&b&b), sodass der Text z.B. von Rot fließend nach Blau
     * übergeht. Da Farbcodes in Minecraft jede vorherige Formatierung zurücksetzen, wird
     * "extraFormatting" (z.B. "&l&n") nach jedem Farbcode erneut eingefügt.
     *
     * @param text            reiner Text ohne Farbcodes (z.B. Clan-Name oder Suffix-Text)
     * @param startHex        Startfarbe, z.B. "#FF0000"
     * @param endHex          Endfarbe, z.B. "#0000FF"
     * @param extraFormatting zusätzliche Formatierungscodes, die pro Zeichen wiederholt werden (darf null sein)
     */
    public static String applyGradient(String text, String startHex, String endHex, String extraFormatting) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        int[] start = parseHexToRgb(startHex);
        int[] end = parseHexToRgb(endHex);
        String formatting = extraFormatting == null ? "" : extraFormatting;

        int length = text.length();
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            double ratio = length == 1 ? 0.0 : (double) i / (length - 1);

            int r = interpolateChannel(start[0], end[0], ratio);
            int g = interpolateChannel(start[1], end[1], ratio);
            int b = interpolateChannel(start[2], end[2], ratio);

            result.append("&x");
            appendHexColorCode(result, r, g, b);
            result.append(formatting);
            result.append(c);
        }

        return result.toString();
    }

    public static String applyGradient(String text, String startHex, String endHex) {
        return applyGradient(text, startHex, endHex, "");
    }

    private static int interpolateChannel(int from, int to, double ratio) {
        return (int) Math.round(from + (to - from) * ratio);
    }

    private static int[] parseHexToRgb(String hex) {
        String clean = (hex == null ? "" : hex.trim()).replace("#", "");
        if (clean.length() != 6) {
            return new int[]{255, 255, 255};
        }
        try {
            int r = Integer.parseInt(clean.substring(0, 2), 16);
            int g = Integer.parseInt(clean.substring(2, 4), 16);
            int b = Integer.parseInt(clean.substring(4, 6), 16);
            return new int[]{r, g, b};
        } catch (NumberFormatException e) {
            return new int[]{255, 255, 255};
        }
    }

    private static void appendHexColorCode(StringBuilder sb, int r, int g, int b) {
        String hex = String.format("%02x%02x%02x", r, g, b);
        for (char h : hex.toCharArray()) {
            sb.append('&').append(h);
        }
    }
}
