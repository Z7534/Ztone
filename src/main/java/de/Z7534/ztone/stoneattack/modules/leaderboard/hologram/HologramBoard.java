package de.Z7534.ztone.stoneattack.modules.leaderboard.hologram;

import de.Z7534.ztone.stoneattack.modules.leaderboard.LeaderboardType;
import de.Z7534.ztone.stoneattack.modules.leaderboard.TimePeriod;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Der ZENTRALE, fuer alle Spieler gleiche Teil eines Leaderboards:
 * Hintergrund-ItemDisplays (oder Fallback-Flaeche), Modus-Hervorhebungsstreifen
 * und die unsichtbaren Klick-Entities (Interactions) fuer Buttons/Pfeile.
 */
public class HologramBoard {

    public static final double BACKGROUND_WIDTH = 3.0;

    private final LeaderboardType type;
    /** Die Masse, mit denen dieses Board gebaut wurde. */
    private final HologramLayout layout;
    private final BoardFrame frame;
    private final List<Entity> entities = new ArrayList<>();
    private final Map<TimePeriod, List<Entity>> highlights = new EnumMap<>(TimePeriod.class);
    /** Je Button seine Flaeche als {u0, v0, breite, hoehe} - fuer die Hover-Erkennung. */
    private final Map<ClickBinding.Action, double[]> buttonAreas = new EnumMap<>(ClickBinding.Action.class);
    private final List<Entity> interactions = new ArrayList<>();

    /**
     * Modus A: modularer Custom-Hintergrund (Kopf + N Reihen + Fueller + Fuss),
     * jedes Stueck ein eigenes ItemDisplay aus dem Resourcepack.
     */
    private final List<ItemDisplay> packBackgrounds = new ArrayList<>();
    /** Modus B: Prozedurales Hintergrundsystem (Dark-Grey/Aqua Boxen) */
    private final List<Entity> fallbackEntities = new ArrayList<>();

    public HologramBoard(LeaderboardType type, HologramLayout layout, BoardFrame frame) {
        this.type = type;
        this.layout = layout;
        this.frame = frame;
    }

    public LeaderboardType getType() {
        return type;
    }

    public HologramLayout getLayout() {
        return layout;
    }

    public BoardFrame getFrame() {
        return frame;
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

    public List<ItemDisplay> getPackBackgrounds() {
        return packBackgrounds;
    }

    public void addPackBackground(ItemDisplay piece) {
        if (piece != null) {
            packBackgrounds.add(piece);
        }
    }

    public List<Entity> getFallbackEntities() {
        return fallbackEntities;
    }

    /** false, sobald auch nur eine Entity verschwunden ist (Chunk entladen, /kill ...) -> Board neu aufbauen. */
    public boolean isValid() {
        for (Entity entity : entities) {
            if (entity == null || !entity.isValid()) {
                return false;
            }
        }
        return !entities.isEmpty();
    }
}
