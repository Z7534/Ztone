package de.Z7534.ztone.mapcontrol.handlers;

import de.Z7534.ztone.mapcontrol.MapControl;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public class XaeroHandler implements PluginMessageListener {

    // Formatting code protocols for Xaero's Minimap and World Map
    public static final String CODE_FAIR_PLAY = "§f§a§i§r§x§a§e§r§o";
    public static final String CODE_NO_MINIMAP = "§n§o§m§i§n§i§m§a§p";
    public static final String CODE_RESET = "§r§e§s§e§t§x§a§e§r§o";
    public static final String CODE_WORLDMAP_NETHER_FAIR = "§x§a§e§r§o§w§m§n§e§t§h§e§r§i§s§f§a§i§r";
    public static final String CODE_MINIMAP_NETHER_FAIR = "§x§a§e§r§o§m§m§n§e§t§h§e§r§i§s§f§a§i§r";

    public static final String CHANNEL_MINIMAP = "xaerominimap:main";
    public static final String CHANNEL_WORLDMAP = "xaeroworldmap:main";
    public static final String CHANNEL_LIB = "xaerolib:main";

    private final MapControl plugin;

    public XaeroHandler(MapControl plugin) {
        this.plugin = plugin;
    }

    public void registerChannels() {
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin.getPlugin(), CHANNEL_MINIMAP, this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin.getPlugin(), CHANNEL_WORLDMAP, this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin.getPlugin(), CHANNEL_LIB, this);

        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin.getPlugin(), CHANNEL_MINIMAP);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin.getPlugin(), CHANNEL_WORLDMAP);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin.getPlugin(), CHANNEL_LIB);
    }

    public void unregisterChannels() {
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin.getPlugin(), CHANNEL_MINIMAP, this);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin.getPlugin(), CHANNEL_WORLDMAP, this);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin.getPlugin(), CHANNEL_LIB, this);

        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin.getPlugin(), CHANNEL_MINIMAP);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin.getPlugin(), CHANNEL_WORLDMAP);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin.getPlugin(), CHANNEL_LIB);
    }

    public void applyRestrictions(Player player) {
        if (!player.isOnline()) return;

        World world = player.getWorld();
        if (!plugin.getPluginConfig().isXaeroEnabled(world)) {
            return;
        }

        String bypassPerm = plugin.getPluginConfig().getBypassPermission();
        if (bypassPerm != null && !bypassPerm.isEmpty() && player.hasPermission(bypassPerm)) {
            sendCode(player, CODE_RESET);
            if (plugin.getPluginConfig().isDebug()) {
                plugin.getLogger().info("Xaero: Player " + player.getName() + " bypassed restrictions.");
            }
            return;
        }

        boolean disableMinimap = plugin.getPluginConfig().isXaeroDisableMinimap(world);
        boolean disableRadar = plugin.getPluginConfig().isXaeroDisableRadar(world);
        boolean disableCave = plugin.getPluginConfig().isXaeroDisableCaveMode(world);
        boolean allowNetherCave = plugin.getPluginConfig().isXaeroAllowNetherCaveMode(world);

        if (disableMinimap) {
            sendCode(player, CODE_NO_MINIMAP);
        }

        if (disableRadar || disableCave) {
            sendCode(player, CODE_FAIR_PLAY);

            if (allowNetherCave) {
                sendCode(player, CODE_WORLDMAP_NETHER_FAIR);
                sendCode(player, CODE_MINIMAP_NETHER_FAIR);
            }
        } else if (!disableMinimap) {
            sendCode(player, CODE_RESET);
        }

        if (plugin.getPluginConfig().isDebug()) {
            plugin.getLogger().info("Xaero: Enforced restrictions for " + player.getName() + " in world " + world.getName());
        }
    }

    private void sendCode(Player player, String code) {
        player.sendMessage(Component.text(code));
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (plugin.getPluginConfig().isDebug()) {
            plugin.getLogger().info("Xaero: Received message on channel " + channel + " from " + player.getName());
        }
        applyRestrictions(player);
    }
}
