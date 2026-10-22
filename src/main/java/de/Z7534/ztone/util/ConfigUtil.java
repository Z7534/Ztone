package de.Z7534.ztone.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;

public final class ConfigUtil {

    private ConfigUtil() {}

    /**
     * Lädt eine Konfigurationsdatei vom Datenträger, vergleicht sie mit den internen Standardwerten
     * aus der JAR und ergänzt fehlende Optionen, Sektionen und Kommentare automatisch direkt in der Datei,
     * ohne bestehende Benutzereinstellungen zu überschreiben.
     *
     * @param plugin Das Ztone-Plugin
     * @param file Die physische Datei auf der Festplatte
     * @param resourcePath Der Pfad zur Standard-Ressource in der JAR (z. B. "pvpmanager/config.yml")
     * @return Die geladene, synchronisierte und ggf. aktualisierte YamlConfiguration
     */
    public static YamlConfiguration loadAndSync(Plugin plugin, File file, String resourcePath) {
        if (resourcePath != null && resourcePath.startsWith("/")) {
            resourcePath = resourcePath.substring(1);
        }

        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try {
                plugin.saveResource(resourcePath, false);
            } catch (Exception e) {
                plugin.getLogger().warning("Konnte Standardressource '" + resourcePath + "' nicht initial anlegen: " + e.getMessage());
            }
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            setFallbackDefaults(plugin, cfg, resourcePath);
            return cfg;
        }

        YamlConfiguration diskConfig = YamlConfiguration.loadConfiguration(file);

        try (InputStream stream = plugin.getResource(resourcePath)) {
            if (stream == null) {
                return diskConfig;
            }

            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));

            boolean changed = false;

            // Header-Kommentar übernehmen, falls auf der Platte noch keiner vorhanden ist
            try {
                List<String> defaultHeader = defaultConfig.options().getHeader();
                List<String> currentHeader = diskConfig.options().getHeader();
                if ((currentHeader == null || currentHeader.isEmpty()) && defaultHeader != null && !defaultHeader.isEmpty()) {
                    diskConfig.options().setHeader(defaultHeader);
                    changed = true;
                }
            } catch (Throwable ignored) {
            }

            // Alle Schlüssel aus der Standard-Config prüfen
            for (String key : defaultConfig.getKeys(true)) {
                if (!diskConfig.contains(key, true)) {
                    if (defaultConfig.isConfigurationSection(key)) {
                        if (!diskConfig.isConfigurationSection(key)) {
                            diskConfig.createSection(key);
                            changed = true;
                        }
                    } else {
                        diskConfig.set(key, defaultConfig.get(key));
                        changed = true;
                    }

                    // Kommentare für den neuen Schlüssel aus der Standard-Config übertragen
                    try {
                        List<String> comments = defaultConfig.getComments(key);
                        if (comments != null && !comments.isEmpty()) {
                            diskConfig.setComments(key, comments);
                        }
                        List<String> inlineComments = defaultConfig.getInlineComments(key);
                        if (inlineComments != null && !inlineComments.isEmpty()) {
                            diskConfig.setInlineComments(key, inlineComments);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }

            diskConfig.setDefaults(defaultConfig);

            if (changed) {
                diskConfig.save(file);
                String displayPath;
                try {
                    displayPath = plugin.getDataFolder().toPath().relativize(file.toPath()).toString().replace('\\', '/');
                } catch (Exception ignored) {
                    displayPath = file.getName();
                }
                plugin.getLogger().info("Config aktualisiert: Fehlende Optionen in '" + displayPath + "' wurden automatisch ergänzt.");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Fehler beim Abgleich von " + file.getName() + " mit den Standardwerten", e);
        }

        return diskConfig;
    }

    private static void setFallbackDefaults(Plugin plugin, YamlConfiguration config, String resourcePath) {
        try (InputStream stream = plugin.getResource(resourcePath)) {
            if (stream != null) {
                config.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)));
            }
        } catch (Exception ignored) {
        }
    }
}
