package de.Z7534.ztone.pvpmanager.utils;

import de.Z7534.ztone.pvpmanager.Pvpmanager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class UpdateChecker {

    private static final int RESOURCE_ID = -1;

    private final Pvpmanager plugin;

    public UpdateChecker(Pvpmanager plugin) {
        this.plugin = plugin;
    }

    public void check() {
        if (RESOURCE_ID <= 0) {
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin.getPlugin(), () -> {
            try {
                URL url = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + RESOURCE_ID);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String latest = reader.readLine();
                    String current = plugin.getPluginMeta().getVersion();
                    if (latest != null && !latest.equalsIgnoreCase(current)) {
                        plugin.getLogger().info("Eine neue Version ist verfügbar: " + latest
                                + " (aktuell installiert: " + current + ")");
                    }
                }
            } catch (Exception exception) {
                plugin.getLogger().fine("Update-Check fehlgeschlagen: " + exception.getMessage());
            }
        });
    }
}
