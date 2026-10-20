package de.Z7534.ztone.clansystem.gui;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.managers.ClanManager;
import de.Z7534.ztone.clansystem.managers.MessageManager;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.models.ClanRank;
import de.Z7534.ztone.clansystem.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public class ClanRankGUI extends AbstractGUI {

    private final Clan clan;
    private final boolean adminView;

    public ClanRankGUI(Ztone plugin, Player player, Clan clan) {
        this(plugin, player, clan, false);
    }

    public ClanRankGUI(Ztone plugin, Player player, Clan clan, boolean adminView) {
        super(plugin, player, plugin.getMessageManager().getRaw("gui.ranks"), 54);
        this.clan = clan;
        this.adminView = adminView;
    }

    @Override
    public void setup() {
        fillFancyBorder();

        List<ClanRank> ranks = clan.getSortedRanks();
        int slot = 10;

        for (ClanRank rank : ranks) {

            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 44) break;

            Material material;
            if (rank.getPriority() >= 100) {
                material = Material.NETHERITE_INGOT;
            } else if (rank.getPriority() >= 75) {
                material = Material.DIAMOND;
            } else if (rank.getPriority() >= 50) {
                material = Material.GOLD_INGOT;
            } else if (rank.getPriority() >= 25) {
                material = Material.IRON_INGOT;
            } else {
                material = Material.COPPER_INGOT;
            }

            int memberCount = (int) clan.getMembers().values().stream()
                    .filter(m -> m.getRankId() == rank.getId())
                    .count();

            final int rankId = rank.getId();
            setItem(slot, new ItemBuilder(material)
                    .name("&e" + rank.getName())
                    .lore(
                            "&7Priorität: &e" + rank.getPriority(),
                            "&7Mitglieder: &e" + memberCount,
                            "&7Berechtigungen: &e" + rank.getPermissions().size(),
                            "",
                            "&eLinksklick: Bearbeiten",
                            "&cRechtsklick: Löschen"
                    )
                    .build(),
                    () -> { // Linksklick: bearbeiten
                        ClanRank clickedRank = clan.getRank(rankId);
                        if (clickedRank != null) {
                            new ClanRankEditGUI(plugin, player, clan, clickedRank, adminView).open();
                        }
                    },
                    () -> { // Rechtsklick: löschen (mit Sicherheitsabfrage)
                        ClanRank clickedRank = clan.getRank(rankId);
                        if (clickedRank == null) {
                            return;
                        }
                        new ConfirmGUI(plugin, player,
                                "Rang '" + clickedRank.getName() + "' löschen?",
                                () -> {
                                    ClanManager.RankDeleteResult result =
                                            plugin.getClanManager().deleteRank(clan, clickedRank);
                                    switch (result) {
                                        case HAS_MEMBERS -> plugin.getMessageManager()
                                                .send(player, "rank.cannot-delete-with-members");
                                        case LAST_RANK -> plugin.getMessageManager()
                                                .send(player, "rank.cannot-delete-default");
                                        case NOT_FOUND -> plugin.getMessageManager()
                                                .send(player, "rank.rank-not-found",
                                                        MessageManager.of("rank", clickedRank.getName()));
                                        case SUCCESS -> plugin.getMessageManager()
                                                .send(player, "rank.rank-deleted",
                                                        MessageManager.of("rank", clickedRank.getName()));
                                    }
                                    new ClanRankGUI(plugin, player, clan, adminView).open();
                                },
                                () -> new ClanRankGUI(plugin, player, clan, adminView).open()
                        ).open();
                    });

            slot++;
        }

        setItem(49, new ItemBuilder(Material.EMERALD)
                .name("&a&l➤ Neuen Rang erstellen")
                .lore(
                        "&7Erstelle einen neuen Rang",
                        "&7für deinen Clan.",
                        "",
                        "&a▶ &7Klicke zum Erstellen"
                )
                .build(), () -> plugin.getChatInputManager().request(player,
                "Bitte gib den Namen für den neuen Rang im Chat ein:",
                input -> {
                    String name = input.trim().replace(" ", "_");
                    ClanManager.RankCreateResult result = plugin.getClanManager().createRank(clan, name);
                    if (result == ClanManager.RankCreateResult.ALREADY_EXISTS) {
                        plugin.getMessageManager().send(player, "rank.name-taken",
                                MessageManager.of("rank", name));
                    } else {
                        plugin.getMessageManager().send(player, "rank.rank-created",
                                MessageManager.of("rank", name));
                    }
                    new ClanRankGUI(plugin, player, clan, adminView).open();
                }));

        addBackButton(45, () -> new ClanSettingsGUI(plugin, player, clan, adminView).open());

        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }
}
