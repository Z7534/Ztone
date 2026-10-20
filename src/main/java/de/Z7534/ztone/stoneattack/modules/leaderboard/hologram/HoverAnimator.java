package de.Z7534.ztone.stoneattack.modules.leaderboard.hologram;

import de.Z7534.ztone.stoneattack.Stoneattack;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Map;

/**
 * Die komplette Hover-Animation der Leaderboard-Buttons.
 *
 * <h2>Was der Spieler sieht</h2>
 * <ul>
 *   <li><b>Einblenden:</b> Beim ersten Ansehen eines Buttons "poppt" der Cursor aus leicht
 *       verkleinerter Groesse auf 100 % auf und blendet dabei weich ein.</li>
 *   <li><b>Gleiten:</b> Wandert der Blick von einem Modus-Button zum naechsten, gleitet
 *       der Cursor hinueber, statt auszublenden und neu zu erscheinen.</li>
 *   <li><b>Glanz:</b> Ein heller Streifen wischt beim Ansehen einmal ueber den Button.</li>
 *   <li><b>Beschriftung:</b> Der Text des Buttons waechst leicht; die Pfeile rutschen dazu ein
 *       Stueck nach aussen.</li>
 *   <li><b>Klick:</b> Cursor und Beschriftung druecken sich kurz ein und federn zurueck.</li>
 *   <li><b>Ausblenden:</b> Nach einer kurzen Toleranzzeit (damit der Cursor beim Ueberqueren
 *       der Luecke zwischen zwei Buttons nicht flackert) schrumpft er leicht und blendet weich aus.</li>
 * </ul>
 *
 * <h2>Technik</h2>
 * Bewegung und Groesse laufen ueber die Interpolation der Display-Entities: der Server schickt
 * nur das Ziel, der Client rechnet die Zwischenbilder selbst (fluessig, unabhaengig von der
 * Server-TPS). Farben lassen sich in Minecraft nicht interpolieren - das Ein-/Ausblenden
 * wird deshalb in einzelnen Alpha-Stufen pro Tick gesendet. Alles gehoert einem einzelnen
 * Spieler ({@link HologramInstance}), Animationen verschiedener Spieler stoeren sich nie.
 *
 * Wird einmal pro Tick pro Spieler und Board aufgerufen. Solange nichts passiert, werden
 * keine Pakete verschickt.
 */
final class HoverAnimator {

    /** Hintergruende mit kleinerem Alpha zeichnet Minecraft nicht mehr sauber. */
    private static final int MIN_ALPHA = 26;

    // Abstand der Ebenen von der Wand (siehe HologramManager). Fuellung < Rahmen < Glanz < Text.
    private static final double LAYER_FILL = 0.0270;
    private static final double LAYER_RING = 0.0295;
    private static final double LAYER_SHINE = 0.0320;

    private static final double CORNER_RADIUS = 0.12;
    private static final double SHINE_WIDTH = 0.05;
    /** Der Glanz bleibt so weit vom Rand weg, dass er nie in die runden Ecken ragt. */
    private static final double SHINE_INSET = 0.14;
    private static final double SHINE_V_INSET = 0.05;

    private final DisplayFactory factory;

    private final int fadeTicks;
    private final int glideTicks;
    private final int popTicks;
    private final int graceTicks;
    private final int labelTicks;
    private final int shineTicks;
    private final float popScale;
    private final float leaveScale;
    private final float labelLift;
    private final float pressScale;
    private final float overshootScale;
    private final double labelNudge;
    private final boolean shineEnabled;
    private final boolean soundEnabled;
    private final String sound;
    private final float soundVolume;
    private final float soundPitch;

    private final double thickness;
    private final int ringRgb;
    private final int ringAlpha;
    private final int fillRgb;
    private final int fillAlpha;
    private final int shineRgb;
    private final int shineAlpha;

    HoverAnimator(Stoneattack plugin, DisplayFactory factory) {
        this.factory = factory;
        FileConfiguration cfg = plugin.getConfigManager().get();
        String base = "leaderboard.hover.animation.";

        fadeTicks = (int) clamp(cfg.getInt(base + "fade-ticks", 4), 1, 20);
        glideTicks = (int) clamp(cfg.getInt(base + "glide-ticks", 4), 1, 20);
        popTicks = (int) clamp(cfg.getInt(base + "pop-ticks", 3), 1, 20);
        graceTicks = (int) clamp(cfg.getInt(base + "leave-grace-ticks", 2), 0, 20);
        labelTicks = (int) clamp(cfg.getInt(base + "label-ticks", 3), 1, 20);
        shineTicks = (int) clamp(cfg.getInt(base + "shine-ticks", 8), 3, 40);
        popScale = (float) clamp(cfg.getDouble(base + "pop-scale", 0.90), 0.5, 1.0);
        leaveScale = (float) clamp(cfg.getDouble(base + "leave-scale", 0.94), 0.5, 1.0);
        labelLift = (float) clamp(cfg.getDouble(base + "label-lift", 1.12), 1.0, 1.5);
        labelNudge = clamp(cfg.getDouble(base + "label-nudge", 0.03), 0.0, 0.15);
        pressScale = (float) clamp(cfg.getDouble(base + "press-scale", 0.90), 0.5, 1.0);
        overshootScale = (float) clamp(cfg.getDouble(base + "overshoot-scale", 1.05), 1.0, 1.3);
        shineEnabled = cfg.getBoolean(base + "shine-enabled", true);
        soundEnabled = cfg.getBoolean(base + "sound-enabled", true);
        sound = cfg.getString(base + "sound", "block.note_block.hat");
        soundVolume = (float) cfg.getDouble(base + "sound-volume", 0.10);
        soundPitch = (float) cfg.getDouble(base + "sound-pitch", 2.0);

        thickness = Math.max(0.005, cfg.getDouble("leaderboard.style.hover-thickness", 0.022));
        ringRgb = parseRgb(cfg.getString("leaderboard.style.hover-color", "#9BD1FF"), "#9BD1FF");
        ringAlpha = alphaOf(cfg.getDouble("leaderboard.style.hover-transparency", 0.15));
        fillRgb = parseRgb(cfg.getString("leaderboard.style.hover-fill-color", "#9BD1FF"), "#9BD1FF");
        fillAlpha = alphaOf(cfg.getDouble("leaderboard.style.hover-fill-transparency", 0.80));
        shineRgb = parseRgb(cfg.getString(base + "shine-color", "#FFFFFF"), "#FFFFFF");
        shineAlpha = alphaOf(cfg.getDouble(base + "shine-transparency", 0.45));
    }

    // ------------------------------------------------------------
    // Ein Durchlauf pro Tick, Spieler und Board
    // ------------------------------------------------------------

    /**
     * @param hovered Button, auf den der Spieler gerade schaut (null = keiner)
     */
    void update(Player viewer, BoardFrame frame, HologramBoard board, HologramInstance inst,
                ClickBinding.Action hovered) {
        // ---- Klick-Zeitleiste: 2 Ticks eindruecken, 2 Ticks ueberschwingen, dann normal ----
        float press = 1f;
        int pressDuration = popTicks;
        ClickBinding.Action pressed = null;
        if (inst.clickedAction != null) {
            if (inst.sinceClick < 2) {
                press = pressScale;
                pressDuration = 1;
                pressed = inst.clickedAction;
            } else if (inst.sinceClick < 4) {
                press = overshootScale;
                pressDuration = 2;
                pressed = inst.clickedAction;
            } else {
                inst.clickedAction = null;
            }
            inst.sinceClick++;
        }

        ClickBinding.Action modeTarget = isMode(hovered) ? hovered : null;
        ClickBinding.Action arrowTarget = hovered != null && !isMode(hovered) ? hovered : null;

        if (modeTarget != null && inst.modeCursor == null) {
            double[] area = board.getButtonAreas().get(modeTarget);
            if (area != null) {
                inst.modeCursor = build(viewer, frame, area, true);
            }
        }
        if (arrowTarget != null && inst.arrowCursor == null) {
            double[] area = board.getButtonAreas().get(arrowTarget);
            if (area != null) {
                inst.arrowCursor = build(viewer, frame, area, false);
            }
        }

        if (inst.modeCursor != null) {
            boolean isPressed = modeTarget != null && modeTarget == pressed;
            step(viewer, inst.modeCursor, modeTarget, board, isPressed ? press : 1f, pressDuration);
        }
        if (inst.arrowCursor != null) {
            boolean isPressed = arrowTarget != null && arrowTarget == pressed;
            step(viewer, inst.arrowCursor, arrowTarget, board, isPressed ? press : 1f, pressDuration);
        }
        stepLabels(inst, hovered, pressed, press, pressDuration);
    }

    /** Ein Klick: Cursor und Beschriftung druecken sich ein und federn zurueck. */
    void press(HologramInstance inst, ClickBinding.Action action) {
        inst.clickedAction = action;
        inst.sinceClick = 0;
    }

    // ------------------------------------------------------------
    // Cursor
    // ------------------------------------------------------------

    private void step(Player viewer, HoverCursor c, ClickBinding.Action target, HologramBoard board,
                      float press, int pressDuration) {
        if (target != null) {
            double[] area = board.getButtonAreas().get(target);
            if (area == null) {
                return;
            }
            c.lost = 0;
            double offU = area[0] + area[2] / 2.0 - c.centerU;
            double offV = area[1] + area[3] / 2.0 - c.centerV;

            if (c.at == null) {
                // Kalt: erst unsichtbar und leicht verkleinert an den Button setzen. Die eigentliche
                // Animation startet im naechsten Tick - so kommt die Startposition sicher vor dem Ziel
                // beim Client an und es wird nicht einfach zum Endzustand "gesprungen".
                c.at = target;
                c.offU = offU;
                c.offV = offV;
                c.scale = popScale;
                c.level = 0;
                c.shineTick = shineEnabled && c.shine != null ? 0 : -1;
                applyTransforms(c, 0);
                syncAlpha(c);
                playHoverSound(viewer);
                return;
            }

            boolean moved = c.at != target;
            if (moved) {
                c.at = target;
                c.offU = offU;
                c.offV = offV;
                c.shineTick = shineEnabled && c.shine != null ? 0 : -1;
                playHoverSound(viewer);
            }
            boolean scaleChanged = Math.abs(press - c.scale) > 0.001f;
            if (moved || scaleChanged) {
                c.scale = press;
                int duration = press != 1f ? pressDuration : (moved ? glideTicks : popTicks);
                applyTransforms(c, duration);
            }
            if (c.level < fadeTicks) {
                c.level++;
            }
            syncAlpha(c);
            stepShine(c);
            return;
        }

        // ---- Blick ist weg ----
        if (c.at == null) {
            return;
        }
        c.lost++;
        if (c.lost <= graceTicks) {
            stepShine(c);
            return;
        }
        if (Math.abs(leaveScale - c.scale) > 0.001f) {
            c.scale = leaveScale;
            applyTransforms(c, fadeTicks);
        }
        c.level--;
        if (c.level <= 0) {
            c.level = 0;
            c.at = null;
            c.shineTick = -1;
            syncAlpha(c);
            if (c.shine != null) {
                setAlpha(c.shine, 0);
            }
            return;
        }
        syncAlpha(c);
        stepShine(c);
    }

    /**
     * Skaliert die ganze Gruppe um die Mitte des Buttons, an dem der Cursor sitzt. Jedes Teil wird um
     * seinen eigenen Drehpunkt skaliert; die Verschiebung {@code (k-1) * Drehpunkt} rechnet das auf
     * "Skalierung um die Buttonmitte" um. Dazu kommt die Verschiebung zum aktuellen Button.
     */
    private void applyTransforms(HoverCursor c, int durationTicks) {
        float k = c.scale;
        for (HoverCursor.Part p : c.body) {
            Vector3f translation = new Vector3f(
                    (float) (c.offU + (k - 1f) * p.pivotU),
                    (float) (c.offV + (k - 1f) * p.pivotV),
                    0f);
            Vector3f scale = new Vector3f(k * p.scaleX, k * p.scaleY, 1f);
            factory.transform(p.display,
                    new Transformation(translation, new AxisAngle4f(), scale, new AxisAngle4f()), durationTicks);
        }
    }

    /** Sichtbarkeit: Stufe 0..fadeTicks, weich (Smoothstep) auf das Ziel-Alpha jedes Teils abgebildet. */
    private void syncAlpha(HoverCursor c) {
        double t = c.level / (double) fadeTicks;
        double eased = t * t * (3.0 - 2.0 * t);
        for (HoverCursor.Part p : c.body) {
            int alpha = c.level <= 0 ? 0 : Math.max(MIN_ALPHA, (int) Math.round(p.maxAlpha * eased));
            setAlpha(p, Math.min(p.maxAlpha, alpha));
        }
    }

    private void setAlpha(HoverCursor.Part p, int alpha) {
        if (alpha == p.shownAlpha) {
            return;
        }
        p.shownAlpha = alpha;
        factory.setBackground(p.display, (alpha << 24) | p.rgb);
    }

    /** Der Glanz: wandert einmal von links nach rechts und blendet dabei ein und wieder aus. */
    private void stepShine(HoverCursor c) {
        if (c.shine == null || c.shineTick < 0) {
            return;
        }
        int n = shineTicks;
        if (c.shineTick == 0) {
            // Startposition ganz links, ohne Animation, unsichtbar
            shineTransform(c, 0.0, 0);
            setAlpha(c.shine, 0);
        } else {
            if (c.shineTick == 1) {
                shineTransform(c, c.shineTravel, n - 1);
            }
            double progress = Math.min(1.0, c.shineTick / (double) n);
            int alpha = (int) Math.round(c.shine.maxAlpha * Math.sin(Math.PI * progress));
            setAlpha(c.shine, alpha < MIN_ALPHA ? 0 : alpha);
        }
        c.shineTick++;
        if (c.shineTick > n) {
            c.shineTick = -1;
            setAlpha(c.shine, 0);
        }
    }

    private void shineTransform(HoverCursor c, double travel, int durationTicks) {
        HoverCursor.Part p = c.shine;
        Transformation t = new Transformation(
                new Vector3f((float) (c.offU + travel), (float) c.offV, 0f),
                new AxisAngle4f(),
                new Vector3f(p.scaleX, p.scaleY, 1f),
                new AxisAngle4f());
        factory.transform(p.display, t, durationTicks);
    }

    // ------------------------------------------------------------
    // Beschriftungen
    // ------------------------------------------------------------

    private void stepLabels(HologramInstance inst, ClickBinding.Action hovered, ClickBinding.Action pressed,
                            float press, int pressDuration) {
        for (Map.Entry<ClickBinding.Action, HoverLabel> entry : inst.labels.entrySet()) {
            HoverLabel label = entry.getValue();
            boolean on = entry.getKey() == hovered;
            float factor = on ? labelLift : 1f;
            double nudge = on ? label.nudgeDirection * labelNudge : 0.0;
            boolean isPressed = entry.getKey() == pressed;
            if (isPressed) {
                factor *= press;
            }
            if (Math.abs(factor - label.factor) < 0.001f && Math.abs(nudge - label.nudge) < 1.0E-4) {
                continue;
            }
            label.factor = factor;
            label.nudge = nudge;
            int duration = isPressed && press != 1f ? pressDuration : labelTicks;

            // Ein Text-Display haengt an der Unterkante seiner Textbox. Damit der Text um seine MITTE
            // waechst (und nicht nach oben wegwandert), wird die Verschiebung der Mitte ausgeglichen.
            double shift = -(factor - 1.0) * label.centerOffset * label.baseScale;
            float s = label.baseScale * factor;
            factory.transform(label.display, new Transformation(
                    new Vector3f((float) nudge, (float) shift, 0f),
                    new AxisAngle4f(),
                    new Vector3f(s, s, s),
                    new AxisAngle4f()), duration);
        }
    }

    // ------------------------------------------------------------
    // Aufbau
    // ------------------------------------------------------------

    /** Baut einen Cursor in der Groesse des Buttons {@code area = {u0, v0, breite, hoehe}}. */
    private HoverCursor build(Player viewer, BoardFrame f, double[] area, boolean modeKind) {
        // Deaktiviert gegen Laggs: keine zusaetzlichen Entities spawnen
        return null;
    }

    /** Spawnt ein unsichtbares (Alpha 0) Rechteck {@code q = {u0, v0, breite, hoehe}} nur fuer diesen Spieler. */
    private HoverCursor.Part part(Player viewer, BoardFrame f, HoverCursor c, double[] q, double layer,
                                  int rgb, int maxAlpha) {
        // Der Hintergrund eines Text-Displays reicht von -0,4*Breite bis +0,6*Breite um den Drehpunkt
        // (siehe DisplayFactory#spawnRect) -> Drehpunkt 0,4*Breite rechts der linken Kante.
        double pivotU = q[0] + 0.4 * q[2];
        double pivotV = q[1];
        TextDisplay display = factory.spawnRectFor(viewer, f.at(pivotU, pivotV, layer), q[2], q[3], rgb & 0xFFFFFF);
        return new HoverCursor.Part(display, pivotU - c.centerU, pivotV - c.centerV,
                (float) (8.0 * q[2]), (float) (4.0 * q[3]), rgb & 0xFFFFFF, maxAlpha);
    }

    // ------------------------------------------------------------
    // Hilfen
    // ------------------------------------------------------------

    private void playHoverSound(Player viewer) {
        if (!soundEnabled || sound == null || sound.isBlank()) {
            return;
        }
        viewer.playSound(viewer.getLocation(), sound, soundVolume, soundPitch);
    }

    private static boolean isMode(ClickBinding.Action action) {
        return action == ClickBinding.Action.GLOBAL_BUTTON
                || action == ClickBinding.Action.MONTH_BUTTON
                || action == ClickBinding.Action.WEEK_BUTTON;
    }

    /** Alpha (0-255) aus einer Durchsichtigkeit (0 = voll deckend, 1 = unsichtbar). */
    private static int alphaOf(double transparency) {
        double t = clamp(transparency, 0.0, 1.0);
        return (int) clamp(Math.round((1.0 - t) * 255.0), MIN_ALPHA, 255);
    }

    private static int parseRgb(String hex, String fallback) {
        try {
            return Integer.parseInt(hex.replace("#", "").trim(), 16) & 0xFFFFFF;
        } catch (Exception e) {
            return Integer.parseInt(fallback.replace("#", ""), 16) & 0xFFFFFF;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
