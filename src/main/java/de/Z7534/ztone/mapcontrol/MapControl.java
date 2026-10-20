package de.Z7534.ztone.mapcontrol;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.mapcontrol.commands.MapControlCommand;
import de.Z7534.ztone.mapcontrol.config.PluginConfig;
import de.Z7534.ztone.mapcontrol.handlers.JourneyMapHandler;
import de.Z7534.ztone.mapcontrol.handlers.VoxelMapHandler;
import de.Z7534.ztone.mapcontrol.handlers.WorldInfoHandler;
import de.Z7534.ztone.mapcontrol.handlers.XaeroHandler;
import de.Z7534.ztone.mapcontrol.listeners.PlayerListener;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

public final class MapControl {

    private final Ztone plugin;
    private FileConfiguration config;

    private PluginConfig pluginConfig;
    private XaeroHandler xaeroHandler;
    private JourneyMapHandler journeyMapHandler;
    private VoxelMapHandler voxelMapHandler;
    private WorldInfoHandler worldInfoHandler;

    public MapControl(Ztone plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        // Load Configuration
        this.pluginConfig = new PluginConfig(this);
        this.pluginConfig.load();

        // Initialize Handlers
        this.xaeroHandler = new XaeroHandler(this);
        this.journeyMapHandler = new JourneyMapHandler(this);
        this.voxelMapHandler = new VoxelMapHandler(this);
        this.worldInfoHandler = new WorldInfoHandler(this);

        // Register Plugin Messaging Channels
        this.xaeroHandler.registerChannels();
        this.journeyMapHandler.registerChannels();
        this.voxelMapHandler.registerChannels();
        this.worldInfoHandler.registerChannels();

        // Register Listeners
        plugin.getServer().getPluginManager().registerEvents(new PlayerListener(this), plugin);

        // Register Commands
        PluginCommand cmd = plugin.getCommand("mapcontrol");
        if (cmd != null) {
            MapControlCommand commandHandler = new MapControlCommand(this);
            cmd.setExecutor(commandHandler);
            cmd.setTabCompleter(commandHandler);
        }

        // Apply restrictions to any online players (e.g. after reload)
        applyRestrictionsToAll();

        getLogger().info("MapControl-Modul erfolgreich aktiviert!");
    }

    public void onDisable() {
        // Unregister Channels
        if (xaeroHandler != null) xaeroHandler.unregisterChannels();
        if (journeyMapHandler != null) journeyMapHandler.unregisterChannels();
        if (voxelMapHandler != null) voxelMapHandler.unregisterChannels();
        if (worldInfoHandler != null) worldInfoHandler.unregisterChannels();

        getLogger().info("MapControl-Modul deaktiviert!");
    }

    public void applyAllRestrictions(Player player) {
        if (player == null || !player.isOnline()) return;

        if (xaeroHandler != null) {
            xaeroHandler.applyRestrictions(player);
        }
        if (journeyMapHandler != null) {
            journeyMapHandler.applyRestrictions(player);
        }
        if (voxelMapHandler != null) {
            voxelMapHandler.applyRestrictions(player);
        }
        if (worldInfoHandler != null) {
            worldInfoHandler.sendWorldInfo(player);
        }
    }

    public void applyRestrictionsToAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyAllRestrictions(player);
        }
    }

    public void saveDefaultConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            getDataFolder().mkdirs();
            try (InputStream in = plugin.getResource("mapcontrol/config.yml")) {
                if (in != null) {
                    Files.copy(in, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                getLogger().warning("Konnte mapcontrol/config.yml nicht erstellen: " + e.getMessage());
            }
        }
    }

    public void reloadConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveDefaultConfig();
        }
        this.config = YamlConfiguration.loadConfiguration(configFile);
        try (InputStream defStream = plugin.getResource("mapcontrol/config.yml")) {
            if (defStream != null) {
                config.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defStream, StandardCharsets.UTF_8)));
            }
        } catch (Exception ignored) {
        }
    }

    public FileConfiguration getConfig() {
        if (config == null) {
            reloadConfig();
        }
        return config;
    }

    public File getDataFolder() {
        return new File(plugin.getDataFolder(), "mapcontrol");
    }

    public Ztone getPlugin() {
        return plugin;
    }

    public Server getServer() {
        return plugin.getServer();
    }

    public Logger getLogger() {
        return plugin.getLogger();
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public XaeroHandler getXaeroHandler() {
        return xaeroHandler;
    }

    public JourneyMapHandler getJourneyMapHandler() {
        return journeyMapHandler;
    }

    public VoxelMapHandler getVoxelMapHandler() {
        return voxelMapHandler;
    }

    public WorldInfoHandler getWorldInfoHandler() {
        return worldInfoHandler;
    }
}
