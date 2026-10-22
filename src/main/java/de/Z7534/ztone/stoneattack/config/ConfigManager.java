package de.Z7534.ztone.stoneattack.config;

import de.Z7534.ztone.stoneattack.Stoneattack;
import de.Z7534.ztone.util.ConfigUtil;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * Laedt/haelt die config.yml. Fehlende Schluessel werden beim Start
 * automatisch aus der im Jar mitgelieferten Standard-config.yml
 * ergaenzt (ConfigUtil.loadAndSync), damit Updates keine bestehenden Server-
 * Configs zerstoeren.
 */
public class ConfigManager {

    private final Stoneattack plugin;
    private File file;
    private FileConfiguration config;

    public ConfigManager(Stoneattack plugin) {
        this.plugin = plugin;
    }

    public void load() {
        file = new File(plugin.getDataFolder(), "config.yml");
        config = ConfigUtil.loadAndSync(plugin.getPlugin(), file, "stoneattack/config.yml");
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte config.yml nicht speichern: " + e.getMessage());
        }
    }

    public void reload() {
        load();
    }

    public FileConfiguration get() {
        return config;
    }
}
