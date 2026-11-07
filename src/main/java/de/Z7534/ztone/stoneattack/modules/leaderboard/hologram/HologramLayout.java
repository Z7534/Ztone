package de.Z7534.ztone.stoneattack.modules.leaderboard.hologram;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Alle Maße des Boards in BLÖCKEN im Board-Koordinatensystem (siehe
 * {@link BoardFrame}: u = rechts, v = hoch, Ursprung = Mitte des angeklickten Blocks).
 *
 * Das Board ist modular in der Höhe (3.0 bis 12.0 Blöcke).
 * Standard: 3.0 x 3.0 Blöcke groß.
 *
 * Koordinaten:
 * Ein Block reicht von v = -0.5 bis +0.5.
 * Die Unterkante des Blocks ist v = -0.5.
 * Das Panel sitzt mit seiner Unterkante bei v = -0.5 (Unterkante des Zielblocks) und wächst nach oben.
 *
 * Textur-Raster (leaderboard_bg.png, 768 x 768 px = 3 x 3 Blöcke, 256 px pro Block):
 * <pre>
 *   y   0 .. 227  Kopf  (Titel, Leuchtlinie, Reihe 1-3 Gold/Silber/Bronze)
 *   y 227 .. 269  Reihe (ein normaler Slot inkl. 3 px Abstand oben/unten, Raster 42 px)
 *   y 268 .. 269  Füller (1 px Slot-Zwischenraum, wird bei Resthöhe gestreckt)
 *   y 521 .. 768  Fuß   (Du-Leiste, Seitenumschalter, Modus-Tabs)
 * </pre>
 * Das Plugin stapelt Kopf + N Reihen + Füller + Fuß, sodass jede Text-Reihe
 * exakt in ihrem Textur-Slot liegt - unabhängig von der eingestellten Höhe.
 */
public final class HologramLayout {

    public static final double BOARD_WIDTH = 3.0;
    public static final double BACKGROUND_WIDTH = 3.0;
    public static final double BACKGROUND_HEIGHT = 3.0;

    public static final int SLOT_COUNT = 10;
    public static final double DEFAULT_HEIGHT = 3.0;
    public static final double MIN_HEIGHT = 3.0;
    public static final double MAX_HEIGHT = 12.0;

    // ---- Textur-Raster in Pixeln (256 px = 1 Block) ----
    public static final double PX_PER_BLOCK = 256.0;
    /** Kopf inkl. der drei Top-Reihen (Gold/Silber/Bronze). */
    public static final double BG_TOP_PX = 227.0;
    /** Raster eines Slots (36 px Slot + 6 px Abstand). */
    public static final double BG_ROW_PX = 42.0;
    /** Fuß: Du-Leiste, Pager, Modus-Tabs. */
    public static final double BG_FOOTER_PX = 247.0;
    /** Anzahl der Reihen, die bereits im Kopfstück enthalten sind. */
    public static final int BG_TOP_ROWS = 3;
    /** Mitte des ersten Slots, gemessen von der Oberkante. */
    public static final double BG_FIRST_ROW_CENTER_PX = 122.0;

    static final double MARGIN = 0.03;
    static final double PADDING = 0.08;

    /** Button-Maße: 4:1 Seitenverhältnis entsprechend 256x64 Textur */
    public static final double MODE_WIDTH = 0.8515;
    public static final double MODE_HEIGHT = 0.2109;

    public static final double ARROW_WIDTH = 58.0 / 256.0;
    public static final double ARROW_HEIGHT = 38.0 / 256.0;

    public static final double PAGER_HEIGHT = 0.22;

    /**
     * Spalten-Koordinaten für 100% pixelgenaue Ausrichtung innerhalb der Slots:
     * Boardbreite = 3.0 (-1.5 bis +1.5). Die Slotbreite beträgt ~2.77 (-1.385 bis +1.385).
     * Mit 17 Textur-Pixeln Randabstand liegen alle Inhalte garantiert sauber im Slot.
     */
    public static final double COL_RANK_LEFT = -1.24;   // Alle Ränge (➊, ➋, ➌, #4, #10) starten exakt hier
    public static final double COL_NAME_LEFT = -0.78;   // Alle Spielernamen starten exakt hier (mit klarem Abstand zum Rang)
    public static final double COL_ARROW_CENTER = 0.14; // Alle Trennpfeile » sind hier zentriert
    public static final double COL_VAL_RIGHT = 1.24;    // Alle Werte/Icons enden exakt hier rechtsbündig
    public static final double NAME_MAX_WIDTH = 0.65;   // Maximalbreite für Namen vor dem Pfeil (mit deutlichem Abstand zu »)

    private static final double DEFAULT_TEXT_CENTER_OFFSET = 0.1125;

    private final double textCenterOffset;
    private final double requestedHeight;

    public final int perPage;
    public final float textScale;
    public final float rowTextScale;
    public final float buttonTextScale;
    public final float pagerTextScale;
    public final float arrowTextScale;
    public final float selfTextScale;

    final double line;
    final double left;
    final double width;

    final double totalHeight;
    public final double panelBottom;
    public final double panelHeight;
    public final double panelCenter;
    public final double panelTop;
    public final double pagerBottom;
    public final double modeBottom;
    public final double pagerCenter;
    public final double modeCenter;

    public final double titleCenter;
    public final double selfCenter;

    /** Höhe des Füllstücks zwischen letzter Reihe und Fuß (in Pixeln, 0 .. 42). */
    public final double fillerPx;

    final double listU;

    public HologramLayout(FileConfiguration cfg, double requestedHeight) {
        this.requestedHeight = requestedHeight;

        textScale = (float) clamp(cfg.getDouble("leaderboard.layout.text-scale", 0.85), 0.4, 1.5);
        rowTextScale = 0.52f;
        selfTextScale = 0.52f;
        buttonTextScale = (float) clamp(cfg.getDouble("leaderboard.layout.button-text-scale", 0.55), 0.3, 1.2);
        pagerTextScale = (float) clamp(cfg.getDouble("leaderboard.layout.pager-text-scale", 0.55), 0.3, 1.2);
        arrowTextScale = (float) clamp(cfg.getDouble("leaderboard.layout.arrow-text-scale", 0.70), 0.3, 1.2);
        textCenterOffset = clamp(cfg.getDouble("leaderboard.layout.text-center-offset", DEFAULT_TEXT_CENTER_OFFSET), 0.0, 0.3);

        left = -(BOARD_WIDTH / 2.0 - MARGIN);
        width = BOARD_WIDTH - 2 * MARGIN;
        listU = 0.0;
        line = BG_ROW_PX / PX_PER_BLOCK;

        totalHeight = clamp(requestedHeight, MIN_HEIGHT, MAX_HEIGHT);

        // Das Panel wächst dynamisch mit totalHeight (Standard: 3.0 Blöcke)
        panelHeight = totalHeight;
        panelBottom = -0.5;
        panelTop = panelBottom + panelHeight;
        panelCenter = panelBottom + panelHeight / 2.0;

        // Kopf ist an der Oberkante verankert, Fuß an der Unterkante.
        titleCenter = panelTop - 0.1973;
        selfCenter = panelBottom + (768.0 - 557.0) / PX_PER_BLOCK;   // 0.3242
        pagerCenter = panelBottom + (768.0 - 624.0) / PX_PER_BLOCK;  // 0.0625
        pagerBottom = pagerCenter - PAGER_HEIGHT / 2.0;
        modeCenter = panelBottom + (768.0 - 695.0) / PX_PER_BLOCK;   // -0.2148
        modeBottom = modeCenter - MODE_HEIGHT / 2.0;

        perPage = rowsForHeight(totalHeight);
        double usedPx = BG_TOP_PX + (perPage - BG_TOP_ROWS) * BG_ROW_PX + BG_FOOTER_PX;
        fillerPx = Math.max(0.0, totalHeight * PX_PER_BLOCK - usedPx);
    }

    /** Anzahl der Slots, die bei dieser Höhe exakt in das Textur-Raster passen. */
    private static int rowsForHeight(double clampedHeight) {
        double middlePx = clampedHeight * PX_PER_BLOCK - BG_TOP_PX - BG_FOOTER_PX;
        int extra = (int) Math.floor(middlePx / BG_ROW_PX + 1e-6);
        return BG_TOP_ROWS + Math.max(0, extra);
    }

    /** Anzahl der normalen (wiederholten) Reihen-Stücke unterhalb des Kopfes. */
    public int middleRowCount() {
        return Math.max(0, perPage - BG_TOP_ROWS);
    }

    /** Absolutes v-Zentrum eines der Slots im Board-Koordinatensystem */
    public double slotCenter(int slot) {
        return panelTop - (BG_FIRST_ROW_CENTER_PX + slot * BG_ROW_PX) / PX_PER_BLOCK;
    }

    // ---- Hintergrund-Stücke (v-Zentrum und Höhe in Blöcken) ----

    public double bgTopCenter() {
        return panelTop - BG_TOP_PX / 2.0 / PX_PER_BLOCK;
    }

    public double bgTopHeight() {
        return BG_TOP_PX / PX_PER_BLOCK;
    }

    /** v-Zentrum des normalen Reihen-Stücks index (0 = erste Reihe unter dem Kopf, also Slot #4). */
    public double bgRowCenter(int index) {
        return panelTop - (BG_TOP_PX + index * BG_ROW_PX + BG_ROW_PX / 2.0) / PX_PER_BLOCK;
    }

    public double bgRowHeight() {
        return BG_ROW_PX / PX_PER_BLOCK;
    }

    public double bgFillerCenter() {
        double startPx = BG_TOP_PX + middleRowCount() * BG_ROW_PX;
        return panelTop - (startPx + fillerPx / 2.0) / PX_PER_BLOCK;
    }

    public double bgFillerHeight() {
        return fillerPx / PX_PER_BLOCK;
    }

    public double bgFooterCenter() {
        return panelBottom + BG_FOOTER_PX / 2.0 / PX_PER_BLOCK;
    }

    public double bgFooterHeight() {
        return BG_FOOTER_PX / PX_PER_BLOCK;
    }

    public double modeWidth() {
        return MODE_WIDTH;
    }

    /** Horizontales Zentrum des Modus-Buttons index (0 = Global, 1 = Monat, 2 = Woche) */
    public double modeCenter(int index) {
        return switch (index) {
            case 0 -> -0.9336;
            case 1 -> 0.0;
            case 2 -> 0.9336;
            default -> 0.0;
        };
    }

    public double modeLeft(int index) {
        return modeCenter(index) - MODE_WIDTH / 2.0;
    }

    public double arrowLeftCenter() {
        return -0.6875;
    }

    public double arrowRightCenter() {
        return 0.6875;
    }

    double centerOffset() {
        return textCenterOffset;
    }

    public double effectiveHeight() {
        return totalHeight;
    }

    public double requestedHeight() {
        return requestedHeight;
    }

    public static int entriesForHeight(FileConfiguration cfg, double height) {
        return rowsForHeight(clamp(height, MIN_HEIGHT, MAX_HEIGHT));
    }

    double rowCenter(int index) {
        return slotCenter(index);
    }

    double pagerCenter() {
        return pagerCenter;
    }

    double modeCenter() {
        return modeCenter;
    }

    double textPivot(double lineCenter, double scale) {
        return lineCenter - textCenterOffset * scale;
    }

    public int maxLinePixelWidth() {
        return 2000;
    }

    public int getExactPixelWidth(float scale) {
        return 2000;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
