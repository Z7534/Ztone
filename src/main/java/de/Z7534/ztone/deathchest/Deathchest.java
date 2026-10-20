package de.Z7534.ztone.deathchest;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.deathchest.command.DeathChestCommand;
import de.Z7534.ztone.deathchest.config.DeathChestConfig;
import de.Z7534.ztone.deathchest.listener.DeathChestListener;
import de.Z7534.ztone.deathchest.manager.DeathChestManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;

public class Deathchest {

    private final Ztone plugin;
    private DeathChestConfig config;
    private DeathChestManager deathChestManager;
    private DeathChestListener listener;
    private DeathChestCommand command;

    public Deathchest(Ztone plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        this.config = new DeathChestConfig(plugin);
        this.config.load();

        this.deathChestManager = new DeathChestManager(plugin, config);
        this.deathChestManager.loadActiveChests();
        this.deathChestManager.start();

        this.listener = new DeathChestListener(plugin, deathChestManager, config);
        Bukkit.getPluginManager().registerEvents(listener, plugin);

        this.command = new DeathChestCommand(plugin, deathChestManager, config);
        PluginCommand cmd = plugin.getCommand("deathchest");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }

        plugin.getLogger().info("Deathchest-Modul wurde aktiviert.");
    }

    public void onDisable() {
        if (deathChestManager != null) {
            deathChestManager.stop();
            deathChestManager.saveActiveChests();
            deathChestManager.removeAllHolograms();
        }
        plugin.getLogger().info("Deathchest-Modul wurde deaktiviert.");
    }

    public void reload() {
        if (config != null) {
            config.load();
        }
    }

    public Ztone getPlugin() {
        return plugin;
    }

    public DeathChestConfig getConfig() {
        return config;
    }

    public DeathChestManager getDeathChestManager() {
        return deathChestManager;
    }
}
