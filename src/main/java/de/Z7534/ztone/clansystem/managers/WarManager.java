package de.Z7534.ztone.clansystem.managers;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.models.ClanMember;
import de.Z7534.ztone.clansystem.models.ClanWar;
import de.Z7534.ztone.clansystem.models.Permission;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WarManager {

    private final Ztone plugin;
    private final Map<Integer, ClanWar> activeWars;
    private final Map<String, ClanWar> warsByClanPair;
    private final Map<Integer, BukkitTask> preparationTasks;

    public WarManager(Ztone plugin) {
        this.plugin = plugin;
        this.activeWars = new ConcurrentHashMap<>();
        this.warsByClanPair = new ConcurrentHashMap<>();
        this.preparationTasks = new ConcurrentHashMap<>();
    }

    public void registerWar(ClanWar war) {
        activeWars.put(war.getId(), war);
        warsByClanPair.put(getWarKey(war.getAttackerClanId(), war.getDefenderClanId()), war);

        if (war.isInPreparation()) {
            int prepSeconds = plugin.getConfigManager().getWarPreparationTimeSeconds();
            long finishTime = war.getPvpStartTime() > 0 ? war.getPvpStartTime() : (war.getStartedAt() + (prepSeconds * 1000L));
            long remainingMillis = finishTime - System.currentTimeMillis();
            if (remainingMillis > 0) {
                long ticks = Math.max(1L, remainingMillis / 50L);
                schedulePvpActivation(war.getId(), ticks);
            } else {
                activateWarPvP(war.getId());
            }
        }
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
        if (attacker.isInDebt() || defender.isInDebt()) {
            return null;
        }
        if (!attacker.getActiveWars().isEmpty() || !defender.getActiveWars().isEmpty()) {
            return null;
        }
        if (attacker.hasWarCooldown() || defender.hasWarCooldown()) {
            return null;
        }
        if (!attacker.isWarsEnabled() || !defender.isWarsEnabled()) {
            return null;
        }

        ClanWar war = new ClanWar(attacker.getId(), defender.getId());
        int prepSeconds = plugin.getConfigManager().getWarPreparationTimeSeconds();
        if (prepSeconds > 0) {
            war.setStatus(ClanWar.WarStatus.PREPARATION);
            war.setPvpActive(false);
            war.setPvpStartTime(System.currentTimeMillis() + (prepSeconds * 1000L));
        } else {
            war.setStatus(ClanWar.WarStatus.ACTIVE);
            war.setPvpActive(true);
            war.setPvpStartTime(System.currentTimeMillis());
        }

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

        if (prepSeconds > 0) {
            String prepMessage = plugin.getMessageManager().get("war.preparation-started",
                    MessageManager.of("time", String.valueOf(prepSeconds), "clan", attacker.getColoredName(), "enemy", defender.getColoredName()));
            attacker.broadcastMessage(prepMessage);
            defender.broadcastMessage(prepMessage);

            schedulePvpActivation(warId, prepSeconds * 20L);
        }

        return war;
    }

    public void schedulePvpActivation(int warId, long ticks) {
        BukkitTask existing = preparationTasks.remove(warId);
        if (existing != null) {
            existing.cancel();
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> activateWarPvP(warId), ticks);
        preparationTasks.put(warId, task);
    }

    public void activateWarPvP(int warId) {
        BukkitTask task = preparationTasks.remove(warId);
        if (task != null) {
            task.cancel();
        }

        ClanWar war = activeWars.get(warId);
        if (war == null || war.getStatus() == ClanWar.WarStatus.ENDED) {
            return;
        }

        war.setStatus(ClanWar.WarStatus.ACTIVE);
        war.setPvpActive(true);
        war.setPvpStartTime(System.currentTimeMillis());
        plugin.getDatabaseManager().updateWar(war);

        Clan clanA = plugin.getClanManager().getClan(war.getAttackerClanId());
        Clan clanB = plugin.getClanManager().getClan(war.getDefenderClanId());

        String activatedMsg = plugin.getMessageManager().get("war.pvp-activated",
                MessageManager.of("clan", clanA != null ? clanA.getColoredName() : "?",
                        "enemy", clanB != null ? clanB.getColoredName() : "?"));

        if (clanA != null) {
            clanA.broadcastMessage(activatedMsg);
            broadcastWarStartEffects(clanA);
        }
        if (clanB != null) {
            clanB.broadcastMessage(activatedMsg);
            broadcastWarStartEffects(clanB);
        }
    }

    private void broadcastWarStartEffects(Clan clan) {
        for (ClanMember member : clan.getMembers().values()) {
            Player p = Bukkit.getPlayer(member.getUuid());
            if (p != null && p.isOnline()) {
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                Title title = Title.title(
                        Component.text("§c§lKRIEG GESTARTET!"),
                        Component.text("§ePvP ist ab sofort aktiv!"),
                        Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(1000))
                );
                p.showTitle(title);
            }
        }
    }

    public void endWar(int clanIdA, int clanIdB, Integer winnerId) {
        String key = getWarKey(clanIdA, clanIdB);
        ClanWar war = warsByClanPair.get(key);

        if (war == null || !war.isActive()) {
            return;
        }

        BukkitTask task = preparationTasks.remove(war.getId());
        if (task != null) {
            task.cancel();
        }

        war.end(winnerId);
        plugin.getDatabaseManager().updateWar(war);

        activeWars.remove(war.getId());
        warsByClanPair.remove(key);

        Clan clanA = plugin.getClanManager().getClan(clanIdA);
        Clan clanB = plugin.getClanManager().getClan(clanIdB);

        long warCooldown = System.currentTimeMillis() + (12L * 60 * 60 * 1000L);
        if (clanA != null) {
            clanA.removeWar(clanIdB);
            clanA.setWarCooldownUntil(warCooldown);
            plugin.getClanManager().updateClanInDatabase(clanA);
        }
        if (clanB != null) {
            clanB.removeWar(clanIdA);
            clanB.setWarCooldownUntil(warCooldown);
            plugin.getClanManager().updateClanInDatabase(clanB);
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

                winner.broadcastMessage(plugin.getMessageManager().get("war.won",
                        MessageManager.of("points", "500", "warps", "3", "money", "50.000")));
            }

            if (loser != null) {
                loser.setPoints(Math.max(0, loser.getPoints() - 200));
                loser.setBalance(loser.getBalance() - 50000.0);
                plugin.getClanManager().updateClanInDatabase(loser);

                loser.broadcastMessage(plugin.getMessageManager().get("war.lost",
                        MessageManager.of("points", "200", "money", "50.000")));
                if (loser.isInDebt()) {
                    loser.broadcastMessage(plugin.getMessageManager().get("war.debt-warning",
                            MessageManager.of("debt", String.format(java.util.Locale.GERMANY, "%,.2f", loser.getDebt()))));
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

    public boolean deleteWar(int clanIdA, int clanIdB) {
        String key = getWarKey(clanIdA, clanIdB);
        ClanWar war = warsByClanPair.remove(key);
        if (war == null) {
            for (ClanWar w : activeWars.values()) {
                if (w.involves(clanIdA) && w.involves(clanIdB)) {
                    war = w;
                    break;
                }
            }
        }
        if (war == null) {
            return false;
        }

        activeWars.remove(war.getId());
        warsByClanPair.remove(key);

        BukkitTask task = preparationTasks.remove(war.getId());
        if (task != null) {
            task.cancel();
        }

        plugin.getDatabaseManager().deleteWar(war.getId());

        Clan clanA = plugin.getClanManager().getClan(clanIdA);
        Clan clanB = plugin.getClanManager().getClan(clanIdB);

        if (clanA != null) {
            clanA.removeWar(clanIdB);
            plugin.getClanManager().updateClanInDatabase(clanA);
            if (plugin.getGlowManager() != null) {
                plugin.getGlowManager().refresh(clanA);
            }
        }
        if (clanB != null) {
            clanB.removeWar(clanIdA);
            plugin.getClanManager().updateClanInDatabase(clanB);
            if (plugin.getGlowManager() != null) {
                plugin.getGlowManager().refresh(clanB);
            }
        }

        String adminDeletedMsg = plugin.getMessageManager().get("war.admin-deleted",
                MessageManager.of("clan", clanA != null ? clanA.getColoredName() : "?",
                        "enemy", clanB != null ? clanB.getColoredName() : "?"));
        if (clanA != null) {
            clanA.broadcastMessage(adminDeletedMsg);
        }
        if (clanB != null) {
            clanB.broadcastMessage(adminDeletedMsg);
        }

        return true;
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

    public boolean isWarPvpActive(int clanIdA, int clanIdB) {
        ClanWar war = getWarBetween(clanIdA, clanIdB);
        return war != null && war.isPvpActive();
    }

    public void recordKill(UUID killerUuid, UUID victimUuid, int killerClanId, int victimClanId) {
        ClanWar war = getWarBetween(killerClanId, victimClanId);
        if (war == null || !war.isPvpActive()) {
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
        Collections.unmodifiableCollection(activeWars.values());
        return activeWars.values();
    }
}
