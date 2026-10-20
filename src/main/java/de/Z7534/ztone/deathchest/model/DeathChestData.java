package de.Z7534.ztone.deathchest.model;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Objects;
import java.util.UUID;

public class DeathChestData {

    private final UUID id;
    private final UUID victimUuid;
    private final String victimName;
    private final UUID killerUuid;
    private final String killerName;

    private final Location primaryLocation;
    private final Location secondaryLocation;

    private final long createdAtMillis;
    private final int killerLockSeconds;
    private final int totalDespawnSeconds;

    private UUID textDisplayUuid;
    private final SavedBlockState originalState1;
    private final SavedBlockState originalState2;
    private final Inventory inventory;

    public DeathChestData(UUID id, UUID victimUuid, String victimName, UUID killerUuid, String killerName,
                          Location primaryLocation, Location secondaryLocation,
                          long createdAtMillis, int killerLockSeconds, int totalDespawnSeconds,
                          UUID textDisplayUuid, SavedBlockState originalState1, SavedBlockState originalState2) {
        this(id, victimUuid, victimName, killerUuid, killerName, primaryLocation, secondaryLocation,
                createdAtMillis, killerLockSeconds, totalDespawnSeconds, textDisplayUuid, originalState1, originalState2, null);
    }

    public DeathChestData(UUID id, UUID victimUuid, String victimName, UUID killerUuid, String killerName,
                          Location primaryLocation, Location secondaryLocation,
                          long createdAtMillis, int killerLockSeconds, int totalDespawnSeconds,
                          UUID textDisplayUuid, SavedBlockState originalState1, SavedBlockState originalState2,
                          Inventory inventory) {
        this.id = id != null ? id : UUID.randomUUID();
        this.victimUuid = victimUuid;
        this.victimName = victimName;
        this.killerUuid = killerUuid;
        this.killerName = killerName;
        this.primaryLocation = primaryLocation;
        this.secondaryLocation = secondaryLocation;
        this.createdAtMillis = createdAtMillis;
        this.killerLockSeconds = killerLockSeconds;
        this.totalDespawnSeconds = totalDespawnSeconds;
        this.textDisplayUuid = textDisplayUuid;
        this.originalState1 = originalState1;
        this.originalState2 = originalState2;
        this.inventory = inventory;
    }

    public boolean isKillerLocked() {
        if (killerUuid == null) return false;
        return System.currentTimeMillis() < (createdAtMillis + (killerLockSeconds * 1000L));
    }

    public boolean isKillerLockedFor(Player player) {
        if (!isKillerLocked()) return false;
        return player != null && player.getUniqueId().equals(killerUuid);
    }

    public long getRemainingKillerLockSeconds() {
        if (!isKillerLocked()) return 0L;
        long rem = (createdAtMillis + (killerLockSeconds * 1000L)) - System.currentTimeMillis();
        return Math.max(0L, rem / 1000L);
    }

    public long getRemainingDespawnSeconds() {
        long rem = (createdAtMillis + (totalDespawnSeconds * 1000L)) - System.currentTimeMillis();
        return Math.max(0L, rem / 1000L);
    }

    public boolean isExpired() {
        return getRemainingDespawnSeconds() <= 0L;
    }

    public boolean isChestBlock(Block block) {
        if (block == null) return false;
        return isSameBlock(block.getLocation(), primaryLocation) || isSameBlock(block.getLocation(), secondaryLocation);
    }

    public boolean isChestLocation(Location loc) {
        if (loc == null) return false;
        return isSameBlock(loc, primaryLocation) || isSameBlock(loc, secondaryLocation);
    }

    private boolean isSameBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        if (a.getWorld() == null || b.getWorld() == null) return false;
        return Objects.equals(a.getWorld().getUID(), b.getWorld().getUID())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    public Inventory getInventory() {
        if (inventory != null) {
            return inventory;
        }
        if (primaryLocation == null || primaryLocation.getWorld() == null) return null;
        Block b = primaryLocation.getBlock();
        if (b.getState() instanceof Chest chest) {
            return chest.getInventory();
        }
        if (secondaryLocation != null && secondaryLocation.getWorld() != null) {
            Block b2 = secondaryLocation.getBlock();
            if (b2.getState() instanceof Chest chest2) {
                return chest2.getInventory();
            }
        }
        return null;
    }

    public World getWorld() {
        return primaryLocation != null ? primaryLocation.getWorld() : null;
    }

    public UUID getId() { return id; }
    public UUID getVictimUuid() { return victimUuid; }
    public String getVictimName() { return victimName; }
    public UUID getKillerUuid() { return killerUuid; }
    public String getKillerName() { return killerName; }
    public Location getPrimaryLocation() { return primaryLocation; }
    public Location getSecondaryLocation() { return secondaryLocation; }
    public long getCreatedAtMillis() { return createdAtMillis; }
    public int getKillerLockSeconds() { return killerLockSeconds; }
    public int getTotalDespawnSeconds() { return totalDespawnSeconds; }
    public UUID getTextDisplayUuid() { return textDisplayUuid; }
    public void setTextDisplayUuid(UUID textDisplayUuid) { this.textDisplayUuid = textDisplayUuid; }
    public SavedBlockState getOriginalState1() { return originalState1; }
    public SavedBlockState getOriginalState2() { return originalState2; }
}
