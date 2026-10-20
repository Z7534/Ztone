package de.Z7534.ztone.clearlag;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clearlag.command.ClearLagCommand;
import de.Z7534.ztone.clearlag.config.ClearLagConfig;
import de.Z7534.ztone.clearlag.hook.ClearLagPlaceholderExpansion;
import de.Z7534.ztone.clearlag.manager.ClearLagManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;

public class ClearLagModule {

    private final Ztone plugin;
    private ClearLagConfig config;
    private ClearLagManager manager;
    private ClearLagPlaceholderExpansion placeholderExpansion;

    public ClearLagModule(Ztone plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        this.config = new ClearLagConfig(plugin);
        this.config.load();

        this.manager = new ClearLagManager(plugin, config);
        this.manager.start();

        PluginCommand cmd = plugin.getCommand("clearlag");
        if (cmd != null) {
            ClearLagCommand executor = new ClearLagCommand(plugin, manager, config);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            this.placeholderExpansion = new ClearLagPlaceholderExpansion(manager);
            this.placeholderExpansion.register();
        }

        plugin.getLogger().info("ClearLag-Modul wurde aktiviert.");
    }

    public void onDisable() {
        if (manager != null) {
            manager.stop();
        }
        if (placeholderExpansion != null) {
            try {
                placeholderExpansion.unregister();
            } catch (Throwable ignored) {}
            placeholderExpansion = null;
        }
        plugin.getLogger().info("ClearLag-Modul wurde deaktiviert.");
    }

    public void reload() {
        if (config != null) {
            config.load();
        }
    }

    public ClearLagConfig getConfig() {
        return config;
    }

    public ClearLagManager getManager() {
        return manager;
    }
}
