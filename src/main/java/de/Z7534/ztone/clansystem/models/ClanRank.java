package de.Z7534.ztone.clansystem.models;

import java.util.HashSet;
import java.util.Set;

public class ClanRank {

    private int id;
    private int clanId;
    private String name;
    private int priority;
    private Set<Permission> permissions;

    public ClanRank(int id, int clanId, String name, int priority) {
        this.id = id;
        this.clanId = clanId;
        this.name = name;
        this.priority = priority;
        this.permissions = new HashSet<>();
    }

    public ClanRank(String name, int priority) {
        this(-1, -1, name, priority);
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

    public void setClanId(int clanId) {
        this.clanId = clanId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }

    public void addPermission(Permission permission) {
        permissions.add(permission);
    }

    public void removePermission(Permission permission) {
        permissions.remove(permission);
    }

    public void togglePermission(Permission permission) {
        if (hasPermission(permission)) {
            removePermission(permission);
        } else {
            addPermission(permission);
        }
    }

    public String permissionsToString() {
        StringBuilder sb = new StringBuilder();
        for (Permission perm : permissions) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(perm.name());
        }
        return sb.toString();
    }

    public void permissionsFromString(String str) {
        permissions.clear();
        if (str == null || str.isEmpty()) {
            return;
        }
        String[] parts = str.split(",");
        for (String part : parts) {
            Permission perm = Permission.fromString(part.trim());
            if (perm != null) {
                permissions.add(perm);
            }
        }
    }

    public boolean isHigherThan(ClanRank other) {
        return this.priority > other.priority;
    }

    public boolean isHigherOrEqual(ClanRank other) {
        return this.priority >= other.priority;
    }
}
