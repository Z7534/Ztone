package de.Z7534.ztone.stoneattack.modules.leaderboard;

/** Die drei umschaltbaren Zeitraeume je Hologramm. */
public enum TimePeriod {
    GLOBAL("global"),
    MONTH("month"),
    WEEK("week");

    private final String key;

    TimePeriod(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public TimePeriod next() {
        return switch (this) {
            case GLOBAL -> MONTH;
            case MONTH -> WEEK;
            case WEEK -> GLOBAL;
        };
    }
}
