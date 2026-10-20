package de.Z7534.ztone.clansystem.models;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

public class ClanMember {

    private UUID uuid;
    private int clanId;
    private int rankId;
    private long joinedAt;

    private ClanRank rank;

    private boolean leader;

    public ClanMember(UUID uuid, int clanId, int rankId, long joinedAt) {
        this.uuid = uuid;
        this.clanId = clanId;
        this.rankId = rankId;
        this.joinedAt = joinedAt;
    }

    public ClanMember(UUID uuid, int clanId, int rankId) {
        this(uuid, clanId, rankId, System.currentTimeMillis());
    }

    public UUID getUuid() {
        return uuid;
    }

    public int getClanId() {
        return clanId;
    }

    public void setClanId(int clanId) {
        this.clanId = clanId;
    }

    public int getRankId() {
        return rankId;
    }

    public void setRankId(int rankId) {
        this.rankId = rankId;
    }

    public long getJoinedAt() {
        return joinedAt;
    }

    public ClanRank getRank() {
        return rank;
    }

    public void setRank(ClanRank rank) {
        this.rank = rank;
        if (rank != null) {
            this.rankId = rank.getId();
        }
    }

    public boolean hasPermission(Permission permission) {
        return leader || (rank != null && rank.hasPermission(permission));
    }

    public boolean isLeader() {
        return leader;
    }

    public void setLeader(boolean leader) {
        this.leader = leader;
    }

    public Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    public OfflinePlayer getOfflinePlayer() {
        return Bukkit.getOfflinePlayer(uuid);
    }

    public String getName() {
        OfflinePlayer player = getOfflinePlayer();
        return player.getName() != null ? player.getName() : uuid.toString();
    }

    public boolean isOnline() {
        return getPlayer() != null;
    }

    public String getDisplayRankName() {
        if (leader) {
            return "Leader";
        }
        return rank != null ? rank.getName() : "?";
    }
}
