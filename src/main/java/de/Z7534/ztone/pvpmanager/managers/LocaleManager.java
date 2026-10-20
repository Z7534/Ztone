package de.Z7534.ztone.pvpmanager.managers;

import de.Z7534.ztone.pvpmanager.Pvpmanager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class LocaleManager {

    private final Pvpmanager plugin;
    private final Properties messages = new Properties();
    private String activeLocale = "de";

    public LocaleManager(Pvpmanager plugin) {
        this.plugin = plugin;
    }

    public void load() {
        String requested = plugin.getConfigManager().config().getString("General Settings.Locale", "de");
        requested = requested == null ? "de" : requested.toLowerCase();
        messages.clear();

        if (!loadLocale(requested)) {
            if (!requested.equalsIgnoreCase("de")) {
                plugin.getLogger().warning("Sprachdatei für '" + requested
                        + "' nicht gefunden - falle auf Deutsch (de) zurück.");
            }
            loadLocale("de");
            activeLocale = "de";
        } else {
            activeLocale = requested;
        }
    }

    private boolean loadLocale(String locale) {
        File pvpFolder = new File(plugin.getDataFolder(), "pvpmanager");
        File externalFile = new File(pvpFolder, "locale/messages_" + locale + ".properties");

        if (!externalFile.exists()) {
            File legacyFile = new File(plugin.getDataFolder(), "locale/messages_" + locale + ".properties");
            if (legacyFile.exists()) {
                externalFile = legacyFile;
            }
        }

        try {
            if (externalFile.exists()) {
                try (InputStream in = new java.io.FileInputStream(externalFile);
                     InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    messages.load(reader);
                    return true;
                }
            }

            String resourcePath = "pvpmanager/locale/messages_" + locale + ".properties";
            InputStream in = plugin.getResource(resourcePath);
            if (in == null) {
                resourcePath = "locale/messages_" + locale + ".properties";
                in = plugin.getResource(resourcePath);
            }
            if (in == null) {
                return false;
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                messages.load(reader);
                plugin.saveResource("pvpmanager/locale/messages_" + locale + ".properties", false);
                return true;
            }
        } catch (IOException exception) {
            plugin.getLogger().severe("Konnte Sprachdatei '" + locale + "' nicht laden: " + exception.getMessage());
            return false;
        }
    }

    public String raw(String key) {
        if (plugin.getClansystem() != null && plugin.getClansystem().getMessageManager() != null) {
            String fromYaml = plugin.getClansystem().getMessageManager().getRawOrNull("pvpmanager." + key);
            if (fromYaml != null && !fromYaml.isEmpty()) {
                return fromYaml;
            }
            fromYaml = plugin.getClansystem().getMessageManager().getRawOrNull(key);
            if (fromYaml != null && !fromYaml.isEmpty()) {
                return fromYaml;
            }
        }
        return messages.getProperty(key, "");
    }

    public String activeLocale() {
        return activeLocale;
    }

    public void reload() {
        load();
    }
}
