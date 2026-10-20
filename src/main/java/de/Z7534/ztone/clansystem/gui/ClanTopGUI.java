package de.Z7534.ztone.clansystem.gui;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.models.ClanMember;
import de.Z7534.ztone.clansystem.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ClanTopGUI extends AbstractGUI {

    public ClanTopGUI(Ztone plugin, Player player) {
        super(plugin, player, "§6🏆 Clan-Rangliste (Punkte)", 54);
    }

    @Override
    public void setup() {
        fillFancyBorder();

        // Header info item
        List<String> headerLore = new ArrayList<>();
        headerLore.add("§7Die stärksten Clans des Servers,");
        headerLore.add("§7gemessen an ihren §eClan-Punkten§7!");
        headerLore.add("");
        headerLore.add("§7Punkte erhält man durch:");
        headerLore.add("§8» §fGewonnene Clankriege (§e+500 Punkte§f)");
        headerLore.add("§8» §fClan-Level Aufstiege & Quests");
        headerLore.add("");
        headerLore.add("§8Sortierung: Höchste Clan-Punkte zuerst");

        setItem(4, new ItemBuilder(Material.NETHER_STAR)
                .name("§6§l🏆 CLAN-RANGLISTE (TOP 10) 🏆")
                .lore(headerLore)
                .glow()
                .build());

        List<Clan> sortedClans = plugin.getClanManager().getClansSortedByPoints();

        // Top 3 Podium slots: #2 at 12, #1 at 13, #3 at 14
        int[] top3Slots = {13, 12, 14}; // index 0 is Rank 1, index 1 is Rank 2, index 2 is Rank 3
        String[] medals = {"§6§l🥇 #1", "§f§l🥈 #2", "§6§l🥉 #3"};

        for (int i = 0; i < 3; i++) {
            int slot = top3Slots[i];
            if (i < sortedClans.size()) {
                Clan c = sortedClans.get(i);
                int rankNum = i + 1;
                ClanMember leader = c.getMember(c.getLeaderUuid());
                String leaderName = leader != null ? leader.getName() : "Unbekannt";

                List<String> lore = new ArrayList<>();
                lore.add("§7Clan-Punkte: §e§l" + c.getPoints() + " Punkte");
                lore.add("§7Clan-Level: §b" + c.getLevel());
                lore.add("§7Mitglieder: §a" + c.getMemberCount());
                lore.add("§7Anführer: §e★ " + leaderName);
                lore.add("§7Suffix: §r" + (c.getSuffix().isEmpty() ? "§8Keiner" : c.getColoredSuffix()));
                lore.add("");
                lore.add("§a▶ §7Klicke für Clan-Details");

                ItemBuilder ib = new ItemBuilder(Material.PLAYER_HEAD)
                        .skullOwner(c.getLeaderUuid())
                        .name(medals[i] + " " + c.getColoredName())
                        .lore(lore)
                        .glow();

                setItem(slot, ib.build(), () -> new ClanInfoGUI(plugin, player, c).open());
            } else {
                setItem(slot, new ItemBuilder(Material.BARRIER)
                        .name("§8" + medals[i] + " Nicht belegt")
                        .lore("§7Es gibt noch keinen Clan auf diesem Platz.")
                        .build());
            }
        }

        // Ranks 4 to 10 in row 3 (slots 20 to 26)
        int[] ranks4to10Slots = {20, 21, 22, 23, 24, 25, 26};
        for (int i = 0; i < ranks4to10Slots.length; i++) {
            int slot = ranks4to10Slots[i];
            int clanIdx = i + 3; // index 3 is rank 4
            int rankNum = clanIdx + 1;

            if (clanIdx < sortedClans.size()) {
                Clan c = sortedClans.get(clanIdx);
                ClanMember leader = c.getMember(c.getLeaderUuid());
                String leaderName = leader != null ? leader.getName() : "Unbekannt";

                List<String> lore = new ArrayList<>();
                lore.add("§7Clan-Punkte: §e§l" + c.getPoints() + " Punkte");
                lore.add("§7Clan-Level: §b" + c.getLevel());
                lore.add("§7Mitglieder: §a" + c.getMemberCount());
                lore.add("§7Anführer: §e★ " + leaderName);
                lore.add("§7Suffix: §r" + (c.getSuffix().isEmpty() ? "§8Keiner" : c.getColoredSuffix()));
                lore.add("");
                lore.add("§a▶ §7Klicke für Clan-Details");

                ItemBuilder ib = new ItemBuilder(Material.PLAYER_HEAD)
                        .skullOwner(c.getLeaderUuid())
                        .name("§e§l#" + rankNum + " " + c.getColoredName())
                        .lore(lore);

                setItem(slot, ib.build(), () -> new ClanInfoGUI(plugin, player, c).open());
            } else {
                setItem(slot, new ItemBuilder(Material.GRAY_DYE)
                        .name("§8#" + rankNum + " Nicht belegt")
                        .lore("§7Es gibt noch keinen Clan auf diesem Platz.")
                        .build());
            }
        }

        // Own clan rank indicator at slot 31
        Clan ownClan = plugin.getClanManager().getClanByPlayer(player);
        if (ownClan != null) {
            int ownRank = 1;
            for (Clan c : sortedClans) {
                if (c.getId() == ownClan.getId()) break;
                ownRank++;
            }
            setItem(31, new ItemBuilder(Material.BEACON)
                    .name("§b§lDein Clan: " + ownClan.getColoredName())
                    .lore(
                            "§7Platzierung: §6§l#" + ownRank + " §7von §e" + sortedClans.size(),
                            "§7Deine Clan-Punkte: §e§l" + ownClan.getPoints() + " Punkte",
                            "",
                            "§a▶ §7Klicke für dein Clan-Hauptmenü"
                    )
                    .glow()
                    .build(), () -> new ClanMainGUI(plugin, player, ownClan).open());
        }

        // Back / Close button at slot 49
        if (ownClan != null) {
            addBackButton(49, () -> new ClanMainGUI(plugin, player, ownClan).open());
        } else {
            addCloseButton(49);
        }

        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }
}
