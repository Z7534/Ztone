package de.Z7534.ztone.claims.manager;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlayerLookupService {

    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\"");
    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final Pattern NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

    private final JavaPlugin plugin;
    private final ClaimManager claimManager;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public PlayerLookupService(JavaPlugin plugin, ClaimManager claimManager) {
        this.plugin = plugin;
        this.claimManager = claimManager;
    }

    public record Resolved(UUID uuid, String name) {
    }

    public CompletableFuture<Optional<Resolved>> resolve(String rawName) {

        org.bukkit.entity.Player online = Bukkit.getPlayerExact(rawName);
        if (online != null) {
            return CompletableFuture.completedFuture(Optional.of(new Resolved(online.getUniqueId(), online.getName())));
        }

        return CompletableFuture.supplyAsync(() -> {
          try {

            Optional<de.Z7534.ztone.claims.model.PlayerData> known = claimManager.findPlayerDataByNameBlocking(rawName);
            if (known.isPresent()) {
                return Optional.of(new Resolved(known.get().uuid(), known.get().name()));
            }

            @SuppressWarnings("deprecation")
            OfflinePlayer offline = Bukkit.getOfflinePlayer(rawName);
            if (offline.hasPlayedBefore() && offline.getUniqueId() != null) {
                return Optional.of(new Resolved(offline.getUniqueId(), offline.getName() != null ? offline.getName() : rawName));
            }

            if (Bukkit.getOnlineMode() && VALID_NAME.matcher(rawName).matches()) {
                Optional<Resolved> mojang = queryMojang(rawName);
                if (mojang.isPresent()) {
                    return mojang;
                }
            }
            return Optional.<Resolved>empty();
          } catch (Exception ex) {
            // Das Future darf nie exceptional enden, sonst bekommt der Spieler/Admin gar keine Rückmeldung.
            plugin.getLogger().warning("Spielersuche für '" + rawName + "' fehlgeschlagen: " + ex.getMessage());
            return Optional.<Resolved>empty();
          }
        });
    }

    private Optional<Resolved> queryMojang(String name) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + name))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            String body = response.body();
            Matcher idMatcher = ID_PATTERN.matcher(body);
            Matcher nameMatcher = NAME_PATTERN.matcher(body);
            if (idMatcher.find() && nameMatcher.find()) {
                UUID uuid = dashify(idMatcher.group(1));
                return Optional.of(new Resolved(uuid, nameMatcher.group(1)));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Mojang-API-Anfrage für '" + name + "' fehlgeschlagen: " + e.getMessage());
        }
        return Optional.empty();
    }

    private UUID dashify(String raw) {
        String d = raw.replaceFirst(
                "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
                "$1-$2-$3-$4-$5");
        return UUID.fromString(d);
    }
}
