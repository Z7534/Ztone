package de.Z7534.ztone.clansystem.gui;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.managers.LevelManager;
import de.Z7534.ztone.clansystem.managers.MessageManager;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.models.ClanRank;
import de.Z7534.ztone.clansystem.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClanApplicationActionsGUI extends AbstractGUI {

    private final Clan clan;
    private final UUID applicantUuid;
    private final String applicantName;
    private final boolean adminView;

    public ClanApplicationActionsGUI(Ztone plugin, Player player, Clan clan, UUID applicantUuid, String applicantName) {
        this(plugin, player, clan, applicantUuid, applicantName, false);
    }

    public ClanApplicationActionsGUI(Ztone plugin, Player player, Clan clan, UUID applicantUuid, String applicantName, boolean adminView) {
        super(plugin, player, "&8&l✦ &e" + applicantName, 27);
        this.clan = clan;
        this.applicantUuid = applicantUuid;
        this.applicantName = applicantName;
        this.adminView = adminView;
    }

    @Override
    public void setup() {
        fillFancyBorder();

        OfflinePlayer applicant = Bukkit.getOfflinePlayer(applicantUuid);
        LevelManager levelManager = plugin.getLevelManager();
        int maxMembers = levelManager.getMaxMembers(clan.getLevel());
        boolean canAccept = clan.getMemberCount() < maxMembers;

        setItem(4, new ItemBuilder(Material.PLAYER_HEAD)
                .skullOwner(applicantUuid)
                .name("&e" + applicantName)
                .lore("&7Status: " + (applicant.isOnline() ? "&aOnline" : "&cOffline"))
                .build());

        setItem(11, new ItemBuilder(canAccept ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(canAccept ? "&a&l✔ Annehmen" : "&7&l✘ Annehmen")
                .lore(canAccept ? List.of(
                        "&a▶ &7Klicke zum Annehmen"
                ) : List.of(
                        "&cDer Clan ist voll!",
                        "&7(" + clan.getMemberCount() + "/" + maxMembers + " Mitglieder)"
                ))
                .build(), () -> {
            if (!canAccept) {
                return;
            }
            accept();
        });

        setItem(15, new ItemBuilder(Material.BARRIER)
                .name("&c&l✘ Ablehnen")
                .lore("&a▶ &7Klicke zum Ablehnen")
                .build(), this::decline);

        addBackButton(22, () -> new ClanApplicationsGUI(plugin, player, clan, adminView).open());

        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }

    private void accept() {
        Player applicantPlayer = Bukkit.getPlayer(applicantUuid);
        clan.removeApplication(applicantUuid);

        if (applicantPlayer == null) {
            plugin.getMessageManager().send(player, "general.player-not-found",
                    MessageManager.of("player", applicantName));
            new ClanApplicationsGUI(plugin, player, clan, adminView).open();
            return;
        }

        ClanRank lowestRank = clan.getLowestRank();
        plugin.getClanManager().addMember(clan, applicantPlayer, lowestRank);

        plugin.getMessageManager().send(applicantPlayer, "clan.application-accepted");

        clan.broadcastMessage(plugin.getMessageManager().get("clan.member-joined",
                MessageManager.of("player", applicantName)), applicantUuid);

        if (plugin.getConfigManager().isBroadcastMemberJoined()) {
            plugin.getMessageManager().broadcastExcept("broadcast.member-joined", applicantPlayer,
                    MessageManager.of("player", applicantName, "clan", clan.getColoredName()));
        }

        new ClanApplicationsGUI(plugin, player, clan, adminView).open();
    }

    private void decline() {
        clan.removeApplication(applicantUuid);

        Player applicantPlayer = Bukkit.getPlayer(applicantUuid);
        if (applicantPlayer != null) {
            plugin.getMessageManager().send(applicantPlayer, "clan.application-denied");
        }

        plugin.getMessageManager().send(player, "clan.application-denied-confirm",
                MessageManager.of("player", applicantName));

        new ClanApplicationsGUI(plugin, player, clan, adminView).open();
    }
}
