package de.Z7534.ztone.stoneattack.util;

import de.Z7534.ztone.util.SmallCapsUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zentrale Stelle fuer das Parsen von MiniMessage- und Farb-Strings.
 * Unterstuetzt MiniMessage-Tags (<red>, <#rrggbb>, <gradient...>),
 * Legacy-Farbcodes (&a, §a), Legacy-Hex (§x§r§r§g§g§b§b, &#rrggbb)
 * und Platzhalter ohne Farbverlust oder Encoding-Probleme.
 */
public final class ColorUtil {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    private static final Pattern LEGACY_HEX_PATTERN =
            Pattern.compile("(?i)[\\u00A7&]x[\\u00A7&]([0-9a-f])[\\u00A7&]([0-9a-f])[\\u00A7&]([0-9a-f])[\\u00A7&]([0-9a-f])[\\u00A7&]([0-9a-f])[\\u00A7&]([0-9a-f])");
    private static final Pattern HASH_HEX_PATTERN =
            Pattern.compile("(?i)[\\u00A7&]#([0-9a-fA-F]{6})");
    private static final Pattern LEGACY_CODE_PATTERN =
            Pattern.compile("(?i)[\\u00A7&]([0-9a-fk-orA-FK-OR])");
    private static final Pattern MINIMESSAGE_TAG_PATTERN =
            Pattern.compile("<[^<>]*>");

    private ColorUtil() {
    }

    /**
     * Wandelt saemtliche Legacy-Farbcodes und Hex-Formate in echte MiniMessage-Tags um,
     * damit Farben sauber von Adventure interpretiert werden.
     */
    public static String legacyToMiniMessage(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // 1. §x§r§r§g§g§b§b / &x&r&r&g&g&b&b -> <#rrggbb>
        Matcher hexMatcher = LEGACY_HEX_PATTERN.matcher(text);
        text = hexMatcher.replaceAll("<#$1$2$3$4$5$6>");

        // 2. &#rrggbb / §#rrggbb -> <#rrggbb>
        Matcher hashMatcher = HASH_HEX_PATTERN.matcher(text);
        text = hashMatcher.replaceAll("<#$1>");

        // 3. §a, &a etc. -> MiniMessage Tags
        Matcher codeMatcher = LEGACY_CODE_PATTERN.matcher(text);
        if (!codeMatcher.find()) {
            return text;
        }

        StringBuffer sb = new StringBuffer();
        do {
            char code = Character.toLowerCase(codeMatcher.group(1).charAt(0));
            String tag = switch (code) {
                case '0' -> "<black>";
                case '1' -> "<dark_blue>";
                case '2' -> "<dark_green>";
                case '3' -> "<dark_aqua>";
                case '4' -> "<dark_red>";
                case '5' -> "<dark_purple>";
                case '6' -> "<gold>";
                case '7' -> "<gray>";
                case '8' -> "<dark_gray>";
                case '9' -> "<blue>";
                case 'a' -> "<green>";
                case 'b' -> "<aqua>";
                case 'c' -> "<red>";
                case 'd' -> "<light_purple>";
                case 'e' -> "<yellow>";
                case 'f' -> "<white>";
                case 'k' -> "<obfuscated>";
                case 'l' -> "<bold>";
                case 'm' -> "<strikethrough>";
                case 'n' -> "<underlined>";
                case 'o' -> "<italic>";
                case 'r' -> "<reset>";
                default -> "";
            };
            codeMatcher.appendReplacement(sb, Matcher.quoteReplacement(tag));
        } while (codeMatcher.find());
        codeMatcher.appendTail(sb);

        return sb.toString();
    }

    public static Component parse(String miniMessage) {
        if (miniMessage == null || miniMessage.isEmpty()) {
            return Component.empty();
        }
        String prepared = legacyToMiniMessage(miniMessage);
        try {
            return MINI.deserialize(SmallCapsUtil.toSmallCaps(prepared));
        } catch (Exception e) {
            try {
                return LEGACY_SECTION.deserialize(miniMessage);
            } catch (Exception ignored) {
                return Component.text(miniMessage);
            }
        }
    }

    /**
     * Parsed einen Text mit Platzhaltern (%platzhalter%).
     * Platzhalter-Werte mit Farb- oder Hex-Codes werden sauber in MiniMessage-Tags
     * umgewandelt und bleiben voll erhalten.
     */
    public static Component parse(String miniMessage, Map<String, String> placeholders) {
        if (miniMessage == null || miniMessage.isEmpty()) {
            return Component.empty();
        }
        String result = miniMessage;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String value = entry.getValue() == null ? "" : entry.getValue();
                value = legacyToMiniMessage(value);
                result = result.replace("%" + entry.getKey() + "%", value);
            }
        }
        String prepared = legacyToMiniMessage(result);
        try {
            return MINI.deserialize(SmallCapsUtil.toSmallCaps(prepared));
        } catch (Exception e) {
            try {
                return LEGACY_SECTION.deserialize(result);
            } catch (Exception ignored) {
                return Component.text(result);
            }
        }
    }

    /**
     * Entfernt JEDE Formatierung aus einem Fremdtext: §- und &-Farbcodes,
     * Hex-Codes und MiniMessage-Tags. Uebrig bleibt reiner Text.
     */
    public static String stripFormatting(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String result = LEGACY_HEX_PATTERN.matcher(text).replaceAll("");
        result = HASH_HEX_PATTERN.matcher(result).replaceAll("");
        result = LEGACY_CODE_PATTERN.matcher(result).replaceAll("");
        result = MINIMESSAGE_TAG_PATTERN.matcher(result).replaceAll("");
        return result.trim();
    }

    public static String stripToPlain(String miniMessage) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(parse(miniMessage));
    }

    /** Kleiner Fluent-Helfer zum Sammeln von Platzhaltern. */
    public static PlaceholderMap placeholders() {
        return new PlaceholderMap();
    }

    public static final class PlaceholderMap {
        private final Map<String, String> map = new LinkedHashMap<>();

        public PlaceholderMap put(String key, Object value) {
            map.put(key, String.valueOf(value));
            return this;
        }

        public Map<String, String> build() {
            return map;
        }
    }
}
