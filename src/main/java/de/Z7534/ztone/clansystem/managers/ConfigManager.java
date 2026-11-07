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

    public static final double MAX_TAX_LIMIT = 300_000_000.0;

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

    private final Ztone plugin;
    private FileConfiguration globalConfig;
    private FileConfiguration clanConfig;

    // Modul-Aktivierung
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
    private int warPreparationTimeSeconds = 60;

    private boolean broadcastClanCreated;
    private boolean broadcastClanDisbanded;
    private boolean broadcastMemberJoined;
    private boolean broadcastMemberLeft;

    private final List<ClanRank> defaultRanks;
    private boolean chestLogging;

    // Steuern (Taxes)
    private boolean taxesEnabled = true;
    private int taxesIntervalDays = 7;
    private int taxesGracePeriodDays = 14;
    private Map<Integer, Double> taxesLevelBase = new HashMap<>();
    private double taxesFallbackBase = 500.0;
    private List<TaxBracket> taxesBrackets = new ArrayList<>();

    public ConfigManager(Ztone plugin) {
        this.plugin = plugin;
        this.defaultRanks = new ArrayList<>();
        reload();
    }

    public void reload() {
        File rootConfigFile = new File(plugin.getDataFolder(), "config.yml");
        this.globalConfig = ConfigUtil.loadAndSync(plugin, rootConfigFile, "config.yml");
        plugin.reloadConfig();
        this.globalConfig = plugin.getConfig();
        loadGlobalSettings();

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
        loadWarSettings();
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
            this.backupMaxCount = backupSec.getInt("max-count", 50);
        }
    }

    private void loadClanSettings() {
        ConfigurationSection clan = clanConfig.getConfigurationSection("clan");
        if (clan != null) {
            clanNameMinLength = clan.getInt("name.min-length", 3);
            clanNameMaxLength = clan.getInt("name.max-length", 16);
            clanNamePattern = Pattern.compile(clan.getString("name.allowed-characters", "^[a-zA-Z0-9_]+$"));
            leaveCooldown = clan.getInt("leave-cooldown", 300);

            try {
                defaultJoinType = Clan.JoinType.valueOf(clan.getString("default-join-type", "INVITE").toUpperCase());
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

    private void loadWarSettings() {
        ConfigurationSection war = clanConfig.getConfigurationSection("war");
        if (war != null) {
            warPreparationTimeSeconds = war.getInt("preparation-time-seconds", 60);
        } else {
            warPreparationTimeSeconds = clanConfig.getInt("war.preparation-time-seconds", 60);
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
            leader.getPermissions().addAll(Arrays.asList(Permission.values()));
            defaultRanks.add(leader);

            ClanRank coLeader = new ClanRank("Co-Leader", 80);
            coLeader.getPermissions().addAll(List.of(
                    Permission.INVITE, Permission.KICK, Permission.SET_HOME,
                    Permission.DEL_HOME, Permission.SET_WARP, Permission.DEL_WARP,
                    Permission.CHEST_ACCESS, Permission.CHEST_WITHDRAW,
                    Permission.BANK_DEPOSIT, Permission.BANK_WITHDRAW, Permission.BANK_VIEW_TRANSACTIONS
            ));
            defaultRanks.add(coLeader);

            ClanRank elder = new ClanRank("Elder", 50);
            elder.getPermissions().addAll(List.of(
                    Permission.INVITE, Permission.CHEST_ACCESS,
                    Permission.BANK_DEPOSIT, Permission.BANK_VIEW_TRANSACTIONS
            ));
            defaultRanks.add(elder);

            ClanRank member = new ClanRank("Member", 20);
            member.getPermissions().addAll(List.of(
                    Permission.CHEST_ACCESS, Permission.BANK_DEPOSIT
            ));
            defaultRanks.add(member);

            ClanRank recruit = new ClanRank("Recruit", 0);
            defaultRanks.add(recruit);
        }
    }

    private void loadTaxesSettings() {
        ConfigurationSection taxes = clanConfig.getConfigurationSection("taxes");
        if (taxes == null) {
            this.taxesEnabled = true;
            this.taxesIntervalDays = 7;
            this.taxesGracePeriodDays = 14;
            this.taxesLevelBase = new HashMap<>();
            this.taxesFallbackBase = 500.0;
            this.taxesBrackets = new ArrayList<>();
            return;
        }

        this.taxesEnabled = taxes.getBoolean("enabled", true);
        this.taxesIntervalDays = Math.max(1, taxes.getInt("interval-days", 7));
        this.taxesGracePeriodDays = Math.max(0, taxes.getInt("grace-period-days", 14));

        this.taxesLevelBase = new HashMap<>();
        ConfigurationSection lb = taxes.getConfigurationSection("level-base");
        if (lb != null) {
            for (String key : lb.getKeys(false)) {
                try {
                    int lvl = Integer.parseInt(key);
                    taxesLevelBase.put(lvl, Math.min(MAX_TAX_LIMIT, lb.getDouble(key, 500.0)));
                } catch (NumberFormatException ignored) {}
            }
        }
        this.taxesFallbackBase = Math.min(MAX_TAX_LIMIT, taxes.getDouble("fallback-base", 500.0));

        this.taxesBrackets = new ArrayList<>();
        List<Map<?, ?>> list = taxes.getMapList("balance-brackets");
        if (list != null && !list.isEmpty()) {
            for (Map<?, ?> map : list) {
                try {
                    double min = map.containsKey("min") ? ((Number) map.get("min")).doubleValue() : 0.0;
                    double max = map.containsKey("max") ? ((Number) map.get("max")).doubleValue() : -1.0;
                    double mul = map.containsKey("multiplier") ? ((Number) map.get("multiplier")).doubleValue() : 0.0;
                    double flat = map.containsKey("flat-tax") ? ((Number) map.get("flat-tax")).doubleValue() : 0.0;
                    taxesBrackets.add(new TaxBracket(min, max, mul, Math.min(MAX_TAX_LIMIT, flat)));
                } catch (Exception ignored) {}
            }
        }
    }

    public boolean isClansystemEnabled() { return clansystemEnabled; }
    public boolean isClankasseEnabled() { return clankasseEnabled; }
    public boolean isPvpmanagerEnabled() { return pvpmanagerEnabled; }
    public boolean isClaimsEnabled() { return claimsEnabled; }
    public boolean isStoneattackEnabled() { return stoneattackEnabled; }
    public boolean isStonelogEnabled() { return stonelogEnabled; }
    public boolean isMapcontrolEnabled() { return mapcontrolEnabled; }
    public boolean isStonevoiceEnabled() { return stonevoiceEnabled; }
    public boolean isPlayrewardsEnabled() { return playrewardsEnabled; }
    public boolean isDeathchestEnabled() { return deathchestEnabled; }
    public boolean isClearlagEnabled() { return clearlagEnabled; }
    public boolean isStonesitEnabled() { return stonesitEnabled; }
    public boolean isDragoneggEnabled() { return dragoneggEnabled; }

    public boolean isModuleEnabled(String moduleName) {
        if (moduleName == null) return false;
        return switch (moduleName.toLowerCase()) {
            case "clansystem" -> isClansystemEnabled();
            case "clankasse" -> isClankasseEnabled();
            case "pvpmanager" -> isPvpmanagerEnabled();
            case "claims" -> isClaimsEnabled();
            case "stoneattack" -> isStoneattackEnabled();
            case "stonelog" -> isStonelogEnabled();
            case "mapcontrol" -> isMapcontrolEnabled();
            case "stonevoice" -> isStonevoiceEnabled();
            case "playrewards" -> isPlayrewardsEnabled();
            case "deathchest" -> isDeathchestEnabled();
            case "clearlag" -> isClearlagEnabled();
            case "stonesit" -> isStonesitEnabled();
            case "dragonegg" -> isDragoneggEnabled();
            default -> false;
        };
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
    public int getWarPreparationTimeSeconds() { return warPreparationTimeSeconds; }

    public boolean isBroadcastClanCreated() { return broadcastClanCreated; }
    public boolean isBroadcastClanDisbanded() { return broadcastClanDisbanded; }
    public boolean isBroadcastMemberJoined() { return broadcastMemberJoined; }
    public boolean isBroadcastMemberLeft() { return broadcastMemberLeft; }

    public List<ClanRank> getDefaultRanks() { return defaultRanks; }
    public boolean isChestLogging() { return chestLogging; }

    public boolean isTaxesEnabled() {
        return taxesEnabled && isClankasseEnabled();
    }

    public int getTaxesIntervalDays() { return taxesIntervalDays; }
    public int getTaxesGracePeriodDays() { return taxesGracePeriodDays; }
    public Map<Integer, Double> getTaxesLevelBase() { return Collections.unmodifiableMap(taxesLevelBase); }
    public double getTaxesFallbackBase() { return taxesFallbackBase; }
    public List<TaxBracket> getTaxesBrackets() { return Collections.unmodifiableList(taxesBrackets); }

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
        total = Math.min(MAX_TAX_LIMIT, total);
        return Math.max(0.0, Math.round(total * 100.0) / 100.0);
    }

    public void setLevelBaseTax(int level, double amount) {
        double clamped = Math.min(MAX_TAX_LIMIT, Math.max(0.0, amount));
        taxesLevelBase.put(level, clamped);
        if (clanConfig != null) {
            clanConfig.set("taxes.level-base." + level, clamped);
            try {
                File clanConfigFile = new File(new File(plugin.getDataFolder(), "clansystem"), "config.yml");
                clanConfig.save(clanConfigFile);
            } catch (Exception ignored) {}
        }
    }

    public FileConfiguration getGlobalConfig() { return globalConfig; }
    public FileConfiguration getClanConfig() { return clanConfig; }
}
