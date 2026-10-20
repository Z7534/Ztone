package de.Z7534.ztone.pvpmanager.config;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ConfigManager {

    private final Pvpmanager plugin;
    private FileConfiguration config;
    private FileConfiguration hooks;
    private File hooksFile;
    private File configFile;

    public ConfigManager(Pvpmanager plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File pvpFolder = new File(plugin.getDataFolder(), "pvpmanager");
        this.configFile = new File(pvpFolder, "config.yml");

        if (!configFile.exists()) {
            File legacyInFolder = new File(pvpFolder, "pvpmanager.yml");
            File legacyRoot = new File(plugin.getDataFolder(), "pvpmanager.yml");
            if (legacyInFolder.exists()) {
                this.configFile = legacyInFolder;
            } else if (legacyRoot.exists()) {
                this.configFile = legacyRoot;
            } else {
                plugin.saveResource("pvpmanager/config.yml", false);
            }
        }
        this.config = YamlConfiguration.loadConfiguration(configFile);

        InputStream defaultStream = plugin.getResource("pvpmanager/config.yml");
        if (defaultStream == null) {
            defaultStream = plugin.getResource("pvpmanager.yml");
        }
        if (defaultStream != null) {
            try {
                config.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaultStream, StandardCharsets.UTF_8)));
            } finally {
                try {
                    defaultStream.close();
                } catch (IOException ignored) {
                }
            }
        }

        this.hooksFile = new File(pvpFolder, "plugin-hooks.yml");
        if (!hooksFile.exists()) {
            File legacyHooks = new File(plugin.getDataFolder(), "plugin-hooks.yml");
            if (legacyHooks.exists()) {
                this.hooksFile = legacyHooks;
            } else {
                plugin.saveResource("pvpmanager/plugin-hooks.yml", false);
            }
        }
        this.hooks = YamlConfiguration.loadConfiguration(hooksFile);

        InputStream defaultHooksStream = plugin.getResource("pvpmanager/plugin-hooks.yml");
        if (defaultHooksStream == null) {
            defaultHooksStream = plugin.getResource("plugin-hooks.yml");
        }
        if (defaultHooksStream != null) {
            try {
                hooks.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaultHooksStream, StandardCharsets.UTF_8)));
            } finally {
                try {
                    defaultHooksStream.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public void reload() {
        load();
    }

    public FileConfiguration config() {
        return config;
    }

    public FileConfiguration hooks() {
        return hooks;
    }

    public boolean isDebug() {
        return config.getBoolean("Debug Mode", false);
    }
}
