package de.Z7534.ztone.stoneattack.modules.leaderboard.hologram;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Alle Masse des Boards in BLOECKEN im Board-Koordinatensystem (siehe
 * {@link BoardFrame}: u = rechts, v = hoch, Ursprung = Mitte des Boards).
 *
 * Aufbau von oben nach unten:
 * <pre>
 *   +-------------------------------+
 *   |            Titel              |   <- ein einziger Screen (Panel)
 *   |  ------------------------     |
 *   |  1. Name  »  Wert             |
 *   |  2. Name  »  Wert             |
 *   |  ...                          |
 *   |  ------------------------     |
 *   |            Du                 |
 *   +-------------------------------+
 *   [ < ]        [ 1/10 ]        [ > ]      <- Blaettern (unten links/rechts)
 *   [ Global ]  [ Monat ]  [ Woche ]        <- die 3 Modi zusammen
 * </pre>
 *
 * <h2>Hoehe</h2>
 * Die GESAMTHOEHE wird pro Leaderboard vorgegeben ({@code /leaderboard height ...},
 * Standard {@value #DEFAULT_HEIGHT} Bloecke) und ist immer eine GANZE Blockzahl.
 * Das Board sitzt mit seiner Unterkante auf der unteren Kante des angeklickten
 * Blocks und waechst nach oben. Alles andere - Schriftgroesse,
 * Buttonhoehen, Raender - bleibt dabei unveraendert; aus der zusaetzlichen Hoehe
 * entstehen ausschliesslich MEHR ZEILEN in der Spielerliste. Bleibt ein Rest
 * uebrig, der fuer keine ganze Zeile mehr reicht, wird er gleichmaessig auf
 * oberen/unteren Innenrand des Panels verteilt, damit das Board exakt auf der
 * gewuenschten Blockzahl endet.
 *
 * Die Breite ist immer {@value #BOARD_WIDTH} Bloecke.
 */
public final class HologramLayout {

    public static final double BOARD_WIDTH = 3.0;
    public static final double DEFAULT_HEIGHT = 3.0;
    public static final double MIN_HEIGHT = 3.0;
    public static final double MAX_HEIGHT = 12.0;

    static final double MARGIN = 0.03;
    static final double PADDING = 0.10;
    static final double GAP = 0.07;
    /** Platz, den eine Trennlinie inkl. Luft nach oben/unten belegt. */
    static final double SEPARATOR_SPACE = 0.13;
    static final double SEPARATOR_THICKNESS = 0.010;
    static final double PAGER_HEIGHT = 0.34;
    static final double MODE_HEIGHT = 0.40;
    static final double ARROW_WIDTH = 0.62;
    static final double PILL_WIDTH = 1.30;
    static final double MODE_GAP = 0.08;

    /**
     * Zeilenabstand eines Text-Displays bei Skalierung 1, in Bloecken.
     *
     * Ein Textpixel ist 1/40 Block. Eine Zeile belegt in der Textbox 10 Pixel
     * (8 px Zeichen + 2 px Abstand) -> 10/40 = 0,25 Bloecke. Da die komplette
     * Eintragsliste EIN Text-Display mit mehreren Zeilen ist, MUSS der
     * Zeilenabstand des Layouts exakt dieser Wert sein: ist er zu klein,
     * wandert jede weitere Zeile ein Stueck nach oben aus ihrem Feld heraus -
     * bei wenigen Zeilen kaum sichtbar, bei vielen Zeilen landet der erste
     * Platz irgendwann im Titel.
     */
    private static final double DEFAULT_LINE_HEIGHT = 0.25;

    /**
     * Hoehe der Textmitte ueber dem Ankerpunkt eines Text-Displays (bei Skalierung 1).
     *
     * Ein Text-Display haengt am UNTEREN Rand seiner Textbox. Die unterste Zeile
     * belegt dort die Pixel 1 bis 9 -> geometrische Mitte = 5 px = 0,125 Bloecke.
     * Grossbuchstaben und Small Caps nutzen die Unterlaenge nicht, sitzen also
     * optisch einen halben Pixel hoeher: 5,5 px = 0,1375 Bloecke. Genau dieser
     * Wert sorgt dafuer, dass Trennlinien und Buttons mittig zum Text sitzen.
     */
    private static final double DEFAULT_TEXT_CENTER_OFFSET = 0.1375;

    private final double textCenterOffset;
    /** Die per Command gesetzte Wunschhoehe - dient zum Erkennen von Aenderungen. */
    private final double requestedHeight;

    /** Wie viele Eintraege auf eine Seite passen - ergibt sich aus der Hoehe. */
    public final int perPage;
    public final float textScale;
    final float buttonTextScale;
    final float pagerTextScale;
    final float arrowTextScale;
    /** Zeilenabstand des Listentexts in Bloecken. */
    final double line;

    /** Linke Kante und Breite des nutzbaren Bereichs (u). */
    final double left;
    final double width;

    /** Tatsaechliche Gesamthoehe (== gewuenschte Hoehe, solange sie nicht unter das Minimum faellt). */
    final double totalHeight;
    final double panelBottom;
    final double panelHeight;
    final double pagerBottom;
    final double modeBottom;

    final double titleCenter;
    final double separatorTopCenter;
    final double separatorBottomCenter;
    final double selfCenter;
    private final double rowsTop;

    /** Mitte des Listentexts (u) - seit dem Wegfall der Spielerkoepfe genau mittig. */
    final double listU;

    public HologramLayout(FileConfiguration cfg, double requestedHeight) {
        textScale = (float) clamp(cfg.getDouble("leaderboard.layout.text-scale", 0.85), 0.4, 1.5);
        buttonTextScale = (float) clamp(cfg.getDouble("leaderboard.layout.button-text-scale", 0.62), 0.3, 1.2);
        pagerTextScale = (float) clamp(cfg.getDouble("leaderboard.layout.pager-text-scale", 0.62), 0.3, 1.2);
        arrowTextScale = (float) clamp(cfg.getDouble("leaderboard.layout.arrow-text-scale", 0.80), 0.3, 1.2);
        textCenterOffset = clamp(cfg.getDouble("leaderboard.layout.text-center-offset", DEFAULT_TEXT_CENTER_OFFSET),
                0.0, 0.3);
        int maxEntries = (int) clamp(cfg.getInt("leaderboard.layout.max-entries-per-page", 50), 1, 60);
        double lineHeight = clamp(cfg.getDouble("leaderboard.layout.line-height", DEFAULT_LINE_HEIGHT), 0.15, 0.4);

        this.requestedHeight = requestedHeight;

        line = lineHeight * textScale;
        left = -(BOARD_WIDTH / 2.0 - MARGIN);
        width = BOARD_WIDTH - 2 * MARGIN;
        listU = 0.0;

        // Alles, was unabhaengig von der Zeilenzahl immer da ist.
        double fixed = 2 * MARGIN                       // Rand oben/unten
                + 2 * PADDING                           // Innenrand des Panels
                + line                                  // Titelzeile
                + SEPARATOR_SPACE                       // obere Trennlinie
                + SEPARATOR_SPACE                       // untere Trennlinie
                + line                                  // "Du"-Zeile
                + GAP + PAGER_HEIGHT                    // Blaetter-Leiste
                + GAP + MODE_HEIGHT;                    // Modus-Leiste

        double height = clamp(requestedHeight, MIN_HEIGHT, MAX_HEIGHT);
        int rows = (int) Math.floor((height - fixed) / line);
        rows = (int) clamp(rows, 1, maxEntries);
        perPage = rows;

        // Board nie kleiner machen als das, was tatsaechlich hineinpasst, und immer
        // auf VOLLE BLOECKE aufrunden - sonst haengt es zwischen zwei Blockkanten.
        double needed = fixed + rows * line;
        totalHeight = Math.ceil(Math.max(height, needed) - 0.0001);
        // Rest, der fuer keine weitere Zeile reicht -> als Luft ins Panel (oben/unten gleich viel).
        double slack = Math.max(0.0, totalHeight - needed) / 2.0;

        panelHeight = 2 * PADDING + 2 * slack + line + SEPARATOR_SPACE + rows * line + SEPARATOR_SPACE + line;

        // Der angeklickte Block ist der UNTERSTE Block des Boards: die Unterkante
        // liegt genau auf seiner unteren Blockkante (v = -0,5 relativ zur Blockmitte),
        // gewachsen wird ausschliesslich nach oben. Zusammen mit der immer ganzzahligen
        // Gesamthoehe sitzt das Board dadurch exakt im Blockraster.
        double bottom = -0.5;
        double top = bottom + totalHeight;
        double panelTop = top - MARGIN;
        panelBottom = panelTop - panelHeight;
        pagerBottom = panelBottom - GAP - PAGER_HEIGHT;
        modeBottom = pagerBottom - GAP - MODE_HEIGHT;

        double innerTop = panelTop - PADDING - slack;
        titleCenter = innerTop - line / 2.0;
        separatorTopCenter = innerTop - line - SEPARATOR_SPACE / 2.0;
        rowsTop = innerTop - line - SEPARATOR_SPACE;
        double rowsBottom = rowsTop - rows * line;
        separatorBottomCenter = rowsBottom - SEPARATOR_SPACE / 2.0;
        selfCenter = rowsBottom - SEPARATOR_SPACE - line / 2.0;
    }

    /** Breite eines der drei Modus-Buttons. */
    double modeWidth() {
        return (width - 2 * MODE_GAP) / 3.0;
    }

    /** Linke Kante des Modus-Buttons {@code index} (0 = Global, 1 = Monat, 2 = Woche). */
    double modeLeft(int index) {
        return left + index * (modeWidth() + MODE_GAP);
    }

    /** Hoehe der Textmitte ueber dem Ankerpunkt eines Text-Displays bei Skalierung 1. */
    double centerOffset() {
        return textCenterOffset;
    }

    /** Die tatsaechliche Gesamthoehe in (immer ganzen) Bloecken. */
    public double effectiveHeight() {
        return totalHeight;
    }

    /** Die Hoehe, mit der dieses Layout angefordert wurde (vor dem Begrenzen auf Min/Max). */
    public double requestedHeight() {
        return requestedHeight;
    }

    /**
     * Wie viele Eintraege bei der gegebenen Hoehe angezeigt werden koennen -
     * fuer die Rueckmeldung von {@code /leaderboard height}.
     */
    public static int entriesForHeight(FileConfiguration cfg, double height) {
        return new HologramLayout(cfg, height).perPage;
    }

    /** Mitte der Zeile {@code index} (0 = oberste) der Eintragsliste. */
    double rowCenter(int index) {
        return rowsTop - line * (index + 0.5);
    }

    double pagerCenter() {
        return pagerBottom + PAGER_HEIGHT / 2.0;
    }

    double modeCenter() {
        return modeBottom + MODE_HEIGHT / 2.0;
    }

    /** v-Position (Ankerpunkt) eines Text-Displays, dessen unterste Zeile bei {@code lineCenter} liegen soll. */
    double textPivot(double lineCenter, double scale) {
        return lineCenter - textCenterOffset * scale;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
