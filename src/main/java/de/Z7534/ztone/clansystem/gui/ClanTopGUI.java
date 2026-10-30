package de.Z7534.ztone.clansystem.gui;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.managers.EconomyManager;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.models.ClanMember;
import de.Z7534.ztone.clansystem.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ClanTopGUI extends AbstractGUI {

    public enum Mode {
        POINTS,
        BALANCE
    }

    private final Mode mode;

    public ClanTopGUI(Ztone plugin, Player player) {
        this(plugin, player, Mode.POINTS);
    }

    public ClanTopGUI(Ztone plugin, Player player, Mode mode) {
        super(plugin, player,
                mode == Mode.BALANCE ? "§6💰 Clan-Rangliste (Clankasse)" : "§6🏆 Clan-Rangliste (Punkte)",
                54);
        this.mode = mode != null ? mode : Mode.POINTS;
    }

    private String formatBalance(double balance) {
        EconomyManager eco = plugin.getEconomyManager();
        if (eco != null) {
            return eco.format(balance);
        }
        return String.format(Locale.GERMANY, "%,.2f $", balance);
    }

    @Override
    public void setup() {
        fillFancyBorder();

        boolean clankasseEnabled = plugin.getConfigManager().isClankasseEnabled();

        // Header info item
        List<String> headerLore = new ArrayList<>();
        if (mode == Mode.BALANCE) {
            headerLore.add("§7Die reichsten Clans des Servers,");
            headerLore.add("§7gemessen an ihrer §6Clankasse§7!");
            headerLore.add("");
            headerLore.add("§7Einzahlen mit:");
            headerLore.add("§8» §e/clan deposit <Betrag>");
            headerLore.add("§8» §e/clan bank");
            headerLore.add("");
            headerLore.add("§8Sortierung: Höchstes Clan-Guthaben zuerst");

            setItem(4, new ItemBuilder(Material.GOLD_INGOT)
                    .name("§6§l💰 CLAN-RANGLISTE: CLANKASSE (TOP 10) 💰")
                    .lore(headerLore)
                    .glow()
                    .build());
        } else {
            headerLore.add("§7Die stärksten Clans des Servers,");
            headerLore.add("§7gemessen an ihren §eClan-Punkten§7!");
            headerLore.add("");
            headerLore.add("§7Punkte erhält man durch:");
            headerLore.add("§8» §fGewonnene Clankriege (§e+500 Punkte§f)");
            headerLore.add("§8» §fClan-Level Aufstiege & Quests");
            headerLore.add("");
            headerLore.add("§8Sortierung: Höchste Clan-Punkte zuerst");

            setItem(4, new ItemBuilder(Material.NETHER_STAR)
                    .name("§6§l🏆 CLAN-RANGLISTE: PUNKTE (TOP 10) 🏆")
                    .lore(headerLore)
                    .glow()
                    .build());
        }

        List<Clan> sortedClans = (mode == Mode.BALANCE)
                ? plugin.getClanManager().getClansSortedByBalance()
                : plugin.getClanManager().getClansSortedByPoints();

        // Top 3 Podium slots: #2 at 12, #1 at 13, #3 at 14
        int[] top3Slots = {13, 12, 14}; // index 0 is Rank 1, index 1 is Rank 2, index 2 is Rank 3
        String[] medals = {"§6§l🥇 #1", "§f§l🥈 #2", "§6§l🥉 #3"};

        for (int i = 0; i < 3; i++) {
            int slot = top3Slots[i];
            if (i < sortedClans.size()) {
                Clan c = sortedClans.get(i);
                ItemBuilder ib = new ItemBuilder(Material.PLAYER_HEAD)
                        .skullOwner(c.getLeaderUuid())
                        .name(medals[i] + " " + c.getColoredName())
                        .lore(buildClanLore(c, clankasseEnabled))
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
                ItemBuilder ib = new ItemBuilder(Material.PLAYER_HEAD)
                        .skullOwner(c.getLeaderUuid())
                        .name("§e§l#" + rankNum + " " + c.getColoredName())
                        .lore(buildClanLore(c, clankasseEnabled));

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
            List<String> ownLore = new ArrayList<>();
            ownLore.add("§7Platzierung: §6§l#" + ownRank + " §7von §e" + sortedClans.size());
            if (mode == Mode.BALANCE) {
                ownLore.add("§7Deine Clankasse: §6§l" + formatBalance(ownClan.getBalance()));
                ownLore.add("§7Deine Clan-Punkte: §e" + ownClan.getPoints() + " Punkte");
            } else {
                ownLore.add("§7Deine Clan-Punkte: §e§l" + ownClan.getPoints() + " Punkte");
                if (clankasseEnabled) {
                    ownLore.add("§7Deine Clankasse: §6" + formatBalance(ownClan.getBalance()));
                }
            }
            ownLore.add("");
            ownLore.add("§a▶ §7Klicke für dein Clan-Hauptmenü");

            setItem(31, new ItemBuilder(Material.BEACON)
                    .name("§b§lDein Clan: " + ownClan.getColoredName())
                    .lore(ownLore)
                    .glow()
                    .build(), () -> new ClanMainGUI(plugin, player, ownClan).open());
        }

        // Leaderboard mode switch buttons (Slots 47 & 51) when Clankasse is enabled
        if (clankasseEnabled) {
            boolean pointsActive = (mode == Mode.POINTS);
            setItem(47, new ItemBuilder(Material.NETHER_STAR)
                    .name(pointsActive ? "§e§l🏆 Punkte-Leaderboard §a(Aktiv)" : "§7🏆 Punkte-Leaderboard")
                    .lore(
                            "§7Zeigt die Top 10 Clans sortiert",
                            "§7nach ihren §eClan-Punkten§7.",
                            "",
                            pointsActive ? "§a✔ Aktuell ausgewählt" : "§a▶ §7Klicke zum Wechseln"
                    )
                    .glow(pointsActive)
                    .build(), () -> {
                if (!pointsActive) {
                    new ClanTopGUI(plugin, player, Mode.POINTS).open();
                }
            });

            boolean balanceActive = (mode == Mode.BALANCE);
            setItem(51, new ItemBuilder(Material.GOLD_INGOT)
                    .name(balanceActive ? "§6§l💰 Clankasse-Leaderboard §a(Aktiv)" : "§7💰 Clankasse-Leaderboard")
                    .lore(
                            "§7Zeigt die Top 10 Clans sortiert",
                            "§7nach ihrem §6Clankasse-Guthaben§7.",
                            "",
                            balanceActive ? "§a✔ Aktuell ausgewählt" : "§a▶ §7Klicke zum Wechseln"
                    )
                    .glow(balanceActive)
                    .build(), () -> {
                if (!balanceActive) {
                    new ClanTopGUI(plugin, player, Mode.BALANCE).open();
                }
            });
        }

        // Back / Close button at slot 49
        if (ownClan != null) {
            addBackButton(49, () -> new ClanMainGUI(plugin, player, ownClan).open());
        } else {
            addCloseButton(49);
        }

        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }

    private List<String> buildClanLore(Clan c, boolean clankasseEnabled) {
        ClanMember leader = c.getMember(c.getLeaderUuid());
        String leaderName = leader != null ? leader.getName() : "Unbekannt";

        List<String> lore = new ArrayList<>();
        if (mode == Mode.BALANCE) {
            lore.add("§7Clankasse: §6§l" + formatBalance(c.getBalance()));
            lore.add("§7Clan-Punkte: §e" + c.getPoints() + " Punkte");
        } else {
            lore.add("§7Clan-Punkte: §e§l" + c.getPoints() + " Punkte");
            if (clankasseEnabled) {
                lore.add("§7Clankasse: §6" + formatBalance(c.getBalance()));
            }
        }
        lore.add("§7Clan-Level: §b" + c.getLevel());
        lore.add("§7Mitglieder: §a" + c.getMemberCount());
        lore.add("§7Anführer: §e★ " + leaderName);
        lore.add("§7Suffix: §r" + (c.getSuffix().isEmpty() ? "§8Keiner" : c.getColoredSuffix()));
        lore.add("");
        lore.add("§a▶ §7Klicke für Clan-Details");
        return lore;
    }
}
