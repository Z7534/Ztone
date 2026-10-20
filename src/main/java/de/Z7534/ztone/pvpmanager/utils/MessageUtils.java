package de.Z7534.ztone.pvpmanager.utils;

import de.Z7534.ztone.util.SmallCapsUtil;
import org.bukkit.entity.Player;

import java.util.Map;

public final class MessageUtils {

    private MessageUtils() {
    }

    public static void send(Player player, String rawMessage, Map<String, String> placeholders) {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return;
        }

        boolean forceActionBar = rawMessage.startsWith("!actionbar");
        boolean forceChat = rawMessage.startsWith("!chat");
        String message = rawMessage;
        if (forceActionBar) {
            message = message.substring("!actionbar".length()).trim();
        } else if (forceChat) {
            message = message.substring("!chat".length()).trim();
        }

        message = SmallCapsUtil.toSmallCaps(message);

        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }

        String colored = ColorUtils.colorize(message);

        if (forceActionBar) {
            player.sendActionBar(colored);
        } else {
            player.sendMessage(colored);
        }
    }

    public static void send(Player player, String rawMessage) {
        send(player, rawMessage, null);
    }
}
