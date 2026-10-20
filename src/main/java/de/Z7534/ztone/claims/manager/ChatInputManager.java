package de.Z7534.ztone.claims.manager;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ChatInputManager {

    private final JavaPlugin plugin;

    private record PendingInput(Consumer<String> onInput, Runnable onCancelOrTimeout, BukkitTask timeoutTask) {
    }

    // Concurrent: handleChat/isAwaiting werden vom asynchronen Chat-Thread aufgerufen.
    private final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();

    public ChatInputManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isAwaiting(UUID player) {
        return pending.containsKey(player);
    }

    public void await(Player player, int timeoutSeconds, Consumer<String> onInput, Runnable onCancelOrTimeout) {
        cancelSilently(player.getUniqueId());
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            PendingInput input = pending.remove(player.getUniqueId());
            if (input != null) {
                plugin.getServer().getScheduler().runTask(plugin, input.onCancelOrTimeout());
            }
        }, timeoutSeconds * 20L);
        pending.put(player.getUniqueId(), new PendingInput(onInput, onCancelOrTimeout, task));
    }

    public boolean handleChat(Player player, String message) {
        PendingInput input = pending.remove(player.getUniqueId());
        if (input == null) return false;
        input.timeoutTask().cancel();
        if (message.equalsIgnoreCase("abbrechen")) {
            plugin.getServer().getScheduler().runTask(plugin, input.onCancelOrTimeout());
        } else {
            plugin.getServer().getScheduler().runTask(plugin, () -> input.onInput().accept(message));
        }
        return true;
    }

    private void cancelSilently(UUID uuid) {
        PendingInput old = pending.remove(uuid);
        if (old != null) {
            old.timeoutTask().cancel();
        }
    }

    public void cancel(UUID uuid) {
        cancelSilently(uuid);
    }
}
