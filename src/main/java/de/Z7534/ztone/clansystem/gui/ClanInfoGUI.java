package de.Z7534.ztone.clansystem.gui;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.managers.EconomyManager;
import de.Z7534.ztone.clansystem.managers.LevelManager;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.models.ClanMember;
import de.Z7534.ztone.clansystem.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ClanInfoGUI extends AbstractGUI {

    private final Clan clan;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    public ClanInfoGUI(Ztone plugin, Player player, Clan clan) {
        super(plugin, player,
                plugin.getMessageManager().getRawWithPlaceholders("gui.info",
                        de.Z7534.ztone.clansystem.managers.MessageManager.of("clan", clan.getColoredName())), 45);
        this.clan = clan;
    }

    @Override
    public void setup() {
        fillFancyBorder();

        LevelManager levelManager = plugin.getLevelManager();
        int maxMembers = levelManager.getMaxMembers(clan.getLevel());

        ClanMember leader = clan.getMember(clan.getLeaderUuid());
        String leaderName = leader != null ? leader.getName() : "Unbekannt";

        EconomyManager eco = plugin.getEconomyManager();
        String formattedBalance = eco != null ? eco.format(clan.getBalance()) : (clan.getBalance() + " $");

        setItem(13, new ItemBuilder(de.Z7534.ztone.clansystem.utils.ClanIcons.fromName(clan.getIcon()))
                .name("&6&l✧ " + clan.getName() + " &6&l✧")
                .lore(
                        "&7Suffix: &r" + (clan.getSuffix().isEmpty() ? "&8Keiner" : clan.getColoredSuffix()),
                        "",
                        "&7Anführer: &e★ " + leaderName,
                        "&7Clangeld: &e" + formattedBalance,
                        "&7Gegründet: &e" + dateFormat.format(new Date(clan.getCreatedAt())),
                        "",
                        "&7Beitritt: &e" + getJoinTypeName(clan.getJoinType())
                )
                .glow()
                .build());

        boolean maxLevel = levelManager.isMaxLevel(clan.getLevel());

        List<String> levelLore = new ArrayList<>();
        levelLore.add("&7Level: &e" + clan.getLevel() + "&8/&e" + levelManager.getMaxLevel());
        if (maxLevel) {
            levelLore.add("&6&l(Höchstes Level erreicht)");
            levelLore.add("&7" + clan.getPoints() + " Punkte gesamt");
        } else {
            long nextLevelPoints = levelManager.getPointsRequired(clan.getLevel() + 1);
            String bar = ItemBuilder.progressBar(clan.getPoints(), nextLevelPoints, 22, "&6", "&8");
            levelLore.add(bar);
            levelLore.add("&7" + clan.getPoints() + " &8/ &7" + nextLevelPoints + " Punkte");
        }

        setItem(20, new ItemBuilder(Material.NETHER_STAR)
                .name("&a&l✧ Level & Punkte")
                .lore(levelLore)
                .glow()
                .build());

        List<String> memberLore = new ArrayList<>();
        memberLore.add("&7Anzahl: &e" + clan.getMemberCount() + "&8/&e" + maxMembers);
        memberLore.add("&7Online: &a" + clan.getOnlineMembers().size());
        memberLore.add("");
        memberLore.add("&7Top-Mitglieder:");

        List<ClanMember> sortedMembers = new ArrayList<>(clan.getMembers().values());
        sortedMembers.sort((a, b) -> {
            int aPriority = a.getRank() != null ? a.getRank().getPriority() : 0;
            int bPriority = b.getRank() != null ? b.getRank().getPriority() : 0;
            return Integer.compare(bPriority, aPriority);
        });

        int shown = 0;
        for (ClanMember member : sortedMembers) {
            if (shown >= 5) break;
            String rankName = member.getDisplayRankName();
            memberLore.add("&8- &e" + member.getName() + " &8(&7" + rankName + "&8)");
            shown++;
        }

        setItem(22, new ItemBuilder(Material.PAPER)
                .name("&e&l➤ Mitglieder")
                .lore(memberLore)
                .build());

        List<String> diplomacyLore = new ArrayList<>();
        diplomacyLore.add("&7Allianzen: &e" + clan.getAllies().size());
        diplomacyLore.add("&7Aktive Kriege: &c" + clan.getActiveWars().size());

        if (!clan.getAllies().isEmpty()) {
            diplomacyLore.add("");
            diplomacyLore.add("&7Verbündete:");
            int allyCount = 0;
            for (int allyId : clan.getAllies()) {
                if (allyCount >= 3) {
                    diplomacyLore.add("&8... und mehr");
                    break;
                }
                Clan ally = plugin.getClanManager().getClan(allyId);
                if (ally != null) {
                    diplomacyLore.add("&8- &a" + ally.getName());
                    allyCount++;
                }
            }
        }

        setItem(24, new ItemBuilder(Material.IRON_SWORD)
                .name("&c&l⚔ Diplomatie")
                .lore(diplomacyLore)
                .hideFlags()
                .glow(!clan.getActiveWars().isEmpty())
                .build());

        Clan playerClan = plugin.getClanManager().getClanByPlayer(player);
        if (playerClan == null) {
            if (clan.getJoinType() == Clan.JoinType.OPEN) {
                setItem(31, new ItemBuilder(Material.LIME_DYE)
                        .name("&a&l✔ Clan beitreten")
                        .lore(
                                "&7Dieser Clan nimmt",
                                "&7offene Beitritte an.",
                                "",
                                "&a▶ &7Klicke zum Beitreten"
                        )
                        .glow()
                        .build(), () -> {
                    player.closeInventory();
                    player.performCommand("clan join " + clan.getName());
                });
            } else if (clan.getJoinType() == Clan.JoinType.APPLY) {
                setItem(31, new ItemBuilder(Material.YELLOW_DYE)
                        .name("&e&l✉ Bewerben")
                        .lore(
                                "&7Dieser Clan nimmt",
                                "&7Bewerbungen an.",
                                "",
                                "&a▶ &7Klicke zum Bewerben"
                        )
                        .glow()
                        .build(), () -> {
                    player.closeInventory();
                    player.performCommand("clan apply " + clan.getName());
                });
            } else {
                setItem(31, new ItemBuilder(Material.RED_DYE)
                        .name("&c&l✖ Nur auf Einladung")
                        .lore(
                                "&7Dieser Clan nimmt nur",
                                "&7Spieler auf Einladung an."
                        )
                        .build());
            }
        }

        addBackButton(40, () -> new ClanListGUI(plugin, player).open());

        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }

    private String getJoinTypeName(Clan.JoinType type) {
        return switch (type) {
            case OPEN -> "Offen";
            case APPLY -> "Bewerbung";
            case INVITE -> "Nur Einladung";
        };
    }
}
