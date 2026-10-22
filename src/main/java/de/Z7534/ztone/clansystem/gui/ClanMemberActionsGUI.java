package de.Z7534.ztone.clansystem.gui;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.models.ClanMember;
import de.Z7534.ztone.clansystem.models.Permission;
import de.Z7534.ztone.clansystem.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class ClanMemberActionsGUI extends AbstractGUI {

    private final Clan clan;
    private final ClanMember viewer;
    private final ClanMember target;

    public ClanMemberActionsGUI(Ztone plugin, Player player, Clan clan, ClanMember target) {
        super(plugin, player, "&8&l✦ &7" + target.getName(), 27);
        this.clan = clan;
        this.viewer = clan.getMember(player.getUniqueId());
        this.target = target;
    }

    @Override
    public void setup() {
        fillFancyBorder();

        boolean targetIsLeader = clan.isLeader(target.getUuid()) || target.isLeader();
        String rankName = target.getDisplayRankName();

        java.util.List<String> infoLore = new java.util.ArrayList<>();
        infoLore.add("&7Rang: &e" + rankName);
        infoLore.add("&7Status: " + (target.isOnline() ? "&a● Online" : "&c● Offline"));
        if (targetIsLeader) {
            infoLore.add("");
            infoLore.add("&6★ Clan-Leader");
        }

        setItem(4, new ItemBuilder(Material.PLAYER_HEAD)
                .skullOwner(target.getUuid())
                .name((target.isOnline() ? "&a" : "&7") + (targetIsLeader ? "&l★ " : "") + target.getName())
                .lore(infoLore)
                .glow(targetIsLeader)
                .build());

        boolean self = target.getUuid().equals(player.getUniqueId());
        boolean viewerIsLeader = clan.isLeader(player.getUniqueId()) || (viewer != null && viewer.isLeader());

        if (!self && !targetIsLeader && (viewerIsLeader || (viewer != null && viewer.hasPermission(Permission.PROMOTE)))) {
            setItem(11, new ItemBuilder(Material.LIME_DYE)
                    .name("&a&l⇧ Befördern")
                    .lore("&a▶ &7Klicke zum Befördern")
                    .build(), () -> {
                player.performCommand("clan promote " + target.getName());
                new ClanMembersGUI(plugin, player, clan).open();
            });
        }

        if (!self && !targetIsLeader && (viewerIsLeader || (viewer != null && viewer.hasPermission(Permission.DEMOTE)))) {
            setItem(13, new ItemBuilder(Material.YELLOW_DYE)
                    .name("&e&l⇩ Degradieren")
                    .lore("&a▶ &7Klicke zum Degradieren")
                    .build(), () -> {
                player.performCommand("clan demote " + target.getName());
                new ClanMembersGUI(plugin, player, clan).open();
            });
        }

        if (!self && !targetIsLeader && (viewerIsLeader || (viewer != null && viewer.hasPermission(Permission.KICK)))) {
            setItem(15, new ItemBuilder(Material.BARRIER)
                    .name("&c&l✘ Kicken")
                    .lore("&a▶ &7Klicke zum Kicken")
                    .build(), () -> new ConfirmGUI(plugin, player,
                    target.getName() + " aus dem Clan kicken?",
                    () -> {
                        player.performCommand("clan kick " + target.getName());
                        new ClanMembersGUI(plugin, player, clan).open();
                    },
                    () -> new ClanMemberActionsGUI(plugin, player, clan, target).open()
            ).open());
        }

        if (!self && viewerIsLeader) {
            setItem(22, new ItemBuilder(Material.NETHER_STAR)
                    .name("&6&l★ Eigentümer übertragen")
                    .lore(
                            "&7Macht " + target.getName() + " zum",
                            "&7neuen Eigentümer des Clans.",
                            "&c&lACHTUNG: Nicht rückgängig machbar!",
                            "",
                            "&a▶ &7Klicke zum Übertragen"
                    )
                    .glow()
                    .build(), () -> new ConfirmGUI(plugin, player,
                    "Eigentümerschaft an " + target.getName() + " übertragen?",
                    () -> {
                        plugin.getClanManager().transferLeadership(clan, target.getUuid());
                        plugin.getMessageManager().send(player, "clan.ownership-transferred",
                                de.Z7534.ztone.clansystem.managers.MessageManager.of("player", target.getName()));
                        if (target.isOnline()) {
                            plugin.getMessageManager().send(target.getPlayer(), "clan.ownership-received",
                                    de.Z7534.ztone.clansystem.managers.MessageManager.of("clan", clan.getColoredName()));
                        }
                        player.closeInventory();
                    },
                    () -> new ClanMemberActionsGUI(plugin, player, clan, target).open()
            ).open());
        }

        addBackButton(18, () -> new ClanMembersGUI(plugin, player, clan).open());

        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }
}
