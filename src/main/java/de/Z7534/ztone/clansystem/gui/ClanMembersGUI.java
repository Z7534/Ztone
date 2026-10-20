package de.Z7534.ztone.clansystem.gui;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.models.ClanMember;
import de.Z7534.ztone.clansystem.models.ClanRank;
import de.Z7534.ztone.clansystem.models.Permission;
import de.Z7534.ztone.clansystem.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ClanMembersGUI extends AbstractGUI {

    private final Clan clan;
    private final ClanMember viewer;
    private int page;
    private static final int ITEMS_PER_PAGE = 28;

    public ClanMembersGUI(Ztone plugin, Player player, Clan clan) {
        this(plugin, player, clan, 0);
    }

    public ClanMembersGUI(Ztone plugin, Player player, Clan clan, int page) {
        super(plugin, player, plugin.getMessageManager().getRaw("gui.members"), 54);
        this.clan = clan;
        this.viewer = clan.getMember(player.getUniqueId());
        this.page = page;
    }

    @Override
    public void setup() {
        fillFancyBorder();

        List<ClanMember> members = new ArrayList<>(clan.getMembers().values());

        members.sort((a, b) -> {
            int aPriority = a.getRank() != null ? a.getRank().getPriority() : 0;
            int bPriority = b.getRank() != null ? b.getRank().getPriority() : 0;
            return Integer.compare(bPriority, aPriority);
        });

        int totalPages = (int) Math.ceil((double) members.size() / ITEMS_PER_PAGE);
        int startIndex = page * ITEMS_PER_PAGE;
        int slot = 10;

        for (int i = startIndex; i < Math.min(startIndex + ITEMS_PER_PAGE, members.size()); i++) {
            ClanMember member = members.get(i);

            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 44) break;

            String rankName = member.getDisplayRankName();
            boolean isOnline = member.isOnline();
            boolean isLeader = clan.isLeader(member.getUuid());

            List<String> lore = new ArrayList<>();
            lore.add("&7Rang: &e" + rankName);
            lore.add("&7Status: " + (isOnline ? "&a● Online" : "&c● Offline"));

            if (isLeader) {
                lore.add("");
                lore.add("&6★ Clan-Leader");
            }

            boolean canManage = viewer != null &&
                    (viewer.hasPermission(Permission.KICK) || viewer.hasPermission(Permission.PROMOTE) || viewer.hasPermission(Permission.DEMOTE));
            boolean canOpenActions = canManage || clan.isLeader(player.getUniqueId());
            if (canOpenActions && !member.getUuid().equals(player.getUniqueId())) {
                lore.add("");
                lore.add("&a▶ &7Klicke für Aktionen");
            }

            final ClanMember targetMember = member;
            setItem(slot, new ItemBuilder(Material.PLAYER_HEAD)
                    .skullOwner(member.getUuid())
                    .name((isOnline ? "&a" : "&7") + (isLeader ? "&l★ " : "") + member.getName())
                    .lore(lore)
                    .glow(isLeader)
                    .build(), () -> {
                if (canOpenActions && !targetMember.getUuid().equals(player.getUniqueId())) {
                    new ClanMemberActionsGUI(plugin, player, clan, targetMember).open();
                }
            });

            slot++;
        }

        if (page > 0) {
            setItem(45, ItemBuilder.previousPage(), () -> {
                page--;
                refresh();
                player.updateInventory();
            });
        }

        if (page < totalPages - 1) {
            setItem(53, ItemBuilder.nextPage(), () -> {
                page++;
                refresh();
                player.updateInventory();
            });
        }

        setItem(49, new ItemBuilder(Material.PAPER)
                .name("&7Seite &e" + (page + 1) + "&8/&e" + Math.max(1, totalPages))
                .lore("&7Gesamt: &e" + members.size() + " Mitglieder")
                .build());

        addBackButton(48, () -> new ClanMainGUI(plugin, player, clan).open());

        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }
}
