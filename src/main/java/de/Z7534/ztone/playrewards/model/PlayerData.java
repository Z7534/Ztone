package de.Z7534.ztone.playrewards.model;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerData {

    private final UUID uuid;
    private int currentStreak;
    private LocalDate lastStreakDate;
    private boolean streakClaimedToday;
    private int protectionPointsLeft;
    private LocalDate lastDailyDate;
    private final Set<String> claimedDailyTiers;
    private final Set<String> claimedPlaytimeTiers;
    private final Set<Integer> claimedStreakMilestones;
    private long localPlaytimeSeconds;
    private long dailyPlaytimeSeconds;
    private long sessionStartTime;
    private long lastActiveTime;
    private boolean isAfk;
    private long totalAfkSecondsInSession;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.currentStreak = 0;
        this.lastStreakDate = null;
        this.streakClaimedToday = false;
        this.protectionPointsLeft = 1;
        this.lastDailyDate = null;
        this.claimedDailyTiers = new HashSet<>();
        this.claimedPlaytimeTiers = new HashSet<>();
        this.claimedStreakMilestones = new HashSet<>();
        this.localPlaytimeSeconds = 0;
        this.dailyPlaytimeSeconds = 0;
        this.sessionStartTime = System.currentTimeMillis();
        this.lastActiveTime = System.currentTimeMillis();
        this.isAfk = false;
        this.totalAfkSecondsInSession = 0;
    }

    public UUID getUuid() {
        return uuid;
    }

    public int getCurrentStreak() {
        return currentStreak;
    }

    public void setCurrentStreak(int currentStreak) {
        this.currentStreak = currentStreak;
    }

    public LocalDate getLastStreakDate() {
        return lastStreakDate;
    }

    public void setLastStreakDate(LocalDate lastStreakDate) {
        this.lastStreakDate = lastStreakDate;
    }

    public boolean isStreakClaimedToday() {
        return streakClaimedToday;
    }

    public void setStreakClaimedToday(boolean streakClaimedToday) {
        this.streakClaimedToday = streakClaimedToday;
    }

    public int getProtectionPointsLeft() {
        return protectionPointsLeft;
    }

    public void setProtectionPointsLeft(int protectionPointsLeft) {
        this.protectionPointsLeft = protectionPointsLeft;
    }

    public LocalDate getLastDailyDate() {
        return lastDailyDate;
    }

    public void setLastDailyDate(LocalDate lastDailyDate) {
        this.lastDailyDate = lastDailyDate;
    }

    public Set<String> getClaimedDailyTiers() {
        return claimedDailyTiers;
    }

    public Set<String> getClaimedPlaytimeTiers() {
        return claimedPlaytimeTiers;
    }

    public Set<Integer> getClaimedStreakMilestones() {
        return claimedStreakMilestones;
    }

    public long getLocalPlaytimeSeconds() {
        return localPlaytimeSeconds;
    }

    public void setLocalPlaytimeSeconds(long localPlaytimeSeconds) {
        this.localPlaytimeSeconds = localPlaytimeSeconds;
    }

    public void addLocalPlaytimeSeconds(long seconds) {
        this.localPlaytimeSeconds += seconds;
    }

    public long getDailyPlaytimeSeconds() {
        return dailyPlaytimeSeconds;
    }

    public void setDailyPlaytimeSeconds(long dailyPlaytimeSeconds) {
        this.dailyPlaytimeSeconds = dailyPlaytimeSeconds;
    }

    public void addDailyPlaytimeSeconds(long seconds) {
        this.dailyPlaytimeSeconds += seconds;
    }

    public long getSessionStartTime() {
        return sessionStartTime;
    }

    public void resetSessionStartTime() {
        this.sessionStartTime = System.currentTimeMillis();
    }

    public long getLastActiveTime() {
        return lastActiveTime;
    }

    public void setLastActiveTime(long lastActiveTime) {
        this.lastActiveTime = lastActiveTime;
    }

    public boolean isAfk() {
        return isAfk;
    }

    public void setAfk(boolean afk) {
        isAfk = afk;
    }

    public long getTotalAfkSecondsInSession() {
        return totalAfkSecondsInSession;
    }

    public void addAfkSeconds(long seconds) {
        this.totalAfkSecondsInSession += seconds;
    }
}
