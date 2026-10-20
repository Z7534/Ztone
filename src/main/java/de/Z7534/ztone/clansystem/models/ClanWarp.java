package de.Z7534.ztone.clansystem.models;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class ClanWarp {

    private int id;
    private int clanId;
    private String name;
    private String worldName;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private boolean visibleToAllies;

    public ClanWarp(int id, int clanId, String name, String worldName,
                    double x, double y, double z, float yaw, float pitch,
                    boolean visibleToAllies) {
        this.id = id;
        this.clanId = clanId;
        this.name = name;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.visibleToAllies = visibleToAllies;
    }

    public ClanWarp(int clanId, String name, Location location, boolean visibleToAllies) {
        this(-1, clanId, name,
                location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(),
                visibleToAllies);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getClanId() {
        return clanId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWorldName() {
        return worldName;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public boolean isVisibleToAllies() {
        return visibleToAllies;
    }

    public void setVisibleToAllies(boolean visibleToAllies) {
        this.visibleToAllies = visibleToAllies;
    }

    public Location getLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z, yaw, pitch);
    }

    public void setLocation(Location location) {
        this.worldName = location.getWorld().getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
    }
}
