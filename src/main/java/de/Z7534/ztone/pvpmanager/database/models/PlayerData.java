package de.Z7534.ztone.pvpmanager.database.models;

import java.util.UUID;

public class PlayerData {

    private final UUID uuid;
    private boolean pvpEnabled;
    private boolean teleportOverride;

    public PlayerData(UUID uuid, boolean pvpEnabled, boolean teleportOverride) {
        this.uuid = uuid;
        this.pvpEnabled = pvpEnabled;
        this.teleportOverride = teleportOverride;
    }

    public static PlayerData createDefault(UUID uuid, boolean defaultPvp) {
        return new PlayerData(uuid, defaultPvp, false);
    }

    public UUID getUuid() {
        return uuid;
    }

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    public void setPvpEnabled(boolean pvpEnabled) {
        this.pvpEnabled = pvpEnabled;
    }

    public boolean isTeleportOverride() {
        return teleportOverride;
    }

    public void setTeleportOverride(boolean teleportOverride) {
        this.teleportOverride = teleportOverride;
    }
}
