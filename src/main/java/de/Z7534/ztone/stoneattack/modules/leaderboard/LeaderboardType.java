package de.Z7534.ztone.stoneattack.modules.leaderboard;

/** Die vier unabhaengigen Leaderboard-Typen. */
public enum LeaderboardType {
    PLAYTIME("playtime"),
    KILLS("kills"),
    DEATHS("deaths"),
    MONEY("money");

    private final String key;

    LeaderboardType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static LeaderboardType fromKey(String key) {
        for (LeaderboardType type : values()) {
            if (type.key.equalsIgnoreCase(key)) {
                return type;
            }
        }
        return null;
    }
}
