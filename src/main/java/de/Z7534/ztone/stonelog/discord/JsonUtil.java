package de.Z7534.ztone.stonelog.discord;

/**
 * Baut das JSON-Payload fuer Discord-Webhooks manuell zusammen.
 * Bewusst ohne externe JSON-Library, damit Stonelog keine zusaetzlichen
 * Abhaengigkeiten in die Server-Jar mitbringen muss.
 */
public class JsonUtil {

    public static String buildPayload(String username, String avatarUrl, Embed embed) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        if (username != null && !username.isBlank()) {
            String sanitized = username.trim();
            // Discord API: username must be between 1 and 80 characters
            if (sanitized.length() > 80) {
                sanitized = sanitized.substring(0, 77) + "...";
            }
            sb.append("\"username\":\"").append(escape(sanitized)).append("\",");
        }
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            sb.append("\"avatar_url\":\"").append(escape(avatarUrl)).append("\",");
        }

        sb.append("\"embeds\":[{");

        if (embed.title != null) {
            sb.append("\"title\":\"").append(escape(embed.title)).append("\",");
        }
        if (embed.description != null) {
            sb.append("\"description\":\"").append(escape(embed.description)).append("\",");
        }
        if (embed.color != null) {
            sb.append("\"color\":").append(embed.color).append(",");
        }
        if (embed.footerText != null) {
            sb.append("\"footer\":{\"text\":\"").append(escape(embed.footerText)).append("\"},");
        }

        sb.append("\"fields\":[");
        for (int i = 0; i < embed.fields.size(); i++) {
            Embed.Field f = embed.fields.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"name\":\"").append(escape(f.name)).append("\",")
                    .append("\"value\":\"").append(escape(f.value)).append("\",")
                    .append("\"inline\":").append(f.inline).append("}");
        }
        sb.append("]");

        sb.append("}]}");
        return sb.toString();
    }

    private static String escape(String input) {
        if (input == null) return "";
        StringBuilder out = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
