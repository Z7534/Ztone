package de.Z7534.ztone.stoneattack.config;

import de.Z7534.ztone.stoneattack.Stoneattack;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Laedt/haelt die config.yml. Fehlende Schluessel werden beim Start
 * automatisch aus der im Jar mitgelieferten Standard-config.yml
 * ergaenzt (copyDefaults), damit Updates keine bestehenden Server-
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
        if (!file.exists()) {
            plugin.saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);

        try (InputStream defStream = plugin.getResource("config.yml")) {
            if (defStream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defStream, StandardCharsets.UTF_8));
                config.setDefaults(defaults);
                config.options().copyDefaults(true);
                save();
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte Standard-config.yml nicht laden: " + e.getMessage());
        }
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
