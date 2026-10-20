package de.Z7534.ztone.clansystem.gui;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.managers.MessageManager;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.models.ClanHome;
import de.Z7534.ztone.clansystem.models.ClanMember;
import de.Z7534.ztone.clansystem.models.Permission;
import org.bukkit.Material;
import de.Z7534.ztone.clansystem.utils.ItemBuilder;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ClanHomeActionsGUI extends AbstractGUI {

    private final Clan clan;
    private final ClanHome home;

    public ClanHomeActionsGUI(Ztone plugin, Player player, Clan clan, ClanHome home) {
        super(plugin, player, "&8&l✦ &c" + home.getName(), 27);
        this.clan = clan;
        this.home = home;
    }

    @Override
    public void setup() {
        fillFancyBorder();

        ClanMember member = clan.getMember(player.getUniqueId());

        List<String> infoLore = new ArrayList<>();
        infoLore.add("&7Welt: &e" + home.getWorldName());
        infoLore.add("&7Position: &e" + (int) home.getX() + ", " + (int) home.getY() + ", " + (int) home.getZ());

        setItem(4, new ItemBuilder(Material.RED_BED)
                .name("&e" + home.getName())
                .lore(infoLore)
                .build());

        setItem(11, new ItemBuilder(Material.ENDER_EYE)
                .name("&a&l➤ Teleportieren")
                .lore("&a▶ &7Klicke zum Teleportieren")
                .build(), () -> {
            player.closeInventory();
            player.performCommand("clan home " + home.getName());
        });

        if (member != null && member.hasPermission(Permission.SET_HOME)) {
            setItem(13, new ItemBuilder(Material.COMPASS)
                    .name("&e&l✎ Position aktualisieren")
                    .lore(
                            "&7Verschiebt dieses Home an",
                            "&7deine aktuelle Position.",
                            "",
                            "&a▶ &7Klicke zum Aktualisieren"
                    )
                    .build(), () -> {
                home.setLocation(player.getLocation());
                plugin.getDatabaseManager().saveHome(home);
                plugin.getMessageManager().send(player, "home.position-updated",
                        MessageManager.of("name", home.getName()));
                new ClanHomeActionsGUI(plugin, player, clan, home).open();
            });
        }

        if (member != null && member.hasPermission(Permission.DEL_HOME)) {
            setItem(15, new ItemBuilder(Material.BARRIER)
                    .name("&c&l✘ Löschen")
                    .lore("&a▶ &7Klicke zum Löschen")
                    .build(), () -> new ConfirmGUI(plugin, player,
                    "Home '" + home.getName() + "' löschen?",
                    () -> {
                        clan.removeHome(home.getName());
                        plugin.getDatabaseManager().deleteHome(clan.getId(), home.getName());
                        plugin.getMessageManager().send(player, "home.deleted",
                                MessageManager.of("name", home.getName()));
                        new ClanHomesGUI(plugin, player, clan).open();
                    },
                    () -> new ClanHomeActionsGUI(plugin, player, clan, home).open()
            ).open());
        }

        addBackButton(22, () -> new ClanHomesGUI(plugin, player, clan).open());

        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }
}
