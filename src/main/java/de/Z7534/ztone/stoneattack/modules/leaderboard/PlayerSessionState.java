package de.Z7534.ztone.stoneattack.modules.leaderboard;

/** Auswahl eines Spielers fuer EIN Hologramm (Zeitraum + Seite). Nur pro Session, nichts persistent. */
public class PlayerSessionState {
    private volatile TimePeriod period = TimePeriod.GLOBAL;
    private volatile int page = 1;

    public TimePeriod getPeriod() {
        return period;
    }

    public void setPeriod(TimePeriod period) {
        this.period = period;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(1, page);
    }

    public void reset() {
        this.period = TimePeriod.GLOBAL;
        this.page = 1;
    }
}
