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
import java.util.*;
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
    private boolean mapcontrolEnabled = true;
    private boolean stonevoiceEnabled = true;
    private boolean playrewardsEnabled = true;
    private boolean deathchestEnabled = true;
    private boolean clearlagEnabled = true;
    private boolean stonesitEnabled = true;
    private boolean dragoneggEnabled = true;

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

    // Datenbank-Backup-Einstellungen
    private boolean backupEnabled = true;
    private int backupIntervalMinutes = 5;
    private int backupMaxCount = 50;

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

    // Clan-spezifische Steuern-Einstellungen
    private boolean taxesEnabled = true;
    private int taxesIntervalDays = 7;
    private int taxesGracePeriodDays = 14;
    private Map<Integer, Double> taxesLevelBase = new HashMap<>();
    private double taxesFallbackBase = 500.0;
    private List<TaxBracket> taxesBrackets = new ArrayList<>();

    public static class TaxBracket {
        private final double min;
        private final double max;
        private final double multiplier;
        private final double flatTax;

        public TaxBracket(double min, double max, double multiplier, double flatTax) {
            this.min = min;
            this.max = max;
            this.multiplier = multiplier;
            this.flatTax = flatTax;
        }

        public double getMin() {
            return min;
        }

        public double getMax() {
            return max;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public double getFlatTax() {
            return flatTax;
        }

        public boolean matches(double balance) {
            if (balance < min) {
                return false;
            }
            if (max >= 0 && balance > max) {
                return false;
            }
            return true;
        }

        public double calculate(double balance) {
            double effectiveBalance = Math.max(0.0, balance);
            return (effectiveBalance * multiplier) + flatTax;
        }
    }

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
        loadTaxesSettings();
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
            this.mapcontrolEnabled = modules.getBoolean("mapcontrol", true);
            this.stonevoiceEnabled = modules.getBoolean("stonevoice", true);
            this.playrewardsEnabled = modules.getBoolean("playrewards", true);
            this.deathchestEnabled = modules.getBoolean("deathchest", true);
            this.clearlagEnabled = modules.getBoolean("clearlag", true);
            this.stonesitEnabled = modules.getBoolean("stonesit", true);
            this.dragoneggEnabled = modules.getBoolean("dragonegg", true);
        } else {
            this.clansystemEnabled = globalConfig.getBoolean("modules.clansystem", true);
            this.clankasseEnabled = globalConfig.getBoolean("modules.clankasse", clansystemEnabled);
            this.pvpmanagerEnabled = globalConfig.getBoolean("modules.pvpmanager", true);
            this.claimsEnabled = globalConfig.getBoolean("modules.claims", true);
            this.stoneattackEnabled = globalConfig.getBoolean("modules.stoneattack", true);
            this.stonelogEnabled = globalConfig.getBoolean("modules.stonelog", true);
            this.mapcontrolEnabled = globalConfig.getBoolean("modules.mapcontrol", true);
            this.stonevoiceEnabled = globalConfig.getBoolean("modules.stonevoice", true);
            this.playrewardsEnabled = globalConfig.getBoolean("modules.playrewards", true);
            this.deathchestEnabled = globalConfig.getBoolean("modules.deathchest", true);
            this.clearlagEnabled = globalConfig.getBoolean("modules.clearlag", true);
            this.stonesitEnabled = globalConfig.getBoolean("modules.stonesit", true);
            this.dragoneggEnabled = globalConfig.getBoolean("modules.dragonegg", true);
        }

        ConfigurationSection db = globalConfig.getConfigurationSection("database");
        if (db != null) {
            String typeStr = db.getString("type", "SQLITE");
            try {
                this.storageType = StorageType.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                this.storageType = StorageType.SQLITE;
            }

            String file = db.getString("file");
            if (file == null || file.isBlank()) {
                file = db.getString("sqlite.file", "clans.db");
            }
            this.dbFile = file;

            String prefix = db.getString("table-prefix");
            if (prefix == null || prefix.isBlank()) {
                prefix = db.getString("prefix", "clan_");
            }
            this.dbTablePrefix = prefix;

            this.mariaDbHost = db.getString("mariadb.host", "localhost");
            this.mariaDbPort = db.getInt("mariadb.port", 3306);
            this.mariaDbDatabase = db.getString("mariadb.database", "ztone");
            this.mariaDbUsername = db.getString("mariadb.username", "root");
            this.mariaDbPassword = db.getString("mariadb.password", "");
            this.mariaDbUseSsl = db.getBoolean("mariadb.use-ssl", false);
            this.mariaDbPoolSize = db.getInt("mariadb.pool-size", 10);
        } else {
            this.storageType = StorageType.SQLITE;
            this.dbFile = "clans.db";
            this.dbTablePrefix = "clan_";
            this.mariaDbHost = "localhost";
            this.mariaDbPort = 3306;
            this.mariaDbDatabase = "ztone";
            this.mariaDbUsername = "root";
            this.mariaDbPassword = "";
            this.mariaDbUseSsl = false;
            this.mariaDbPoolSize = 10;
        }

        ConfigurationSection backupSec = globalConfig.getConfigurationSection("database-backup");
        if (backupSec == null && db != null) {
            backupSec = db.getConfigurationSection("backup");
        }
        if (backupSec != null) {
            this.backupEnabled = backupSec.getBoolean("enabled", true);
            this.backupIntervalMinutes = backupSec.getInt("interval-minutes", 5);
            this.backupMaxCount = backupSec.getInt("max-backups", 50);
        } else {
            this.backupEnabled = true;
            this.backupIntervalMinutes = 5;
            this.backupMaxCount = 50;
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
        ConfigurationSection tp = clanConfig.getConfigurationSection("teleport");
        if (tp != null) {
            teleportWarmup = tp.getInt("warmup", 3);
            teleportCooldown = tp.getInt("cooldown", 60);
            cancelOnMove = tp.getBoolean("cancel-on-move", true);
            cancelOnDamage = tp.getBoolean("cancel-on-damage", true);
        }
    }

    private void loadPvPSettings() {
        ConfigurationSection pvp = clanConfig.getConfigurationSection("pvp");
        if (pvp != null) {
            warKillPoints = pvp.getInt("war-kill-points", 10);
            normalKillPoints = pvp.getInt("normal-kill-points", 1);
            countNormalKills = pvp.getBoolean("count-normal-kills", false);
            disableFriendlyFire = pvp.getBoolean("disable-friendly-fire", true);
            disableAllyFire = pvp.getBoolean("disable-ally-fire", true);
        }
    }

    private void loadBroadcastSettings() {
        ConfigurationSection bc = clanConfig.getConfigurationSection("broadcast");
        if (bc != null) {
            broadcastClanCreated = bc.getBoolean("clan-created", true);
            broadcastClanDisbanded = bc.getBoolean("clan-disbanded", true);
            broadcastMemberJoined = bc.getBoolean("member-joined", true);
            broadcastMemberLeft = bc.getBoolean("member-left", true);
        }
    }

    private void loadDefaultRanks() {
        defaultRanks.clear();
        ConfigurationSection ranks = clanConfig.getConfigurationSection("default-ranks");
        if (ranks != null) {
            for (String key : ranks.getKeys(false)) {
                ConfigurationSection rank = ranks.getConfigurationSection(key);
                if (rank != null) {
                    String name = rank.getString("name", key);
                    int priority = rank.getInt("priority", 0);
                    List<String> permStrings = rank.getStringList("permissions");
                    ClanRank rankObj = new ClanRank(name, priority);
                    for (String permStr : permStrings) {
                        try {
                            rankObj.getPermissions().add(Permission.valueOf(permStr.toUpperCase()));
                        } catch (IllegalArgumentException ignored) {}
                    }
                    defaultRanks.add(rankObj);
                }
            }
        }

        if (defaultRanks.isEmpty()) {
            ClanRank leader = new ClanRank("Leader", 100);
            leader.getPermissions().addAll(java.util.Arrays.asList(Permission.values()));
            defaultRanks.add(leader);

            ClanRank coLeader = new ClanRank("Co-Leader", 80);
            coLeader.getPermissions().addAll(java.util.List.of(
                    Permission.INVITE, Permission.KICK, Permission.SET_HOME,
                    Permission.DEL_HOME, Permission.SET_WARP, Permission.DEL_WARP,
                    Permission.CHEST_ACCESS, Permission.CHEST_WITHDRAW,
                    Permission.BANK_DEPOSIT, Permission.BANK_WITHDRAW, Permission.BANK_VIEW_TRANSACTIONS
            ));
            defaultRanks.add(coLeader);

            ClanRank member = new ClanRank("Mitglied", 10);
            member.getPermissions().addAll(java.util.List.of(
                    Permission.CHEST_ACCESS, Permission.BANK_DEPOSIT, Permission.BANK_VIEW_TRANSACTIONS
            ));
            defaultRanks.add(member);
        }
    }

    private void loadTaxesSettings() {
        taxesLevelBase.clear();
        taxesBrackets.clear();

        ConfigurationSection taxesSec = clanConfig.getConfigurationSection("taxes");
        if (taxesSec == null && clanConfig.getDefaultSection() != null) {
            taxesSec = clanConfig.getDefaultSection().getConfigurationSection("taxes");
        }
        if (taxesSec == null) {
            this.taxesEnabled = true;
            this.taxesIntervalDays = 7;
            this.taxesGracePeriodDays = 14;
            this.taxesFallbackBase = 500.0;
        } else {
            this.taxesEnabled = taxesSec.getBoolean("enabled", true);
            this.taxesIntervalDays = Math.max(1, taxesSec.getInt("interval-days", 7));
            this.taxesGracePeriodDays = Math.max(0, taxesSec.getInt("grace-period-days", 14));
            this.taxesFallbackBase = taxesSec.getDouble("fallback-base", 500.0);

            ConfigurationSection levelSec = taxesSec.getConfigurationSection("level-base");
            if (levelSec != null) {
                for (String key : levelSec.getKeys(false)) {
                    try {
                        int lvl = Integer.parseInt(key);
                        taxesLevelBase.put(lvl, levelSec.getDouble(key, taxesFallbackBase));
                    } catch (NumberFormatException ignored) {}
                }
            }

            List<Map<?, ?>> bracketList = taxesSec.getMapList("balance-brackets");
            if (bracketList != null && !bracketList.isEmpty()) {
                for (Map<?, ?> map : bracketList) {
                    try {
                        double min = map.containsKey("min") ? Double.parseDouble(map.get("min").toString()) : 0.0;
                        double max = map.containsKey("max") ? Double.parseDouble(map.get("max").toString()) : -1.0;
                        double mult = map.containsKey("multiplier") ? Double.parseDouble(map.get("multiplier").toString()) : 0.0;
                        double flat = map.containsKey("flat-tax") ? Double.parseDouble(map.get("flat-tax").toString()) : 0.0;
                        taxesBrackets.add(new TaxBracket(min, max, mult, flat));
                    } catch (Exception ignored) {}
                }
            }
        }

        if (taxesBrackets.isEmpty()) {
            taxesBrackets.add(new TaxBracket(0.0, 50000.0, 0.0, 0.0));
            taxesBrackets.add(new TaxBracket(50000.0, 100000.0, 0.01, 0.0));
            taxesBrackets.add(new TaxBracket(100000.0, 500000.0, 0.02, 0.0));
            taxesBrackets.add(new TaxBracket(500000.0, 1000000.0, 0.03, 0.0));
            taxesBrackets.add(new TaxBracket(1000000.0, -1.0, 0.05, 0.0));
        }
    }

    public boolean isClansystemEnabled() {
        return clansystemEnabled;
    }

    public boolean isClankasseEnabled() {
        return clankasseEnabled && clansystemEnabled;
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

    public boolean isStonesitEnabled() {
        return stonesitEnabled;
    }

    public boolean isDragoneggEnabled() {
        return dragoneggEnabled;
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
        if ("stonesit".equalsIgnoreCase(module) || "gsit".equalsIgnoreCase(module) || "sit".equalsIgnoreCase(module)) {
            return stonesitEnabled;
        }
        if ("dragonegg".equalsIgnoreCase(module) || "egg".equalsIgnoreCase(module)) {
            return dragoneggEnabled;
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

    public boolean isBackupEnabled() { return backupEnabled; }
    public int getBackupIntervalMinutes() { return backupIntervalMinutes; }
    public int getBackupMaxCount() { return backupMaxCount; }

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

    public List<ClanRank> getDefaultRanks() { return defaultRanks; }
    public boolean isChestLogging() { return chestLogging; }

    public boolean isTaxesEnabled() {
        return taxesEnabled && isClankasseEnabled();
    }

    public int getTaxesIntervalDays() {
        return taxesIntervalDays;
    }

    public int getTaxesGracePeriodDays() {
        return taxesGracePeriodDays;
    }

    public Map<Integer, Double> getTaxesLevelBase() {
        return Collections.unmodifiableMap(taxesLevelBase);
    }

    public double getTaxesFallbackBase() {
        return taxesFallbackBase;
    }

    public List<TaxBracket> getTaxesBrackets() {
        return Collections.unmodifiableList(taxesBrackets);
    }

    public double getTaxBaseForLevel(int level) {
        return taxesLevelBase.getOrDefault(level, taxesFallbackBase);
    }

    public TaxBracket getMatchingTaxBracket(double balance) {
        for (TaxBracket bracket : taxesBrackets) {
            if (bracket.matches(balance)) {
                return bracket;
            }
        }
        return null;
    }

    public double calculateTotalTax(int level, double balance) {
        double base = getTaxBaseForLevel(level);
        double bracketTax = 0.0;
        TaxBracket bracket = getMatchingTaxBracket(balance);
        if (bracket != null) {
            bracketTax = bracket.calculate(balance);
        }
        double total = base + bracketTax;
        return Math.max(0.0, Math.round(total * 100.0) / 100.0);
    }

    public FileConfiguration getGlobalConfig() { return globalConfig; }
    public FileConfiguration getClanConfig() { return clanConfig; }
}
