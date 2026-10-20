package de.Z7534.ztone.claims.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Claim {

    private long id;
    private UUID owner;
    private String name;
    private final String world;
    private final Set<ChunkPos> chunks = new HashSet<>();
    private final Map<UUID, TrustedPlayer> trusted = new HashMap<>();
    private final Set<UUID> banned = new HashSet<>();
    private int visitorPermissionMask = ClaimPermission.NONE_MASK;
    private boolean clanTrustEnabled = false;
    private boolean allyTrustEnabled = false;
    private final Set<UUID> managers = new HashSet<>();
    private long createdAt;

    public Claim(long id, UUID owner, String name, String world) {
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.world = world;
        this.createdAt = System.currentTimeMillis();
    }

    public long id() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public UUID owner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String world() {
        return world;
    }

    public Set<ChunkPos> chunks() {
        return chunks;
    }

    public Map<UUID, TrustedPlayer> trusted() {
        return trusted;
    }

    public Set<UUID> banned() {
        return banned;
    }

    public int visitorPermissionMask() {
        return visitorPermissionMask;
    }

    public void setVisitorPermissionMask(int mask) {
        this.visitorPermissionMask = mask;
    }

    public long createdAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isClanTrustEnabled() {
        return clanTrustEnabled;
    }

    public void setClanTrustEnabled(boolean enabled) {
        this.clanTrustEnabled = enabled;
    }

    public boolean isAllyTrustEnabled() {
        return allyTrustEnabled;
    }

    public void setAllyTrustEnabled(boolean enabled) {
        this.allyTrustEnabled = enabled;
    }

    public Set<UUID> managers() {
        return managers;
    }

    public boolean isManager(UUID uuid) {
        return managers.contains(uuid);
    }

    public boolean isOwnerOrManager(UUID uuid) {
        return isOwner(uuid) || isManager(uuid);
    }

    public boolean isTrusted(UUID uuid) {
        return trusted.containsKey(uuid);
    }

    /**
     * Der Besitzer kann nie von seinem eigenen Claim gebannt sein - sonst würde er sich
     * z.B. per /staffclaim ban oder nach einer Übertragung selbst aussperren.
     */
    public boolean isBanned(UUID uuid) {
        return !isOwner(uuid) && banned.contains(uuid);
    }

    public boolean isOwner(UUID uuid) {
        return owner.equals(uuid);
    }

    public boolean hasPermission(UUID uuid, ClaimPermission permission) {
        // Claim-weites PvP: Ist PvP in den Claim-Einstellungen ausgeschaltet,
        // gilt das absolut für alle Spieler im Claim (auch Besitzer, Manager & Trusts).
        if (permission == ClaimPermission.PVP && !ClaimPermission.has(visitorPermissionMask, ClaimPermission.PVP)) {
            return false;
        }
        if (isOwner(uuid) || isManager(uuid)) {
            return true;
        }
        if (isBanned(uuid)) {
            return false;
        }
        TrustedPlayer trust = trusted.get(uuid);
        if (trust != null) {
            return trust.has(permission);
        }
        if (clanTrustEnabled && de.Z7534.ztone.Ztone.getInstance() != null && de.Z7534.ztone.Ztone.getInstance().isSameClan(owner, uuid)) {
            return true;
        }
        if (allyTrustEnabled && de.Z7534.ztone.Ztone.getInstance() != null && de.Z7534.ztone.Ztone.getInstance().isAlly(owner, uuid)) {
            return true;
        }
        return ClaimPermission.has(visitorPermissionMask, permission);
    }

    public int[] boundingBox() {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (ChunkPos c : chunks) {
            minX = Math.min(minX, c.x());
            maxX = Math.max(maxX, c.x());
            minZ = Math.min(minZ, c.z());
            maxZ = Math.max(maxZ, c.z());
        }
        if (chunks.isEmpty()) {
            return new int[]{0, 0, 0, 0, 0, 0};
        }
        int width = maxX - minX + 1;
        int height = maxZ - minZ + 1;
        return new int[]{minX, minZ, maxX, maxZ, width, height};
    }

    public int areaBlocks() {
        return chunks.size() * 256;
    }

    public int[] cornerBlockCoords() {
        int[] box = boundingBox();
        return new int[]{box[0] << 4, box[1] << 4};
    }
}
