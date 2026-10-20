package de.Z7534.ztone.claims.listener;

import de.Z7534.ztone.claims.manager.ChatInputManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class ChatListener implements Listener {

    private final ChatInputManager chatInputManager;

    public ChatListener(ChatInputManager chatInputManager) {
        this.chatInputManager = chatInputManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        if (!chatInputManager.isAwaiting(e.getPlayer().getUniqueId())) return;
        String message = PlainTextComponentSerializer.plainText().serialize(e.message());
        e.setCancelled(true);
        chatInputManager.handleChat(e.getPlayer(), message.trim());
    }
}
