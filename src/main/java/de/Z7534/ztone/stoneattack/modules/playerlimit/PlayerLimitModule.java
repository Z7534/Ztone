package de.Z7534.ztone.stoneattack.modules.playerlimit;

import de.Z7534.ztone.stoneattack.Stoneattack;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Modul 1: Dynamisches Spielerlimit + Join-Reihenfolge.
 *
 * Wichtig: Das Limit wird NICHT ueber server.properties (max-players)
 * gesteuert. Stattdessen entscheidet dieses Modul in JEDEM
 * PlayerLoginEvent selbst, ob ein Spieler reinkommt - unabhaengig vom
 * Vanilla-Wert. Dadurch funktioniert der Bypass zuverlaessig auch bei
 * "vollem" Server, ohne fragile Reflection auf interne Server-Felder.
 */
public class PlayerLimitModule {

    private final Stoneattack plugin;
    private final JoinOrderManager joinOrder = new JoinOrderManager();
    private JoinOrderLogger logger;

    private static final String CONFIG_PATH = "playerlimit.default-limit";

    private volatile int limit;

    public PlayerLimitModule(Stoneattack plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        this.logger = new JoinOrderLogger(plugin);
        loadState();

        // Bereits online befindliche Spieler (z. B. nach /stoneattack reload)
        // in die Reihenfolge einsortieren, sofern noch nicht enthalten.
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.hasPermission("playerlimit.bypass") && !joinOrder.contains(p.getUniqueId())) {
                joinOrder.add(p.getUniqueId(), p.getName());
            }
        }
    }

    public void disable() {
        if (logger != null) {
            logger.shutdown();
        }
        // Das Limit wird bei jedem /playerlimit set|off sofort in die config.yml
        // geschrieben - hier ist nichts mehr zu speichern.
    }

    /** Liest das Limit erneut aus der config.yml (z. B. nach /stoneattack reload oder manueller Aenderung). */
    public void reload() {
        this.limit = plugin.getConfigManager().get().getInt(CONFIG_PATH, -1);
    }

    public JoinOrderManager getJoinOrder() {
        return joinOrder;
    }

    public JoinOrderLogger getLogger() {
        return logger;
    }

    public int getLimit() {
        return limit;
    }

    public boolean isEnabled() {
        return limit >= 0;
    }

    public void setLimit(int newLimit) {
        this.limit = newLimit;
        persistLimit();
    }

    public void disableLimit() {
        this.limit = -1;
        persistLimit();
    }

    /**
     * Wird aus dem PlayerLoginEvent aufgerufen. Entscheidet, ob der
     * Spieler reinkommen darf, und traegt ihn ggf. in die Join-
     * Reihenfolge ein.
     */
    public boolean canJoin(Player player) {
        if (player.hasPermission("playerlimit.bypass")) {
            return true;
        }
        if (limit < 0) {
            return true;
        }
        int currentOnline = Bukkit.getOnlinePlayers().size();
        if (currentOnline + 1 > limit) {
            return false;
        }
        joinOrder.add(player.getUniqueId(), player.getName());
        return true;
    }

    public void onQuit(Player player) {
        joinOrder.remove(player.getUniqueId());
    }

    /**
     * Berechnet, welche Spieler aufgrund eines neuen (kleineren)
     * Limits gekickt werden muessen: die zuletzt gejointen Nicht-
     * Bypass-Spieler, "von hinten nach vorne". Bypass-Spieler werden
     * niemals eingerechnet.
     */
    public List<Player> calculatePlayersToKick(int newLimit) {
        int online = Bukkit.getOnlinePlayers().size();
        int kicksNeeded = online - newLimit;
        List<Player> result = new ArrayList<>();
        if (kicksNeeded <= 0) {
            return result;
        }

        List<Map.Entry<UUID, String>> ordered = joinOrder.snapshot();
        int available = ordered.size();
        int actualKicks = Math.min(kicksNeeded, available);

        for (int i = 0; i < actualKicks; i++) {
            UUID uuid = ordered.get(available - 1 - i).getKey();
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                result.add(p);
            }
        }
        return result;
    }

    public int positionOf(OfflinePlayer player) {
        return joinOrder.positionOf(player.getUniqueId());
    }

    // ----------------------------------------------------------------
    // Persistenz des Limits
    //
    // Das per /playerlimit set|off gesetzte Limit wird direkt als
    // "playerlimit.default-limit" in die config.yml geschrieben. Damit ist
    // es gleichzeitig der neue Standardwert und bleibt nach jedem Neustart
    // erhalten (und ist in der config.yml sichtbar/editierbar).
    // ----------------------------------------------------------------

    private void loadState() {
        File legacy = new File(plugin.getDataFolder(), "playerlimit-state.yml");
        if (legacy.exists()) {
            // Migration: aeltere Versionen haben das Limit in einer eigenen Datei
            // gespeichert. Diesen Wert einmalig in die config.yml uebernehmen.
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(legacy);
            this.limit = yaml.getInt("limit", plugin.getConfigManager().get().getInt(CONFIG_PATH, -1));
            persistLimit();
            if (!legacy.delete()) {
                plugin.getLogger().warning("Konnte die alte playerlimit-state.yml nicht loeschen (bitte manuell entfernen).");
            }
            return;
        }
        this.limit = plugin.getConfigManager().get().getInt(CONFIG_PATH, -1);
    }

    private void persistLimit() {
        plugin.getConfigManager().get().set(CONFIG_PATH, limit);
        plugin.getConfigManager().save();
    }
}
