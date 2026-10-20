package de.Z7534.ztone.deathchest.manager;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.claims.util.Text;
import de.Z7534.ztone.deathchest.config.DeathChestConfig;
import de.Z7534.ztone.deathchest.model.DeathChestData;
import de.Z7534.ztone.deathchest.model.DeathChestHolder;
import de.Z7534.ztone.deathchest.model.SavedBlockState;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DeathChestManager {

    public record BlockPos(UUID worldUid, int x, int y, int z) {
        public static BlockPos from(Location loc) {
            return new BlockPos(loc.getWorld().getUID(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        }

        public static BlockPos from(Block block) {
            return new BlockPos(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    private final Ztone plugin;
    private final DeathChestConfig config;

    private final Map<UUID, DeathChestData> chestsById = new ConcurrentHashMap<>();
    private final Map<BlockPos, DeathChestData> chestsByPos = new ConcurrentHashMap<>();

    private BukkitTask updateTask;

    public DeathChestManager(Ztone plugin, DeathChestConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    private void tick() {
        for (DeathChestData chest : new ArrayList<>(chestsById.values())) {
            // 1. Check expiration
            if (chest.isExpired()) {
                removeDeathChest(chest, true);
                Player victim = Bukkit.getPlayer(chest.getVictimUuid());
                if (victim != null && victim.isOnline()) {
                    victim.sendMessage(config.getMessage("chest-despawned"));
                }
                continue;
            }

            // 2. Early cleanup check: If inventory is empty
            Inventory inv = chest.getInventory();
            if (inv == null || isInventoryEmpty(inv)) {
                removeDeathChest(chest, true);
                continue;
            }

            // 3. Update TextDisplay Hologram
            updateHologram(chest);
        }
    }

    public void onChunkLoad(Chunk chunk) {
        if (chunk == null) return;
        World world = chunk.getWorld();
        int cx = chunk.getX();
        int cz = chunk.getZ();

        for (DeathChestData chest : chestsById.values()) {
            Location loc = chest.getPrimaryLocation();
            if (loc != null && world.equals(loc.getWorld())
                    && (loc.getBlockX() >> 4) == cx && (loc.getBlockZ() >> 4) == cz) {
                updateHologram(chest);
            }
        }
    }

    public DeathChestData createDeathChest(Player victim, Player killer, List<ItemStack> items) {
        UUID killerUuid = killer != null ? killer.getUniqueId() : null;
        String killerName = killer != null ? killer.getName() : null;
        return createDeathChest(victim.getUniqueId(), victim.getName(), victim.getLocation(), killerUuid, killerName, items);
    }

    public DeathChestData createDeathChest(Player victim, UUID killerUuid, String killerName, List<ItemStack> items) {
        return createDeathChest(victim.getUniqueId(), victim.getName(), victim.getLocation(), killerUuid, killerName, items);
    }

    public DeathChestData createDeathChest(UUID victimUuid, String victimName, Location deathLoc, UUID killerUuid, String killerName, List<ItemStack> items) {
        if (items == null || items.isEmpty() || deathLoc == null) {
            return null;
        }

        Location[] placement = findSafePlacement(deathLoc);
        if (placement == null) {
            return null;
        }

        Location loc1 = placement[0];
        Location loc2 = placement[1];
        Block b1 = loc1.getBlock();
        Block b2 = loc2.getBlock();

        // 1. Save original block states for anti-griefing
        SavedBlockState state1 = new SavedBlockState(b1);
        SavedBlockState state2 = new SavedBlockState(b2);

        // 2. Determine facing & left/right connection
        BlockFace facing = BlockFace.NORTH;
        if (b2.getX() > b1.getX()) {
            facing = BlockFace.NORTH;
        } else if (b2.getZ() > b1.getZ()) {
            facing = BlockFace.EAST;
        } else if (b2.getX() < b1.getX()) {
            facing = BlockFace.SOUTH;
        } else if (b2.getZ() < b1.getZ()) {
            facing = BlockFace.WEST;
        }

        boolean w1 = b1.getType() == Material.WATER || (b1.getBlockData() instanceof Waterlogged wl && wl.isWaterlogged());
        boolean w2 = b2.getType() == Material.WATER || (b2.getBlockData() instanceof Waterlogged wl && wl.isWaterlogged());

        b1.setType(Material.CHEST, false);
        b2.setType(Material.CHEST, false);

        org.bukkit.block.data.type.Chest data1 = (org.bukkit.block.data.type.Chest) Bukkit.createBlockData(Material.CHEST);
        data1.setFacing(facing);
        data1.setType(org.bukkit.block.data.type.Chest.Type.LEFT);
        if (w1) data1.setWaterlogged(true);
        b1.setBlockData(data1, false);

        org.bukkit.block.data.type.Chest data2 = (org.bukkit.block.data.type.Chest) Bukkit.createBlockData(Material.CHEST);
        data2.setFacing(facing);
        data2.setType(org.bukkit.block.data.type.Chest.Type.RIGHT);
        if (w2) data2.setWaterlogged(true);
        b2.setBlockData(data2, false);

        // CRITICAL ANTI-DUPE: Ensure physical chest tile entities in world are completely empty!
        // The chest blocks in world are purely visual dummies. Items are only held in the virtual inventory!
        try {
            if (b1.getState() instanceof InventoryHolder h) {
                h.getInventory().clear();
            }
            if (b2.getState() instanceof InventoryHolder h) {
                h.getInventory().clear();
            }
        } catch (Throwable ignored) {}

        // 3. Create dedicated 54-slot virtual inventory for complete items
        DeathChestHolder holder = new DeathChestHolder();
        Inventory deathInv = Bukkit.createInventory(holder, 54, Text.line("&4Todeskiste &8? &c" + victimName));
        holder.setInventory(deathInv);
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                deathInv.addItem(item);
            }
        }

        // 4. Create DeathChestData
        DeathChestData chestData = new DeathChestData(
                UUID.randomUUID(),
                victimUuid,
                victimName,
                killerUuid,
                killerName,
                loc1,
                loc2,
                System.currentTimeMillis(),
                config.getKillerLockSeconds(),
                config.getTotalDespawnSeconds(),
                null,
                state1,
                state2,
                deathInv
        );
        holder.setChestData(chestData);

        // 5. Spawn floating Paper TextDisplay hologram
        double midX = (b1.getX() + b2.getX()) / 2.0 + 0.5;
        double midY = Math.max(b1.getY(), b2.getY()) + 1.35;
        double midZ = (b1.getZ() + b2.getZ()) / 2.0 + 0.5;
        Location holoLoc = new Location(b1.getWorld(), midX, midY, midZ);

        cleanNearbyHolograms(holoLoc);
        TextDisplay display = b1.getWorld().spawn(holoLoc, TextDisplay.class, d -> {
            applyDisplaySettings(d, chestData);
        });
        chestData.setTextDisplayUuid(display.getUniqueId());

        // Atmospheric effects: Sounds & Particle circle
        World world = b1.getWorld();
        world.playSound(holoLoc, Sound.ENTITY_WITHER_SPAWN, 0.45f, 1.3f);
        world.playSound(holoLoc, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.7f, 0.85f);

        double radius = 1.3;
        int particleCount = 20;
        for (int i = 0; i < particleCount; i++) {
            double angle = 2 * Math.PI * i / particleCount;
            double px = midX + radius * Math.cos(angle);
            double pz = midZ + radius * Math.sin(angle);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, px, b1.getY() + 0.1, pz, 1, 0, 0, 0, 0.01);
        }
        world.spawnParticle(Particle.SMOKE, midX, b1.getY() + 0.5, midZ, 12, 0.5, 0.3, 0.5, 0.02);

        // 6. Register
        chestsById.put(chestData.getId(), chestData);
        chestsByPos.put(BlockPos.from(loc1), chestData);
        chestsByPos.put(BlockPos.from(loc2), chestData);

        // 7. Feedback message
        Player onlineVictim = Bukkit.getPlayer(victimUuid);
        if (onlineVictim != null && onlineVictim.isOnline()) {
            onlineVictim.sendMessage(config.getMessage("chest-created", Map.of(
                    "x", String.valueOf(b1.getX()),
                    "y", String.valueOf(b1.getY()),
                    "z", String.valueOf(b1.getZ())
            )));
        }

        return chestData;
    }

    private void applyDisplaySettings(TextDisplay d, DeathChestData chest) {
        d.setBillboard(Display.Billboard.CENTER);
        d.setAlignment(TextDisplay.TextAlignment.CENTER);
        d.setShadowed(true);
        d.setDefaultBackground(false);
        d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        d.setBrightness(new Display.Brightness(15, 15));
        d.setTextOpacity((byte) -1);
        d.setPersistent(false);
        d.addScoreboardTag("ztone_deathchest");
        d.addScoreboardTag("dc_" + chest.getId());
        d.text(buildHologramText(chest));
    }

    private void cleanNearbyHolograms(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        try {
            for (Entity e : loc.getWorld().getNearbyEntities(loc, 2.0, 2.0, 2.0)) {
                if (e instanceof TextDisplay td && td.getScoreboardTags().contains("ztone_deathchest")) {
                    td.remove();
                }
            }
        } catch (Throwable ignored) {}
    }

    public void removeDeathChest(DeathChestData chest, boolean restoreBlocks) {
        removeDeathChest(chest, restoreBlocks, null);
    }

    public void removeDeathChest(DeathChestData chest, boolean restoreBlocks, UUID skipViewerUuid) {
        if (chest == null) return;

        // Idempotency: If this chest was already removed from the registry, do not run again
        if (chestsById.remove(chest.getId()) == null) {
            return;
        }

        chestsByPos.remove(BlockPos.from(chest.getPrimaryLocation()));
        chestsByPos.remove(BlockPos.from(chest.getSecondaryLocation()));

        // Close viewer inventories safely - NEVER call closeInventory() on the viewer who triggered the close event!
        Inventory inv = chest.getInventory();
        if (inv != null) {
            for (HumanEntity viewer : new ArrayList<>(inv.getViewers())) {
                if (skipViewerUuid != null && viewer.getUniqueId().equals(skipViewerUuid)) {
                    continue; // Skip the player who is currently in the close event
                }
                try {
                    viewer.closeInventory();
                } catch (Throwable ignored) {}
            }
            inv.clear();
        }

        Location loc1 = chest.getPrimaryLocation();
        Location loc2 = chest.getSecondaryLocation();
        World world = chest.getWorld();

        if (world != null && loc1 != null) {
            boolean wasLoaded = loc1.isChunkLoaded();
            if (!wasLoaded) {
                try {
                    loc1.getChunk().load();
                } catch (Throwable ignored) {}
            }

            // CRITICAL ANTI-DUPE: Clear physical chest block inventories before block replacement/restoration
            // so Minecraft NEVER drops items on the ground when ChestBlock is replaced with air or original block!
            try {
                if (loc1.getBlock().getState() instanceof InventoryHolder h) {
                    h.getInventory().clear();
                }
            } catch (Throwable ignored) {}
            try {
                if (loc2 != null && loc2.getBlock().getState() instanceof InventoryHolder h) {
                    h.getInventory().clear();
                }
            } catch (Throwable ignored) {}

            // Remove TextDisplay
            if (chest.getTextDisplayUuid() != null) {
                Entity ent = Bukkit.getEntity(chest.getTextDisplayUuid());
                if (ent != null) {
                    ent.remove();
                }
            }

            double midX = (loc1.getX() + (loc2 != null ? loc2.getX() : loc1.getX())) / 2.0 + 0.5;
            double midY = Math.max(loc1.getY(), (loc2 != null ? loc2.getY() : loc1.getY())) + 1.35;
            double midZ = (loc1.getZ() + (loc2 != null ? loc2.getZ() : loc1.getZ())) / 2.0 + 0.5;
            Location holoLoc = new Location(world, midX, midY, midZ);
            cleanNearbyHolograms(holoLoc);

            // Despawn effects: Poof & Smoke + soft sound
            world.playSound(holoLoc, Sound.BLOCK_CHEST_CLOSE, 0.7f, 1.0f);
            world.playSound(holoLoc, Sound.ENTITY_CHICKEN_EGG, 0.7f, 0.6f);
            world.spawnParticle(Particle.POOF, midX, midY, midZ, 20, 0.4, 0.4, 0.4, 0.04);
            world.spawnParticle(Particle.SMOKE, midX, midY, midZ, 12, 0.3, 0.3, 0.3, 0.02);

            // Restore original blocks
            if (restoreBlocks) {
                if (chest.getOriginalState1() != null) chest.getOriginalState1().restore();
                if (chest.getOriginalState2() != null) chest.getOriginalState2().restore();
            }
        }
    }

    public int clearAll() {
        List<DeathChestData> all = new ArrayList<>(chestsById.values());
        int count = all.size();
        for (DeathChestData chest : all) {
            removeDeathChest(chest, true);
        }
        chestsById.clear();
        chestsByPos.clear();

        // Also sweep all loaded worlds for any orphan TextDisplays
        removeAllHolograms();

        File file = new File(plugin.getDataFolder(), "deathchest/chests.yml");
        if (file.exists()) {
            file.delete();
        }
        return count;
    }

    public void removeAllHolograms() {
        for (DeathChestData chest : chestsById.values()) {
            if (chest.getTextDisplayUuid() != null) {
                Entity ent = Bukkit.getEntity(chest.getTextDisplayUuid());
                if (ent != null) {
                    ent.remove();
                }
            }
        }
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof TextDisplay td && td.getScoreboardTags().contains("ztone_deathchest")) {
                    td.remove();
                }
            }
        }
    }

    private void updateHologram(DeathChestData chest) {
        if (chest.getWorld() == null || chest.getPrimaryLocation() == null) return;
        Location loc1 = chest.getPrimaryLocation();
        Location loc2 = chest.getSecondaryLocation();
        double midX = (loc1.getX() + (loc2 != null ? loc2.getX() : loc1.getX())) / 2.0 + 0.5;
        double midY = Math.max(loc1.getY(), (loc2 != null ? loc2.getY() : loc1.getY())) + 1.35;
        double midZ = (loc1.getZ() + (loc2 != null ? loc2.getZ() : loc1.getZ())) / 2.0 + 0.5;
        Location holoLoc = new Location(chest.getWorld(), midX, midY, midZ);

        if (!holoLoc.isChunkLoaded()) {
            return;
        }

        Entity entity = chest.getTextDisplayUuid() != null ? Bukkit.getEntity(chest.getTextDisplayUuid()) : null;
        if (entity instanceof TextDisplay display && display.isValid()) {
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setBillboard(Display.Billboard.CENTER);
            display.setShadowed(true);
            display.setDefaultBackground(false);
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            display.setBrightness(new Display.Brightness(15, 15));
            display.text(buildHologramText(chest));
        } else {
            cleanNearbyHolograms(holoLoc);
            TextDisplay display = chest.getWorld().spawn(holoLoc, TextDisplay.class, d -> {
                applyDisplaySettings(d, chest);
            });
            chest.setTextDisplayUuid(display.getUniqueId());
        }
    }

    public Component buildHologramText(DeathChestData chest) {
        Component line1 = Text.line(config.getHologramLine1().replace("{victim}", chest.getVictimName()));

        Component line2;
        if (chest.isKillerLocked()) {
            line2 = Text.line(config.getHologramLine2Locked()
                    .replace("{killer}", chest.getKillerName() != null ? chest.getKillerName() : "Killer")
                    .replace("{seconds}", String.valueOf(chest.getRemainingKillerLockSeconds())));
        } else {
            line2 = Text.line(config.getHologramLine2Unlocked());
        }

        long despawnSec = chest.getRemainingDespawnSeconds();
        long mins = despawnSec / 60;
        long secs = despawnSec % 60;
        Component line3 = Text.line(config.getHologramLine3()
                .replace("{minutes}", String.valueOf(mins))
                .replace("{seconds}", String.format("%02d", secs)));

        return line1.append(Component.newline()).append(line2).append(Component.newline()).append(line3);
    }

    public Location[] findSafePlacement(Location deathLoc) {
        World world = deathLoc.getWorld();
        if (world == null) return null;

        int minHeight = world.getMinHeight();
        int maxHeight = world.getMaxHeight();

        int startY = deathLoc.getBlockY();
        if (startY < minHeight) {
            int targetY = Math.max(minHeight + 1, -63);
            int highestSolid = -9999;
            for (int y = minHeight; y < Math.min(targetY + 40, maxHeight); y++) {
                Block b = world.getBlockAt(deathLoc.getBlockX(), y, deathLoc.getBlockZ());
                if (b.getType().isSolid()) {
                    highestSolid = y;
                }
            }
            if (highestSolid != -9999) {
                startY = highestSolid + 1;
            } else {
                startY = targetY;
            }
        } else if (startY >= maxHeight - 1) {
            startY = maxHeight - 2;
        }

        Location center = new Location(world, deathLoc.getBlockX(), startY, deathLoc.getBlockZ());
        BlockFace[] faces = new BlockFace[]{BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH};

        for (int r = 0; r <= 8; r++) {
            for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3, 4, -4}) {
                int y = startY + dy;
                if (y < minHeight || y >= maxHeight) continue;

                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;

                        Block b1 = world.getBlockAt(center.getBlockX() + dx, y, center.getBlockZ() + dz);
                        if (!isReplaceable(b1)) continue;
                        for (BlockFace face : faces) {
                            Block b2 = b1.getRelative(face);
                            if (isReplaceable(b2)) {
                                return new Location[]{b1.getLocation(), b2.getLocation()};
                            }
                        }
                    }
                }
            }
        }

        Block fallback1 = center.getBlock();
        Block fallback2 = fallback1.getRelative(BlockFace.EAST);
        return new Location[]{fallback1.getLocation(), fallback2.getLocation()};
    }

    private boolean isReplaceable(Block b) {
        if (b == null) return false;
        Material m = b.getType();
        if (m.isAir() || m == Material.WATER) return true;
        if (b.isPassable() && m != Material.LAVA) return true;
        return false;
    }

    public boolean isInventoryEmpty(Inventory inv) {
        if (inv == null) return true;
        for (ItemStack item : inv.getContents()) {
            if (item != null && !item.getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    public DeathChestData getDeathChestAt(Block block) {
        if (block == null) return null;
        return chestsByPos.get(BlockPos.from(block));
    }

    public DeathChestData getDeathChestAt(Location loc) {
        if (loc == null) return null;
        return chestsByPos.get(BlockPos.from(loc));
    }

    public boolean isDeathChest(Block block) {
        return getDeathChestAt(block) != null;
    }

    public boolean isKillerLockedFor(Block block, Player player) {
        DeathChestData data = getDeathChestAt(block);
        if (data == null) return false;
        return data.isKillerLockedFor(player);
    }

    public DeathChestData getDeathChestByInventory(Inventory inv) {
        if (inv == null) return null;
        if (inv.getHolder() instanceof DeathChestHolder holder) {
            return holder.getChestData();
        }
        for (DeathChestData data : chestsById.values()) {
            if (data.getInventory() == inv) {
                return data;
            }
        }
        InventoryHolder holder = inv.getHolder();
        if (holder instanceof DoubleChest dc) {
            if (dc.getLeftSide() instanceof Chest c) {
                DeathChestData data = getDeathChestAt(c.getBlock());
                if (data != null) return data;
            }
            if (dc.getRightSide() instanceof Chest c) {
                DeathChestData data = getDeathChestAt(c.getBlock());
                if (data != null) return data;
            }
        } else if (holder instanceof Chest c) {
            DeathChestData data = getDeathChestAt(c.getBlock());
            if (data != null) return data;
        }
        return null;
    }

    public void saveActiveChests() {
        if (!config.isPersistenceEnabled()) {
            for (DeathChestData chest : new ArrayList<>(chestsById.values())) {
                removeDeathChest(chest, true);
            }
            return;
        }

        File folder = new File(plugin.getDataFolder(), "deathchest");
        if (!folder.exists()) folder.mkdirs();
        File file = new File(folder, "chests.yml");

        if (chestsById.isEmpty()) {
            if (file.exists()) file.delete();
            return;
        }

        YamlConfiguration yaml = new YamlConfiguration();
        int index = 0;
        for (DeathChestData chest : chestsById.values()) {
            Inventory inv = chest.getInventory();
            if (inv == null || isInventoryEmpty(inv)) {
                continue;
            }

            String path = "chests." + index + ".";
            yaml.set(path + "id", chest.getId().toString());
            yaml.set(path + "victim-uuid", chest.getVictimUuid().toString());
            yaml.set(path + "victim-name", chest.getVictimName());
            yaml.set(path + "killer-uuid", chest.getKillerUuid() != null ? chest.getKillerUuid().toString() : null);
            yaml.set(path + "killer-name", chest.getKillerName());
            yaml.set(path + "world", chest.getWorld().getName());
            yaml.set(path + "x1", chest.getPrimaryLocation().getBlockX());
            yaml.set(path + "y1", chest.getPrimaryLocation().getBlockY());
            yaml.set(path + "z1", chest.getPrimaryLocation().getBlockZ());
            yaml.set(path + "x2", chest.getSecondaryLocation().getBlockX());
            yaml.set(path + "y2", chest.getSecondaryLocation().getBlockY());
            yaml.set(path + "z2", chest.getSecondaryLocation().getBlockZ());
            yaml.set(path + "created-at", chest.getCreatedAtMillis());
            yaml.set(path + "killer-lock-seconds", chest.getKillerLockSeconds());
            yaml.set(path + "total-despawn-seconds", chest.getTotalDespawnSeconds());

            SavedBlockState s1 = chest.getOriginalState1();
            if (s1 != null) {
                yaml.set(path + "s1.blockdata", s1.getBlockDataString());
                yaml.set(path + "s1.tiletype", s1.getTileType());
                yaml.set(path + "s1.extradata", s1.getExtraData());
            }

            SavedBlockState s2 = chest.getOriginalState2();
            if (s2 != null) {
                yaml.set(path + "s2.blockdata", s2.getBlockDataString());
                yaml.set(path + "s2.tiletype", s2.getTileType());
                yaml.set(path + "s2.extradata", s2.getExtraData());
            }

            List<ItemStack> nonAir = new ArrayList<>();
            for (ItemStack it : inv.getContents()) {
                if (it != null && !it.getType().isAir()) {
                    nonAir.add(it);
                }
            }
            yaml.set(path + "items", nonAir);
            index++;
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte Deathchests nicht speichern: " + e.getMessage());
        }
    }

    public void loadActiveChests() {
        File file = new File(plugin.getDataFolder(), "deathchest/chests.yml");
        if (!file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("chests");
        if (section == null) {
            file.delete();
            return;
        }

        for (String key : section.getKeys(false)) {
            String path = "chests." + key + ".";
            try {
                UUID id = UUID.fromString(yaml.getString(path + "id"));
                UUID victimUuid = UUID.fromString(yaml.getString(path + "victim-uuid"));
                String victimName = yaml.getString(path + "victim-name");
                String killerUuidStr = yaml.getString(path + "killer-uuid");
                UUID killerUuid = killerUuidStr != null ? UUID.fromString(killerUuidStr) : null;
                String killerName = yaml.getString(path + "killer-name");

                String worldName = yaml.getString(path + "world");
                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;

                int x1 = yaml.getInt(path + "x1");
                int y1 = yaml.getInt(path + "y1");
                int z1 = yaml.getInt(path + "z1");
                int x2 = yaml.getInt(path + "x2");
                int y2 = yaml.getInt(path + "y2");
                int z2 = yaml.getInt(path + "z2");

                Location loc1 = new Location(world, x1, y1, z1);
                Location loc2 = new Location(world, x2, y2, z2);

                long createdAt = yaml.getLong(path + "created-at");
                int killerLockSec = yaml.getInt(path + "killer-lock-seconds", config.getKillerLockSeconds());
                int totalDespawnSec = yaml.getInt(path + "total-despawn-seconds", config.getTotalDespawnSeconds());

                SavedBlockState s1 = new SavedBlockState(worldName, x1, y1, z1,
                        yaml.getString(path + "s1.blockdata"), yaml.getString(path + "s1.tiletype"), yaml.getString(path + "s1.extradata"));
                SavedBlockState s2 = new SavedBlockState(worldName, x2, y2, z2,
                        yaml.getString(path + "s2.blockdata"), yaml.getString(path + "s2.tiletype"), yaml.getString(path + "s2.extradata"));

                DeathChestHolder holder = new DeathChestHolder();
                Inventory deathInv = Bukkit.createInventory(holder, 54, Text.line("&4Todeskiste &8? &c" + victimName));
                holder.setInventory(deathInv);
                List<?> rawItems = yaml.getList(path + "items");
                if (rawItems != null) {
                    for (Object obj : rawItems) {
                        if (obj instanceof ItemStack is && !is.getType().isAir()) {
                            deathInv.addItem(is);
                        }
                    }
                }

                DeathChestData chestData = new DeathChestData(id, victimUuid, victimName, killerUuid, killerName,
                        loc1, loc2, createdAt, killerLockSec, totalDespawnSec, null, s1, s2, deathInv);
                holder.setChestData(chestData);

                if (chestData.isExpired() || isInventoryEmpty(deathInv)) {
                    s1.restore();
                    s2.restore();
                    continue;
                }

                // If chunk is loaded, ensure chest blocks are placed and their tile entity inventory is empty!
                if (loc1.isChunkLoaded()) {
                    Block b1 = loc1.getBlock();
                    Block b2 = loc2.getBlock();
                    if (b1.getType() != Material.CHEST) b1.setType(Material.CHEST, false);
                    if (b2.getType() != Material.CHEST) b2.setType(Material.CHEST, false);
                    if (b1.getState() instanceof InventoryHolder h) h.getInventory().clear();
                    if (b2.getState() instanceof InventoryHolder h) h.getInventory().clear();
                }

                chestsById.put(id, chestData);
                chestsByPos.put(BlockPos.from(loc1), chestData);
                chestsByPos.put(BlockPos.from(loc2), chestData);

                updateHologram(chestData);
            } catch (Exception e) {
                plugin.getLogger().warning("Fehler beim Laden einer Deathchest: " + e.getMessage());
            }
        }

        file.delete();
    }

    public Map<UUID, DeathChestData> getChestsById() { return chestsById; }
    public DeathChestConfig getConfig() { return config; }
}
