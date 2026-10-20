package de.Z7534.ztone.clansystem.models;

import java.util.UUID;

public class ClanBankTransaction {

    public enum Type {
        DEPOSIT,
        WITHDRAW
    }

    public enum TransactionType {
        DEPOSIT,
        WITHDRAW
    }

    private final int id;
    private final int clanId;
    private final UUID playerUuid;
    private final String playerName;
    private final Type type;
    private final double amount;
    private final long timestamp;

    public ClanBankTransaction(int clanId, UUID playerUuid, String playerName, Type type, double amount, long timestamp) {
        this(-1, clanId, playerUuid, playerName, type, amount, timestamp);
    }

    public ClanBankTransaction(int clanId, UUID playerUuid, String playerName, TransactionType transType, double amount, long timestamp) {
        this(-1, clanId, playerUuid, playerName, Type.valueOf(transType.name()), amount, timestamp);
    }

    public ClanBankTransaction(int id, int clanId, UUID playerUuid, String playerName, Type type, double amount, long timestamp) {
        this.id = id;
        this.clanId = clanId;
        this.playerUuid = playerUuid;
        this.playerName = playerName != null ? playerName : "Unbekannt";
        this.type = type;
        this.amount = amount;
        this.timestamp = timestamp;
    }

    public int getId() {
        return id;
    }

    public int getClanId() {
        return clanId;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public Type getType() {
        return type;
    }

    public double getAmount() {
        return amount;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
