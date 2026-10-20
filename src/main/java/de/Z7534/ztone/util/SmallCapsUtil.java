package de.Z7534.ztone.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Universeller Small-Caps-Konverter für das gesamte Ztone-Plugin.
 * Wandelt Text in Small Caps um (ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ),
 * während MiniMessage-Tags (<...>), Legacy-Farbcodes (&a, §a, &#RRGGBB)
 * und Platzhalter (%...%, {...}) unberührt bleiben.
 */
public final class SmallCapsUtil {

    private static final String NORMAL = "abcdefghijklmnopqrstuvwxyz";
    private static final String SMALL =
            "\u1D00\u0299\u1D04\u1D05\u1D07\uA730\u0262\u029C\u026A\u1D0A\u1D0B\u029F\u1D0D"
                    + "\u0274\u1D0F\u1D18\u01EB\u0280\uA731\u1D1B\u1D1C\u1D20\u1D21x\u028F\u1D22";

    private SmallCapsUtil() {
    }

    public static char toSmallCap(char c) {
        if (c == '\u0493') return '\uA730'; // Kyrillisches f -> Lateinisches Small-Capital F
        if (c == 's') return '\uA731';       // Normales s -> Lateinisches Small-Capital S
        char lower = Character.toLowerCase(c);
        int idx = NORMAL.indexOf(lower);
        if (idx >= 0) {
            return SMALL.charAt(idx);
        }
        return c;
    }

    public static String toSmallCaps(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length());
        int len = text.length();
        int i = 0;
        while (i < len) {
            char c = text.charAt(i);

            // MiniMessage tags <...>
            if (c == '<') {
                int close = text.indexOf('>', i + 1);
                if (close != -1) {
                    sb.append(text, i, close + 1);
                    i = close + 1;
                    continue;
                }
            }

            // Placeholders %...%
            if (c == '%') {
                int next = text.indexOf('%', i + 1);
                if (next != -1 && (next - i) <= 32 && !text.substring(i + 1, next).contains(" ")) {
                    sb.append(text, i, next + 1);
                    i = next + 1;
                    continue;
                }
            }

            // Placeholders {...}
            if (c == '{') {
                int next = text.indexOf('}', i + 1);
                if (next != -1 && (next - i) <= 32 && !text.substring(i + 1, next).contains(" ")) {
                    sb.append(text, i, next + 1);
                    i = next + 1;
                    continue;
                }
            }

            // Hex &#RRGGBB or §#RRGGBB
            if ((c == '&' || c == '\u00A7') && (i + 7 < len) && text.charAt(i + 1) == '#') {
                sb.append(text, i, i + 8);
                i += 8;
                continue;
            }

            // Color codes &a, §a
            if ((c == '&' || c == '\u00A7') && (i + 1 < len)) {
                char next = text.charAt(i + 1);
                if ("0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(next) >= 0) {
                    sb.append(c).append(next);
                    i += 2;
                    continue;
                }
            }

            // Deutsches Eszett
            if (c == '\u00DF') {
                sb.append("\uA731\uA731");
                i++;
                continue;
            }

            sb.append(toSmallCap(c));
            i++;
        }
        return sb.toString();
    }

    public static Component apply(Component component) {
        if (component == null) {
            return null;
        }
        Component result = component;
        if (result instanceof TextComponent text) {
            result = text.content(toSmallCaps(text.content()));
        }
        List<Component> children = result.children();
        if (!children.isEmpty()) {
            List<Component> mapped = new ArrayList<>(children.size());
            for (Component child : children) {
                mapped.add(apply(child));
            }
            result = result.children(mapped);
        }
        return result;
    }
}
