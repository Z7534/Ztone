package de.Z7534.ztone.stonesit;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.stonesit.command.PoseCommand;
import de.Z7534.ztone.stonesit.command.SitCommand;
import de.Z7534.ztone.stonesit.command.SpinCommand;
import de.Z7534.ztone.stonesit.command.StoneSitReloadCommand;
import de.Z7534.ztone.stonesit.config.StoneSitConfig;
import de.Z7534.ztone.stonesit.hook.StoneSitPlaceholderExpansion;
import de.Z7534.ztone.stonesit.listener.SitListener;
import de.Z7534.ztone.stonesit.manager.SitManager;
import de.Z7534.ztone.stonesit.model.PoseType;
import de.Z7534.ztone.stonesit.util.EnvironmentUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

public final class StoneSitModule {

    private final Ztone plugin;
    private StoneSitConfig config;
    private SitManager sitManager;
    private EnvironmentUtil environmentUtil;
    private SitListener sitListener;
    private StoneSitPlaceholderExpansion stonesitExpansion;
    private StoneSitPlaceholderExpansion gsitExpansion;

    public StoneSitModule(Ztone plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        this.config = new StoneSitConfig(plugin);
        this.config.load();

        this.sitManager = new SitManager(plugin, config);
        this.environmentUtil = new EnvironmentUtil(plugin, config);

        this.sitListener = new SitListener(plugin, config, sitManager, environmentUtil);
        Bukkit.getPluginManager().registerEvents(sitListener, plugin);

        SitCommand sitExecutor = new SitCommand(config, sitManager, environmentUtil);
        PluginCommand sitCmd = plugin.getCommand("sit");
        if (sitCmd != null) {
            sitCmd.setExecutor(sitExecutor);
            sitCmd.setTabCompleter(sitExecutor);
        }

        PoseCommand layExecutor = new PoseCommand(
                config, sitManager, environmentUtil, PoseType.LAY,
                "stonesit.lay", "stonesit.pose.*", "GSit.Lay", "GSit.Pose.*"
        );
        PluginCommand layCmd = plugin.getCommand("lay");
        if (layCmd != null) {
            layCmd.setExecutor(layExecutor);
            layCmd.setTabCompleter(layExecutor);
        }

        PoseCommand legsUpExecutor = new PoseCommand(
                config, sitManager, environmentUtil, PoseType.LEGS_UP,
                "stonesit.legsup", "stonesit.pose.*", "GSit.LegsUp", "GSit.Pose.*"
        );
        PluginCommand legsUpCmd = plugin.getCommand("legsup");
        if (legsUpCmd != null) {
            legsUpCmd.setExecutor(legsUpExecutor);
            legsUpCmd.setTabCompleter(legsUpExecutor);
        }

        SpinCommand spinExecutor = new SpinCommand(config, sitManager, environmentUtil);
        PluginCommand spinCmd = plugin.getCommand("spin");
        if (spinCmd != null) {
            spinCmd.setExecutor(spinExecutor);
            spinCmd.setTabCompleter(spinExecutor);
        }

        StoneSitReloadCommand reloadExecutor = new StoneSitReloadCommand(config);
        PluginCommand reloadCmd = plugin.getCommand("stonesitreload");
        if (reloadCmd != null) {
            reloadCmd.setExecutor(reloadExecutor);
            reloadCmd.setTabCompleter(reloadExecutor);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                this.stonesitExpansion = new StoneSitPlaceholderExpansion(sitManager, "stonesit");
                this.stonesitExpansion.register();
                this.gsitExpansion = new StoneSitPlaceholderExpansion(sitManager, "gsit");
                this.gsitExpansion.register();
            } catch (Throwable ignored) {
            }
        }

        plugin.getLogger().info("StoneSit-Modul wurde aktiviert.");
    }

    public void onDisable() {
        if (sitListener != null) {
            HandlerList.unregisterAll(sitListener);
            sitListener = null;
        }
        if (sitManager != null) {
            sitManager.removeAllSeats();
            sitManager.removeAllPoses();
            sitManager.removeAllPlayerSitStacks();
        }
        if (stonesitExpansion != null) {
            try {
                stonesitExpansion.unregister();
            } catch (Throwable ignored) {
            }
            stonesitExpansion = null;
        }
        if (gsitExpansion != null) {
            try {
                gsitExpansion.unregister();
            } catch (Throwable ignored) {
            }
            gsitExpansion = null;
        }
        plugin.getLogger().info("StoneSit-Modul wurde deaktiviert.");
    }

    public void reload() {
        if (sitManager != null) {
            sitManager.removeAllSeats();
            sitManager.removeAllPoses();
            sitManager.removeAllPlayerSitStacks();
        }
        if (config != null) {
            config.load();
        }
    }

    public StoneSitConfig getConfig() {
        return config;
    }

    public SitManager getSitManager() {
        return sitManager;
    }
}
