package de.Z7534.ztone.clansystem.managers;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.models.Clan;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AllianceManager {

    private final Ztone plugin;
    private final Map<String, Long> pendingAlliances;

    public AllianceManager(Ztone plugin) {
        this.plugin = plugin;
        this.pendingAlliances = new ConcurrentHashMap<>();
    }

    private String getAllianceKey(int clanIdA, int clanIdB) {
        int first = Math.min(clanIdA, clanIdB);
        int second = Math.max(clanIdA, clanIdB);
        return first + "-" + second;
    }

    public void sendAllianceRequest(Clan sender, Clan target) {
        String key = sender.getId() + "->" + target.getId();
        pendingAlliances.put(key, System.currentTimeMillis());
    }

    public boolean hasPendingRequest(Clan sender, Clan target) {
        String key = sender.getId() + "->" + target.getId();
        Long timestamp = pendingAlliances.get(key);

        if (timestamp == null) {
            return false;
        }

        if (System.currentTimeMillis() - timestamp > 300000) {
            pendingAlliances.remove(key);
            return false;
        }

        return true;
    }

    public void removePendingRequest(Clan sender, Clan target) {
        String key = sender.getId() + "->" + target.getId();
        pendingAlliances.remove(key);
    }

    public void formAlliance(Clan clanA, Clan clanB) {

        removePendingRequest(clanA, clanB);
        removePendingRequest(clanB, clanA);

        clanA.addAlly(clanB.getId());
        clanB.addAlly(clanA.getId());

        plugin.getDatabaseManager().createAlliance(clanA.getId(), clanB.getId());

        String formedMessage = plugin.getMessageManager().get("broadcast.alliance-formed",
                MessageManager.of("clan", clanA.getColoredName(), "ally", clanB.getColoredName()));
        clanA.broadcastMessage(formedMessage);
        clanB.broadcastMessage(formedMessage);
    }

    public void breakAlliance(int clanIdA, int clanIdB) {
        Clan clanA = plugin.getClanManager().getClan(clanIdA);
        Clan clanB = plugin.getClanManager().getClan(clanIdB);

        if (clanA != null) {
            clanA.removeAlly(clanIdB);
        }
        if (clanB != null) {
            clanB.removeAlly(clanIdA);
        }

        plugin.getDatabaseManager().deleteAlliance(clanIdA, clanIdB);

        String brokenMessage = plugin.getMessageManager().get("broadcast.alliance-broken",
                MessageManager.of("clan", clanA != null ? clanA.getColoredName() : "?",
                        "ally", clanB != null ? clanB.getColoredName() : "?"));
        if (clanA != null) {
            clanA.broadcastMessage(brokenMessage);
        }
        if (clanB != null) {
            clanB.broadcastMessage(brokenMessage);
        }
    }

    public boolean areAllied(int clanIdA, int clanIdB) {
        Clan clanA = plugin.getClanManager().getClan(clanIdA);
        if (clanA == null) {
            return false;
        }
        return clanA.isAlliedWith(clanIdB);
    }

    public List<Clan> getAllies(Clan clan) {
        List<Clan> allies = new ArrayList<>();
        for (int allyId : clan.getAllies()) {
            Clan ally = plugin.getClanManager().getClan(allyId);
            if (ally != null) {
                allies.add(ally);
            }
        }
        return allies;
    }
}
