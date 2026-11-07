package de.Z7534.ztone.stoneattack.modules.leaderboard.hologram;

import de.Z7534.ztone.stoneattack.modules.leaderboard.TimePeriod;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Der PRO SPIELER sichtbare Teil eines Leaderboards (nur fuer diesen einen
 * Spieler eingeblendet): Titel, 4-Spalten-Eintragsliste (Slots), "Du"-Zeile, Seitenanzeige,
 * die Beschriftungen der Buttons/Pfeile, die Button-ItemDisplays und Hover-Zustände.
 */
public class HologramInstance {

    TextDisplay title;
    TextDisplay list;

    /** 4-Spalten-Eintragszeilen des Leaderboards (dynamisch nach Board-Höhe) */
    final List<TextDisplay> rowDisplays = new ArrayList<>();       // Spalte 1: Rang-Badge
    final List<TextDisplay> rowNameDisplays = new ArrayList<>();   // Spalte 2: Spielername (skalierbar)
    final List<TextDisplay> rowArrowDisplays = new ArrayList<>();  // Spalte 3: Trennpfeil »
    final List<TextDisplay> rowValueDisplays = new ArrayList<>();  // Spalte 4: Wert & Icon

    /** "Du"-Zeile im Footer (identische 4 Spalten) */
    TextDisplay self;       // DU-Badge
    TextDisplay selfName;   // #ownRank
    TextDisplay selfArrow;  // »
    TextDisplay selfValue;  // Punktestand

    /** null, wenn die Seitenanzeige in der Config abgeschaltet ist. */
    TextDisplay pager;
    TextDisplay arrowLeft;
    TextDisplay arrowRight;

    /** Welche Modus-Markierung diesem Spieler aktuell gezeigt wird (null = noch keine). */
    TimePeriod shownHighlight;

    /** Beschriftungen der drei Modus-Buttons (Global, Monat, Woche) - pro Spieler, damit sie einzeln animierbar sind. */
    final TextDisplay[] modeLabels = new TextDisplay[3];

    /** Alle beim Hover animierten Beschriftungen (Modus-Buttons + Pfeile). */
    final Map<ClickBinding.Action, HoverLabel> labels = new EnumMap<>(ClickBinding.Action.class);

    /** Modus A: Custom Button-ItemDisplays für Resourcepack (CustomModelData 1001/1002/1003) */
    final Map<ClickBinding.Action, ItemDisplay> buttonItemDisplays = new EnumMap<>(ClickBinding.Action.class);

    /** Hover-Cursor: einer fuer die drei Modus-Buttons, einer fuer die Pfeile (erst beim ersten Hover erzeugt). */
    HoverCursor modeCursor;
    HoverCursor arrowCursor;

    /** Button, auf den dieser Spieler gerade schaut (null = keiner). */
    ClickBinding.Action hoveredButton;

    /** Zuletzt gezeichnete Seite / Seitenzahl - damit ein Pfeil, der nichts tut, nicht leuchtet. */
    int page = 1;
    int maxPage = 1;

    /** Zuletzt geklickter Button und Ticks seit dem Klick (fuer die Druck-Animation). */
    ClickBinding.Action clickedAction;
    int sinceClick;

    /**
     * Zustand, der zuletzt gezeichnet wurde (Datenstand + Seite + Zeitraum).
     * Aendert sich nichts, wird der komplette Text-Aufbau uebersprungen -
     * dadurch kann sehr oft getickt werden, ohne Leistung zu kosten.
     */
    String renderKey;

    public List<Entity> all() {
        List<Entity> all = new ArrayList<>();
        for (Entity e : new Entity[]{title, list, self, selfName, selfArrow, selfValue, pager, arrowLeft, arrowRight}) {
            if (e != null) {
                all.add(e);
            }
        }
        for (TextDisplay row : rowDisplays) {
            if (row != null) all.add(row);
        }
        for (TextDisplay row : rowNameDisplays) {
            if (row != null) all.add(row);
        }
        for (TextDisplay row : rowArrowDisplays) {
            if (row != null) all.add(row);
        }
        for (TextDisplay row : rowValueDisplays) {
            if (row != null) all.add(row);
        }
        for (TextDisplay label : modeLabels) {
            if (label != null) {
                all.add(label);
            }
        }
        all.addAll(buttonItemDisplays.values());
        if (modeCursor != null) {
            all.addAll(modeCursor.entities());
        }
        if (arrowCursor != null) {
            all.addAll(arrowCursor.entities());
        }
        return all;
    }

    public boolean isValid() {
        for (Entity e : all()) {
            if (e == null || !e.isValid()) {
                return false;
            }
        }
        return title != null;
    }
}
