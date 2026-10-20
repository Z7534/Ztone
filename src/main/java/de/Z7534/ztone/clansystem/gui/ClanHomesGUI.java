package de.Z7534.ztone.clansystem.gui;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.managers.LevelManager;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.models.ClanHome;
import de.Z7534.ztone.clansystem.models.ClanMember;
import de.Z7534.ztone.clansystem.models.Permission;
import de.Z7534.ztone.clansystem.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ClanHomesGUI extends AbstractGUI {

    private final Clan clan;
    private final ClanMember member;

    public ClanHomesGUI(Ztone plugin, Player player, Clan clan) {
        super(plugin, player, plugin.getMessageManager().getRaw("gui.homes"), 45);
        this.clan = clan;
        this.member = clan.getMember(player.getUniqueId());
    }

    @Override
    public void setup() {
        fillFancyBorder();

        LevelManager levelManager = plugin.getLevelManager();
        int maxHomes = levelManager.getMaxHomes(clan.getLevel());

        setItem(4, new ItemBuilder(Material.RED_BED)
                .name("&cClan-Homes")
                .lore(
                        "&7Homes: &e" + clan.getHomeCount() + "&8/&e" + maxHomes,
                        "",
                        "&7Klicke auf ein Home, um es",
                        "&7zu nutzen, zu bearbeiten",
                        "&7oder zu löschen."
                )
                .build());

        List<ClanHome> homes = new ArrayList<>(clan.getHomes().values());
        int slot = 10;

        for (ClanHome home : homes) {

            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 35) break;

            List<String> lore = new ArrayList<>();
            lore.add("&7Welt: &e" + home.getWorldName());
            lore.add("&7Position: &e" + (int) home.getX() + ", " + (int) home.getY() + ", " + (int) home.getZ());
            lore.add("");
            lore.add("&a▶ &7Klicke zum Öffnen");

            setItem(slot, new ItemBuilder(Material.RED_BED)
                    .name("&e" + home.getName())
                    .lore(lore)
                    .build(), () -> new ClanHomeActionsGUI(plugin, player, clan, home).open());

            slot++;
        }

        if (member != null && member.hasPermission(Permission.SET_HOME) && clan.getHomeCount() < maxHomes) {
            setItem(40, new ItemBuilder(Material.EMERALD)
                    .name("&a&l➤ Neues Home setzen")
                    .lore(
                            "&7Setze ein neues Clan-Home",
                            "&7an deiner aktuellen Position.",
                            "",
                            "&a▶ &7Klicke zum Setzen"
                    )
                    .build(), () -> {
                        // Es gibt nur ein Home pro Clan - ein Name ist daher unnötig
                        // und wird nicht mehr abgefragt.
                        player.performCommand("clan sethome");
                        new ClanHomesGUI(plugin, player, clan).open();
                    });
        }

        addBackButton(36, () -> new ClanMainGUI(plugin, player, clan).open());

        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }
}
