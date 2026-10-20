package de.Z7534.ztone.playrewards.util;

import de.Z7534.ztone.util.SmallCapsUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class MessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    private MessageUtil() {}

    public static char toSmallCap(char c) {
        return SmallCapsUtil.toSmallCap(c);
    }

    public static String toSmallCaps(String text) {
        return SmallCapsUtil.toSmallCaps(text);
    }

    /**
     * Konvertiert einen String (MiniMessage oder Legacy Farbcodes mit &) in ein Adventure Component
     * und formatiert den sichtbaren Text dabei in stilvolle Small Caps.
     */
    public static Component parse(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        String converted = toSmallCaps(message);
        if (converted.contains("<") && converted.contains(">")) {
            return MINI_MESSAGE.deserialize(converted);
        }
        if (converted.contains("§")) {
            return LEGACY_SECTION.deserialize(converted);
        }
        if (converted.contains("&")) {
            String translated = ChatColor.translateAlternateColorCodes('&', converted);
            return LEGACY_SECTION.deserialize(translated);
        }
        return MINI_MESSAGE.deserialize(converted);
    }

    /**
     * Wandelt eine Liste von Strings in eine Liste von Components um.
     */
    public static List<Component> parseList(List<String> lines) {
        List<Component> components = new ArrayList<>();
        if (lines != null) {
            for (String line : lines) {
                components.add(parse(line));
            }
        }
        return components;
    }

    public static void sendMessage(CommandSender sender, String message) {
        if (sender != null && message != null && !message.isEmpty()) {
            sender.sendMessage(parse(message));
        }
    }

    public static void sendMessage(CommandSender sender, String prefix, String message) {
        if (sender != null && message != null && !message.isEmpty()) {
            String combined = (prefix != null ? prefix : "") + message;
            sender.sendMessage(parse(combined));
        }
    }

    public static void sendActionBar(Player player, String message) {
        if (player != null && message != null && !message.isEmpty()) {
            player.sendActionBar(parse(message));
        }
    }

    public static void sendActionBar(Player player, String prefix, String message) {
        if (player != null && message != null && !message.isEmpty()) {
            String combined = (prefix != null ? prefix : "") + message;
            player.sendActionBar(parse(combined));
        }
    }
}
