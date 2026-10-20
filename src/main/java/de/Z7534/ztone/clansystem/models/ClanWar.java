package de.Z7534.ztone.clansystem.models;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClanWar {

    public enum WarStatus {
        ACTIVE,
        ENDED
    }

    private int id;
    private int attackerClanId;
    private int defenderClanId;
    private WarStatus status;
    private long startedAt;
    private long endedAt;
    private Integer winnerClanId;

    private Map<UUID, Integer> attackerKills;
    private Map<UUID, Integer> defenderKills;
    private Map<UUID, Integer> participantDeaths;

    private int attackerPoints;
    private int defenderPoints;

    public ClanWar(int id, int attackerClanId, int defenderClanId,
                   WarStatus status, long startedAt, long endedAt, Integer winnerClanId) {
        this.id = id;
        this.attackerClanId = attackerClanId;
        this.defenderClanId = defenderClanId;
        this.status = status;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.winnerClanId = winnerClanId;
        this.attackerKills = new HashMap<>();
        this.defenderKills = new HashMap<>();
        this.participantDeaths = new HashMap<>();
        this.attackerPoints = 0;
        this.defenderPoints = 0;
    }

    public ClanWar(int attackerClanId, int defenderClanId) {
        this(-1, attackerClanId, defenderClanId, WarStatus.ACTIVE,
                System.currentTimeMillis(), 0, null);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getAttackerClanId() {
        return attackerClanId;
    }

    public int getDefenderClanId() {
        return defenderClanId;
    }

    public WarStatus getStatus() {
        return status;
    }

    public void setStatus(WarStatus status) {
        this.status = status;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public long getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(long endedAt) {
        this.endedAt = endedAt;
    }

    public Integer getWinnerClanId() {
        return winnerClanId;
    }

    public void setWinnerClanId(Integer winnerClanId) {
        this.winnerClanId = winnerClanId;
    }

    public boolean isActive() {
        return status == WarStatus.ACTIVE;
    }

    public boolean involves(int clanId) {
        return attackerClanId == clanId || defenderClanId == clanId;
    }

    public int getOpponent(int clanId) {
        if (attackerClanId == clanId) {
            return defenderClanId;
        } else if (defenderClanId == clanId) {
            return attackerClanId;
        }
        return -1;
    }

    public boolean isAttacker(int clanId) {
        return attackerClanId == clanId;
    }

    public boolean isDefender(int clanId) {
        return defenderClanId == clanId;
    }

    public int getAttackerPoints() {
        return attackerPoints;
    }

    public int getDefenderPoints() {
        return defenderPoints;
    }

    public void addKill(UUID killer, int killerClanId, int points) {
        if (killerClanId == attackerClanId) {
            attackerKills.merge(killer, 1, Integer::sum);
            attackerPoints += points;
        } else if (killerClanId == defenderClanId) {
            defenderKills.merge(killer, 1, Integer::sum);
            defenderPoints += points;
        }
    }

    public int getTotalAttackerKills() {
        return attackerKills.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int getTotalDefenderKills() {
        return defenderKills.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int getParticipantDeaths(UUID uuid) {
        return participantDeaths.getOrDefault(uuid, 0);
    }

    public int addParticipantDeath(UUID uuid) {
        int d = getParticipantDeaths(uuid) + 1;
        participantDeaths.put(uuid, d);
        return d;
    }

    public void setParticipantDeaths(UUID uuid, int count) {
        participantDeaths.put(uuid, count);
    }

    public Map<UUID, Integer> getParticipantDeaths() {
        return participantDeaths;
    }

    public int getKillsForPlayer(UUID uuid) {
        int kills = 0;
        if (attackerKills.containsKey(uuid)) {
            kills += attackerKills.get(uuid);
        }
        if (defenderKills.containsKey(uuid)) {
            kills += defenderKills.get(uuid);
        }
        return kills;
    }

    public void end(Integer winnerId) {
        this.status = WarStatus.ENDED;
        this.endedAt = System.currentTimeMillis();
        this.winnerClanId = winnerId;
    }

    public Integer determineWinner() {
        if (attackerPoints > defenderPoints) {
            return attackerClanId;
        } else if (defenderPoints > attackerPoints) {
            return defenderClanId;
        }
        return null;
    }
}
