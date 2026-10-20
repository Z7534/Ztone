package de.Z7534.ztone.clansystem.managers;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.models.ClanMember;
import de.Z7534.ztone.clansystem.models.ClanWar;
import de.Z7534.ztone.clansystem.models.Permission;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WarManager {

    private final Ztone plugin;
    private final Map<Integer, ClanWar> activeWars;
    private final Map<String, ClanWar> warsByClanPair;

    public WarManager(Ztone plugin) {
        this.plugin = plugin;
        this.activeWars = new ConcurrentHashMap<>();
        this.warsByClanPair = new ConcurrentHashMap<>();
    }

    public void registerWar(ClanWar war) {
        activeWars.put(war.getId(), war);
        warsByClanPair.put(getWarKey(war.getAttackerClanId(), war.getDefenderClanId()), war);
    }

    private String getWarKey(int clanIdA, int clanIdB) {
        int first = Math.min(clanIdA, clanIdB);
        int second = Math.max(clanIdA, clanIdB);
        return first + "-" + second;
    }

    public ClanWar declareWar(Clan attacker, Clan defender) {
        if (!attacker.hasAnyMemberWithPermission(Permission.WAR_ALWAYS_PVP) || !defender.hasAnyMemberWithPermission(Permission.WAR_ALWAYS_PVP)) {
            return null;
        }
        ClanWar war = new ClanWar(attacker.getId(), defender.getId());

        int warId = plugin.getDatabaseManager().createWar(war).join();
        if (warId == -1) {
            return null;
        }

        war.setId(warId);
        registerWar(war);

        attacker.addWar(defender.getId());
        defender.addWar(attacker.getId());

        String declaredMessage = plugin.getMessageManager().get("broadcast.war-declared",
                MessageManager.of("clan", attacker.getColoredName(), "enemy", defender.getColoredName()));
        attacker.broadcastMessage(declaredMessage);
        defender.broadcastMessage(declaredMessage);

        return war;
    }

    public void endWar(int clanIdA, int clanIdB, Integer winnerId) {
        String key = getWarKey(clanIdA, clanIdB);
        ClanWar war = warsByClanPair.get(key);

        if (war == null || !war.isActive()) {
            return;
        }

        war.end(winnerId);
        plugin.getDatabaseManager().updateWar(war);

        activeWars.remove(war.getId());
        warsByClanPair.remove(key);

        Clan clanA = plugin.getClanManager().getClan(clanIdA);
        Clan clanB = plugin.getClanManager().getClan(clanIdB);

        if (clanA != null) {
            clanA.removeWar(clanIdB);
        }
        if (clanB != null) {
            clanB.removeWar(clanIdA);
        }

        String winnerName = "Unentschieden";
        if (winnerId != null) {
            int loserId = (winnerId == clanIdA) ? clanIdB : clanIdA;
            Clan winner = plugin.getClanManager().getClan(winnerId);
            Clan loser = plugin.getClanManager().getClan(loserId);

            if (winner != null) {
                winnerName = winner.getColoredName();
                plugin.getClanManager().addPointsToClan(winner, 500);
                winner.setBonusWarps(winner.getBonusWarps() + 3);
                winner.setBalance(winner.getBalance() + 50000.0);
                plugin.getClanManager().updateClanInDatabase(winner);

                winner.broadcastMessage("§8[§6Krieg§8] §aDein Clan hat den Krieg gewonnen! Belohnung: §e+500 Punkte§a, §e+3 dauerhafte Warps§a, §e+50.000$ Clankasse§a!");
            }

            if (loser != null) {
                loser.setPoints(Math.max(0, loser.getPoints() - 200));
                loser.setBalance(loser.getBalance() - 50000.0);
                plugin.getClanManager().updateClanInDatabase(loser);

                loser.broadcastMessage("§8[§6Krieg§8] §cDein Clan hat den Krieg verloren! Strafe: §e-200 Punkte§c, §e-50.000$ Clankasse§c!");
                if (loser.isInDebt()) {
                    loser.broadcastMessage("§8[§6Krieg§8] §4ACHTUNG: §cDein Clan ist mit §e-" + String.format(java.util.Locale.GERMANY, "%,.2f", loser.getDebt()) + "$ §cim Minus! Clan-Aktionen sind gesperrt, bis die Schulden per §e/clan deposit §cbeglichen wurden!");
                }
            }
        }

        String endedMessage = plugin.getMessageManager().get("broadcast.war-ended",
                MessageManager.of("clan", clanA != null ? clanA.getColoredName() : "?",
                        "enemy", clanB != null ? clanB.getColoredName() : "?",
                        "winner", winnerName));
        if (clanA != null) {
            clanA.broadcastMessage(endedMessage);
        }
        if (clanB != null) {
            clanB.broadcastMessage(endedMessage);
        }
    }

    public void surrender(Clan surrendering, Clan opponent) {
        ClanWar war = getWarBetween(surrendering.getId(), opponent.getId());
        if (war == null) {
            return;
        }

        endWar(surrendering.getId(), opponent.getId(), opponent.getId());
    }

    public ClanWar getWarBetween(int clanIdA, int clanIdB) {
        return warsByClanPair.get(getWarKey(clanIdA, clanIdB));
    }

    public boolean areAtWar(int clanIdA, int clanIdB) {
        ClanWar war = getWarBetween(clanIdA, clanIdB);
        return war != null && war.isActive();
    }

    public void recordKill(UUID killerUuid, UUID victimUuid, int killerClanId, int victimClanId) {
        ClanWar war = getWarBetween(killerClanId, victimClanId);
        if (war == null || !war.isActive()) {
            return;
        }

        int points = plugin.getConfigManager().getWarKillPoints();
        war.addKill(killerUuid, killerClanId, points);

        plugin.getDatabaseManager().addWarKill(war.getId(), killerUuid, victimUuid, killerClanId, points);
        plugin.getDatabaseManager().updateWar(war);

        Clan killerClan = plugin.getClanManager().getClan(killerClanId);
        Clan victimClan = plugin.getClanManager().getClan(victimClanId);

        if (killerClan != null) {
            plugin.getClanManager().addPointsToClan(killerClan, points);
        }

        if (victimClan != null) {
            ClanMember victimMember = victimClan.getMember(victimUuid);
            if (victimMember != null && victimMember.hasPermission(Permission.WAR_ALWAYS_PVP)) {
                int deaths = war.addParticipantDeath(victimUuid);
                String deathMsg = "§8[§6Krieg§8] §e" + victimMember.getName() + " §7wurde im Clankrieg getötet! (" + (deaths >= 3 ? "§43/3 K.O." : "§c" + deaths + "/3 Tode") + "§7)";
                if (killerClan != null) killerClan.broadcastMessage(deathMsg);
                victimClan.broadcastMessage(deathMsg);

                boolean allDefeated = true;
                for (ClanMember m : victimClan.getMembers().values()) {
                    if (m.hasPermission(Permission.WAR_ALWAYS_PVP)) {
                        if (war.getParticipantDeaths(m.getUuid()) < 3) {
                            allDefeated = false;
                            break;
                        }
                    }
                }
                if (allDefeated) {
                    endWar(killerClanId, victimClanId, killerClanId);
                }
            }
        }
    }

    public List<ClanWar> getActiveWarsForClan(int clanId) {
        List<ClanWar> wars = new ArrayList<>();
        for (ClanWar war : activeWars.values()) {
            if (war.involves(clanId) && war.isActive()) {
                wars.add(war);
            }
        }
        return wars;
    }

    public List<ClanWar> getWarHistory(int clanId) {
        return plugin.getDatabaseManager().loadWarHistory(clanId);
    }

    public Collection<ClanWar> getAllActiveWars() {
        return Collections.unmodifiableCollection(activeWars.values());
    }
}
