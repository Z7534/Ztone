package de.Z7534.ztone.clansystem.gui;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.managers.MessageManager;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.models.ClanMember;
import de.Z7534.ztone.clansystem.models.ClanWarp;
import de.Z7534.ztone.clansystem.models.Permission;
import de.Z7534.ztone.clansystem.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ClanWarpActionsGUI extends AbstractGUI {

    private final Clan clan;
    private final ClanWarp warp;

    public ClanWarpActionsGUI(Ztone plugin, Player player, Clan clan, ClanWarp warp) {
        super(plugin, player, "&8&l✦ &d" + warp.getName(), 27);
        this.clan = clan;
        this.warp = warp;
    }

    @Override
    public void setup() {
        fillFancyBorder();

        ClanMember member = clan.getMember(player.getUniqueId());

        List<String> infoLore = new ArrayList<>();
        infoLore.add("&7Welt: &e" + warp.getWorldName());
        infoLore.add("&7Position: &e" + (int) warp.getX() + ", " + (int) warp.getY() + ", " + (int) warp.getZ());
        infoLore.add("&7Für Allianzen: " + (warp.isVisibleToAllies() ? "&aJa" : "&cNein"));

        setItem(4, new ItemBuilder(Material.ENDER_PEARL)
                .name("&d" + warp.getName())
                .lore(infoLore)
                .glow(warp.isVisibleToAllies())
                .build());

        setItem(10, new ItemBuilder(Material.ENDER_EYE)
                .name("&a&l➤ Teleportieren")
                .lore("&a▶ &7Klicke zum Teleportieren")
                .build(), () -> {
            player.closeInventory();
            player.performCommand("clan warp " + warp.getName());
        });

        if (member != null && member.hasPermission(Permission.SET_WARP)) {
            boolean shared = warp.isVisibleToAllies();
            setItem(12, new ItemBuilder(shared ? Material.LIME_DYE : Material.GRAY_DYE)
                    .name(shared ? "&a&l✔ Für Allianzen freigegeben" : "&7&l✘ Für Allianzen gesperrt")
                    .lore(
                            "&7Verbündete Clans können",
                            "&7freigegebene Warps ebenfalls",
                            "&7mit /clan warp " + warp.getName() + " nutzen.",
                            "",
                            "&a▶ &7Klicke zum Umschalten"
                    )
                    .glow(shared)
                    .build(), () -> {
                warp.setVisibleToAllies(!warp.isVisibleToAllies());
                plugin.getDatabaseManager().saveWarp(warp);
                plugin.getMessageManager().send(player, warp.isVisibleToAllies()
                        ? "warp.now-shared" : "warp.now-unshared",
                        MessageManager.of("name", warp.getName()));
                new ClanWarpActionsGUI(plugin, player, clan, warp).open();
            });
        }

        if (member != null && member.hasPermission(Permission.SET_WARP)) {
            setItem(14, new ItemBuilder(Material.COMPASS)
                    .name("&e&l✎ Position aktualisieren")
                    .lore(
                            "&7Verschiebt diesen Warp an",
                            "&7deine aktuelle Position.",
                            "",
                            "&a▶ &7Klicke zum Aktualisieren"
                    )
                    .build(), () -> {
                warp.setLocation(player.getLocation());
                plugin.getDatabaseManager().saveWarp(warp);
                plugin.getMessageManager().send(player, "warp.position-updated",
                        MessageManager.of("name", warp.getName()));
                new ClanWarpActionsGUI(plugin, player, clan, warp).open();
            });
        }

        if (member != null && member.hasPermission(Permission.DEL_WARP)) {
            setItem(16, new ItemBuilder(Material.BARRIER)
                    .name("&c&l✘ Löschen")
                    .lore("&a▶ &7Klicke zum Löschen")
                    .build(), () -> new ConfirmGUI(plugin, player,
                    "Warp '" + warp.getName() + "' löschen?",
                    () -> {
                        clan.removeWarp(warp.getName());
                        plugin.getDatabaseManager().deleteWarp(clan.getId(), warp.getName());
                        plugin.getMessageManager().send(player, "warp.deleted",
                                MessageManager.of("name", warp.getName()));
                        new ClanWarpsGUI(plugin, player, clan).open();
                    },
                    () -> new ClanWarpActionsGUI(plugin, player, clan, warp).open()
            ).open());
        }

        addBackButton(22, () -> new ClanWarpsGUI(plugin, player, clan).open());

        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }
}
