package de.Z7534.ztone.claims;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.claims.command.ClaimCommand;
import de.Z7534.ztone.claims.command.PvPClaimBypassCommand;
import de.Z7534.ztone.claims.command.StaffClaimCommand;
import de.Z7534.ztone.claims.config.ConfigManager;
import de.Z7534.ztone.claims.database.ClaimRepository;
import de.Z7534.ztone.claims.database.Database;
import de.Z7534.ztone.claims.database.PlayerRepository;
import de.Z7534.ztone.claims.hook.SmartSpawnerHook;
import de.Z7534.ztone.claims.integration.ClaimsPlaceholderExpansion;
import de.Z7534.ztone.claims.listener.*;
import de.Z7534.ztone.claims.manager.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class Claims {

    private final Ztone plugin;
    private ConfigManager configManager;
    private Database database;
    private ClaimManager claimManager;
    private BypassManager bypassManager;
    private ActivityManager activityManager;
    private ParticleManager particleManager;
    private ClaimMapManager claimMapManager;
    private PvPClaimBypassManager pvpClaimBypassManager;
    private ClaimsPlaceholderExpansion placeholderExpansion;
    private BukkitTask activityTask;
    private BukkitTask particleTask;

    public Claims(Ztone plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        this.configManager = new ConfigManager(plugin);
        this.configManager.load();

        database = new Database(plugin.getLogger(), plugin.getDataFolder(), plugin.getDatabaseManager(), configManager.databaseFile());
        database.connect();
        if (!database.isConnected()) {
            plugin.getLogger().severe("Datenbank nicht verfuegbar - Claims-Modul wird deaktiviert.");
            return;
        }

        ClaimRepository claimRepository = new ClaimRepository(database, plugin.getLogger());
        PlayerRepository playerRepository = new PlayerRepository(database, plugin.getLogger());

        this.bypassManager = new BypassManager();
        claimManager = new ClaimManager(plugin, configManager, claimRepository, playerRepository, bypassManager);
        claimManager.loadAll();

        pvpClaimBypassManager = new PvPClaimBypassManager(plugin, claimManager);
        pvpClaimBypassManager.load();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderExpansion = new ClaimsPlaceholderExpansion(claimManager, configManager);
            placeholderExpansion.register();
            plugin.getLogger().info("PlaceholderAPI gefunden - Platzhalter %claims_claim% registriert.");
        }

        SelectionManager selectionManager = new SelectionManager(claimManager);
        ChatInputManager chatInputManager = new ChatInputManager(plugin);
        PlayerLookupService lookupService = new PlayerLookupService(plugin, claimManager);
        SafeSpotFinder safeSpotFinder = new SafeSpotFinder();
        activityManager = new ActivityManager(plugin, claimManager, configManager);
        particleManager = new ParticleManager(claimManager, selectionManager, configManager);
        BanEnforcer banEnforcer = new BanEnforcer(configManager, safeSpotFinder, bypassManager);
        claimMapManager = new ClaimMapManager(plugin, claimManager, selectionManager);

        selectionManager.setOnEnd(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                claimMapManager.restoreOffHand(p);
            }
        });

        ClaimsContext ctx = new ClaimsContext(plugin, configManager, claimManager, selectionManager,
                chatInputManager, lookupService, safeSpotFinder, activityManager, particleManager, banEnforcer,
                bypassManager, claimMapManager, pvpClaimBypassManager);

        ClaimCommand claimCommand = new ClaimCommand(ctx);
        var cmd = plugin.getCommand("claim");
        if (cmd != null) {
            cmd.setExecutor(claimCommand);
            cmd.setTabCompleter(claimCommand);
        }

        StaffClaimCommand staffClaimCommand = new StaffClaimCommand(ctx);
        var staffCmd = plugin.getCommand("staffclaim");
        if (staffCmd != null) {
            staffCmd.setExecutor(staffClaimCommand);
            staffCmd.setTabCompleter(staffClaimCommand);
        }

        PvPClaimBypassCommand pvpClaimBypassCommand = new PvPClaimBypassCommand(ctx);
        var pvpClaimCmd = plugin.getCommand("pvpclaimbypass");
        if (pvpClaimCmd != null) {
            pvpClaimCmd.setExecutor(pvpClaimBypassCommand);
            pvpClaimCmd.setTabCompleter(pvpClaimBypassCommand);
        }

        Bukkit.getPluginManager().registerEvents(new GuiListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new ChatListener(chatInputManager), plugin);
        Bukkit.getPluginManager().registerEvents(new SelectionListener(selectionManager, claimManager, configManager, bypassManager, claimMapManager), plugin);
        Bukkit.getPluginManager().registerEvents(new ProtectionListener(claimManager, configManager, bypassManager, pvpClaimBypassManager), plugin);
        Bukkit.getPluginManager().registerEvents(
                new ActivityListener(plugin, activityManager, claimManager, selectionManager, chatInputManager,
                        configManager, bypassManager, banEnforcer, claimMapManager), plugin);
        Bukkit.getPluginManager().registerEvents(new ClaimMapListener(ctx, claimMapManager), plugin);

        // SmartSpawner Hook registrieren
        new SmartSpawnerHook(plugin, claimManager, configManager, bypassManager).register();

        this.activityTask = Bukkit.getScheduler().runTaskTimer(plugin, activityManager::tick, 20L, 20L);
        int particleInterval = Math.max(5, configManager.particleUpdateIntervalTicks());
        this.particleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                particleManager.tick(player);
            }
        }, particleInterval, particleInterval);

        plugin.getLogger().info("Claims-Modul wurde aktiviert.");
    }

    public void onDisable() {
        if (activityTask != null) activityTask.cancel();
        if (particleTask != null) particleTask.cancel();
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
        }
        if (claimMapManager != null) {
            claimMapManager.restoreAll();
        }
        if (pvpClaimBypassManager != null) {
            pvpClaimBypassManager.save();
        }
        if (claimManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                claimManager.findPlayerData(player.getUniqueId()).ifPresent(claimManager::savePlayerAsync);
            }
        }
        if (database != null) {
            database.close();
        }
        plugin.getLogger().info("Claims-Modul wurde deaktiviert.");
    }

    public void reloadConfig() {
        if (configManager != null) {
            configManager.reload();
        }
    }

    public Ztone getPlugin() {
        return plugin;
    }

    public ClaimManager getClaimManager() {
        return claimManager;
    }

    public BypassManager getBypassManager() {
        return bypassManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ClaimMapManager getClaimMapManager() {
        return claimMapManager;
    }

    public PvPClaimBypassManager getPvPClaimBypass() {
        return pvpClaimBypassManager;
    }

    public PvPClaimBypassManager getPvPClaimBypassManager() {
        return pvpClaimBypassManager;
    }
}
