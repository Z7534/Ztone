package de.Z7534.ztone.claims.model;

public enum ClaimPermission {

    BUILD("Blöcke abbauen und platzieren"),
    BUCKETS("Eimer mit Wasser und Lava platzieren oder aufheben"),
    CONTAINERS("Container, Werkbänke und Co. benutzen"),
    DOORS("Türen, Zäune, Falltüren benutzen"),
    REDSTONE("Hebel, Knöpfe, Druckplatten benutzen"),
    ITEMS("Items droppen und aufheben"),
    ATTACK_MOBS("Tiere, Monster, Dorfbewohner und Co. angreifen"),
    PVP("PvP"),
    VEHICLES("Boote, Loren, Pferde und Co. benutzen"),
    ENTITY_INTERACT("Rahmen, Armorstands, handeln - mit Entities interagieren"),
    FIRE_EXPLOSIONS("Feuer legen und TNT zünden"),
    BELLS_NOTEBLOCKS("Glocken, Notenblöcke und Co. verwenden"),
    SPAWNER("Spawner benutzen und verwalten");

    private final String description;

    ClaimPermission(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public static final int ALL_MASK;
    public static final int NONE_MASK = 0;

    static {
        int mask = 0;
        for (ClaimPermission p : values()) {
            mask |= p.bit();
        }
        ALL_MASK = mask;
    }

    public static boolean has(int mask, ClaimPermission permission) {
        return (mask & permission.bit()) != 0;
    }

    public static int with(int mask, ClaimPermission permission, boolean value) {
        return value ? (mask | permission.bit()) : (mask & ~permission.bit());
    }
}
