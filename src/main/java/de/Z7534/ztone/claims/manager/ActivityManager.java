package de.Z7534.ztone.claims.manager;

import de.Z7534.ztone.claims.config.ConfigManager;
import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.claims.model.PlayerData;
import de.Z7534.ztone.claims.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ActivityManager {

    private final JavaPlugin plugin;
    private final ClaimManager claimManager;
    private final ConfigManager config;

    private final Map<UUID, Long> lastActivity = new HashMap<>();
    private final Map<UUID, Boolean> afk = new HashMap<>();
    private final Map<UUID, Long> lastClaimId = new HashMap<>();
    private int tickCounter = 0;

    public ActivityManager(JavaPlugin plugin, ClaimManager claimManager, ConfigManager config) {
        this.plugin = plugin;
        this.claimManager = claimManager;
        this.config = config;
    }

    public void recordActivity(UUID uuid) {
        lastActivity.put(uuid, System.currentTimeMillis());
    }

    public void forgetPlayer(UUID uuid) {
        lastActivity.remove(uuid);
        afk.remove(uuid);
        lastClaimId.remove(uuid);
    }

    public boolean isAfk(UUID uuid) {
        return afk.getOrDefault(uuid, false);
    }

    public void tick() {
        long now = System.currentTimeMillis();
        long timeoutMs = config.afkTimeoutSeconds() * 1000L;
        long intervalSeconds = (long) config.awardIntervalMinutes() * 60L;
        tickCounter++;
        boolean periodicSave = (tickCounter % 60 == 0);

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            lastActivity.putIfAbsent(uuid, now);
            boolean nowAfk = (now - lastActivity.get(uuid)) > timeoutMs;
            afk.put(uuid, nowAfk);

            PlayerData pd = claimManager.getOrCreatePlayerData(uuid, player.getName());

            if (!nowAfk) {
                pd.setPlaytimeSeconds(pd.playtimeSeconds() + 1);
                boolean awarded = false;
                while (pd.playtimeSeconds() >= intervalSeconds) {
                    pd.setPlaytimeSeconds(pd.playtimeSeconds() - intervalSeconds);
                    pd.setBonusClaimSlots(pd.bonusClaimSlots() + config.awardAmount());
                    awarded = true;
                }
                if (awarded) {
                    claimManager.savePlayerAsync(pd);
                    player.sendMessage(Text.line(config.prefix() + config.message("claim-slot-awarded")));
                }
            }

            checkEntryHint(player, player.getLocation(), pd);

            if (periodicSave) {
                claimManager.savePlayerAsync(pd);
            }
        }
    }

    public void checkEntryHint(Player player) {
        checkEntryHint(player, player.getLocation());
    }

    public void checkEntryHint(Player player, Location location) {
        PlayerData pd = claimManager.getOrCreatePlayerData(player.getUniqueId(), player.getName());
        checkEntryHint(player, location, pd);
    }

    private void checkEntryHint(Player player, Location location, PlayerData pd) {
        if (!pd.entryHintEnabled()) return;
        UUID uuid = player.getUniqueId();
        Claim claim = claimManager.getClaimAt(location);
        long currentId = claim != null ? claim.id() : 0L;
        Long last = lastClaimId.get(uuid);
        if (last == null || last != currentId) {
            lastClaimId.put(uuid, currentId);
            sendEntryHint(player, claim);
        }
    }

    private void sendEntryHint(Player player, Claim claim) {
        Component msg;
        if (claim == null) {
            msg = Text.line(config.message("entry-wilderness"));
        } else if (claim.owner().equals(player.getUniqueId())) {
            msg = Text.line(config.message("entry-own-claim"));
        } else {
            String ownerName = Bukkit.getOfflinePlayer(claim.owner()).getName();
            msg = Text.line(config.message("entry-foreign-claim"), Map.of("besitzer", ownerName != null ? ownerName : "?"));
        }
        player.sendActionBar(msg);
    }
}
