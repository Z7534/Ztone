package de.Z7534.ztone.stoneattack.modules.leaderboard.hologram;

import de.Z7534.ztone.stoneattack.Stoneattack;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Erzeugt die Hologramm-Bausteine (TextDisplay / Interaction) ueber die
 * normale Paper-API.
 *
 * Alle Displays haben Billboard FIXED: Sie drehen sich NIE mit dem Blick des
 * Spielers, sondern zeigen konstant in die Richtung, in die ihr Yaw (aus dem
 * {@link BoardFrame}) zeigt.
 *
 * Geteilte Entities (Platten, Beschriftungen ...) sind standardmaessig fuer
 * alle sichtbar. Entities, die nur fuer einen Spieler sind, werden mit
 * {@code visibleByDefault = false} gespawnt und per {@link Player#showEntity}
 * genau diesem einen Spieler gezeigt.
 *
 * Alle Methoden muessen auf dem Main-Thread aufgerufen werden.
 */
public class DisplayFactory {

    public static final String ENTITY_TAG = "stoneattack_hologram";

    private final Stoneattack plugin;

    /** Entity-ID -> Entity (alle von dieser Factory erzeugten). */
    private final Map<Integer, Entity> entities = new HashMap<>();
    /** Zuletzt gesetzter Inhalt je Entity, damit Unveraendertes nicht jede Sekunde neu gesendet wird. */
    private final Map<Integer, Object> lastState = new HashMap<>();

    public DisplayFactory(Stoneattack plugin) {
        this.plugin = plugin;
    }

    // ----------------------------------------------------------------
    // Spawnen
    // ----------------------------------------------------------------

    private <T extends Entity> T spawn(Location loc, Class<T> type, Player viewer, boolean visibleByDefault,
                                       Consumer<T> init) {
        World world = loc.getWorld();
        T entity = world.spawn(loc, type, e -> {
            e.setVisibleByDefault(visibleByDefault);
            e.setPersistent(false);
            e.addScoreboardTag(ENTITY_TAG);
            init.accept(e);
        });
        if (viewer != null) {
            viewer.showEntity(plugin.getPlugin(), entity);
        }
        entities.put(entity.getEntityId(), entity);
        return entity;
    }

    /** Gemeinsame Einstellungen aller Displays (Ausrichtung fix, Sichtweite, Helligkeit). */
    private void applyCommon(Display display, float scaleX, float scaleY, float scaleZ) {
        FileConfiguration cfg = plugin.getConfigManager().get();
        display.setBillboard(Display.Billboard.FIXED);
        display.setViewRange(viewRange(cfg));
        if (cfg.getBoolean("leaderboard.render.fullbright", true)) {
            display.setBrightness(new Display.Brightness(15, 15));
        }
        display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                new Vector3f(scaleX, scaleY, scaleZ), new AxisAngle4f()));
    }

    /** Die Sichtweite eines Displays wird in Vielfachen von 64 Bloecken angegeben; die Config gibt Bloecke an. */
    private float viewRange(FileConfiguration cfg) {
        double blocks = cfg.getDouble("leaderboard.render.view-distance-blocks", 32);
        return (float) Math.max(1.0, blocks / 64.0);
    }

    /**
     * Ein Text-Display (Drehpunkt = Mitte der Unterkante) OHNE eigenen Hintergrund. Der Text wird mit
     * {@link #setText} gesetzt. Zeilen werden nie umgebrochen.
     */
    public TextDisplay spawnText(Location loc, Player viewer, boolean visibleByDefault, float scale) {
        boolean shadow = plugin.getConfigManager().get().getBoolean("leaderboard.render.text-shadow", true);
        return spawn(loc, TextDisplay.class, viewer, visibleByDefault, d -> {
            applyCommon(d, scale, scale, scale);
            d.setSeeThrough(false);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0));
            d.setShadowed(shadow);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setLineWidth(2000);
        });
    }

    /**
     * Eine einfarbige Rechteck-Flaeche. Technik: Der Hintergrund eines Text-Displays mit dem Text " " ist bei
     * Skalierung 1 genau 5 x 10 Pixel gross (= 0,125 x 0,25 Bloecke) und reicht von -2 bis +3 Pixel um den
     * Drehpunkt und von 0 bis 10 Pixel nach oben. Mit der Skalierung (8*breite, 4*hoehe) entsteht daraus
     * exakt ein Rechteck in Blockgroesse.
     *
     * @param pivot Position der Mitte-Unterkante MIT dem Versatz von 0,4 * Breite nach rechts
     *              (siehe {@link HologramManager}, dort wird das umgerechnet)
     */
    public TextDisplay spawnRect(Location pivot, double width, double height, int argb, boolean visibleByDefault) {
        return spawnRectInternal(pivot, width, height, argb, null, visibleByDefault);
    }

    /**
     * Wie {@link #spawnRect}, aber NUR fuer einen einzelnen Spieler sichtbar (fuer alle anderen unsichtbar).
     * Gedacht fuer Hover-Effekte: jeder Spieler bekommt seine eigenen Flaechen und kann sie unabhaengig
     * von den anderen animieren.
     */
    public TextDisplay spawnRectFor(Player viewer, Location pivot, double width, double height, int argb) {
        return spawnRectInternal(pivot, width, height, argb, viewer, false);
    }

    private TextDisplay spawnRectInternal(Location pivot, double width, double height, int argb, Player viewer,
                                          boolean visibleByDefault) {
        return spawn(pivot, TextDisplay.class, viewer, visibleByDefault, d -> {
            applyCommon(d, (float) (8.0 * width), (float) (4.0 * height), 1f);
            d.setSeeThrough(false);
            d.setDefaultBackground(false);
            d.setShadowed(false);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setLineWidth(2000);
            d.text(Component.text(" "));
            d.setBackgroundColor(Color.fromARGB(argb));
        });
    }

    /**
     * Unsichtbare Klick-Flaeche. Die Hitbox einer Interaction waechst von ihrer Position NACH OBEN
     * (Position = Mitte der Unterkante); die Breite gilt fuer beide waagerechten Achsen.
     */
    public Interaction spawnInteraction(Location loc, float width, float height) {
        return spawn(loc, Interaction.class, null, true, i -> {
            i.setInteractionWidth(width);
            i.setInteractionHeight(height);
        });
    }

    // ----------------------------------------------------------------
    // Aktualisieren
    // ----------------------------------------------------------------

    public void setText(TextDisplay display, Component text) {
        if (display == null || !display.isValid()) {
            return;
        }
        if (text.equals(lastState.get(display.getEntityId()))) {
            return;
        }
        lastState.put(display.getEntityId(), text);
        display.text(text);
    }

    /**
     * Setzt eine neue Transformation und laesst den Client sie ueber {@code durationTicks} Ticks weich
     * anfahren (Position, Groesse - der Client rechnet die Zwischenbilder selbst). 0 = sofort.
     * Wird mitten in einer Animation ein neues Ziel gesetzt, startet die Bewegung vom aktuell
     * gezeigten Zwischenstand aus.
     */
    public void transform(Display display, Transformation transformation, int durationTicks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDuration(Math.max(0, durationTicks));
        display.setInterpolationDelay(0);
        display.setTransformation(transformation);
    }

    /** Aendert nur die Hintergrundfarbe (inkl. Alpha) eines Rechteck-Displays. */
    public void setBackground(TextDisplay display, int argb) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setBackgroundColor(Color.fromARGB(argb));
    }

    // ----------------------------------------------------------------
    // Sichtbarkeit pro Spieler
    // ----------------------------------------------------------------

    public void show(Player viewer, Collection<? extends Entity> targets) {
        for (Entity entity : targets) {
            if (entity.isValid()) {
                viewer.showEntity(plugin.getPlugin(), entity);
            }
        }
    }

    public void hide(Player viewer, Collection<? extends Entity> targets) {
        for (Entity entity : targets) {
            if (entity.isValid()) {
                viewer.hideEntity(plugin.getPlugin(), entity);
            }
        }
    }

    // ----------------------------------------------------------------
    // Entfernen
    // ----------------------------------------------------------------

    public void destroy(Collection<? extends Entity> targets) {
        for (Entity entity : targets) {
            entities.remove(entity.getEntityId());
            lastState.remove(entity.getEntityId());
            entity.remove();
        }
    }

    public void removeAll() {
        for (Entity entity : entities.values()) {
            entity.remove();
        }
        entities.clear();
        lastState.clear();
    }
}
