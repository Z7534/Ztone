package de.Z7534.ztone.deathchest.model;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;

public class SavedBlockState {

    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private final String blockDataString;
    private final String tileType;
    private final String extraData;
    private transient BlockState capturedState;

    public SavedBlockState(Block block) {
        this.worldName = block.getWorld().getName();
        this.x = block.getX();
        this.y = block.getY();
        this.z = block.getZ();
        this.capturedState = block.getState();
        this.blockDataString = block.getBlockData().getAsString();

        if (this.capturedState instanceof CreatureSpawner spawner) {
            this.tileType = "SPAWNER";
            this.extraData = spawner.getSpawnedType() != null ? spawner.getSpawnedType().name() : null;
        } else {
            this.tileType = null;
            this.extraData = null;
        }
    }

    public SavedBlockState(String worldName, int x, int y, int z, String blockDataString, String tileType, String extraData) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockDataString = blockDataString;
        this.tileType = tileType;
        this.extraData = extraData;
        this.capturedState = null;
    }

    public void restore() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        Block block = world.getBlockAt(x, y, z);
        if (capturedState != null) {
            try {
                capturedState.update(true, false);
                return;
            } catch (Throwable ignored) {
            }
        }

        // Fallback using blockDataString and tileType
        try {
            block.setBlockData(Bukkit.createBlockData(blockDataString), false);
            if ("SPAWNER".equalsIgnoreCase(tileType) && extraData != null) {
                if (block.getState() instanceof CreatureSpawner spawner) {
                    try {
                        spawner.setSpawnedType(EntityType.valueOf(extraData));
                        spawner.update(true, false);
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public String getWorldName() { return worldName; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public String getBlockDataString() { return blockDataString; }
    public String getTileType() { return tileType; }
    public String getExtraData() { return extraData; }
    public BlockState getCapturedState() { return capturedState; }
    public void setCapturedState(BlockState state) { this.capturedState = state; }
}
