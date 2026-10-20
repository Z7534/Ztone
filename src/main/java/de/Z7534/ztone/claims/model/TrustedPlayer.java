package de.Z7534.ztone.claims.model;

import java.util.UUID;

public final class TrustedPlayer {

    private final UUID uuid;
    private int permissionMask;

    public TrustedPlayer(UUID uuid, int permissionMask) {
        this.uuid = uuid;
        this.permissionMask = permissionMask;
    }

    public UUID uuid() {
        return uuid;
    }

    public int permissionMask() {
        return permissionMask;
    }

    public void setPermissionMask(int mask) {
        this.permissionMask = mask;
    }

    public boolean has(ClaimPermission permission) {
        return ClaimPermission.has(permissionMask, permission);
    }

    public void set(ClaimPermission permission, boolean value) {
        permissionMask = ClaimPermission.with(permissionMask, permission, value);
    }
}
