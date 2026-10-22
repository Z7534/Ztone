package de.Z7534.ztone.pvpmanager.config;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.util.ConfigUtil;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;

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
            }
        }
        this.config = ConfigUtil.loadAndSync(plugin.getPlugin(), configFile, "pvpmanager/config.yml");

        this.hooksFile = new File(pvpFolder, "plugin-hooks.yml");
        if (!hooksFile.exists()) {
            File legacyHooks = new File(plugin.getDataFolder(), "plugin-hooks.yml");
            if (legacyHooks.exists()) {
                this.hooksFile = legacyHooks;
            }
        }
        this.hooks = ConfigUtil.loadAndSync(plugin.getPlugin(), hooksFile, "pvpmanager/plugin-hooks.yml");
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
