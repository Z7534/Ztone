package de.Z7534.ztone.pvpmanager.managers;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.hooks.ThirdPartyHook;
import de.Z7534.ztone.pvpmanager.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Zeigt Combat-Infos an (Nametag, ActionBar, BossBar).
 *
 * Nametag-Logik:
 *  - Ist TAB installiert und "Plugin Hooks.TAB.Show Above Player" aktiv, wird AUSSCHLIESSLICH die TAB-API benutzt.
 *    Bukkit-Scoreboard-Teams werden dann nicht angefasst, weil sich TAB und Bukkit-Teams sonst gegenseitig
 *    den Client-Zustand zerschiessen (Nametag bleibt haengen bis zum Rejoin).
 *  - Sonst wird pro Spieler ein eigenes Team "pm_<name>" registriert und am Ende des Combats wieder GELOESCHT
 *    (unregister). Das sendet dem Client ein vollstaendiges "Team entfernen" - zuverlaessiger als jeder Refresh-Trick.
 */
public class DisplayManager {

    private static final String TEAM_PREFIX = "pm_";

    private final Pvpmanager plugin;
    private final Map<UUID, BossBar> activeBossBars = new ConcurrentHashMap<>();

    /** Team, in dem der Spieler vor dem Combat war (z. B. von einem Rang-Plugin). */
    private final Map<UUID, String> previousTeams = new ConcurrentHashMap<>();
    /** Spieler, bei denen aktuell ein TAB-Override gesetzt ist. */
    private final Set<UUID> tabOverrides = ConcurrentHashMap.newKeySet();

    private TabBridge tabBridge;
    private boolean errorLogged;

    public DisplayManager(Pvpmanager plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------------------------------
    //  Oeffentliche API
    // ------------------------------------------------------------------------------------------

    public void updateCombatDisplay(Player player, long remainingSeconds) {
        ConfigurationSection displayCfg = plugin.getConfigManager().config()
                .getConfigurationSection("Combat Tag.Display");
        if (displayCfg == null) {
            return;
        }

        if (displayCfg.getBoolean("Nametags.Enabled", true)
                && plugin.getConfigManager().config().getBoolean("General Settings.Use Scoreboard Teams", true)) {
            try {
                applyNametag(player, displayCfg);
            } catch (Throwable throwable) {
                // ActionBar/BossBar sollen trotzdem weiterlaufen, der Fehler soll aber sichtbar sein
                logOnce("Nametag konnte nicht gesetzt werden", throwable);
            }
        }

        if (displayCfg.getBoolean("Action Bar.Enabled", true)) {
            sendActionBar(player, displayCfg, remainingSeconds);
        }

        if (displayCfg.getBoolean("Boss Bar.Enabled", true)) {
            updateBossBar(player, displayCfg, remainingSeconds);
        }
    }

    public void clearCombatDisplay(Player player) {
        try {
            removeNametag(player);
        } catch (Throwable throwable) {
            logOnce("Nametag konnte nicht entfernt werden", throwable);
        }
        BossBar bar = activeBossBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removePlayer(player);
        }
    }

    /**
     * Entfernt Reste aus dem Hauptscoreboard (z. B. nach einem Crash oder aus einer alten Plugin-Version,
     * die in scoreboard.dat gespeichert wurden). Beim Start und beim Stoppen aufrufen.
     */
    public void cleanupStaleTeams() {
        if (Bukkit.getScoreboardManager() == null) {
            return;
        }
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team team : new ArrayList<>(scoreboard.getTeams())) {
            if (team.getName().startsWith(TEAM_PREFIX)) {
                team.unregister();
            }
        }
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearCombatDisplay(player);
        }
        cleanupStaleTeams();
        for (BossBar bar : activeBossBars.values()) {
            bar.removeAll();
        }
        activeBossBars.clear();
    }

    // ------------------------------------------------------------------------------------------
    //  Nametag: setzen
    // ------------------------------------------------------------------------------------------

    private void applyNametag(Player player, ConfigurationSection displayCfg) {
        String configPrefix = ColorUtils.colorize(displayCfg.getString("Nametags.Prefix", "&4⚔ &c"));
        String configSuffix = ColorUtils.colorize(displayCfg.getString("Nametags.Suffix", ""));

        if (tabNametagsEnabled()) {
            TabBridge.Result result = applyTab(player,
                    resolvePlaceholderApi(player, configPrefix),
                    resolvePlaceholderApi(player, configSuffix));
            if (result == TabBridge.Result.APPLIED) {
                tabOverrides.add(player.getUniqueId());
                return;
            }
            if (result == TabBridge.Result.PLAYER_NOT_READY) {
                return; // TAB kennt den Spieler noch nicht - naechste Sekunde nochmal versuchen
            }
            // NO_NAMETAG_FEATURE: TAB verwaltet keine Nametags -> normale Scoreboard-Teams
        }

        applyBukkitTeam(player, configPrefix, configSuffix);
    }

    private boolean tabNametagsEnabled() {
        ThirdPartyHook tab = plugin.getHookManager().tab();
        return tab.isPresent() && tab.configFlag("Show Above Player");
    }

    private TabBridge.Result applyTab(Player player, String prefix, String suffix) {
        if (tabBridge == null) {
            tabBridge = TabBridge.create();
            if (tabBridge == null) {
                logOnce("TAB gefunden, aber die TAB-API konnte nicht geladen werden - nutze Scoreboard-Teams", null);
                return TabBridge.Result.NO_NAMETAG_FEATURE;
            }
        }
        try {
            return tabBridge.apply(player.getUniqueId(), prefix, suffix);
        } catch (Throwable throwable) {
            tabBridge = null; // beim naechsten Mal neu aufloesen (z. B. nach /tab reload)
            logOnce("TAB-Nametag konnte nicht gesetzt werden - nutze Scoreboard-Teams", throwable);
            return TabBridge.Result.NO_NAMETAG_FEATURE;
        }
    }

    private void applyBukkitTeam(Player player, String configPrefix, String configSuffix) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        UUID uuid = player.getUniqueId();
        String entry = player.getName();
        String teamName = getTeamName(player);

        // WICHTIG: Das Team muss existieren, sonst gibt getTeam() null zurueck
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }

        // Fremdes Team (z. B. Rang-Plugin) merken, damit es am Ende wiederhergestellt wird
        Team existing = scoreboard.getEntryTeam(entry);
        if (existing != null && !existing.getName().equals(teamName)) {
            previousTeams.put(uuid, existing.getName());
        }

        String basePrefix = "";
        String baseSuffix = "";
        String previousName = previousTeams.get(uuid);
        Team previousTeam = previousName == null ? null : scoreboard.getTeam(previousName);
        if (previousTeam != null) {
            basePrefix = previousTeam.getPrefix();
            baseSuffix = previousTeam.getSuffix();
        }

        // Rot, damit setGlowing(true) rot leuchtet
        team.setColor(ChatColor.RED);
        team.setPrefix(resolvePlaceholderApi(player, join(basePrefix, configPrefix)));
        team.setSuffix(resolvePlaceholderApi(player, join(baseSuffix, configSuffix)));

        if (!team.hasEntry(entry)) {
            team.addEntry(entry);
        }
    }

    // ------------------------------------------------------------------------------------------
    //  Nametag: entfernen
    // ------------------------------------------------------------------------------------------

    private void removeNametag(Player player) {
        UUID uuid = player.getUniqueId();

        if (tabOverrides.remove(uuid) && tabBridge != null) {
            try {
                tabBridge.reset(uuid);
            } catch (Throwable throwable) {
                logOnce("TAB-Nametag konnte nicht zurueckgesetzt werden", throwable);
            }
        }

        removeBukkitTeam(player);
    }

    private void removeBukkitTeam(Player player) {
        if (Bukkit.getScoreboardManager() == null) {
            return;
        }
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        UUID uuid = player.getUniqueId();
        String entry = player.getName();

        // Team komplett loeschen -> Client bekommt "Team entfernt" und muss den Nametag neu berechnen.
        // Das entfernt auch den Eintrag des Spielers aus dem Team (und aus scoreboard.dat).
        Team combatTeam = scoreboard.getTeam(getTeamName(player));
        if (combatTeam != null) {
            combatTeam.unregister();
        }

        // Vorheriges Team wiederherstellen (nur wenn wir den Spieler vorher daraus geholt haben)
        String previousName = previousTeams.remove(uuid);
        if (previousName != null && player.isOnline()) {
            Team previousTeam = scoreboard.getTeam(previousName);
            if (previousTeam != null && !previousTeam.hasEntry(entry)) {
                previousTeam.addEntry(entry);
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    //  ActionBar / BossBar
    // ------------------------------------------------------------------------------------------

    private void sendActionBar(Player player, ConfigurationSection displayCfg, long remainingSeconds) {
        int totalBars = displayCfg.getInt("Action Bar.Total Bars", 20);
        String symbol = displayCfg.getString("Action Bar.Symbol", "▪");
        int totalSeconds = plugin.getConfigManager().config().getInt("Combat Tag.Time", 15);
        int barsPassed = totalSeconds > 0
                ? (int) Math.round(totalBars * (1.0 - ((double) remainingSeconds / totalSeconds)))
                : 0;
        barsPassed = Math.max(0, Math.min(totalBars, barsPassed));
        int barsLeft = totalBars - barsPassed;

        String format = displayCfg.getString("Action Bar.Message", "&7<time>s &8• &a<barsLeft>&7<barsPassed>");
        String message = format
                .replace("<time>", String.valueOf(remainingSeconds))
                .replace("<barsLeft>", symbol.repeat(barsLeft))
                .replace("<barsPassed>", symbol.repeat(barsPassed));

        message = resolvePlaceholderApi(player, message);
        player.sendActionBar(ColorUtils.colorize(message));
    }

    private void updateBossBar(Player player, ConfigurationSection displayCfg, long remainingSeconds) {
        BossBar bar = activeBossBars.computeIfAbsent(player.getUniqueId(), uuid -> {
            BarColor color = safeEnum(BarColor.class, displayCfg.getString("Boss Bar.BarColor", "RED"), BarColor.RED);
            BarStyle style = safeEnum(BarStyle.class, displayCfg.getString("Boss Bar.BarStyle", "SOLID"), BarStyle.SOLID);
            BossBar newBar = Bukkit.createBossBar("Combat", color, style);
            newBar.addPlayer(player);
            return newBar;
        });

        int totalSeconds = plugin.getConfigManager().config().getInt("Combat Tag.Time", 15);
        double progress = totalSeconds > 0 ? Math.min(1.0, (double) remainingSeconds / totalSeconds) : 0.0;
        bar.setProgress(progress);

        String format = displayCfg.getString("Boss Bar.Message", "&c<time> seconds");
        String message = format.replace("<time>", String.valueOf(remainingSeconds));
        bar.setTitle(ColorUtils.colorize(message));
    }

    // ------------------------------------------------------------------------------------------
    //  Helfer
    // ------------------------------------------------------------------------------------------

    private String getTeamName(Player player) {
        String name = player.getName();
        if (name.length() > 13) {
            name = name.substring(0, 13);
        }
        return TEAM_PREFIX + name;
    }

    private static String join(String base, String extra) {
        if (base == null || base.isEmpty()) {
            return extra;
        }
        if (extra == null || extra.isEmpty()) {
            return base;
        }
        return base + " " + extra;
    }

    private String resolvePlaceholderApi(Player player, String message) {
        if (plugin.getHookManager().placeholderApi() == null) {
            return message;
        }
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, message);
        } catch (Throwable throwable) {
            return message;
        }
    }

    private <T extends Enum<T>> T safeEnum(Class<T> type, String value, T fallback) {
        try {
            return Enum.valueOf(type, value.toUpperCase());
        } catch (Exception exception) {
            return fallback;
        }
    }

    /** Loggt jeden Fehler nur einmal, damit die Konsole nicht im Sekundentakt zugespammt wird. */
    private void logOnce(String message, Throwable throwable) {
        if (errorLogged) {
            return;
        }
        errorLogged = true;
        if (throwable != null) {
            plugin.getLogger().log(Level.WARNING, "[Display] " + message + " (weitere Fehler dieser Art werden nicht mehr geloggt)", throwable);
        } else {
            plugin.getLogger().warning("[Display] " + message);
        }
    }

    // ------------------------------------------------------------------------------------------
    //  TAB-Anbindung per Reflection (keine harte Abhaengigkeit)
    //  Die Methoden werden am Interface aufgeloest, mit den ECHTEN Parametertypen (TabPlayer, String).
    //  Die alte Version hat getMethod("setPrefix", Object.class, String.class) benutzt - das existiert nicht,
    //  die NoSuchMethodException wurde von "catch (Throwable ignored)" verschluckt.
    // ------------------------------------------------------------------------------------------

    private static final class TabBridge {

        enum Result { APPLIED, NO_NAMETAG_FEATURE, PLAYER_NOT_READY }

        private final Object api;
        private final Method getPlayer;
        private final Method getNameTagManager;
        private final Method setPrefix;
        private final Method setSuffix;
        private final Method getOriginalRawPrefix; // optional
        private final Method getOriginalRawSuffix; // optional

        private TabBridge(Object api, Method getPlayer, Method getNameTagManager,
                          Method setPrefix, Method setSuffix,
                          Method getOriginalRawPrefix, Method getOriginalRawSuffix) {
            this.api = api;
            this.getPlayer = getPlayer;
            this.getNameTagManager = getNameTagManager;
            this.setPrefix = setPrefix;
            this.setSuffix = setSuffix;
            this.getOriginalRawPrefix = getOriginalRawPrefix;
            this.getOriginalRawSuffix = getOriginalRawSuffix;
        }

        static TabBridge create() {
            try {
                Class<?> apiClass = Class.forName("me.neznamy.tab.api.TabAPI");
                Class<?> tabPlayerClass = Class.forName("me.neznamy.tab.api.TabPlayer");
                Class<?> nameTagClass = Class.forName("me.neznamy.tab.api.nametag.NameTagManager");

                Object api = apiClass.getMethod("getInstance").invoke(null);
                if (api == null) {
                    return null;
                }
                return new TabBridge(
                        api,
                        apiClass.getMethod("getPlayer", UUID.class),
                        apiClass.getMethod("getNameTagManager"),
                        nameTagClass.getMethod("setPrefix", tabPlayerClass, String.class),
                        nameTagClass.getMethod("setSuffix", tabPlayerClass, String.class),
                        optional(nameTagClass, "getOriginalRawPrefix", tabPlayerClass),
                        optional(nameTagClass, "getOriginalRawSuffix", tabPlayerClass)
                );
            } catch (Throwable throwable) {
                return null;
            }
        }

        private static Method optional(Class<?> type, String name, Class<?>... params) {
            try {
                return type.getMethod(name, params);
            } catch (NoSuchMethodException exception) {
                return null;
            }
        }

        Result apply(UUID uuid, String prefix, String suffix) throws ReflectiveOperationException {
            // Den Manager jedes Mal neu holen: nach /tab reload ist er ein neues Objekt
            Object manager = getNameTagManager.invoke(api);
            if (manager == null) {
                return Result.NO_NAMETAG_FEATURE;
            }
            Object tabPlayer = getPlayer.invoke(api, uuid);
            if (tabPlayer == null) {
                return Result.PLAYER_NOT_READY;
            }

            // Rang-Prefix/-Suffix aus der TAB-Config beibehalten (kommt aus der Config, nicht aus dem Override,
            // deshalb summiert sich nichts auf)
            String basePrefix = original(manager, getOriginalRawPrefix, tabPlayer);
            String baseSuffix = original(manager, getOriginalRawSuffix, tabPlayer);

            setPrefix.invoke(manager, tabPlayer, join(basePrefix, prefix));
            setSuffix.invoke(manager, tabPlayer, join(baseSuffix, suffix));
            return Result.APPLIED;
        }

        void reset(UUID uuid) throws ReflectiveOperationException {
            Object manager = getNameTagManager.invoke(api);
            Object tabPlayer = getPlayer.invoke(api, uuid);
            if (manager == null || tabPlayer == null) {
                return;
            }
            // null = Override entfernen, TAB nimmt wieder Prefix/Suffix aus der eigenen Config
            setPrefix.invoke(manager, tabPlayer, (String) null);
            setSuffix.invoke(manager, tabPlayer, (String) null);
        }

        private static String original(Object manager, Method method, Object tabPlayer) {
            if (method == null) {
                return "";
            }
            try {
                Object value = method.invoke(manager, tabPlayer);
                return value == null ? "" : value.toString();
            } catch (ReflectiveOperationException exception) {
                return "";
            }
        }
    }
}