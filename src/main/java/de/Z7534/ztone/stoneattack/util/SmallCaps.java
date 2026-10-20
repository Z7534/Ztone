package de.Z7534.ztone.stoneattack.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Wandelt Text in Small Caps um.
 *
 * Die Umwandlung passiert auf dem fertig geparsten Component-Baum
 * und ueberspringt eventuelle uebrige Legacy-Farbcodes (§a, &a, §x...),
 * damit niemals Farbcodes in Small-Caps-Zeichen verfaelscht werden.
 */
public final class SmallCaps {

    private static final String NORMAL = "abcdefghijklmnopqrstuvwxyz";
    private static final String SMALL =
            "\u1D00\u0299\u1D04\u1D05\u1D07\uA730\u0262\u029C\u026A\u1D0A\u1D0B\u029F\u1D0D"
                    + "\u0274\u1D0F\u1D18\u01EB\u0280\uA731\u1D1B\u1D1C\u1D20\u1D21x\u028F\u1D22";

    private SmallCaps() {
    }

    public static String convert(String text, boolean convertUppercase) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);

            // Farbcodes §a, &a, §x... nicht in SmallCaps umwandeln!
            if ((c == '\u00A7' || c == '&') && i + 1 < len) {
                out.append(c).append(text.charAt(i + 1));
                i++;
                continue;
            }

            char lower = Character.toLowerCase(c);
            boolean isUpper = c != lower;
            int index = lower >= 'a' && lower <= 'z' ? NORMAL.indexOf(lower) : -1;
            if (index >= 0 && (!isUpper || convertUppercase)) {
                out.append(SMALL.charAt(index));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    public static Component apply(Component component, boolean convertUppercase) {
        if (component == null) {
            return null;
        }
        Component result = component;
        if (result instanceof TextComponent text) {
            result = text.content(convert(text.content(), convertUppercase));
        }
        List<Component> children = result.children();
        if (!children.isEmpty()) {
            List<Component> mapped = new ArrayList<>(children.size());
            for (Component child : children) {
                mapped.add(apply(child, convertUppercase));
            }
            result = result.children(mapped);
        }
        return result;
    }
}
