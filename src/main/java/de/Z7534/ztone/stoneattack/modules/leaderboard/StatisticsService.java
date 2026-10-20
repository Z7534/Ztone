package de.Z7534.ztone.stoneattack.modules.leaderboard;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.Z7534.ztone.stoneattack.Stoneattack;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Liefert die Roh-Statistikwerte fuer playtime/kills/deaths/money.
 *
 * WICHTIGE ENTSCHEIDUNG (siehe Aufgabenstellung "bitte zuverlaessigere
 * Variante waehlen und kommentieren"):
 * - Fuer ONLINE-Spieler wird der lebende Player ueber die normale
 *   Bukkit-Statistic-API abgefragt. Das MUSS auf dem Main-Thread
 *   passieren (Bukkit-Objekte sind nicht threadsicher).
 * - Fuer OFFLINE-Spieler wird NICHT OfflinePlayer#getStatistic()
 *   verwendet, sondern DIREKT die stats-JSON-Datei aus
 *   "<world>/stats/<uuid>.json" gelesen. Grund: Paper/Bukkit-Aufrufe,
 *   die Spielerdaten laden, sind offiziell nicht fuer Aufrufe ausserhalb
 *   des Main-Threads freigegeben (Paper wirft dafuer teils sogar aktiv
 *   Fehler/Warnungen). Das direkte Einlesen der JSON-Datei ist reines,
 *   sicheres Datei-IO und funktioniert zuverlaessig von jedem Thread
 *   aus - genau das, was wir brauchen, da der komplette Scan asynchron
 *   laufen soll.
 * - Money kommt in beiden Faellen ueber die Vault-Economy-API, da
 *   Vault/Economy-Implementierungen (z. B. EssentialsX) in der Praxis
 *   threadsicher fuer reine Lesezugriffe sind.
 */
public class StatisticsService {

    private final Stoneattack plugin;

    public StatisticsService(Stoneattack plugin) {
        this.plugin = plugin;
    }

    /** NUR vom Main-Thread aus aufrufen! */
    public Map<UUID, Double> collectOnlineValues(LeaderboardType type) {
        Map<UUID, Double> result = new HashMap<>();
        String placeholder = configuredPlaceholder(type);
        if (placeholder != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                result.put(player.getUniqueId(), readPlaceholderValue(player, type, placeholder));
            }
            return result;
        }
        if (type == LeaderboardType.MONEY) {
            Economy economy = plugin.getEconomy();
            if (economy == null) {
                return result;
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                result.put(player.getUniqueId(), economy.getBalance(player));
            }
            return result;
        }

        Statistic statistic = toBukkitStatistic(type);
        for (Player player : Bukkit.getOnlinePlayers()) {
            result.put(player.getUniqueId(), (double) player.getStatistic(statistic));
        }
        return result;
    }

    /** Darf/soll von einem Async-Thread aus aufgerufen werden. */
    public double readOfflineValue(OfflinePlayer offlinePlayer, LeaderboardType type) {
        String placeholder = configuredPlaceholder(type);
        if (placeholder != null) {
            return readPlaceholderValue(offlinePlayer, type, placeholder);
        }
        if (type == LeaderboardType.MONEY) {
            Economy economy = plugin.getEconomy();
            if (economy == null) {
                return 0.0;
            }
            return economy.getBalance(offlinePlayer);
        }
        return readFromStatsJson(offlinePlayer.getUniqueId(), type);
    }

    // ------------------------------------------------------------
    // Optionale Wertquelle: beliebiger PlaceholderAPI-Platzhalter
    // ------------------------------------------------------------

    private final Set<LeaderboardType> warned = ConcurrentHashMap.newKeySet();

    /** Nach /stoneattack reload: Warnungen duerfen wieder einmal erscheinen. */
    public void resetWarnings() {
        warned.clear();
    }

    /**
     * Der in {@code leaderboard.value-placeholders.<typ>.placeholder} eingetragene Platzhalter,
     * oder {@code null}, wenn keiner gesetzt ist (-> normale Quelle: Vanilla-Statistik / Vault).
     * Ist PlaceholderAPI nicht installiert, wird einmalig gewarnt und die normale Quelle genutzt.
     */
    private String configuredPlaceholder(LeaderboardType type) {
        String raw = plugin.getConfigManager().get()
                .getString("leaderboard.value-placeholders." + type.key() + ".placeholder", "");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            warnOnce(type, "Fuer das Leaderboard '" + type.key() + "' ist ein Platzhalter eingetragen, aber "
                    + "PlaceholderAPI ist nicht installiert - es wird die normale Quelle verwendet.");
            return null;
        }
        return raw.trim();
    }

    private double readPlaceholderValue(OfflinePlayer player, LeaderboardType type, String placeholder) {
        double value;
        try {
            value = PlaceholderValueSource.read(player, placeholder);
        } catch (RuntimeException | LinkageError e) {
            warnOnce(type, "Platzhalter " + placeholder + " (Leaderboard '" + type.key() + "') warf einen Fehler: " + e);
            return 0.0;
        }
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            warnOnce(type, "Platzhalter " + placeholder + " (Leaderboard '" + type.key() + "') liefert keine Zahl fuer "
                    + player.getName() + " - Wert wird als 0 gewertet. Tipp: /papi parse " + player.getName()
                    + " " + placeholder);
            return 0.0;
        }
        value = Math.max(0.0, value);
        if (type == LeaderboardType.PLAYTIME) {
            String unit = plugin.getConfigManager().get()
                    .getString("leaderboard.value-placeholders.playtime.unit", "seconds");
            value = PlaceholderValueSource.playtimeToTicks(value, unit);
        }
        return value;
    }

    private void warnOnce(LeaderboardType type, String message) {
        if (warned.add(type)) {
            plugin.getLogger().warning(message);
        }
    }

    private double readFromStatsJson(UUID uuid, LeaderboardType type) {
        if (!plugin.getConfigManager().get().getBoolean("leaderboard.statistics-fallback-to-json", true)) {
            return 0.0;
        }
        Path statsFile = Path.of(Bukkit.getWorlds().get(0).getWorldFolder().getAbsolutePath(),
                "stats", uuid + ".json");
        if (!Files.exists(statsFile)) {
            return 0.0;
        }
        try (FileReader reader = new FileReader(statsFile.toFile())) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject stats = root.getAsJsonObject("stats");
            if (stats == null) {
                return 0.0;
            }
            JsonObject custom = stats.getAsJsonObject("minecraft:custom");
            if (custom == null) {
                return 0.0;
            }
            String key = switch (type) {
                case PLAYTIME -> "minecraft:play_time";
                case KILLS -> "minecraft:player_kills";
                case DEATHS -> "minecraft:deaths";
                case MONEY -> throw new IllegalStateException("Money wird nicht aus stats.json gelesen");
            };
            if (custom.has(key)) {
                return custom.get(key).getAsLong();
            }
            return 0.0;
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().fine("Konnte stats.json fuer " + uuid + " nicht lesen: " + e.getMessage());
            return 0.0;
        }
    }

    private Statistic toBukkitStatistic(LeaderboardType type) {
        return switch (type) {
            // PLAY_ONE_MINUTE ist der langjaehrige Bukkit-Name fuer die
            // vanilla Statistik "minecraft:play_time" (Gesamtspielzeit
            // in Ticks) - trotz des irrefuehrenden Namens NICHT auf
            // eine Minute begrenzt.
            case PLAYTIME -> Statistic.PLAY_ONE_MINUTE;
            case KILLS -> Statistic.PLAYER_KILLS;
            case DEATHS -> Statistic.DEATHS;
            default -> throw new IllegalArgumentException("Kein Bukkit-Statistic fuer " + type);
        };
    }
}
