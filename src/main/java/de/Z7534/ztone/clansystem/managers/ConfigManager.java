package de.Z7534.ztone.clansystem.managers;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.models.ClanRank;
import de.Z7534.ztone.clansystem.models.Permission;
import de.Z7534.ztone.clansystem.storage.StorageType;
import de.Z7534.ztone.util.ConfigUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ConfigManager {

    private final Ztone plugin;
    private FileConfiguration globalConfig;
    private FileConfiguration clanConfig;

    // Globale Modul-Einstellungen
    private boolean clansystemEnabled = true;
    private boolean clankasseEnabled = true;
    private boolean pvpmanagerEnabled = true;
    private boolean claimsEnabled = true;
    private boolean stoneattackEnabled = true;
    private boolean stonelogEnabled = true;
    private boolean stonemuteEnabled = true;
    private boolean mapcontrolEnabled = true;
    private boolean stonevoiceEnabled = true;
    private boolean playrewardsEnabled = true;
    private boolean deathchestEnabled = true;
    private boolean clearlagEnabled = true;

    // Globale Datenbank-Einstellungen
    private StorageType storageType;
    private String dbFile;
    private String dbTablePrefix;
    private String mariaDbHost;
    private int mariaDbPort;
    private String mariaDbDatabase;
    private String mariaDbUsername;
    private String mariaDbPassword;
    private boolean mariaDbUseSsl;
    private int mariaDbPoolSize;

    // Clan-spezifische Einstellungen (aus clansystem/config.yml)
    private int clanNameMinLength;
    private int clanNameMaxLength;
    private Pattern clanNamePattern;
    private int leaveCooldown;
    private Clan.JoinType defaultJoinType;

    private int suffixMaxLength;
    private boolean allowMagic;
    private List<String> suffixBlacklist;

    private int teleportWarmup;
    private int teleportCooldown;
    private boolean cancelOnMove;
    private boolean cancelOnDamage;

    private int warKillPoints;
    private int normalKillPoints;
    private boolean countNormalKills;
    private boolean disableFriendlyFire;
    private boolean disableAllyFire;

    private boolean broadcastClanCreated;
    private boolean broadcastClanDisbanded;
    private boolean broadcastMemberJoined;
    private boolean broadcastMemberLeft;

    private List<ClanRank> defaultRanks;
    private boolean chestLogging;

    public ConfigManager(Ztone plugin) {
        this.plugin = plugin;
        this.defaultRanks = new ArrayList<>();
        reload();
    }

    public void reload() {
        // 1. Globale Konfiguration laden und synchronisieren (plugins/Ztone/config.yml)
        File rootConfigFile = new File(plugin.getDataFolder(), "config.yml");
        this.globalConfig = ConfigUtil.loadAndSync(plugin, rootConfigFile, "config.yml");
        plugin.reloadConfig();
        this.globalConfig = plugin.getConfig();
        loadGlobalSettings();

        // 2. Clan-spezifische Konfiguration laden und synchronisieren (plugins/Ztone/clansystem/config.yml)
        File clanFolder = new File(plugin.getDataFolder(), "clansystem");
        File clanConfigFile = new File(clanFolder, "config.yml");

        if (!clanConfigFile.exists() && globalConfig.contains("clan")) {
            this.clanConfig = globalConfig;
        } else {
            this.clanConfig = ConfigUtil.loadAndSync(plugin, clanConfigFile, "clansystem/config.yml");
        }

        loadClanSettings();
        loadSuffixSettings();
        loadTeleportSettings();
        loadPvPSettings();
        loadBroadcastSettings();
        loadDefaultRanks();
        this.chestLogging = clanConfig.getBoolean("chest-logging", true);
    }

    private void loadGlobalSettings() {
        ConfigurationSection modules = globalConfig.getConfigurationSection("modules");
        if (modules != null) {
            this.clansystemEnabled = modules.getBoolean("clansystem", true);
            this.clankasseEnabled = modules.getBoolean("clankasse", clansystemEnabled);
            this.pvpmanagerEnabled = modules.getBoolean("pvpmanager", true);
            this.claimsEnabled = modules.getBoolean("claims", true);
            this.stoneattackEnabled = modules.getBoolean("stoneattack", true);
            this.stonelogEnabled = modules.getBoolean("stonelog", true);
            this.stonemuteEnabled = modules.getBoolean("stonemute", true);
            this.mapcontrolEnabled = modules.getBoolean("mapcontrol", true);
            this.stonevoiceEnabled = modules.getBoolean("stonevoice", true);
            this.playrewardsEnabled = modules.getBoolean("playrewards", true);
            this.deathchestEnabled = modules.getBoolean("deathchest", true);
            this.clearlagEnabled = modules.getBoolean("clearlag", true);
        } else {
            this.clansystemEnabled = globalConfig.getBoolean("modules.clansystem", true);
            this.clankasseEnabled = globalConfig.getBoolean("modules.clankasse", clansystemEnabled);
            this.pvpmanagerEnabled = globalConfig.getBoolean("modules.pvpmanager", true);
            this.claimsEnabled = globalConfig.getBoolean("modules.claims", true);
            this.stoneattackEnabled = globalConfig.getBoolean("modules.stoneattack", true);
            this.stonelogEnabled = globalConfig.getBoolean("modules.stonelog", true);
            this.stonemuteEnabled = globalConfig.getBoolean("modules.stonemute", true);
            this.mapcontrolEnabled = globalConfig.getBoolean("modules.mapcontrol", true);
            this.stonevoiceEnabled = globalConfig.getBoolean("modules.stonevoice", true);
            this.playrewardsEnabled = globalConfig.getBoolean("modules.playrewards", true);
            this.deathchestEnabled = globalConfig.getBoolean("modules.deathchest", true);
            this.clearlagEnabled = globalConfig.getBoolean("modules.clearlag", true);
        }

        ConfigurationSection db = globalConfig.getConfigurationSection("database");
        if (db != null) {
            String typeStr = db.getString("type", "SQLITE");
            try {
                this.storageType = StorageType.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                this.storageType = StorageType.SQLITE;
            }

            this.dbFile = db.getString("sqlite.file", "ztone.db");
            this.dbTablePrefix = db.getString("table-prefix", "ztone_");

            this.mariaDbHost = db.getString("mariadb.host", "localhost");
            this.mariaDbPort = db.getInt("mariadb.port", 3306);
            this.mariaDbDatabase = db.getString("mariadb.database", "ztone");
            this.mariaDbUsername = db.getString("mariadb.username", "root");
            this.mariaDbPassword = db.getString("mariadb.password", "");
            this.mariaDbUseSsl = db.getBoolean("mariadb.use-ssl", false);
            this.mariaDbPoolSize = db.getInt("mariadb.pool-size", 10);
        } else {
            this.storageType = StorageType.SQLITE;
            this.dbFile = "ztone.db";
            this.dbTablePrefix = "ztone_";
            this.mariaDbHost = "localhost";
            this.mariaDbPort = 3306;
            this.mariaDbDatabase = "ztone";
            this.mariaDbUsername = "root";
            this.mariaDbPassword = "";
            this.mariaDbUseSsl = false;
            this.mariaDbPoolSize = 10;
        }
    }

    private void loadClanSettings() {
        ConfigurationSection clan = clanConfig.getConfigurationSection("clan");
        if (clan != null) {
            clanNameMinLength = clan.getInt("name-min-length", 3);
            clanNameMaxLength = clan.getInt("name-max-length", 16);
            clanNamePattern = Pattern.compile(clan.getString("name-pattern", "^[a-zA-Z0-9_]+$"));
            leaveCooldown = clan.getInt("leave-cooldown", 86400);

            String joinTypeStr = clan.getString("default-join-type", "INVITE");
            try {
                defaultJoinType = Clan.JoinType.valueOf(joinTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                defaultJoinType = Clan.JoinType.INVITE;
            }
        }
    }

    private void loadSuffixSettings() {
        ConfigurationSection suffix = clanConfig.getConfigurationSection("suffix");
        if (suffix != null) {
            suffixMaxLength = suffix.getInt("max-length", 8);
            allowMagic = suffix.getBoolean("allow-magic", false);
            suffixBlacklist = suffix.getStringList("blacklist");
        }
    }

    private void loadTeleportSettings() {
        ConfigurationSection teleport = clanConfig.getConfigurationSection("teleport");
        if (teleport != null) {
            teleportWarmup = teleport.getInt("warmup", 3);
            teleportCooldown = teleport.getInt("cooldown", 60);
            cancelOnMove = teleport.getBoolean("cancel-on-move", true);
            cancelOnDamage = teleport.getBoolean("cancel-on-damage", true);
        }
    }

    private void loadPvPSettings() {
        ConfigurationSection pvp = clanConfig.getConfigurationSection("pvp");
        if (pvp != null) {
            warKillPoints = pvp.getInt("war-kill-points", 10);
            normalKillPoints = pvp.getInt("normal-kill-points", 2);
            countNormalKills = pvp.getBoolean("count-normal-kills", true);
            disableFriendlyFire = pvp.getBoolean("disable-friendly-fire", true);
            disableAllyFire = pvp.getBoolean("disable-ally-fire", true);
        }
    }

    private void loadBroadcastSettings() {
        ConfigurationSection broadcasts = clanConfig.getConfigurationSection("broadcasts");
        if (broadcasts != null) {
            broadcastClanCreated = broadcasts.getBoolean("clan-created", true);
            broadcastClanDisbanded = broadcasts.getBoolean("clan-disbanded", true);
            broadcastMemberJoined = broadcasts.getBoolean("member-joined", true);
            broadcastMemberLeft = broadcasts.getBoolean("member-left", true);
        }
    }

    private void loadDefaultRanks() {
        defaultRanks.clear();
        List<?> ranksList = clanConfig.getList("default-ranks");
        if (ranksList != null) {
            for (Object obj : ranksList) {
                if (obj instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> rankMap = (java.util.Map<String, Object>) obj;

                    String name = (String) rankMap.get("name");
                    int priority = (int) rankMap.get("priority");

                    ClanRank rank = new ClanRank(name, priority);

                    @SuppressWarnings("unchecked")
                    List<String> permList = (List<String>) rankMap.get("permissions");
                    if (permList != null) {
                        for (String permStr : permList) {
                            Permission perm = Permission.fromString(permStr);
                            if (perm != null) {
                                rank.addPermission(perm);
                            }
                        }
                    }

                    defaultRanks.add(rank);
                }
            }
        }

        if (defaultRanks.isEmpty()) {
            ClanRank admin = new ClanRank("Admin", 100);
            for (Permission perm : Permission.values()) {
                if (perm != Permission.DISBAND) {
                    admin.addPermission(perm);
                }
            }
            defaultRanks.add(admin);

            ClanRank member = new ClanRank("Mitglied", 10);
            member.addPermission(Permission.CHEST_ACCESS);
            defaultRanks.add(member);
        }
    }

    // Modul-Status
    public boolean isClansystemEnabled() {
        return clansystemEnabled;
    }

    public boolean isClankasseEnabled() {
        return clansystemEnabled && clankasseEnabled;
    }

    public boolean isPvpmanagerEnabled() {
        return pvpmanagerEnabled;
    }

    public boolean isClaimsEnabled() {
        return claimsEnabled;
    }

    public boolean isStoneattackEnabled() {
        return stoneattackEnabled;
    }

    public boolean isStonelogEnabled() {
        return stonelogEnabled;
    }

    public boolean isStonemuteEnabled() {
        return stonemuteEnabled;
    }

    public boolean isMapcontrolEnabled() {
        return mapcontrolEnabled;
    }

    public boolean isStonevoiceEnabled() {
        return stonevoiceEnabled;
    }

    public boolean isPlayrewardsEnabled() {
        return playrewardsEnabled;
    }

    public boolean isDeathchestEnabled() {
        return deathchestEnabled;
    }

    public boolean isClearlagEnabled() {
        return clearlagEnabled;
    }

    public boolean isModuleEnabled(String module) {
        if ("clansystem".equalsIgnoreCase(module) || "clans".equalsIgnoreCase(module) || "clan".equalsIgnoreCase(module)) {
            return clansystemEnabled;
        }
        if ("clankasse".equalsIgnoreCase(module) || "bank".equalsIgnoreCase(module) || "kasse".equalsIgnoreCase(module)) {
            return isClankasseEnabled();
        }
        if ("pvpmanager".equalsIgnoreCase(module) || "pvp".equalsIgnoreCase(module)) {
            return pvpmanagerEnabled;
        }
        if ("claims".equalsIgnoreCase(module) || "claim".equalsIgnoreCase(module)) {
            return claimsEnabled;
        }
        if ("stoneattack".equalsIgnoreCase(module)) {
            return stoneattackEnabled;
        }
        if ("stonelog".equalsIgnoreCase(module)) {
            return stonelogEnabled;
        }
        if ("stonemute".equalsIgnoreCase(module)) {
            return stonemuteEnabled;
        }
        if ("mapcontrol".equalsIgnoreCase(module) || "map".equalsIgnoreCase(module)) {
            return mapcontrolEnabled;
        }
        if ("stonevoice".equalsIgnoreCase(module) || "voice".equalsIgnoreCase(module)) {
            return stonevoiceEnabled;
        }
        if ("playrewards".equalsIgnoreCase(module) || "playreward".equalsIgnoreCase(module) || "rewards".equalsIgnoreCase(module)) {
            return playrewardsEnabled;
        }
        if ("deathchest".equalsIgnoreCase(module)) {
            return deathchestEnabled;
        }
        if ("clearlag".equalsIgnoreCase(module)) {
            return clearlagEnabled;
        }
        return globalConfig.getBoolean("modules." + module, true);
    }

    public StorageType getStorageType() { return storageType; }
    public String getDbFile() { return dbFile; }
    public String getDbTablePrefix() { return dbTablePrefix; }
    public String getMariaDbHost() { return mariaDbHost; }
    public int getMariaDbPort() { return mariaDbPort; }
    public String getMariaDbDatabase() { return mariaDbDatabase; }
    public String getMariaDbUsername() { return mariaDbUsername; }
    public String getMariaDbPassword() { return mariaDbPassword; }
    public boolean isMariaDbUseSsl() { return mariaDbUseSsl; }
    public int getMariaDbPoolSize() { return mariaDbPoolSize; }

    public int getClanNameMinLength() { return clanNameMinLength; }
    public int getClanNameMaxLength() { return clanNameMaxLength; }
    public Pattern getClanNamePattern() { return clanNamePattern; }
    public int getLeaveCooldown() { return leaveCooldown; }
    public Clan.JoinType getDefaultJoinType() { return defaultJoinType; }

    public int getSuffixMaxLength() { return suffixMaxLength; }

    public boolean isAllowMagic() { return allowMagic; }
    public List<String> getSuffixBlacklist() { return suffixBlacklist; }

    public int getTeleportWarmup() { return teleportWarmup; }
    public int getTeleportCooldown() { return teleportCooldown; }
    public boolean isCancelOnMove() { return cancelOnMove; }
    public boolean isCancelOnDamage() { return cancelOnDamage; }

    public int getWarKillPoints() { return warKillPoints; }
    public int getNormalKillPoints() { return normalKillPoints; }
    public boolean isCountNormalKills() { return countNormalKills; }
    public boolean isDisableFriendlyFire() { return disableFriendlyFire; }
    public boolean isDisableAllyFire() { return disableAllyFire; }

    public boolean isBroadcastClanCreated() { return broadcastClanCreated; }
    public boolean isBroadcastClanDisbanded() { return broadcastClanDisbanded; }
    public boolean isBroadcastMemberJoined() { return broadcastMemberJoined; }
    public boolean isBroadcastMemberLeft() { return broadcastMemberLeft; }

    public List<ClanRank> getDefaultRanks() { return new ArrayList<>(defaultRanks); }

    public boolean isChestLogging() { return chestLogging; }

    public FileConfiguration getGlobalConfig() { return globalConfig; }
    public FileConfiguration getClanConfig() { return clanConfig; }
}
