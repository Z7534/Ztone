package de.Z7534.ztone.stonesit.model;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

public final class Seat {

    private Block block;
    private Location location;
    private final LivingEntity entity;
    private final Entity seatEntity;
    private final Location returnLocation;
    private final long spawnTimeNano = System.nanoTime();

    public Seat(Block block, Location location, LivingEntity entity, Entity seatEntity, Location returnLocation) {
        this.block = block;
        this.location = location;
        this.entity = entity;
        this.seatEntity = seatEntity;
        this.returnLocation = returnLocation;
    }

    public Block getBlock() {
        return block;
    }

    public void setBlock(Block block) {
        this.block = block;
    }

    public Location getLocation() {
        return location.clone();
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public Entity getSeatEntity() {
        return seatEntity;
    }

    public Location getReturnLocation() {
        return returnLocation.clone();
    }

    public long getLifetimeInNanoSeconds() {
        return System.nanoTime() - spawnTimeNano;
    }
}
