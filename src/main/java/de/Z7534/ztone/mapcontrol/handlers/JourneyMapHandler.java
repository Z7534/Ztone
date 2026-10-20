package de.Z7534.ztone.mapcontrol.handlers;

import com.google.gson.JsonObject;
import de.Z7534.ztone.mapcontrol.MapControl;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;

public class JourneyMapHandler implements PluginMessageListener {

    public static final String CHANNEL_PERM_REQ = "journeymap:perm_req";
    public static final String CHANNEL_ADMIN_REQ = "journeymap:admin_req";
    public static final String CHANNEL_COMMON = "journeymap:common";
    public static final String CHANNEL_PERM = "journeymap:perm";
    public static final String CHANNEL_MP_OPTIONS = "journeymap:mp_options_req";

    private final MapControl plugin;

    public JourneyMapHandler(MapControl plugin) {
        this.plugin = plugin;
    }

    public void registerChannels() {
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin.getPlugin(), CHANNEL_PERM_REQ, this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin.getPlugin(), CHANNEL_ADMIN_REQ, this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin.getPlugin(), CHANNEL_COMMON, this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin.getPlugin(), CHANNEL_MP_OPTIONS, this);

        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin.getPlugin(), CHANNEL_PERM);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin.getPlugin(), CHANNEL_COMMON);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin.getPlugin(), CHANNEL_ADMIN_REQ);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin.getPlugin(), CHANNEL_PERM_REQ);
    }

    public void unregisterChannels() {
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin.getPlugin(), CHANNEL_PERM_REQ, this);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin.getPlugin(), CHANNEL_ADMIN_REQ, this);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin.getPlugin(), CHANNEL_COMMON, this);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin.getPlugin(), CHANNEL_MP_OPTIONS, this);

        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin.getPlugin(), CHANNEL_PERM);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin.getPlugin(), CHANNEL_COMMON);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin.getPlugin(), CHANNEL_ADMIN_REQ);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin.getPlugin(), CHANNEL_PERM_REQ);
    }

    public void applyRestrictions(Player player) {
        if (!player.isOnline()) return;

        World world = player.getWorld();
        if (!plugin.getPluginConfig().isJourneyMapEnabled(world)) {
            return;
        }

        byte[] payload = createPermissionPayload(player, world);

        // Send to both common channels for compatibility with different JourneyMap versions
        try {
            player.sendPluginMessage(plugin.getPlugin(), CHANNEL_PERM, payload);
        } catch (Exception ignored) {}

        try {
            player.sendPluginMessage(plugin.getPlugin(), CHANNEL_COMMON, payload);
        } catch (Exception ignored) {}

        if (plugin.getPluginConfig().isDebug()) {
            plugin.getLogger().info("JourneyMap: Sent permission payload to " + player.getName() + " in world " + world.getName());
        }
    }

    private byte[] createPermissionPayload(Player player, World world) {
        String bypassPerm = plugin.getPluginConfig().getBypassPermission();
        boolean hasBypass = bypassPerm != null && !bypassPerm.isEmpty() && player.hasPermission(bypassPerm);

        JsonObject json = new JsonObject();
        json.addProperty("useWorldId", true);
        json.addProperty("saveSurfaceMap", true);
        json.addProperty("saveCaveMap", true);

        if (hasBypass) {
            json.addProperty("allowSurfaceMapping", true);
            json.addProperty("allowCaveMapping", true);
            json.addProperty("allowRadar", true);
            json.addProperty("allowPlayerRadar", true);
            json.addProperty("allowMobRadar", true);
            json.addProperty("allowAnimalRadar", true);
            json.addProperty("allowVillagerRadar", true);
            json.addProperty("allowTeleport", true);
            json.addProperty("showWaypoints", true);
            json.addProperty("allowDeathPoints", true);
        } else {
            json.addProperty("allowSurfaceMapping", plugin.getPluginConfig().isJourneyMapAllowSurfaceMapping(world));
            json.addProperty("allowCaveMapping", plugin.getPluginConfig().isJourneyMapAllowCaveMapping(world));
            json.addProperty("allowRadar", plugin.getPluginConfig().isJourneyMapAllowRadar(world));
            json.addProperty("allowPlayerRadar", plugin.getPluginConfig().isJourneyMapAllowPlayerRadar(world));
            json.addProperty("allowMobRadar", plugin.getPluginConfig().isJourneyMapAllowMobRadar(world));
            json.addProperty("allowAnimalRadar", plugin.getPluginConfig().isJourneyMapAllowAnimalRadar(world));
            json.addProperty("allowVillagerRadar", plugin.getPluginConfig().isJourneyMapAllowVillagerRadar(world));
            json.addProperty("allowTeleport", plugin.getPluginConfig().isJourneyMapAllowTeleport(world));
            json.addProperty("showWaypoints", plugin.getPluginConfig().isJourneyMapShowWaypoints(world));
            json.addProperty("allowDeathPoints", plugin.getPluginConfig().isJourneyMapAllowDeathPoints(world));
        }

        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (plugin.getPluginConfig().isDebug()) {
            plugin.getLogger().info("JourneyMap: Received message on channel " + channel + " from " + player.getName());
        }
        applyRestrictions(player);
    }
}
