package de.Z7534.ztone.claims.manager;

import de.Z7534.ztone.claims.model.Claim;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Verwaltet den PvP-Claim-Bypass.
 * <p>
 * Erlaubt es Admins, für bestimmte Claims PvP dauerhaft oder temporär zuzulassen
 * (/pvpclaimbypass), oder sich selbst / Spielern einen Bypass zu geben, um auf
 * jedem Claim kämpfen zu können.
 */
public final class PvPClaimBypassManager {

    public static final String PERMISSION = "claims.admin.pvpbypass";

    private final JavaPlugin plugin;
    private final ClaimManager claimManager;
    private final Set<Long> bypassedClaimIds = ConcurrentHashMap.newKeySet();
    private final Set<UUID> bypassedPlayers = ConcurrentHashMap.newKeySet();
    private final File storageFile;

    public PvPClaimBypassManager(JavaPlugin plugin, ClaimManager claimManager) {
        this.plugin = plugin;
        this.claimManager = claimManager;
        File folder = new File(plugin.getDataFolder(), "claims");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        this.storageFile = new File(folder, "pvp_bypassed_claims.json");
    }

    public void load() {
        if (!storageFile.exists()) {
            return;
        }
        try (FileReader reader = new FileReader(storageFile)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[1024];
            int read;
            while ((read = reader.read(buf)) != -1) {
                sb.append(buf, 0, read);
            }
            String content = sb.toString().trim();
            if (content.startsWith("[") && content.endsWith("]")) {
                content = content.substring(1, content.length() - 1).trim();
                if (!content.isEmpty()) {
                    String[] ids = content.split(",");
                    for (String idStr : ids) {
                        try {
                            bypassedClaimIds.add(Long.parseLong(idStr.trim()));
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            plugin.getLogger().info("PvP-Claim-Bypasses geladen: " + bypassedClaimIds.size() + " Claim(s).");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Fehler beim Laden von pvp_bypassed_claims.json", e);
        }
    }

    public synchronized void save() {
        try {
            if (!storageFile.exists()) {
                File parent = storageFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                storageFile.createNewFile();
            }
            try (FileWriter writer = new FileWriter(storageFile)) {
                StringBuilder sb = new StringBuilder("[");
                int count = 0;
                for (Long id : bypassedClaimIds) {
                    if (count > 0) sb.append(", ");
                    sb.append(id);
                    count++;
                }
                sb.append("]");
                writer.write(sb.toString());
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Fehler beim Speichern von pvp_bypassed_claims.json", e);
        }
    }

    /**
     * Prüft, ob auf dem Claim mit der angegebenen ID PvP freigegeben ist.
     */
    public boolean isClaimBypassed(long claimId) {
        return bypassedClaimIds.contains(claimId);
    }

    /**
     * Prüft, ob an der Position ein Claim liegt und dieser PvP-bypassed ist.
     */
    public boolean isClaimBypassed(Location loc) {
        if (loc == null) return false;
        Claim claim = claimManager.getClaimAt(loc);
        return claim != null && isClaimBypassed(claim.id());
    }

    public void setClaimBypassed(long claimId, boolean bypassed) {
        if (bypassed) {
            bypassedClaimIds.add(claimId);
        } else {
            bypassedClaimIds.remove(claimId);
        }
        save();
    }

    public boolean toggleClaimBypass(long claimId) {
        boolean newState;
        if (bypassedClaimIds.contains(claimId)) {
            bypassedClaimIds.remove(claimId);
            newState = false;
        } else {
            bypassedClaimIds.add(claimId);
            newState = true;
        }
        save();
        return newState;
    }

    public boolean isPlayerBypassed(Player player) {
        if (player == null) return false;
        if (!bypassedPlayers.contains(player.getUniqueId())) {
            return false;
        }
        if (!player.hasPermission(PERMISSION)) {
            bypassedPlayers.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public void setPlayerBypassed(UUID playerUuid, boolean bypassed) {
        if (bypassed) {
            bypassedPlayers.add(playerUuid);
        } else {
            bypassedPlayers.remove(playerUuid);
        }
    }

    public boolean togglePlayerBypass(Player player) {
        UUID uuid = player.getUniqueId();
        if (bypassedPlayers.remove(uuid)) {
            return false;
        }
        bypassedPlayers.add(uuid);
        return true;
    }

    public Set<Long> getBypassedClaimIds() {
        return Collections.unmodifiableSet(bypassedClaimIds);
    }

    public Set<UUID> getBypassedPlayerUuids() {
        return Collections.unmodifiableSet(bypassedPlayers);
    }
}
