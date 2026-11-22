package de.Z7534.ztone.claims.manager;

import de.Z7534.ztone.claims.model.Claim;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Verwaltet die Nametag-Deaktivierung auf Claims (Admin-Override).
 * <p>
 * Wenn für einen Claim Nametags deaktiviert sind, wird für jeden Spieler,
 * der sich auf diesem Claim befindet (sofern er keine Bypass-Permission hat),
 * der Nametag über dem Kopf ausgeblendet (via TAB-API oder Scoreboard-Team).
 */
public final class ClaimNametagManager {

    public static final String PERMISSION = "claims.admin.nametags";
    public static final String PERMISSION_BYPASS = "claims.admin.nametags.bypass";

    private static final String TEAM_PREFIX = "cnm_";

    private final JavaPlugin plugin;
    private final ClaimManager claimManager;
    private final BypassManager bypassManager;
    private final Set<Long> disabledClaimIds = ConcurrentHashMap.newKeySet();
    private final Set<UUID> hiddenPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> tabHiddenPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> previousTeams = new ConcurrentHashMap<>();
    private final File storageFile;

    private TabBridge tabBridge;

    public ClaimNametagManager(JavaPlugin plugin, ClaimManager claimManager, BypassManager bypassManager) {
        this.plugin = plugin;
        this.claimManager = claimManager;
        this.bypassManager = bypassManager;
        File folder = new File(plugin.getDataFolder(), "claims");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        this.storageFile = new File(folder, "nametag_disabled_claims.json");
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
                            disabledClaimIds.add(Long.parseLong(idStr.trim()));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
            plugin.getLogger().info("Claim-Nametag-Overrides geladen: " + disabledClaimIds.size() + " Claim(s).");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Fehler beim Laden von nametag_disabled_claims.json", e);
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
                for (Long id : disabledClaimIds) {
                    if (count > 0) sb.append(", ");
                    sb.append(id);
                    count++;
                }
                sb.append("]");
                writer.write(sb.toString());
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Fehler beim Speichern von nametag_disabled_claims.json", e);
        }
    }

    public boolean isNametagDisabled(long claimId) {
        return disabledClaimIds.contains(claimId);
    }

    public boolean isNametagDisabled(Location loc) {
        if (loc == null) return false;
        Claim claim = claimManager.getClaimAt(loc);
        return claim != null && isNametagDisabled(claim.id());
    }

    public void setNametagDisabled(long claimId, boolean disabled) {
        if (disabled) {
            disabledClaimIds.add(claimId);
        } else {
            disabledClaimIds.remove(claimId);
        }
        save();
        refreshAllOnlinePlayers();
    }

    public boolean toggleNametagDisabled(long claimId) {
        boolean newState;
        if (disabledClaimIds.contains(claimId)) {
            disabledClaimIds.remove(claimId);
            newState = false;
        } else {
            disabledClaimIds.add(claimId);
            newState = true;
        }
        save();
        refreshAllOnlinePlayers();
        return newState;
    }

    public Set<Long> getDisabledClaimIds() {
        return Collections.unmodifiableSet(disabledClaimIds);
    }

    /**
     * Aktualisiert den Nametag-Status für alle aktuell online befindlichen Spieler.
     */
    public void refreshAllOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player, player.getLocation());
        }
    }

    /**
     * Prüft, ob der Nametag des Spielers an der angegebenen Position ausgeblendet sein muss,
     * und wendet die Änderung bei Bedarf sofort an.
     */
    public void updatePlayer(Player player, Location location) {
        if (player == null || !player.isOnline()) return;

        boolean shouldHide = false;
        if (location != null && !disabledClaimIds.isEmpty()) {
            Claim claim = claimManager.getClaimAt(location);
            if (claim != null && disabledClaimIds.contains(claim.id())) {
                if ((bypassManager == null || !bypassManager.isActive(player))
                        && !player.hasPermission(PERMISSION_BYPASS)) {
                    shouldHide = true;
                }
            }
        }

        UUID uuid = player.getUniqueId();
        if (shouldHide) {
            if (hiddenPlayers.add(uuid)) {
                hideNametag(player);
            } else {
                // Falls TAB den Spieler beim ersten Versuch noch nicht kannte oder Bukkit-Team verloren ging:
                ensureHidden(player);
            }
        } else {
            if (hiddenPlayers.remove(uuid)) {
                showNametag(player);
            }
        }
    }

    public void handleQuit(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        if (hiddenPlayers.remove(uuid)) {
            showNametag(player);
        }
        previousTeams.remove(uuid);
        tabHiddenPlayers.remove(uuid);
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (hiddenPlayers.remove(player.getUniqueId())) {
                showNametag(player);
            }
        }
        hiddenPlayers.clear();
        tabHiddenPlayers.clear();
        previousTeams.clear();
        cleanupStaleTeams();
    }

    public void cleanupStaleTeams() {
        if (Bukkit.getScoreboardManager() == null) {
            return;
        }
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team team : scoreboard.getTeams()) {
            if (team.getName().startsWith(TEAM_PREFIX)) {
                try {
                    team.unregister();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void hideNametag(Player player) {
        if (tryHideTab(player)) {
            tabHiddenPlayers.add(player.getUniqueId());
            return;
        }
        applyBukkitHideTeam(player);
    }

    private void ensureHidden(Player player) {
        UUID uuid = player.getUniqueId();
        if (tabHiddenPlayers.contains(uuid)) {
            tryHideTab(player);
            return;
        }
        if (tryHideTab(player)) {
            removeBukkitHideTeam(player);
            tabHiddenPlayers.add(uuid);
            return;
        }
        applyBukkitHideTeam(player);
    }

    private void showNametag(Player player) {
        UUID uuid = player.getUniqueId();
        if (tabHiddenPlayers.remove(uuid)) {
            tryShowTab(player);
        }
        removeBukkitHideTeam(player);
    }

    private boolean tryHideTab(Player player) {
        if (Bukkit.getPluginManager().getPlugin("TAB") == null) {
            return false;
        }
        if (tabBridge == null) {
            tabBridge = TabBridge.create();
            if (tabBridge == null) {
                return false;
            }
        }
        try {
            return tabBridge.hide(player.getUniqueId());
        } catch (Throwable t) {
            tabBridge = null;
            return false;
        }
    }

    private void tryShowTab(Player player) {
        if (tabBridge == null) {
            tabBridge = TabBridge.create();
        }
        if (tabBridge != null) {
            try {
                tabBridge.show(player.getUniqueId());
            } catch (Throwable t) {
                tabBridge = null;
            }
        }
    }

    private void applyBukkitHideTeam(Player player) {
        if (Bukkit.getScoreboardManager() == null) return;
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        UUID uuid = player.getUniqueId();
        String entry = player.getName();
        String teamName = getTeamName(player);

        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }

        Team existing = scoreboard.getEntryTeam(entry);
        if (existing != null && !existing.getName().equals(teamName)) {
            previousTeams.put(uuid, existing.getName());
            team.setPrefix(existing.getPrefix());
            team.setSuffix(existing.getSuffix());
            try {
                team.setColor(existing.getColor());
            } catch (Throwable ignored) {
            }
        }

        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        if (!team.hasEntry(entry)) {
            team.addEntry(entry);
        }
    }

    private void removeBukkitHideTeam(Player player) {
        if (Bukkit.getScoreboardManager() == null) return;
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        UUID uuid = player.getUniqueId();
        String entry = player.getName();

        Team hideTeam = scoreboard.getTeam(getTeamName(player));
        if (hideTeam != null) {
            try {
                hideTeam.unregister();
            } catch (Throwable ignored) {
            }
        }

        String previousName = previousTeams.remove(uuid);
        if (previousName != null && player.isOnline()) {
            Team previousTeam = scoreboard.getTeam(previousName);
            if (previousTeam != null && !previousTeam.hasEntry(entry)) {
                previousTeam.addEntry(entry);
            }
        }
    }

    private String getTeamName(Player player) {
        String name = player.getName();
        if (name.length() > 12) {
            name = name.substring(0, 12);
        }
        return TEAM_PREFIX + name;
    }

    private static final class TabBridge {

        private final Object api;
        private final Method getPlayer;
        private final Method getNameTagManager;
        private final Method hideNameTag;
        private final Method showNameTag;

        private TabBridge(Object api, Method getPlayer, Method getNameTagManager,
                          Method hideNameTag, Method showNameTag) {
            this.api = api;
            this.getPlayer = getPlayer;
            this.getNameTagManager = getNameTagManager;
            this.hideNameTag = hideNameTag;
            this.showNameTag = showNameTag;
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
                        nameTagClass.getMethod("hideNameTag", tabPlayerClass),
                        nameTagClass.getMethod("showNameTag", tabPlayerClass)
                );
            } catch (Throwable ignored) {
                return null;
            }
        }

        boolean hide(UUID uuid) throws ReflectiveOperationException {
            Object manager = getNameTagManager.invoke(api);
            if (manager == null) {
                return false;
            }
            Object tabPlayer = getPlayer.invoke(api, uuid);
            if (tabPlayer == null) {
                return false;
            }
            hideNameTag.invoke(manager, tabPlayer);
            return true;
        }

        void show(UUID uuid) throws ReflectiveOperationException {
            Object manager = getNameTagManager.invoke(api);
            Object tabPlayer = getPlayer.invoke(api, uuid);
            if (manager == null || tabPlayer == null) {
                return;
            }
            showNameTag.invoke(manager, tabPlayer);
        }
    }
}
