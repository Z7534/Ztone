package de.Z7534.ztone.deathchest;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.deathchest.command.DeathChestCommand;
import de.Z7534.ztone.deathchest.config.DeathChestConfig;
import de.Z7534.ztone.deathchest.listener.DeathChestListener;
import de.Z7534.ztone.deathchest.manager.DeathChestBypassManager;
import de.Z7534.ztone.deathchest.manager.DeathChestManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

public class Deathchest {

    private final Ztone plugin;
    private DeathChestConfig config;
    private DeathChestManager deathChestManager;
    private DeathChestBypassManager bypassManager;
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

        this.bypassManager = new DeathChestBypassManager();
        // Spieler, die bereits online sind (Reload), erhalten ggf. ihren Auto-Join-Bypass
        for (Player online : Bukkit.getOnlinePlayers()) {
            bypassManager.handleJoin(online);
        }

        this.listener = new DeathChestListener(plugin, deathChestManager, config, bypassManager);
        Bukkit.getPluginManager().registerEvents(listener, plugin);

        this.command = new DeathChestCommand(plugin, deathChestManager, config, bypassManager);
        PluginCommand cmd = plugin.getCommand("deathchest");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }

        plugin.getLogger().info("Deathchest-Modul wurde aktiviert.");
    }

    public void onDisable() {
        if (bypassManager != null) {
            bypassManager.clear();
        }
        if (deathChestManager != null) {
            deathChestManager.stop();
            deathChestManager.saveActiveChests(true);
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

    public DeathChestBypassManager getBypassManager() {
        return bypassManager;
    }
}
