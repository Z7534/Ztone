package de.Z7534.ztone.mapcontrol.handlers;

import de.Z7534.ztone.mapcontrol.MapControl;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public class VoxelMapHandler implements PluginMessageListener {

    public static final String CODE_DISABLE_RADAR = "§3 §6 §3 §6 §3 §6 §e";
    public static final String CODE_DISABLE_CAVE = "§3 §6 §3 §6 §3 §6 §d";
    public static final String CODE_DISABLE_BOTH = "§3 §6 §3 §6 §3 §6 §e§3 §6 §3 §6 §3 §6 §d ";

    public static final String CHANNEL_VOXELMAP = "voxelmap:settings";

    private final MapControl plugin;

    public VoxelMapHandler(MapControl plugin) {
        this.plugin = plugin;
    }

    public void registerChannels() {
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin.getPlugin(), CHANNEL_VOXELMAP, this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin.getPlugin(), CHANNEL_VOXELMAP);
    }

    public void unregisterChannels() {
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin.getPlugin(), CHANNEL_VOXELMAP, this);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin.getPlugin(), CHANNEL_VOXELMAP);
    }

    public void applyRestrictions(Player player) {
        if (!player.isOnline()) return;

        World world = player.getWorld();
        if (!plugin.getPluginConfig().isVoxelMapEnabled(world)) {
            return;
        }

        String bypassPerm = plugin.getPluginConfig().getBypassPermission();
        if (bypassPerm != null && !bypassPerm.isEmpty() && player.hasPermission(bypassPerm)) {
            if (plugin.getPluginConfig().isDebug()) {
                plugin.getLogger().info("VoxelMap: Player " + player.getName() + " bypassed restrictions.");
            }
            return;
        }

        boolean disableRadar = plugin.getPluginConfig().isVoxelMapDisableRadar(world);
        boolean disableCave = plugin.getPluginConfig().isVoxelMapDisableCaveMode(world);

        if (disableRadar && disableCave) {
            sendCode(player, CODE_DISABLE_BOTH);
        } else if (disableRadar) {
            sendCode(player, CODE_DISABLE_RADAR);
        } else if (disableCave) {
            sendCode(player, CODE_DISABLE_CAVE);
        }

        if (plugin.getPluginConfig().isDebug()) {
            plugin.getLogger().info("VoxelMap: Enforced restrictions for " + player.getName() + " in world " + world.getName());
        }
    }

    private void sendCode(Player player, String code) {
        player.sendMessage(Component.text(code));
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (plugin.getPluginConfig().isDebug()) {
            plugin.getLogger().info("VoxelMap: Received message on channel " + channel + " from " + player.getName());
        }
        applyRestrictions(player);
    }
}
