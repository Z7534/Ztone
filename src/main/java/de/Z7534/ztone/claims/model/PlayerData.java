package de.Z7534.ztone.claims.model;

import java.util.UUID;

public final class PlayerData {

    private final UUID uuid;
    private String name;
    private int bonusClaimSlots;
    private long playtimeSeconds;
    private boolean boardLineEnabled = true;
    private boolean fenceLineEnabled = true;
    private boolean entryHintEnabled = true;
    private boolean explosionsEnabled = false;
    private boolean claimMapEnabled = true;
    private long lastSeen;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.lastSeen = System.currentTimeMillis();
    }

    /**
     * Unabhängige Kopie des aktuellen Stands. Wird für asynchrone Speichervorgänge
     * benutzt, damit der Datenbank-Thread nicht auf dem lebenden Objekt liest,
     * während der Server-Thread es gerade verändert.
     */
    public PlayerData copy() {
        PlayerData c = new PlayerData(uuid, name);
        c.bonusClaimSlots = bonusClaimSlots;
        c.playtimeSeconds = playtimeSeconds;
        c.boardLineEnabled = boardLineEnabled;
        c.fenceLineEnabled = fenceLineEnabled;
        c.entryHintEnabled = entryHintEnabled;
        c.explosionsEnabled = explosionsEnabled;
        c.claimMapEnabled = claimMapEnabled;
        c.lastSeen = lastSeen;
        return c;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int bonusClaimSlots() {
        return bonusClaimSlots;
    }

    public void setBonusClaimSlots(int bonusClaimSlots) {
        this.bonusClaimSlots = Math.max(0, bonusClaimSlots);
    }

    public long playtimeSeconds() {
        return playtimeSeconds;
    }

    public void setPlaytimeSeconds(long playtimeSeconds) {
        this.playtimeSeconds = playtimeSeconds;
    }

    public boolean boardLineEnabled() {
        return boardLineEnabled;
    }

    public void setBoardLineEnabled(boolean boardLineEnabled) {
        this.boardLineEnabled = boardLineEnabled;
    }

    public boolean fenceLineEnabled() {
        return fenceLineEnabled;
    }

    public void setFenceLineEnabled(boolean fenceLineEnabled) {
        this.fenceLineEnabled = fenceLineEnabled;
    }

    public boolean entryHintEnabled() {
        return entryHintEnabled;
    }

    public void setEntryHintEnabled(boolean entryHintEnabled) {
        this.entryHintEnabled = entryHintEnabled;
    }

    public boolean explosionsEnabled() {
        return explosionsEnabled;
    }

    public void setExplosionsEnabled(boolean explosionsEnabled) {
        this.explosionsEnabled = explosionsEnabled;
    }

    public boolean claimMapEnabled() {
        return claimMapEnabled;
    }

    public void setClaimMapEnabled(boolean claimMapEnabled) {
        this.claimMapEnabled = claimMapEnabled;
    }

    public long lastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }
}
