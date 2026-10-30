package de.Z7534.ztone.stonesit.config;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.claims.util.Text;
import de.Z7534.ztone.util.ConfigUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

public final class StoneSitConfig {

    private final Ztone plugin;
    private File configFile;
    private FileConfiguration config;

    // Allgemeine Optionen
    private boolean getUpDamage;
    private boolean getUpSneak;
    private boolean getUpReturn;
    private boolean getUpBreak;
    private boolean allowUnsafe;
    private boolean sameBlockRest;
    private boolean centerBlock;
    private boolean customMessage;

    // Sit-Optionen
    private final Map<Material, Double> sitMaterials = new HashMap<>();
    private final Map<BlockData, Double> sitBlockData = new HashMap<>();
    private boolean bottomPartOnly;
    private boolean sitEmptyHandOnly;
    private double sitMaxDistance;
    private double sitMaxDistanceSquared;

    // Pose-Optionen (/lay & /legsup)
    private boolean poseInteract;
    private boolean poseLayRest;
    private boolean poseLaySnoringSounds;
    private boolean poseLaySnoringNightOnly;
    private boolean poseLayNightSkip;
    private boolean poseLayWakeupMorning;

    // PlayerSit-Optionen (auf Spielern sitzen)
    private boolean playerSitAllow;
    private boolean playerSitAllowNpc;
    private long playerSitMaxStack;
    private boolean playerSitSneakEjects;
    private boolean playerSitBottomReturn;
    private boolean playerSitEmptyHandOnly;
    private double playerSitMaxDistance;
    private double playerSitMaxDistanceSquared;

    // Regionen & Welten
    private boolean trustedRegionOnly;
    private final Set<String> worldBlacklist = new HashSet<>();
    private final Set<String> worldWhitelist = new HashSet<>();
    private final Set<Material> materialBlacklist = new HashSet<>();
    private final List<BlockData> blockDataBlacklist = new ArrayList<>();
    private final Set<String> commandBlacklist = new HashSet<>();

    // Nachrichten
    private String prefix;
    private final Map<String, Component> parsedMessages = new HashMap<>();

    public StoneSitConfig(Ztone plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "stonesit/config.yml");
    }

    public void load() {
        this.config = ConfigUtil.loadAndSync(plugin, configFile, "stonesit/config.yml");

        this.getUpDamage = config.getBoolean("options.get-up.damage", true);
        this.getUpSneak = config.getBoolean("options.get-up.sneak", true);
        this.getUpReturn = config.getBoolean("options.get-up.return", false);
        this.getUpBreak = config.getBoolean("options.get-up.break", true);
        this.allowUnsafe = config.getBoolean("options.allow-unsafe", false);
        this.sameBlockRest = config.getBoolean("options.same-block-rest", false);
        this.centerBlock = config.getBoolean("options.center-block", true);
        this.customMessage = config.getBoolean("options.custom-message", true);

        // Sit-Optionen
        fillSitMaterials();
        this.bottomPartOnly = config.getBoolean("options.sit.bottom-part-only", true);
        this.sitEmptyHandOnly = config.getBoolean("options.sit.empty-hand-only", true);
        this.sitMaxDistance = config.getDouble("options.sit.max-distance", 3.0d);
        this.sitMaxDistanceSquared = sitMaxDistance * sitMaxDistance;

        // Pose-Optionen
        this.poseInteract = config.getBoolean("options.pose.interact", false);
        this.poseLayRest = config.getBoolean("options.pose.lay-rest", true);
        this.poseLaySnoringSounds = config.getBoolean("options.pose.lay-snoring-sounds", true);
        this.poseLaySnoringNightOnly = config.getBoolean("options.pose.lay-snoring-night-only", true);
        this.poseLayNightSkip = config.getBoolean("options.pose.lay-night-skip", true);
        this.poseLayWakeupMorning = config.getBoolean("options.pose.lay-wakeup-morning", true);

        // PlayerSit-Optionen
        this.playerSitAllow = config.getBoolean("options.playersit.allow", true);
        this.playerSitAllowNpc = config.getBoolean("options.playersit.allow-npc", false);
        this.playerSitMaxStack = config.getLong("options.playersit.max-stack", 0L);
        this.playerSitSneakEjects = config.getBoolean("options.playersit.sneak-ejects", true);
        this.playerSitBottomReturn = config.getBoolean("options.playersit.bottom-return", false);
        this.playerSitEmptyHandOnly = config.getBoolean("options.playersit.empty-hand-only", true);
        this.playerSitMaxDistance = config.getDouble("options.playersit.max-distance", 3.0d);
        this.playerSitMaxDistanceSquared = playerSitMaxDistance * playerSitMaxDistance;

        // Regionen & Welten
        this.trustedRegionOnly = config.getBoolean("options.region.trusted-region-only", false);

        this.worldBlacklist.clear();
        for (String w : config.getStringList("options.world-blacklist")) {
            if (w != null && !w.isBlank()) {
                this.worldBlacklist.add(w.toLowerCase(Locale.ROOT));
            }
        }

        this.worldWhitelist.clear();
        for (String w : config.getStringList("options.world-whitelist")) {
            if (w != null && !w.isBlank()) {
                this.worldWhitelist.add(w.toLowerCase(Locale.ROOT));
            }
        }

        fillMaterialBlacklist();
        fillBlockDataBlacklist();

        this.commandBlacklist.clear();
        for (String cmd : config.getStringList("options.command-blacklist")) {
            if (cmd != null && !cmd.isBlank()) {
                this.commandBlacklist.add(cmd.toLowerCase(Locale.ROOT));
            }
        }

        loadMessages();
    }

    private void loadMessages() {
        this.prefix = config.getString("messages.prefix",
                "<gradient:#00C9FF:#92FE9D><bold>sᴛᴏɴᴇsɪᴛ</bold></gradient> <dark_gray>»</dark_gray>");
        this.parsedMessages.clear();

        var msgSection = config.getConfigurationSection("messages");
        if (msgSection != null) {
            for (String key : msgSection.getKeys(false)) {
                if ("prefix".equals(key)) continue;
                String raw = msgSection.getString(key, "");
                if (!raw.isEmpty()) {
                    String resolved = raw.replace("<prefix>", prefix);
                    parsedMessages.put(key, Text.line(resolved));
                }
            }
        }
    }

    public void sendMessage(org.bukkit.command.CommandSender sender, String key) {
        Component comp = parsedMessages.get(key);
        if (comp != null) {
            sender.sendMessage(comp);
        }
    }

    public void sendActionBar(Player player, String key) {
        Component comp = parsedMessages.get(key);
        if (comp != null) {
            player.sendActionBar(comp);
        }
    }

    public Component getMessage(String key, String defaultTemplate) {
        Component comp = parsedMessages.get(key);
        if (comp != null) return comp;
        return Text.line(defaultTemplate.replace("<prefix>", prefix));
    }

    private void fillSitMaterials() {
        sitMaterials.clear();
        sitBlockData.clear();
        List<String> list = config.getStringList("options.sit.sit-materials");
        for (String materialStr : list) {
            if (materialStr == null || materialStr.isBlank()) continue;
            try {
                SitMaterialDefinition def = parseSitMaterial(materialStr.trim());
                if (def.material.startsWith("#")) {
                    Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS,
                            Objects.requireNonNull(org.bukkit.NamespacedKey.fromString(def.material.substring(1))), Material.class);
                    if (tag != null) {
                        for (Material m : tag.getValues()) {
                            if (m.isBlock()) {
                                sitMaterials.put(m, def.yOffset);
                            }
                        }
                    }
                } else if (def.material.contains("[")) {
                    BlockData bd = Bukkit.createBlockData(def.material);
                    sitBlockData.put(bd, def.yOffset);
                } else {
                    Material m = Material.matchMaterial(def.material);
                    if (m != null && m.isBlock()) {
                        sitMaterials.put(m, def.yOffset);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private record SitMaterialDefinition(String material, double yOffset) {}

    private SitMaterialDefinition parseSitMaterial(String input) {
        int idx = input.lastIndexOf(';');
        if (idx != -1) {
            try {
                double offset = Double.parseDouble(input.substring(idx + 1).trim());
                return new SitMaterialDefinition(input.substring(0, idx).trim(), offset);
            } catch (NumberFormatException ignored) {
            }
        }
        return new SitMaterialDefinition(input, 0.0d);
    }

    private void fillMaterialBlacklist() {
        materialBlacklist.clear();
        for (String name : config.getStringList("options.material-blacklist")) {
            if (name == null || name.isBlank()) continue;
            try {
                if (name.startsWith("#")) {
                    Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS,
                            Objects.requireNonNull(org.bukkit.NamespacedKey.fromString(name.substring(1))), Material.class);
                    if (tag != null) {
                        for (Material m : tag.getValues()) {
                            if (m.isBlock()) materialBlacklist.add(m);
                        }
                    }
                } else {
                    Material m = Material.matchMaterial(name.trim());
                    if (m != null && m.isBlock()) {
                        materialBlacklist.add(m);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void fillBlockDataBlacklist() {
        blockDataBlacklist.clear();
        for (String dataStr : config.getStringList("options.block-data-blacklist")) {
            if (dataStr == null || dataStr.isBlank()) continue;
            try {
                blockDataBlacklist.add(Bukkit.createBlockData(dataStr.trim()));
            } catch (Throwable ignored) {
            }
        }
    }

    // Getter
    public FileConfiguration getConfig() { return config; }

    public boolean isGetUpDamage() { return getUpDamage; }
    public boolean isGetUpSneak() { return getUpSneak; }
    public boolean isGetUpReturn() { return getUpReturn; }
    public boolean isGetUpBreak() { return getUpBreak; }
    public boolean isAllowUnsafe() { return allowUnsafe; }
    public boolean isSameBlockRest() { return sameBlockRest; }
    public boolean isCenterBlock() { return centerBlock; }
    public boolean isCustomMessage() { return customMessage; }

    public Map<Material, Double> getSitMaterials() { return sitMaterials; }
    public Map<BlockData, Double> getSitBlockData() { return sitBlockData; }
    public boolean isBottomPartOnly() { return bottomPartOnly; }
    public boolean isSitEmptyHandOnly() { return sitEmptyHandOnly; }
    public double getSitMaxDistance() { return sitMaxDistance; }
    public double getSitMaxDistanceSquared() { return sitMaxDistanceSquared; }

    public boolean isPoseInteract() { return poseInteract; }
    public boolean isPoseLayRest() { return poseLayRest; }
    public boolean isPoseLaySnoringSounds() { return poseLaySnoringSounds; }
    public boolean isPoseLaySnoringNightOnly() { return poseLaySnoringNightOnly; }
    public boolean isPoseLayNightSkip() { return poseLayNightSkip; }
    public boolean isPoseLayWakeupMorning() { return poseLayWakeupMorning; }

    public boolean isPlayerSitAllow() { return playerSitAllow; }
    public boolean isPlayerSitAllowNpc() { return playerSitAllowNpc; }
    public long getPlayerSitMaxStack() { return playerSitMaxStack; }
    public boolean isPlayerSitSneakEjects() { return playerSitSneakEjects; }
    public boolean isPlayerSitBottomReturn() { return playerSitBottomReturn; }
    public boolean isPlayerSitEmptyHandOnly() { return playerSitEmptyHandOnly; }
    public double getPlayerSitMaxDistance() { return playerSitMaxDistance; }
    public double getPlayerSitMaxDistanceSquared() { return playerSitMaxDistanceSquared; }

    public boolean isTrustedRegionOnly() { return trustedRegionOnly; }
    public Set<String> getWorldBlacklist() { return worldBlacklist; }
    public Set<String> getWorldWhitelist() { return worldWhitelist; }
    public Set<Material> getMaterialBlacklist() { return materialBlacklist; }
    public List<BlockData> getBlockDataBlacklist() { return blockDataBlacklist; }
    public Set<String> getCommandBlacklist() { return commandBlacklist; }
    public String getPrefix() { return prefix; }
}
