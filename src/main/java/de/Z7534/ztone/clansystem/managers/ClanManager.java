package de.Z7534.ztone.clansystem.managers;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.models.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClanManager {

    private final Ztone plugin;
    private final Map<Integer, Clan> clansById;
    private final Map<String, Clan> clansByName;
    private final Map<UUID, Clan> playerClans;

    public ClanManager(Ztone plugin) {
        this.plugin = plugin;
        this.clansById = new ConcurrentHashMap<>();
        this.clansByName = new ConcurrentHashMap<>();
        this.playerClans = new ConcurrentHashMap<>();
    }

    public void loadAllClans() {
        clansById.clear();
        clansByName.clear();
        playerClans.clear();

        List<Clan> clans = plugin.getDatabaseManager().loadAllClans();
        for (Clan clan : clans) {
            registerClan(clan);
        }

        List<ClanWar> activeWars = plugin.getDatabaseManager().loadActiveWars();
        for (ClanWar war : activeWars) {
            Clan attacker = getClan(war.getAttackerClanId());
            Clan defender = getClan(war.getDefenderClanId());
            if (attacker != null && defender != null) {
                attacker.addWar(defender.getId());
                defender.addWar(attacker.getId());
                plugin.getWarManager().registerWar(war);
            }
        }

        plugin.getLogger().info("Loaded " + clans.size() + " clans from database.");
    }

    public void saveAllClans() {
        de.Z7534.ztone.clansystem.gui.ClanChestGUI.closeAllOpenChests();
        for (Clan clan : clansById.values()) {
            plugin.getDatabaseManager().updateClan(clan);
            plugin.getDatabaseManager().saveChestSync(clan.getId(), clan.getChestContents());
        }
        plugin.getLogger().info("Saved " + clansById.size() + " clans to database.");
    }

    private void registerClan(Clan clan) {
        clansById.put(clan.getId(), clan);
        clansByName.put(clan.getName().toLowerCase(), clan);
        for (ClanMember member : clan.getMembers().values()) {
            playerClans.put(member.getUuid(), clan);
        }
    }

    public Clan createClan(String name, Player leader) {
        Clan clan = new Clan(name, leader.getUniqueId());

        clan.setIcon(plugin.getLevelManager().getBaseIcon());

        clan.resizeChest(plugin.getLevelManager().getChestSize(clan.getLevel()));

        List<ClanRank> defaultRanks = plugin.getConfigManager().getDefaultRanks();

        return plugin.getDatabaseManager().createClan(clan).thenApply(clanId -> {
            if (clanId == -1) {
                return null;
            }
            clan.setId(clanId);

            for (ClanRank rankTemplate : defaultRanks) {
                ClanRank rank = new ClanRank(rankTemplate.getName(), rankTemplate.getPriority());
                rank.setClanId(clanId);
                rank.setPermissions(new HashSet<>(rankTemplate.getPermissions()));

                int rankId = plugin.getDatabaseManager().createRank(rank).join();
                rank.setId(rankId);
                clan.addRank(rank);
            }

            ClanRank leaderRank = clan.getHighestRank();
            ClanMember leaderMember = new ClanMember(leader.getUniqueId(), clanId, leaderRank.getId());
            leaderMember.setRank(leaderRank);
            leaderMember.setLeader(true);
            clan.addMember(leaderMember);
            plugin.getDatabaseManager().addMember(leaderMember);

            registerClan(clan);

            return clan;
        }).join();
    }

    public void disbandClan(Clan clan) {

        for (int warClanId : new HashSet<>(clan.getActiveWars())) {
            plugin.getWarManager().endWar(clan.getId(), warClanId, null);
        }

        for (int allyId : new HashSet<>(clan.getAllies())) {
            plugin.getAllianceManager().breakAlliance(clan.getId(), allyId);
        }

        plugin.getGlowManager().disband(clan);

        clansById.remove(clan.getId());
        clansByName.remove(clan.getName().toLowerCase());
        for (ClanMember member : clan.getMembers().values()) {
            playerClans.remove(member.getUuid());
        }

        plugin.getDatabaseManager().deleteClan(clan.getId());
    }

    public void addMember(Clan clan, Player player, ClanRank rank) {
        ClanMember member = new ClanMember(player.getUniqueId(), clan.getId(), rank.getId());
        member.setRank(rank);
        clan.addMember(member);
        playerClans.put(player.getUniqueId(), clan);
        plugin.getDatabaseManager().addMember(member);

        plugin.getGlowManager().applyToPlayer(player);
        plugin.getGlowManager().refreshVisibilityForNewMember(clan, player);
    }

    public boolean transferLeadership(Clan clan, UUID newLeaderUuid) {
        ClanMember newLeader = clan.getMember(newLeaderUuid);
        if (newLeader == null) {
            return false;
        }

        ClanMember oldLeader = clan.getMember(clan.getLeaderUuid());
        if (oldLeader != null) {
            oldLeader.setLeader(false);
        }

        newLeader.setLeader(true);
        clan.setLeaderUuid(newLeaderUuid);

        ClanRank highestRank = clan.getHighestRank();
        if (highestRank != null) {
            newLeader.setRank(highestRank);
            plugin.getDatabaseManager().updateMember(newLeader);
        }

        updateClanInDatabase(clan);
        plugin.getGlowManager().refresh(clan);
        return true;
    }

    public void removeMember(Clan clan, UUID uuid) {
        Player player = plugin.getServer().getPlayer(uuid);

        clan.removeMember(uuid);
        playerClans.remove(uuid);
        plugin.getDatabaseManager().removeMember(uuid);

        if (player != null) {

            plugin.getGlowManager().removeFromPlayer(player);
            plugin.getGlowManager().refreshVisibilityForLeavingMember(clan, player);
        }
    }

    public Clan getClan(int id) {
        return clansById.get(id);
    }

    public Clan getClan(String name) {
        return clansByName.get(name.toLowerCase());
    }

    public Clan getClanByPlayer(UUID uuid) {
        return playerClans.get(uuid);
    }

    public Clan getClanByPlayer(Player player) {
        return getClanByPlayer(player.getUniqueId());
    }

    public boolean isInClan(UUID uuid) {
        return playerClans.containsKey(uuid);
    }

    public boolean isInClan(Player player) {
        return isInClan(player.getUniqueId());
    }

    public boolean clanExists(String name) {
        return clansByName.containsKey(name.toLowerCase());
    }

    public Collection<Clan> getAllClans() {
        return Collections.unmodifiableCollection(clansById.values());
    }

    public List<Clan> getClansSortedByLevel() {
        List<Clan> sorted = new ArrayList<>(clansById.values());
        sorted.sort((a, b) -> {
            int levelCompare = Integer.compare(b.getLevel(), a.getLevel());
            if (levelCompare != 0) return levelCompare;
            return Integer.compare(b.getPoints(), a.getPoints());
        });
        return sorted;
    }

    public List<Clan> getClansSortedByPoints() {
        List<Clan> sorted = new ArrayList<>(clansById.values());
        sorted.sort((a, b) -> Integer.compare(b.getPoints(), a.getPoints()));
        return sorted;
    }

    public List<Clan> getClansSortedByMembers() {
        List<Clan> sorted = new ArrayList<>(clansById.values());
        sorted.sort((a, b) -> Integer.compare(b.getMemberCount(), a.getMemberCount()));
        return sorted;
    }

    public boolean renameClan(Clan clan, String newName) {
        if (clanExists(newName)) {
            return false;
        }
        String oldName = clan.getName();
        clansByName.remove(oldName.toLowerCase());
        clan.setName(newName);
        clansByName.put(newName.toLowerCase(), clan);
        updateClanInDatabase(clan);
        return true;
    }

    public boolean isValidClanName(String name) {
        ConfigManager config = plugin.getConfigManager();

        if (name.length() < config.getClanNameMinLength()) {
            return false;
        }
        if (name.length() > config.getClanNameMaxLength()) {
            return false;
        }
        if (config.getClanNamePattern() != null && !config.getClanNamePattern().matcher(name).matches()) {
            return false;
        }
        return true;
    }

    public void updateClanInDatabase(Clan clan) {
        plugin.getDatabaseManager().updateClan(clan);
    }

    public void saveClanChest(Clan clan) {
        plugin.getDatabaseManager().saveChest(clan.getId(), clan.getChestContents());
    }

    public void checkAndApplyLevelUp(Clan clan) {
        LevelManager levelManager = plugin.getLevelManager();
        boolean leveledUp = levelManager.checkLevelUp(clan);

        if (leveledUp) {
            updateClanInDatabase(clan);

            String rewards = levelManager.getRewardsDescription(clan.getLevel());
            for (ClanMember member : clan.getOnlineMembers()) {
                Player player = member.getPlayer();
                if (player != null) {
                    plugin.getMessageManager().send(player, "level.up",
                            MessageManager.of("level", String.valueOf(clan.getLevel())));
                    if (!rewards.isEmpty()) {
                        plugin.getMessageManager().send(player, "level.rewards",
                                MessageManager.of("rewards", rewards));
                    }
                }
            }
        }
    }

    public void addPointsToClan(Clan clan, int points) {
        clan.addPoints(points);
        updateClanInDatabase(clan);
        checkAndApplyLevelUp(clan);
    }

    public enum RankDeleteResult {
        SUCCESS,
        NOT_FOUND,
        HAS_MEMBERS,
        LAST_RANK
    }

    /**
     * Löscht einen Rang aus einem Clan, sofern das sicher möglich ist.
     * Zentral an einer Stelle implementiert, damit sowohl der /clan-Befehl
     * als auch die Rang-GUI (Rechtsklick -> Bestätigung) dieselben
     * Sicherheitsprüfungen verwenden.
     */
    public RankDeleteResult deleteRank(Clan clan, ClanRank rank) {
        if (rank == null || clan.getRank(rank.getId()) == null) {
            return RankDeleteResult.NOT_FOUND;
        }

        long membersWithRank = clan.getMembers().values().stream()
                .filter(m -> m.getRankId() == rank.getId())
                .count();

        if (membersWithRank > 0) {
            return RankDeleteResult.HAS_MEMBERS;
        }

        // Ein Clan muss immer mindestens einen Rang behalten, da neue
        // Mitglieder (z.B. beim Beitritt) automatisch über
        // Clan#getLowestRank() einen Rang zugewiesen bekommen. Ohne diese
        // Prüfung könnte man alle Ränge einzeln löschen (jeweils mit 0
        // Mitgliedern) und getLowestRank() würde anschließend null
        // zurückgeben -> NullPointerException beim nächsten Beitritt.
        if (clan.getSortedRanks().size() <= 1) {
            return RankDeleteResult.LAST_RANK;
        }

        clan.removeRank(rank.getId());
        plugin.getDatabaseManager().deleteRank(rank.getId());
        return RankDeleteResult.SUCCESS;
    }

    public enum RankCreateResult {
        SUCCESS,
        ALREADY_EXISTS
    }

    /**
     * Erstellt einen neuen Rang im übergebenen Clan. Arbeitet direkt auf dem
     * übergebenen Clan-Objekt statt über den /clan-Befehl, damit das auch
     * aus einer Admin-Ansicht (/clanadmin settings <clan>) heraus für den
     * richtigen Clan funktioniert - siehe deleteRank()-Kommentar oben zum
     * gleichen Problem bei performCommand().
     */
    public RankCreateResult createRank(Clan clan, String name) {
        if (clan.getRankByName(name) != null) {
            return RankCreateResult.ALREADY_EXISTS;
        }

        ClanRank newRank = new ClanRank(name, 1);
        newRank.setClanId(clan.getId());
        clan.addRank(newRank);

        plugin.getDatabaseManager().createRank(newRank).thenAccept(rankId -> {
            clan.removeRank(newRank.getId());
            newRank.setId(rankId);
            clan.addRank(newRank);
        });

        return RankCreateResult.SUCCESS;
    }

    /**
     * Benennt einen bereits aufgelösten Rang um. Der Aufrufer übergibt das
     * konkrete ClanRank-Objekt (statt eines Namens), damit hier - anders als
     * beim alten performCommand()-Weg - garantiert der richtige Clan/Rang
     * bearbeitet wird. Verhindert außerdem, dass zwei Ränge im selben Clan
     * denselben Namen bekommen (das würde Clan#getRankByName() für Befehle
     * wie "/clan rank set" oder "/clan rank delete" mehrdeutig machen).
     */
    public enum RankRenameResult {
        SUCCESS,
        NAME_TAKEN
    }

    public RankRenameResult renameRank(Clan clan, ClanRank rank, String newName) {
        ClanRank existing = clan.getRankByName(newName);
        if (existing != null && existing.getId() != rank.getId()) {
            return RankRenameResult.NAME_TAKEN;
        }

        rank.setName(newName);
        plugin.getDatabaseManager().updateRank(rank);
        return RankRenameResult.SUCCESS;
    }

    public List<Clan> getClansSortedByBalance() {
        List<Clan> sorted = new ArrayList<>(clansById.values());
        sorted.sort((a, b) -> Double.compare(b.getBalance(), a.getBalance()));
        return sorted;
    }

    public List<Clan> getTopClansByBalance(int limit) {
        return getClansSortedByBalance().stream().limit(limit).collect(java.util.stream.Collectors.toList());
    }
}
