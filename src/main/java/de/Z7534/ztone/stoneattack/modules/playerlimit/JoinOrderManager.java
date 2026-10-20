package de.Z7534.ztone.stoneattack.modules.playerlimit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Haelt die reine Join-Reihenfolge der Nicht-Bypass-Spieler.
 * Ein LinkedHashMap behaelt Einfuegereihenfolge automatisch bei -
 * verlaesst ein Spieler den Server, rutscht die Reihenfolge der
 * uebrigen automatisch nach (die Position ist einfach der Index+1
 * zum jeweiligen Abfragezeitpunkt, wird also nirgends separat
 * gespeichert).
 *
 * Es wird ausdruecklich KEINE Spielzeit getrackt, nur die Reihenfolge.
 */
public class JoinOrderManager {

    private final Map<UUID, String> order = Collections.synchronizedMap(new LinkedHashMap<>());

    public void add(UUID uuid, String name) {
        synchronized (order) {
            order.remove(uuid); // falls doppelt (sollte nicht vorkommen) ganz nach hinten neu einreihen
            order.put(uuid, name);
        }
    }

    public void remove(UUID uuid) {
        order.remove(uuid);
    }

    public boolean contains(UUID uuid) {
        return order.containsKey(uuid);
    }

    /** 1-basierte Position des Spielers, oder -1 falls nicht enthalten. */
    public int positionOf(UUID uuid) {
        synchronized (order) {
            int pos = 1;
            for (UUID key : order.keySet()) {
                if (key.equals(uuid)) {
                    return pos;
                }
                pos++;
            }
        }
        return -1;
    }

    public int size() {
        return order.size();
    }

    /** Snapshot in aktueller Join-Reihenfolge (Index 0 = Position 1). */
    public List<Map.Entry<UUID, String>> snapshot() {
        synchronized (order) {
            return new ArrayList<>(order.entrySet());
        }
    }

    public void clear() {
        order.clear();
    }
}
