package de.Z7534.ztone.dragonegg;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.dragonegg.command.DragonEggCommand;
import de.Z7534.ztone.dragonegg.config.DragonEggConfig;
import de.Z7534.ztone.dragonegg.listener.DragonEggListener;
import de.Z7534.ztone.dragonegg.manager.DragonEggBypassManager;
import de.Z7534.ztone.dragonegg.manager.DragonEggManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

public class DragonEggModule {

    private final Ztone plugin;
    private DragonEggConfig config;
    private DragonEggBypassManager bypassManager;
    private DragonEggManager manager;
    private DragonEggListener listener;
    private DragonEggCommand command;

    public DragonEggModule(Ztone plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        this.config = new DragonEggConfig(plugin);
        this.config.load();

        this.bypassManager = new DragonEggBypassManager();
        for (Player online : Bukkit.getOnlinePlayers()) {
            bypassManager.handleJoin(online);
        }

        this.manager = new DragonEggManager(plugin, config, bypassManager);
        this.manager.start();
        this.manager.scanAllOnlinePlayers();

        this.listener = new DragonEggListener(plugin, config, manager, bypassManager);
        Bukkit.getPluginManager().registerEvents(listener, plugin);

        this.command = new DragonEggCommand(plugin, config, manager, bypassManager);
        PluginCommand cmd = plugin.getCommand("dragonegg");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }

        plugin.getLogger().info("DragonEgg-Modul wurde aktiviert.");
    }

    public void onDisable() {
        if (manager != null) {
            manager.stop();
        }
        if (bypassManager != null) {
            bypassManager.clear();
        }
        plugin.getLogger().info("DragonEgg-Modul wurde deaktiviert.");
    }

    public void reload() {
        if (config != null) {
            config.load();
        }
        if (manager != null) {
            manager.scanAllOnlinePlayers();
        }
    }

    public DragonEggConfig getConfig() {
        return config;
    }

    public DragonEggBypassManager getBypassManager() {
        return bypassManager;
    }

    public DragonEggManager getManager() {
        return manager;
    }
}
