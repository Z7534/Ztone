package de.Z7534.ztone.stoneattack.modules.leaderboard.hologram;

import de.Z7534.ztone.stoneattack.modules.leaderboard.LeaderboardType;
import de.Z7534.ztone.stoneattack.modules.leaderboard.TimePeriod;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Der GETEILTE Teil eines Leaderboards: alles, was fuer jeden Spieler
 * gleich aussieht (Hintergrund-Platten, Rahmen, Trennlinien, die runden
 * Button-Flaechen und die unsichtbaren
 * Klick-Flaechen). Diese Entities werden nur EINMAL pro Leaderboard
 * gespawnt - nicht mehr pro Spieler - und sind deshalb sehr guenstig.
 *
 * Pro Spieler unterschiedlich (Seite, Zeitraum, "Du"-Zeile) ist nur die
 * {@link HologramInstance}. Die gruene Markierung des aktiven Modus ist
 * hier je Zeitraum einmal vorhanden, standardmaessig unsichtbar, und wird
 * pro Spieler per showEntity/hideEntity ein- bzw. ausgeblendet.
 */
public class HologramBoard {

    private final LeaderboardType type;
    /** Die Masse, mit denen dieses Board gebaut wurde (Hoehe je Leaderboard unterschiedlich). */
    private final HologramLayout layout;
    private final List<Entity> entities = new ArrayList<>();
    private final Map<TimePeriod, List<Entity>> highlights = new EnumMap<>(TimePeriod.class);
    /** Je Button seine Flaeche als {u0, v0, breite, hoehe} - fuer die Hover-Erkennung. */
    private final Map<ClickBinding.Action, double[]> buttonAreas = new EnumMap<>(ClickBinding.Action.class);
    private final List<Entity> interactions = new ArrayList<>();

    public HologramBoard(LeaderboardType type, HologramLayout layout) {
        this.type = type;
        this.layout = layout;
    }

    public LeaderboardType getType() {
        return type;
    }

    public HologramLayout getLayout() {
        return layout;
    }

    public List<Entity> getEntities() {
        return entities;
    }

    public Map<TimePeriod, List<Entity>> getHighlights() {
        return highlights;
    }

    public Map<ClickBinding.Action, double[]> getButtonAreas() {
        return buttonAreas;
    }

    public List<Entity> getInteractions() {
        return interactions;
    }

    /** false, sobald auch nur eine Entity verschwunden ist (Chunk entladen, /kill ...) -> Board neu aufbauen. */
    public boolean isValid() {
        for (Entity entity : entities) {
            if (!entity.isValid()) {
                return false;
            }
        }
        return !entities.isEmpty();
    }
}
