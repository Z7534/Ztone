package de.Z7534.ztone.claims.util;

import de.Z7534.ztone.util.SmallCapsUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final Pattern HEX_COLOR_PAIR =
            Pattern.compile("<#([0-9a-fA-F]{6})>(.*?)</#([0-9a-fA-F]{6})>", Pattern.DOTALL);

    private static final Pattern LEGACY_CODE = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

    private static final Map<Character, String> LEGACY_TAGS = Map.ofEntries(
            Map.entry('0', "black"),
            Map.entry('1', "dark_blue"),
            Map.entry('2', "dark_green"),
            Map.entry('3', "dark_aqua"),
            Map.entry('4', "dark_red"),
            Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"),
            Map.entry('7', "gray"),
            Map.entry('8', "dark_gray"),
            Map.entry('9', "blue"),
            Map.entry('a', "green"),
            Map.entry('b', "aqua"),
            Map.entry('c', "red"),
            Map.entry('d', "light_purple"),
            Map.entry('e', "yellow"),
            Map.entry('f', "white"),
            Map.entry('k', "obfuscated"),
            Map.entry('l', "bold"),
            Map.entry('m', "strikethrough"),
            Map.entry('n', "underlined"),
            Map.entry('o', "italic"),
            Map.entry('r', "reset")
    );

    private Text() {
    }

    public static Component parse(String raw) {
        if (raw == null) {
            return Component.empty();
        }
        String prepared = SmallCapsUtil.toSmallCaps(raw);
        return MM.deserialize(resolveGradients(resolveLegacyColors(prepared)));
    }

    public static Component parse(String raw, Map<String, String> placeholders) {
        if (raw == null) {
            return Component.empty();
        }
        String prepared = SmallCapsUtil.toSmallCaps(raw);
        String result = resolveGradients(resolveLegacyColors(prepared));
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            result = result.replace("{" + e.getKey() + "}", escape(e.getValue()));
        }
        return MM.deserialize(result);
    }

    /**
     * Macht beliebigen Text (z.B. Claim-Namen) MiniMessage-sicher, bevor er in eine
     * Vorlage wie "<green>" + name eingebaut wird.
     */
    public static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return MM.escapeTags(raw);
    }

    public static Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    public static Component line(String raw) {
        return noItalic(parse(raw));
    }

    public static Component line(String raw, Map<String, String> placeholders) {
        return noItalic(parse(raw, placeholders));
    }

    private static String resolveLegacyColors(String raw) {
        if (raw.indexOf('&') < 0) {
            return raw;
        }
        Matcher matcher = LEGACY_CODE.matcher(raw);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String tag = LEGACY_TAGS.get(Character.toLowerCase(matcher.group(1).charAt(0)));
            String replacement = (tag != null) ? "<" + tag + ">" : matcher.group();
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String resolveGradients(String raw) {
        if (raw.indexOf('#') < 0) {
            return raw;
        }
        Matcher matcher = HEX_COLOR_PAIR.matcher(raw);
        if (!matcher.find()) {
            return raw;
        }
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        do {
            result.append(raw, lastEnd, matcher.start());
            String from = matcher.group(1);
            String inner = matcher.group(2);
            String to = matcher.group(3);
            if (from.equalsIgnoreCase(to)) {
                result.append(matcher.group());
            } else {
                result.append("<gradient:#").append(from).append(':').append('#').append(to).append('>')
                        .append(inner)
                        .append("</gradient>");
            }
            lastEnd = matcher.end();
        } while (matcher.find());
        result.append(raw, lastEnd, raw.length());
        return result.toString();
    }
}
