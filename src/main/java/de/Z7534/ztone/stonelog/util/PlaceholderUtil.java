package de.Z7534.ztone.stonelog.util;

import java.util.Map;

public class PlaceholderUtil {

    public static String apply(String input, Map<String, String> placeholders) {
        if (input == null) return null;
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            result = result.replace("{" + entry.getKey() + "}", value);
        }
        return result;
    }
}
