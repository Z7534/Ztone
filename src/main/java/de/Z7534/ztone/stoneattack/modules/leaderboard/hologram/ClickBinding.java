package de.Z7534.ztone.stoneattack.modules.leaderboard.hologram;

import de.Z7534.ztone.stoneattack.modules.leaderboard.LeaderboardType;

/**
 * Ordnet einer (unsichtbaren) Interaction-Entity eine Aktion zu. Die
 * Interaction-Entities eines Boards sind fuer alle Spieler dieselben
 * (geteilt) - wer geklickt hat, ergibt sich erst aus dem Klick-Event.
 */
public class ClickBinding {

    public enum Action {
        GLOBAL_BUTTON, MONTH_BUTTON, WEEK_BUTTON, ARROW_LEFT, ARROW_RIGHT
    }

    private final LeaderboardType type;
    private final Action action;

    public ClickBinding(LeaderboardType type, Action action) {
        this.type = type;
        this.action = action;
    }

    public LeaderboardType getType() {
        return type;
    }

    public Action getAction() {
        return action;
    }
}
