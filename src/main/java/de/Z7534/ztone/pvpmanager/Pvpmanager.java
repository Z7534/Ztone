package de.Z7534.ztone.pvpmanager;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.pvpmanager.commands.*;
import de.Z7534.ztone.pvpmanager.config.ConfigManager;
import de.Z7534.ztone.pvpmanager.database.DatabaseManager;
import de.Z7534.ztone.pvpmanager.database.PlayerDataDAO;
import de.Z7534.ztone.pvpmanager.hooks.HookManager;
import de.Z7534.ztone.pvpmanager.listeners.*;
import de.Z7534.ztone.pvpmanager.managers.*;
import de.Z7534.ztone.pvpmanager.permissions.Permissions;
import de.Z7534.ztone.pvpmanager.utils.UpdateChecker;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.util.UUID;
import java.util.logging.Logger;

public class Pvpmanager {

    private final Ztone plugin;

    private ConfigManager configManager;
    private LocaleManager localeManager;
    private MessageDispatcher messageDispatcher;

    private DatabaseManager databaseManager;
    private PlayerDataDAO playerDataDAO;
    private PlayerDataManager playerDataManager;

    private CombatManager combatManager;
    private DisplayManager displayManager;
    private GlowManager glowManager;
    private PvpToggleManager pvpToggleManager;
    private RespawnProtectionManager respawnProtectionManager;
    private LootProtectionManager lootProtectionManager;
    private CooldownManager cooldownManager;
    private AntiKillAbuseManager antiKillAbuseManager;
    private CombatLogPunishmentManager combatLogPunishmentManager;
    private BorderHoppingManager borderHoppingManager;
    private OverrideManager overrideManager;

    private HookManager hookManager;

    public Pvpmanager(Ztone plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        this.configManager = new ConfigManager(this);
        configManager.load();

        this.localeManager = new LocaleManager(this);
        localeManager.load();

        this.messageDispatcher = new MessageDispatcher(this);

        this.databaseManager = new DatabaseManager(this);
        databaseManager.connect();
        this.playerDataDAO = new PlayerDataDAO(this, databaseManager);
        this.playerDataManager = new PlayerDataManager(this, playerDataDAO);

        this.combatManager = new CombatManager(this);
        this.displayManager = new DisplayManager(this);
        displayManager.cleanupStaleTeams();
        this.pvpToggleManager = new PvpToggleManager(this);
        this.respawnProtectionManager = new RespawnProtectionManager(this);
        this.lootProtectionManager = new LootProtectionManager(this);
        this.cooldownManager = new CooldownManager(this);
        this.antiKillAbuseManager = new AntiKillAbuseManager(this);
        this.combatLogPunishmentManager = new CombatLogPunishmentManager(this);
        this.borderHoppingManager = new BorderHoppingManager(this);
        this.overrideManager = new OverrideManager(this);

        this.hookManager = new HookManager(this);
        hookManager.setupAll();

        this.glowManager = new GlowManager(this);
        glowManager.setup();

        combatManager.start();

        registerCommands();
        registerListeners();

        for (Player player : getServer().getOnlinePlayers()) {
            playerDataManager.loadOnJoin(player, () -> {
                String dimension = overrideManager.getDimensionKey(player.getWorld());
                if (overrideManager.hasDimensionDefault(dimension)) {
                    pvpToggleManager.applyDimensionDefault(player, overrideManager.getDimensionDefault(dimension));
                }
            });
        }

        if (configManager.config().getBoolean("Update Check.Enabled", false)) {
            new UpdateChecker(this).check();
        }

        getLogger().info("PvPManager-Modul wurde im Ztone aktiviert.");
    }

    public void onDisable() {
        if (displayManager != null) {
            displayManager.shutdown();
        }
        if (glowManager != null) {
            glowManager.shutdown();
        }
        if (playerDataManager != null) {
            playerDataManager.saveAll();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("PvPManager-Modul wurde deaktiviert.");
    }

    private void registerCommands() {
        registerCmd("pvpmanager", new PvpManagerCommand(this));
        registerCmd("pvp", new PvpCommand(this));
        registerCmd("pvpoverride", new PvpOverrideCommand(this));
        registerCmd("pvpinfo", new PvpInfoCommand(this));
        registerCmd("pvpstatus", new PvpStatusCommand(this));
        registerCmd("pvplist", new PvpListCommand(this));
        registerCmd("pvptag", new PvpTagCommand(this));
        registerCmd("untag", new UntagCommand(this));
        registerCmd("teleportoverride", new TeleportOverrideCommand(this));
        registerCmd("testrace", new TestRaceCommand(this));
        registerCmd("announce", new AnnounceCommand(this));
        registerCmd("soup", new SoupCommand(this));
        registerCmd("pvpdimensiontoggle", new PvpDimensionToggleCommand(this));
    }

    private void registerCmd(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand cmd = plugin.getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            if (executor instanceof org.bukkit.command.TabCompleter tc) {
                cmd.setTabCompleter(tc);
            }
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new CombatListener(this), plugin);
        getServer().getPluginManager().registerEvents(new CombatLogListener(this), plugin);
        getServer().getPluginManager().registerEvents(new EntityDeathListener(this), plugin);
        getServer().getPluginManager().registerEvents(new PlayerJoinQuitListener(this), plugin);
        getServer().getPluginManager().registerEvents(new ActionBlockListener(this), plugin);
        getServer().getPluginManager().registerEvents(new PotionEffectListener(this), plugin);
        getServer().getPluginManager().registerEvents(new RecyclingListener(this), plugin);
        getServer().getPluginManager().registerEvents(new BorderHoppingListener(this), plugin);
        getServer().getPluginManager().registerEvents(new LootProtectionListener(this), plugin);
        getServer().getPluginManager().registerEvents(new LavaProtectionListener(this), plugin);
    }

    public boolean canPvP(Player player) {
        if (player == null) return false;
        if (isForcedWarPvP(player)) return true;
        if (overrideManager != null && overrideManager.isOverriding(player.getUniqueId())) return true;

        // Claims-Pruefung: Befindet sich der Spieler in einem Claim?
        // In Claims ist PvP komplett deaktiviert, AUSSER ein Admin hat den Claim mit /claimspvpoverride freigegeben!
        if (plugin.getConfigManager() != null && plugin.getConfigManager().isClaimsEnabled() && plugin.getClaims() != null) {
            var claims = plugin.getClaims();
            var pvpClaimBypass = claims.getPvPClaimBypass();
            boolean adminBypassed = (pvpClaimBypass != null && (pvpClaimBypass.isPlayerBypassed(player)))
                    || (claims.getBypassManager() != null && claims.getBypassManager().isActive(player));
            if (!adminBypassed) {
                var claim = claims.getClaimManager().getClaimAt(player.getLocation());
                // Wenn ein Claim existiert und dieser KEIN Admin-PvP-Override hat: PvP ist auf diesem Claim verboten!
                if (claim != null && (pvpClaimBypass == null || !pvpClaimBypass.isClaimBypassed(claim.id()))) {
                    return false;
                }
            }
        }

        String dimensionKey = overrideManager != null ? overrideManager.getDimensionKey(player.getWorld()) : "NORMAL";
        boolean dimensionDefaultEnabled = overrideManager != null && overrideManager.getDimensionDefault(dimensionKey);
        boolean dimensionForced = overrideManager != null && overrideManager.isDimensionForced(dimensionKey);
        boolean personal = pvpToggleManager != null && pvpToggleManager.isPvpEnabled(player.getUniqueId());
        boolean exempt = player.hasPermission(Permissions.EXEMPT_DIMENSION);

        boolean effective = personal;
        if (dimensionForced && !exempt) {
            effective = dimensionDefaultEnabled;
        } else if (!dimensionDefaultEnabled && !exempt) {
            effective = false;
        }

        if (hookManager != null && hookManager.worldGuard() != null) {
            if (configManager != null && configManager.config().getBoolean("PvP Toggle.WorldGuard Overrides", false)
                    && hookManager.worldGuard().isPvpForceAllowed(player.getLocation())) {
                return true;
            }
            if (hookManager.worldGuard().isPvpExplicitlyDenied(player.getLocation())) {
                return false;
            }
        }
        return effective;
    }

    public boolean canPvP(UUID uuid) {
        if (uuid == null) return false;
        Player p = org.bukkit.Bukkit.getPlayer(uuid);
        if (p != null) return canPvP(p);
        return pvpToggleManager != null && pvpToggleManager.isPvpEnabled(uuid);
    }

    public boolean isDimensionPvPEnabled(String dimension) {
        return overrideManager != null && overrideManager.isDimensionPvPEnabled(dimension);
    }

    public boolean isDimensionPvPEnabled(World world) {
        return overrideManager != null && overrideManager.isDimensionPvPEnabled(world);
    }

    public boolean isDimensionPvPForced(String dimension) {
        return overrideManager != null && overrideManager.isDimensionPvPForced(dimension);
    }

    public boolean isDimensionPvPForced(World world) {
        return overrideManager != null && overrideManager.isDimensionPvPForced(world);
    }

    public boolean isDimensionForced(String dimension) {
        return isDimensionPvPForced(dimension);
    }

    public boolean isDimensionForced(World world) {
        return isDimensionPvPForced(world);
    }

    public boolean isForced(String dimension) {
        return isDimensionPvPForced(dimension);
    }

    public boolean isForced(World world) {
        return isDimensionPvPForced(world);
    }

    public boolean getDimensionPvPStatus(String dimension) {
        return isDimensionPvPEnabled(dimension);
    }

    public boolean getDimensionPvPStatus(World world) {
        return isDimensionPvPEnabled(world);
    }

    public Ztone getPlugin() {
        return plugin;
    }

    public Ztone getClansystem() {
        return plugin;
    }

    public void saveConfig() {
        plugin.saveConfig();
    }

    public io.papermc.paper.plugin.configuration.PluginMeta getPluginMeta() {
        return plugin.getPluginMeta();
    }

    public File getDataFolder() {
        return plugin.getDataFolder();
    }

    public Logger getLogger() {
        return plugin.getLogger();
    }

    public Server getServer() {
        return plugin.getServer();
    }

    public PluginCommand getCommand(String name) {
        return plugin.getCommand(name);
    }

    public InputStream getResource(String filename) {
        return plugin.getResource(filename);
    }

    public void saveResource(String resourcePath, boolean replace) {
        plugin.saveResource(resourcePath, replace);
    }

    public void saveDefaultConfig() {
        File f = new File(getDataFolder(), "pvpmanager/config.yml");
        if (!f.exists()) {
            saveResource("pvpmanager/config.yml", false);
        }
    }

    public void reloadConfig() {
        if (configManager != null) {
            configManager.reload();
        }
    }

    public FileConfiguration getConfig() {
        return configManager != null ? configManager.config() : null;
    }

    public boolean isForcedWarPvP(Player player) {
        return plugin.isForcedWarPvP(player);
    }

    public boolean isForcedWarPvP(UUID uuid) {
        return plugin.isForcedWarPvP(uuid);
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LocaleManager getLocaleManager() {
        return localeManager;
    }

    public MessageDispatcher getMessageDispatcher() {
        return messageDispatcher;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public PlayerDataDAO getPlayerDataDAO() {
        return playerDataDAO;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public CombatManager getCombatManager() {
        return combatManager;
    }

    public DisplayManager getDisplayManager() {
        return displayManager;
    }

    public GlowManager getGlowManager() {
        return glowManager;
    }

    public PvpToggleManager getPvpToggleManager() {
        return pvpToggleManager;
    }

    public RespawnProtectionManager getRespawnProtectionManager() {
        return respawnProtectionManager;
    }

    public LootProtectionManager getLootProtectionManager() {
        return lootProtectionManager;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public AntiKillAbuseManager getAntiKillAbuseManager() {
        return antiKillAbuseManager;
    }

    public CombatLogPunishmentManager getCombatLogPunishmentManager() {
        return combatLogPunishmentManager;
    }

    public BorderHoppingManager getBorderHoppingManager() {
        return borderHoppingManager;
    }

    public OverrideManager getOverrideManager() {
        return overrideManager;
    }

    public HookManager getHookManager() {
        return hookManager;
    }
}
