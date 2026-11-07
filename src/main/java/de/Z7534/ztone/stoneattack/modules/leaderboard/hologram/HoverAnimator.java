package de.Z7534.ztone.stoneattack.modules.leaderboard.hologram;

import de.Z7534.ztone.stoneattack.Stoneattack;
import de.Z7534.ztone.stoneattack.modules.leaderboard.TimePeriod;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Steuert Hover- und Klick-Animationen der Leaderboard-Buttons im Dual-Mode.
 * Verhindert Jittering durch striktes Caching der visuellen Zustände (Transformation, CustomModelData & Background).
 */
public final class HoverAnimator {

    // Farben für Modus B (Fallback)
    private static final int COLOR_FALLBACK_IDLE = Color.fromARGB(200, 37, 43, 51).asARGB();       // #252b33
    private static final int COLOR_FALLBACK_HOVER = Color.fromARGB(240, 0, 122, 153).asARGB();     // #007a99
    private static final int COLOR_FALLBACK_ACTIVE = Color.fromARGB(230, 0, 180, 216).asARGB();    // Aqua-Aktiv

    private static final class ItemDisplayState {
        final int modelData;
        final float scaleFactor;
        final float offsetZ;

        ItemDisplayState(int modelData, float scaleFactor, float offsetZ) {
            this.modelData = modelData;
            this.scaleFactor = scaleFactor;
            this.offsetZ = offsetZ;
        }
    }

    private static final class LabelVisualState {
        final float scale;
        final double nudge;
        final int bgColor;

        LabelVisualState(float scale, double nudge, int bgColor) {
            this.scale = scale;
            this.nudge = nudge;
            this.bgColor = bgColor;
        }
    }

    private final Stoneattack plugin;
    private final DisplayFactory factory;
    private final ResourcePackStatusTracker tracker;

    private final boolean soundEnabled;
    private final String soundKey;
    private final float soundVolume;
    private final float soundPitch;

    private final float labelLift;
    private final float pressScale;
    private final int animationTicks;

    private final Map<Integer, ItemDisplayState> itemStates = new HashMap<>();
    private final Map<Integer, LabelVisualState> labelStates = new HashMap<>();

    public HoverAnimator(Stoneattack plugin, DisplayFactory factory, ResourcePackStatusTracker tracker) {
        this.plugin = plugin;
        this.factory = factory;
        this.tracker = tracker;

        FileConfiguration cfg = plugin.getConfigManager().get();
        String base = "leaderboard.hover.animation.";

        soundEnabled = cfg.getBoolean(base + "sound-enabled", true);
        soundKey = cfg.getString(base + "sound", "block.note_block.hat");
        soundVolume = (float) cfg.getDouble(base + "sound-volume", 0.15);
        soundPitch = (float) cfg.getDouble(base + "sound-pitch", 2.0);

        labelLift = (float) cfg.getDouble(base + "label-lift", 1.08);
        pressScale = (float) cfg.getDouble(base + "press-scale", 0.94);
        animationTicks = 1; // Sofortige, verzögerungsfreie Animation (50ms)
    }

    /**
     * Wird pro Tick für jeden Betrachter des Boards aufgerufen.
     */
    public void update(Player viewer, BoardFrame frame, HologramBoard board, HologramInstance inst,
                       ClickBinding.Action hovered) {
        if (viewer == null || !viewer.isOnline() || inst == null) {
            return;
        }

        // Klick-Animation fortschreiten
        float pressFactor = 1.0f;
        ClickBinding.Action pressed = null;
        if (inst.clickedAction != null) {
            if (inst.sinceClick < 1) {
                pressFactor = pressScale;
                pressed = inst.clickedAction;
            } else if (inst.sinceClick < 2) {
                pressFactor = 1.06f;
                pressed = inst.clickedAction;
            } else {
                inst.clickedAction = null;
            }
            inst.sinceClick++;
        }

        // Sound bei Hover-Wechsel
        if (hovered != null && hovered != inst.hoveredButton) {
            playHoverSound(viewer);
        }
        inst.hoveredButton = hovered;

        boolean hasResourcePack = tracker != null && tracker.hasResourcePack(viewer);
        HologramPositionStore store = plugin.getLeaderboardModule().getPositionStore();

        int modelIdle = store != null ? store.getModelButtonIdle() : 1001;
        int modelHover = store != null ? store.getModelButtonHover() : 1002;
        int modelActive = store != null ? store.getModelButtonActive() : 1003;

        String keyIdle = store != null ? store.getModelButtonIdleKey() : "stoneattack:button_idle";
        String keyHover = store != null ? store.getModelButtonHoverKey() : "stoneattack:button_hover";
        String keyActive = store != null ? store.getModelButtonActiveKey() : "stoneattack:button_active";

        ClickBinding.Action[] actions = {
                ClickBinding.Action.ARROW_LEFT,
                ClickBinding.Action.ARROW_RIGHT,
                ClickBinding.Action.GLOBAL_BUTTON,
                ClickBinding.Action.MONTH_BUTTON,
                ClickBinding.Action.WEEK_BUTTON
        };

        for (ClickBinding.Action action : actions) {
            boolean isHovered = (action == hovered);
            boolean isPressed = (action == pressed);
            boolean isActive = isActionActive(action, inst);

            ItemDisplay itemDisplay = inst.buttonItemDisplays.get(action);
            HoverLabel label = inst.labels.get(action);

            if (hasResourcePack) {
                // ========================================================
                // MODUS A: RESOURCEPACK AKTIV
                // ========================================================
                if (itemDisplay != null && itemDisplay.isValid()) {
                    // Hintergrund-Textur besitzt bereits die exakten Rahmen fuer Pfeile & Modus-Tabs;
                    // kein zweites ItemDisplay darueberlegen, damit kein stoerender Kasten entsteht.
                    viewer.hideEntity(plugin.getPlugin(), itemDisplay);
                }

                // TextDisplay im Resourcepack-Modus: Hintergrund vollkommen transparent
                if (label != null && label.display != null && label.display.isValid()) {
                    float targetScale = isPressed ? pressFactor : (isHovered ? labelLift : 1.0f);
                    double targetNudge = isHovered ? (label.nudgeDirection * 0.03) : 0.0;
                    int entityId = label.display.getEntityId();
                    LabelVisualState lState = labelStates.get(entityId);

                    if (lState == null || lState.bgColor != 0) {
                        factory.setBackground(label.display, 0);
                    }

                    if (lState == null
                            || Math.abs(lState.scale - targetScale) > 0.001f
                            || Math.abs(lState.nudge - targetNudge) > 0.0001) {
                        animateLabelText(label, targetScale, isHovered);
                    }

                    labelStates.put(entityId, new LabelVisualState(targetScale, targetNudge, 0));
                }

            } else {
                // ========================================================
                // MODUS B: FALLBACK AKTIV (KEIN PACK GELADEN)
                // ========================================================
                if (itemDisplay != null && itemDisplay.isValid()) {
                    viewer.hideEntity(plugin.getPlugin(), itemDisplay);
                }

                if (label != null && label.display != null && label.display.isValid()) {
                    int targetBg = isHovered ? COLOR_FALLBACK_HOVER : (isActive ? COLOR_FALLBACK_ACTIVE : COLOR_FALLBACK_IDLE);
                    float targetScale = isPressed ? pressFactor : (isHovered ? labelLift : 1.0f);
                    double targetNudge = isHovered ? (label.nudgeDirection * 0.03) : 0.0;
                    int entityId = label.display.getEntityId();
                    LabelVisualState lState = labelStates.get(entityId);

                    if (lState == null || lState.bgColor != targetBg) {
                        factory.setBackground(label.display, targetBg);
                    }

                    if (lState == null
                            || Math.abs(lState.scale - targetScale) > 0.001f
                            || Math.abs(lState.nudge - targetNudge) > 0.0001) {
                        animateLabelText(label, targetScale, isHovered);
                    }

                    labelStates.put(entityId, new LabelVisualState(targetScale, targetNudge, targetBg));
                }
            }
        }
    }

    /**
     * Klick-Impuls für Feedback-Animation.
     */
    public void press(HologramInstance inst, ClickBinding.Action action) {
        if (inst != null) {
            inst.clickedAction = action;
            inst.sinceClick = 0;
        }
    }

    public void clear() {
        itemStates.clear();
        labelStates.clear();
    }

    public void clearEntity(int entityId) {
        itemStates.remove(entityId);
        labelStates.remove(entityId);
    }

    private boolean isActionActive(ClickBinding.Action action, HologramInstance inst) {
        if (inst == null || inst.shownHighlight == null) {
            return false;
        }
        return switch (action) {
            case GLOBAL_BUTTON -> inst.shownHighlight == TimePeriod.GLOBAL;
            case MONTH_BUTTON -> inst.shownHighlight == TimePeriod.MONTH;
            case WEEK_BUTTON -> inst.shownHighlight == TimePeriod.WEEK;
            default -> false;
        };
    }

    private double getButtonWidth(ClickBinding.Action action, HologramLayout layout) {
        return switch (action) {
            case ARROW_LEFT, ARROW_RIGHT -> HologramLayout.ARROW_WIDTH;
            case GLOBAL_BUTTON, MONTH_BUTTON, WEEK_BUTTON -> layout.modeWidth();
        };
    }

    private double getButtonHeight(ClickBinding.Action action) {
        return switch (action) {
            case ARROW_LEFT, ARROW_RIGHT -> HologramLayout.ARROW_HEIGHT;
            case GLOBAL_BUTTON, MONTH_BUTTON, WEEK_BUTTON -> HologramLayout.MODE_HEIGHT;
        };
    }

    private void animateLabelText(HoverLabel label, float targetScale, boolean isHovered) {
        TextDisplay d = label.display;
        float base = label.baseScale;
        double nudge = isHovered ? (label.nudgeDirection * 0.03) : 0.0;

        Transformation t = new Transformation(
                new Vector3f((float) nudge, 0f, 0f),
                new AxisAngle4f(),
                new Vector3f(base * targetScale, base * targetScale, base * targetScale),
                new AxisAngle4f()
        );
        factory.transform(d, t, animationTicks);
    }

    private void playHoverSound(Player viewer) {
        if (!soundEnabled || viewer == null) {
            return;
        }
        try {
            viewer.playSound(viewer.getLocation(), soundKey.toLowerCase(), soundVolume, soundPitch);
        } catch (Throwable ignored) {
        }
    }
}
