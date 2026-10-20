package de.Z7534.ztone;

import de.Z7534.ztone.claims.Claims;
import de.Z7534.ztone.clansystem.commands.ClanAdminCommand;
import de.Z7534.ztone.clansystem.commands.ClanCommand;
import de.Z7534.ztone.clansystem.listeners.ChatListener;
import de.Z7534.ztone.clansystem.listeners.GUIListener;
import de.Z7534.ztone.clansystem.listeners.PlayerListener;
import de.Z7534.ztone.clansystem.listeners.PvPListener;
import de.Z7534.ztone.clansystem.managers.*;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.models.ClanMember;
import de.Z7534.ztone.clansystem.models.Permission;
import de.Z7534.ztone.clansystem.placeholders.ClanPlaceholders;
import de.Z7534.ztone.deathchest.Deathchest;
import de.Z7534.ztone.clearlag.ClearLagModule;
import de.Z7534.ztone.commands.ZtoneCommand;
import org.bukkit.command.PluginCommand;
import de.Z7534.ztone.listeners.ModuleCommandFilterListener;
import de.Z7534.ztone.mapcontrol.MapControl;
import de.Z7534.ztone.playrewards.PlayReward;
import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.stoneattack.Stoneattack;
import de.Z7534.ztone.stonelog.Stonelog;
import de.Z7534.ztone.stonemute.Stonemute;
import de.Z7534.ztone.stonevoice.StoneVoice;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class Ztone extends JavaPlugin {

    private static Ztone instance;

    // Clan-System Manager
    private ConfigManager configManager;
    private MessageManager messageManager;
    private DatabaseManager databaseManager;
    private LevelManager levelManager;
    private ClanManager clanManager;
    private WarManager warManager;
    private AllianceManager allianceManager;
    private ChatInputManager chatInputManager;
    private GlowManager glowManager;
    private TeleportManager teleportManager;
    private EconomyManager economyManager;

    // Module / Unterplugins
    private Pvpmanager pvpManager;
    private Claims claims;
    private Stoneattack stoneattack;
    private Stonelog stonelog;
    private Stonemute stonemute;
    private MapControl mapControl;
    private StoneVoice stoneVoice;
    private PlayReward playReward;
    private Deathchest deathchest;
    private ClearLagModule clearLag;

    // Command Filter für deaktivierte Module
    private ModuleCommandFilterListener moduleCommandFilterListener;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("========================================");
        getLogger().info("  ZTONE v" + getDescription().getVersion());
        getLogger().info("  Autor: Z7534");
        getLogger().info("========================================");

        saveDefaultConfig();

        this.configManager = new ConfigManager(this);

        // Zentrale Datenbank für alle Module direkt zu Beginn verbinden
        this.databaseManager = new DatabaseManager(this);
        if (!databaseManager.connect()) {
            getLogger().severe("Konnte keine Verbindung zur zentralen Datenbank herstellen!");
        }

        boolean clansystemEnabled = configManager.isClansystemEnabled();
        boolean clankasseEnabled = configManager.isClankasseEnabled();
        boolean pvpmanagerEnabled = configManager.isPvpmanagerEnabled();
        boolean claimsEnabled = configManager.isClaimsEnabled();
        boolean stoneattackEnabled = configManager.isStoneattackEnabled();
        boolean stonelogEnabled = configManager.isStonelogEnabled();
        boolean stonemuteEnabled = configManager.isStonemuteEnabled();
        boolean mapcontrolEnabled = configManager.isMapcontrolEnabled();
        boolean stonevoiceEnabled = configManager.isStonevoiceEnabled();
        boolean playrewardsEnabled = configManager.isPlayrewardsEnabled();
        boolean deathchestEnabled = configManager.isDeathchestEnabled();
        boolean clearlagEnabled = configManager.isClearlagEnabled();

        getLogger().info("----------------------------------------");
        getLogger().info("  Unterplugins / Module Status:");
        getLogger().info("  - Clansystem:  " + (clansystemEnabled ? "§aAktiviert" : "§cDeaktiviert"));
        getLogger().info("  - Claims:      " + (claimsEnabled ? "§aAktiviert" : "§cDeaktiviert"));
        getLogger().info("  - PvPManager:  " + (pvpmanagerEnabled ? "§aAktiviert" : "§cDeaktiviert"));
        getLogger().info("  - StoneAttack: " + (stoneattackEnabled ? "§aAktiviert" : "§cDeaktiviert"));
        getLogger().info("  - Stonelog:    " + (stonelogEnabled ? "§aAktiviert" : "§cDeaktiviert"));
        getLogger().info("  - Stonemute:   " + (stonemuteEnabled ? "§aAktiviert" : "§cDeaktiviert"));
        getLogger().info("  - MapControl:  " + (mapcontrolEnabled ? "§aAktiviert" : "§cDeaktiviert"));
        getLogger().info("  - StoneVoice:  " + (stonevoiceEnabled ? "§aAktiviert" : "§cDeaktiviert"));
        getLogger().info("  - PlayRewards: " + (playrewardsEnabled ? "§aAktiviert" : "§cDeaktiviert"));
        getLogger().info("  - Deathchest:  " + (deathchestEnabled ? "§aAktiviert" : "§cDeaktiviert"));
        getLogger().info("  - ClearLag:    " + (clearlagEnabled ? "§aAktiviert" : "§cDeaktiviert"));
        getLogger().info("----------------------------------------");


        // 1. Clan-System Modul initialisieren
        if (clansystemEnabled) {
            try {
                if (databaseManager == null || databaseManager.getDataSource() == null) {
                    getLogger().severe("Keine Verbindung zur zentralen Datenbank! Clansystem-Modul wird deaktiviert.");
                } else {
                    databaseManager.createTables();

                    this.messageManager = new MessageManager(this);
                    this.levelManager = new LevelManager(this);

                    if (clankasseEnabled) {
                        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
                            getLogger().info("Vault nicht gefunden - Clankasse bleibt deaktiviert.");
                            this.economyManager = null;
                        } else {
                            try {
                                this.economyManager = new EconomyManager(this);
                            } catch (Throwable t) {
                                getLogger().severe("Clankasse konnte nicht initialisiert werden: " + t.getMessage());
                                this.economyManager = null;
                            }
                        }
                    }
                    this.clanManager = new ClanManager(this);
                    this.warManager = new WarManager(this);
                    this.allianceManager = new AllianceManager(this);
                    this.chatInputManager = new ChatInputManager(this);
                    this.glowManager = new GlowManager(this);
                    this.teleportManager = new TeleportManager(this);

                    clanManager.loadAllClans();
                    glowManager.setupAll();

                    ClanCommand clanCommand = new ClanCommand(this);
                    if (getCommand("clan") != null) {
                        getCommand("clan").setExecutor(clanCommand);
                        getCommand("clan").setTabCompleter(clanCommand);
                    }

                    ClanAdminCommand clanAdminCommand = new ClanAdminCommand(this);
                    if (getCommand("clanadmin") != null) {
                        getCommand("clanadmin").setExecutor(clanAdminCommand);
                        getCommand("clanadmin").setTabCompleter(clanAdminCommand);
                    }

                    Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
                    Bukkit.getPluginManager().registerEvents(new ChatListener(this), this);
                    Bukkit.getPluginManager().registerEvents(new PvPListener(this), this);
                    Bukkit.getPluginManager().registerEvents(new GUIListener(this), this);
                    Bukkit.getPluginManager().registerEvents(teleportManager, this);

                    if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                        try {
                            new ClanPlaceholders(this, "clansystem").register();
                            try {
                                new ClanPlaceholders(this, "ztone").register();
                            } catch (Throwable ignored) {}
                            getLogger().info("PlaceholderAPI gefunden - Clan-Platzhalter registriert!");
                        } catch (Throwable t) {
                            getLogger().fine("Clan-Platzhalter konnten nicht registriert werden: " + t.getMessage());
                        }
                    }
                }
            } catch (Throwable t) {
                handleModuleInitError("ClanSystem", t);
            }
        }

        // 2. PvPManager Modul initialisieren
        if (pvpmanagerEnabled) {
            try {
                this.pvpManager = new Pvpmanager(this);
                this.pvpManager.onEnable();
            } catch (Throwable t) {
                handleModuleInitError("PvPManager", t);
                this.pvpManager = null;
            }
        }

        // 3. Claims Modul initialisieren
        if (claimsEnabled) {
            try {
                this.claims = new Claims(this);
                this.claims.onEnable();
            } catch (Throwable t) {
                handleModuleInitError("Claims", t);
                this.claims = null;
            }
        }

        // 4. StoneAttack Modul initialisieren
        if (stoneattackEnabled) {
            try {
                this.stoneattack = new Stoneattack(this);
                this.stoneattack.onEnable();
            } catch (Throwable t) {
                handleModuleInitError("StoneAttack", t);
                this.stoneattack = null;
            }
        }

        // 5. Stonelog Modul initialisieren
        if (stonelogEnabled) {
            try {
                this.stonelog = new Stonelog(this);
                this.stonelog.onEnable();
            } catch (Throwable t) {
                handleModuleInitError("Stonelog", t);
                this.stonelog = null;
            }
        }

        // 6. Stonemute Modul initialisieren
        if (stonemuteEnabled) {
            boolean vc = Bukkit.getPluginManager().isPluginEnabled("voicechat");
            boolean lb = Bukkit.getPluginManager().isPluginEnabled("LiteBans");
            if (!vc || !lb) {
                String missing = !vc && !lb ? "SimpleVoiceChat & LiteBans" : (!vc ? "SimpleVoiceChat" : "LiteBans");
                getLogger().severe("Stonemute-Modul wird übersprungen (benötigte Abhängigkeit fehlt: " + missing + ")!");
                this.stonemute = null;
            } else {
                try {
                    this.stonemute = new Stonemute(this);
                    this.stonemute.onEnable();
                } catch (Throwable t) {
                    handleModuleInitError("Stonemute", t);
                    this.stonemute = null;
                }
            }
        }

        // 7. MapControl Modul initialisieren
        if (mapcontrolEnabled) {
            try {
                this.mapControl = new MapControl(this);
                this.mapControl.onEnable();
            } catch (Throwable t) {
                handleModuleInitError("MapControl", t);
                this.mapControl = null;
            }
        }

        // 8. StoneVoice Modul initialisieren
        if (stonevoiceEnabled) {
            if (!Bukkit.getPluginManager().isPluginEnabled("voicechat")) {
                getLogger().severe("StoneVoice-Modul wird übersprungen (benötigte Abhängigkeit fehlt: SimpleVoiceChat)!");
                this.stoneVoice = null;
            } else {
                try {
                    this.stoneVoice = new StoneVoice(this);
                    this.stoneVoice.onEnable();
                } catch (Throwable t) {
                    handleModuleInitError("StoneVoice", t);
                    this.stoneVoice = null;
                }
            }
        }

        // 9. PlayRewards Modul initialisieren
        if (playrewardsEnabled) {
            try {
                this.playReward = new PlayReward(this);
                this.playReward.onEnable();
            } catch (Throwable t) {
                handleModuleInitError("PlayRewards", t);
                this.playReward = null;
            }
        }

        // 10. Deathchest Modul initialisieren
        if (deathchestEnabled) {
            try {
                this.deathchest = new Deathchest(this);
                this.deathchest.onEnable();
            } catch (Throwable t) {
                handleModuleInitError("Deathchest", t);
                this.deathchest = null;
            }
        }

        // 11. ClearLag Modul initialisieren
        if (clearlagEnabled) {
            try {
                this.clearLag = new ClearLagModule(this);
                this.clearLag.onEnable();
            } catch (Throwable t) {
                handleModuleInitError("ClearLag", t);
                this.clearLag = null;
            }
        }

        // Hauptbefehl /ztone registrieren
        PluginCommand ztoneCmd = getCommand("ztone");
        if (ztoneCmd != null) {
            ZtoneCommand ztoneExecutor = new ZtoneCommand(this);
            ztoneCmd.setExecutor(ztoneExecutor);
            ztoneCmd.setTabCompleter(ztoneExecutor);
        }

        // Command-Filter registrieren, damit Befehle deaktivierter Module niemals als Vorschlag im Chat erscheinen
        this.moduleCommandFilterListener = new ModuleCommandFilterListener(this);
        Bukkit.getPluginManager().registerEvents(this.moduleCommandFilterListener, this);

        getLogger().info("Plugin erfolgreich aktiviert!");
    }

    private void handleModuleInitError(String moduleName, Throwable t) {
        if (t instanceof NoClassDefFoundError || t instanceof ClassNotFoundException) {
            String dep = identifyMissingDependency(t.getMessage());
            getLogger().severe(moduleName + "-Modul wird übersprungen (benötigte Abhängigkeit fehlt: " + dep + ")!");
        } else {
            getLogger().severe("Fehler beim Aktivieren des " + moduleName + "-Moduls: " + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()));
        }
        getLogger().fine(moduleName + " Init-Fehler: " + t);
    }

    private String identifyMissingDependency(String className) {
        if (className == null) return "Unbekannte Bibliothek";
        String lower = className.toLowerCase();
        if (lower.contains("voicechat")) return "SimpleVoiceChat";
        if (lower.contains("litebans")) return "LiteBans";
        if (lower.contains("comphenix") || lower.contains("protocollib") || lower.contains("protocol")) return "ProtocolLib";
        if (lower.contains("vault") || lower.contains("milkbowl") || lower.contains("economy")) return "Vault";
        if (lower.contains("placeholderapi") || lower.contains("clip")) return "PlaceholderAPI";
        if (lower.contains("luckperms") || lower.contains("lucko")) return "LuckPerms";
        if (lower.contains("floodgate") || lower.contains("geyser")) return "Floodgate";
        if (lower.contains("plan")) return "Plan";
        if (lower.contains("worldguard") || lower.contains("sk89q")) return "WorldGuard";
        return className.replace('/', '.');
    }

    @Override
    public void onDisable() {

        if (clearLag != null) {
            try {
                clearLag.onDisable();
            } catch (Throwable t) {
                getLogger().warning("Fehler beim Deaktivieren von ClearLag: " + t.getMessage());
            }
        }

        if (deathchest != null) {
            try {
                deathchest.onDisable();
            } catch (Throwable t) {
                getLogger().warning("Fehler beim Deaktivieren von Deathchest: " + t.getMessage());
            }
        }

        if (playReward != null) {
            try {
                playReward.onDisable();
            } catch (Throwable t) {
                getLogger().warning("Fehler beim Deaktivieren von PlayRewards: " + t.getMessage());
            }
        }

        if (stoneVoice != null) {
            try {
                stoneVoice.onDisable();
            } catch (Throwable t) {
                getLogger().warning("Fehler beim Deaktivieren von StoneVoice: " + t.getMessage());
            }
        }

        if (mapControl != null) {
            try {
                mapControl.onDisable();
            } catch (Throwable t) {
                getLogger().warning("Fehler beim Deaktivieren von MapControl: " + t.getMessage());
            }
        }

        if (claims != null) {
            try {
                claims.onDisable();
            } catch (Throwable t) {
                getLogger().warning("Fehler beim Deaktivieren von Claims: " + t.getMessage());
            }
        }

        if (stoneattack != null) {
            try {
                stoneattack.onDisable();
            } catch (Throwable t) {
                getLogger().warning("Fehler beim Deaktivieren von StoneAttack: " + t.getMessage());
            }
        }

        if (stonelog != null) {
            try {
                stonelog.onDisable();
            } catch (Throwable t) {
                getLogger().warning("Fehler beim Deaktivieren von Stonelog: " + t.getMessage());
            }
        }

        if (stonemute != null) {
            try {
                stonemute.onDisable();
            } catch (Throwable t) {
                getLogger().warning("Fehler beim Deaktivieren von Stonemute: " + t.getMessage());
            }
        }

        if (pvpManager != null && configManager != null && configManager.isPvpmanagerEnabled()) {
            try {
                pvpManager.onDisable();
            } catch (Throwable t) {
                getLogger().warning("Fehler beim Deaktivieren von PvPManager: " + t.getMessage());
            }
        }

        if (configManager != null && configManager.isClansystemEnabled()) {
            if (glowManager != null) {
                try {
                    glowManager.shutdown();
                } catch (Throwable t) {
                    getLogger().warning("Fehler beim Deaktivieren des GlowManagers: " + t.getMessage());
                }
            }
            if (clanManager != null) {
                try {
                    clanManager.saveAllClans();
                } catch (Throwable t) {
                    getLogger().warning("Fehler beim Speichern der Clans: " + t.getMessage());
                }
            }
        }

        if (databaseManager != null) {
            try {
                databaseManager.disconnect();
            } catch (Throwable t) {
                getLogger().warning("Fehler beim Trennen der Datenbank: " + t.getMessage());
            }
        }

        getLogger().info("Plugin deaktiviert!");
    }

    public void reload() {
        reloadConfig();
        configManager.reload();

        if (moduleCommandFilterListener != null) {
            moduleCommandFilterListener.recalculateDisabledCommands();
        }

        if (deathchest != null && configManager.isDeathchestEnabled()) {
            deathchest.reload();
        }
        if (clearLag != null && configManager.isClearlagEnabled()) {
            clearLag.reload();
        }
        if (messageManager != null) {
            messageManager.reload();
        }
        if (levelManager != null) {
            levelManager.reload();
        }
        if (pvpManager != null && configManager.isPvpmanagerEnabled()) {
            pvpManager.reloadConfig();
            pvpManager.getLocaleManager().reload();
        }
        if (claims != null && configManager.isClaimsEnabled()) {
            claims.reloadConfig();
        }
        if (stoneattack != null && configManager.isStoneattackEnabled()) {
            stoneattack.reloadAll();
        }
        if (stonelog != null && configManager.isStonelogEnabled()) {
            stonelog.reload();
        }
        if (stonemute != null && configManager.isStonemuteEnabled()) {
            stonemute.reload();
        }
        if (mapControl != null && configManager.isMapcontrolEnabled()) {
            mapControl.getPluginConfig().load();
            mapControl.applyRestrictionsToAll();
        }
        if (playReward != null && configManager.isPlayrewardsEnabled()) {
            playReward.reloadConfig();
            playReward.getConfigManager().reloadAll();
        }
        if (configManager.isClansystemEnabled() && clanManager != null) {
            clanManager.saveAllClans();
            clanManager.loadAllClans();
            if (glowManager != null) {
                glowManager.setupAll();
            }
        }
    }

    public boolean isForcedWarPvP(Player player) {
        if (player == null) return false;
        return isForcedWarPvP(player.getUniqueId());
    }

    public boolean isSameClan(UUID a, UUID b) {
        if (clanManager == null || a == null || b == null) return false;
        Clan clanA = clanManager.getClanByPlayer(a);
        Clan clanB = clanManager.getClanByPlayer(b);
        return clanA != null && clanB != null && clanA.getId() == clanB.getId();
    }

    public boolean isAlly(UUID a, UUID b) {
        if (clanManager == null || a == null || b == null) return false;
        Clan clanA = clanManager.getClanByPlayer(a);
        Clan clanB = clanManager.getClanByPlayer(b);
        return clanA != null && clanB != null && clanA.isAlliedWith(clanB.getId());
    }

    public boolean areAtWar(Player a, Player b) {
        if (a == null || b == null) return false;
        return areAtWar(a.getUniqueId(), b.getUniqueId());
    }

    public boolean areAtWar(UUID a, UUID b) {
        if (clanManager == null || warManager == null || a == null || b == null) return false;
        Clan clanA = clanManager.getClanByPlayer(a);
        Clan clanB = clanManager.getClanByPlayer(b);
        if (clanA == null || clanB == null || clanA.getId() == clanB.getId()) return false;
        return warManager.areAtWar(clanA.getId(), clanB.getId());
    }

    public boolean areWarOpponentsWithPvP(UUID a, UUID b) {
        if (clanManager == null || warManager == null || a == null || b == null) return false;
        Clan clanA = clanManager.getClanByPlayer(a);
        Clan clanB = clanManager.getClanByPlayer(b);
        if (clanA == null || clanB == null || clanA.getId() == clanB.getId()) return false;
        if (!warManager.areAtWar(clanA.getId(), clanB.getId())) return false;
        ClanMember memberA = clanA.getMember(a);
        ClanMember memberB = clanB.getMember(b);
        if (memberA == null || memberB == null) return false;
        return memberA.hasPermission(Permission.WAR_ALWAYS_PVP) && memberB.hasPermission(Permission.WAR_ALWAYS_PVP);
    }

    public boolean isForcedWarPvP(UUID uuid) {
        if (configManager == null || !configManager.isClansystemEnabled() || !configManager.isPvpmanagerEnabled()) {
            return false;
        }
        if (clanManager == null) return false;
        Clan clan = clanManager.getClanByPlayer(uuid);
        if (clan == null || clan.getActiveWars().isEmpty()) {
            return false;
        }
        ClanMember member = clan.getMember(uuid);
        if (member == null) return false;
        return member.hasPermission(Permission.WAR_ALWAYS_PVP);
    }

    // ==========================================
    // Externe API-Methoden für abhängige Plugins
    // ==========================================

    /**
     * Prüft, ob PvP in einer bestimmten Dimension oder Welt standardmäßig aktiviert ist.
     * Unterstützt Namen wie "minecraft:overworld", "world", "world_nether", "nether", "Shopwelt" etc.
     *
     * @param dimension Der Dimensions- oder Weltname
     * @return true wenn PvP aktiv ist, sonst false
     */
    public boolean isDimensionPvPEnabled(String dimension) {
        if (pvpManager != null && configManager != null && configManager.isPvpmanagerEnabled()) {
            return pvpManager.isDimensionPvPEnabled(dimension);
        }
        World world = Bukkit.getWorld(dimension);
        return world != null ? world.getPVP() : true;
    }

    /**
     * Prüft, ob PvP in der angegebenen Bukkit-Welt standardmäßig aktiviert ist.
     *
     * @param world Die Bukkit-Welt
     * @return true wenn PvP aktiv ist, sonst false
     */
    public boolean isDimensionPvPEnabled(World world) {
        if (world == null) return true;
        if (pvpManager != null && configManager != null && configManager.isPvpmanagerEnabled()) {
            return pvpManager.isDimensionPvPEnabled(world);
        }
        return world.getPVP();
    }

    /**
     * Prüft, ob PvP in der Dimension oder Welt forciert ist (nicht von Spielern per Befehl überschreibbar).
     *
     * @param dimension Der Dimensions- oder Weltname
     * @return true wenn forciert, sonst false
     */
    public boolean isDimensionPvPForced(String dimension) {
        if (pvpManager != null && configManager != null && configManager.isPvpmanagerEnabled()) {
            return pvpManager.isDimensionPvPForced(dimension);
        }
        return false;
    }

    /**
     * Prüft, ob PvP in der angegebenen Welt forciert ist.
     *
     * @param world Die Bukkit-Welt
     * @return true wenn forciert, sonst false
     */
    public boolean isDimensionPvPForced(World world) {
        if (world == null) return false;
        if (pvpManager != null && configManager != null && configManager.isPvpmanagerEnabled()) {
            return pvpManager.isDimensionPvPForced(world);
        }
        return false;
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

    /**
     * Alias für isDimensionPvPEnabled(dimension).
     */
    public boolean getDimensionPvPStatus(String dimension) {
        return isDimensionPvPEnabled(dimension);
    }

    /**
     * Alias für isDimensionPvPEnabled(world).
     */
    public boolean getDimensionPvPStatus(World world) {
        return isDimensionPvPEnabled(world);
    }

    /**
     * Statische API-Methode: Prüft, ob PvP in einer bestimmten Dimension oder Welt aktiviert ist.
     */
    public static boolean isPvPEnabledInDimension(String dimension) {
        if (instance != null) {
            return instance.isDimensionPvPEnabled(dimension);
        }
        World world = Bukkit.getWorld(dimension);
        return world != null ? world.getPVP() : true;
    }

    /**
     * Statische API-Methode: Prüft, ob PvP in einer Bukkit-Welt aktiviert ist.
     */
    public static boolean isPvPEnabledInDimension(World world) {
        if (instance != null) {
            return instance.isDimensionPvPEnabled(world);
        }
        return world != null ? world.getPVP() : true;
    }

    /**
     * Statische API-Methode: Alias für isPvPEnabledInDimension(dimension).
     */
    public static boolean getPvPStatus(String dimension) {
        return isPvPEnabledInDimension(dimension);
    }

    /**
     * Statische API-Methode: Alias für isPvPEnabledInDimension(world).
     */
    public static boolean getPvPStatus(World world) {
        return isPvPEnabledInDimension(world);
    }

    /**
     * Statische API-Methode: Prüft, ob PvP in einer Dimension forciert ist.
     */
    public static boolean isPvPForcedInDimension(String dimension) {
        if (instance != null) {
            return instance.isDimensionPvPForced(dimension);
        }
        return false;
    }

    /**
     * Statische API-Methode: Prüft, ob PvP in einer Welt forciert ist.
     */
    public static boolean isPvPForcedInDimension(World world) {
        if (instance != null) {
            return instance.isDimensionPvPForced(world);
        }
        return false;
    }

    /**
     * Prüft, ob ein bestimmter Spieler an seinem aktuellen Standort PvP ausführen kann
     * (unter Berücksichtigung von Welt/Dimension, persönlichem PvP-Status, WorldGuard, Claims und Clankriegen).
     */
    public boolean canPvP(Player player) {
        if (pvpManager != null && configManager != null && configManager.isPvpmanagerEnabled()) {
            return pvpManager.canPvP(player);
        }
        return player != null && player.getWorld().getPVP();
    }

    /**
     * Statische Hilfsmethode: Prüft, ob ein bestimmter Spieler aktuell PvP ausführen kann.
     */
    public static boolean canPlayerPvP(Player player) {
        if (instance != null) {
            return instance.canPvP(player);
        }
        return player != null && player.getWorld().getPVP();
    }

    public static Ztone getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public LevelManager getLevelManager() {
        return levelManager;
    }

    public ClanManager getClanManager() {
        return clanManager;
    }

    public WarManager getWarManager() {
        return warManager;
    }

    public AllianceManager getAllianceManager() {
        return allianceManager;
    }

    public ChatInputManager getChatInputManager() {
        return chatInputManager;
    }

    public GlowManager getGlowManager() {
        return glowManager;
    }

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }


    public Pvpmanager getPvpManager() {
        return pvpManager;
    }

    public Claims getClaims() {
        return claims;
    }

    public Stoneattack getStoneattack() {
        return stoneattack;
    }

    public Stonelog getStonelog() {
        return stonelog;
    }

    public Stonemute getStonemute() {
        return stonemute;
    }

    public MapControl getMapControl() {
        return mapControl;
    }

    public StoneVoice getStoneVoice() {
        return stoneVoice;
    }

    public PlayReward getPlayReward() {
        return playReward;
    }

    public Deathchest getDeathchest() {
        return deathchest;
    }

    public ClearLagModule getClearLag() {
        return clearLag;
    }

    public ModuleCommandFilterListener getModuleCommandFilterListener() {
        return moduleCommandFilterListener;
    }
}
