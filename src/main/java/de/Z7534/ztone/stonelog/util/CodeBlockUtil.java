package de.Z7534.ztone.stonelog.util;

/**
 * Packt einen Wert in einen Discord-Codeblock (```wert```), so wie es bei
 * Chat- und Command-Nachrichten schon der Fall ist.
 */
public final class CodeBlockUtil {

    private CodeBlockUtil() {
    }

    public static String wrap(String value) {
        if (value == null || value.isBlank()) {
            // Discord lehnt leere Feldwerte mit 400 ab.
            return "```-```";
        }
        // Backticks im Wert wuerden den Codeblock zerschiessen.
        String safe = value.replace("`", "'");
        return "```" + safe + "```";
    }
}
