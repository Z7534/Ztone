package de.Z7534.ztone.mapcontrol.handlers;

import de.Z7534.ztone.mapcontrol.MapControl;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;

public class WorldInfoHandler implements PluginMessageListener {

    public static final String CHANNEL_WORLD_INFO = "worldinfo:world_id";

    private final MapControl plugin;

    public WorldInfoHandler(MapControl plugin) {
        this.plugin = plugin;
    }

    public void registerChannels() {
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin.getPlugin(), CHANNEL_WORLD_INFO, this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin.getPlugin(), CHANNEL_WORLD_INFO);
    }

    public void unregisterChannels() {
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin.getPlugin(), CHANNEL_WORLD_INFO, this);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin.getPlugin(), CHANNEL_WORLD_INFO);
    }

    public void sendWorldInfo(Player player) {
        if (!player.isOnline()) return;

        World world = player.getWorld();
        String worldIdentifier = world.getName();
        byte[] payload = worldIdentifier.getBytes(StandardCharsets.UTF_8);

        try {
            player.sendPluginMessage(plugin.getPlugin(), CHANNEL_WORLD_INFO, payload);
        } catch (Exception ignored) {}

        if (plugin.getPluginConfig().isDebug()) {
            plugin.getLogger().info("WorldInfo: Sent world ID '" + worldIdentifier + "' to " + player.getName());
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        sendWorldInfo(player);
    }
}
