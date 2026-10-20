package de.Z7534.ztone.clansystem.gui;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.models.ClanWarp;
import de.Z7534.ztone.clansystem.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ClanAllyWarpsGUI extends AbstractGUI {

    private final Clan clan;

    public ClanAllyWarpsGUI(Ztone plugin, Player player, Clan clan) {
        super(plugin, player, "&8&l✦ &bAllianz-Warps", 45);
        this.clan = clan;
    }

    @Override
    public void setup() {
        fillFancyBorder();

        List<ClanWarp> allyWarps = new ArrayList<>();
        List<String> allyWarpClanNames = new ArrayList<>();

        for (int allyId : clan.getAllies()) {
            Clan ally = plugin.getClanManager().getClan(allyId);
            if (ally == null) {
                continue;
            }
            for (ClanWarp warp : ally.getWarps().values()) {
                if (warp.isVisibleToAllies()) {
                    allyWarps.add(warp);
                    allyWarpClanNames.add(ally.getName());
                }
            }
        }

        setItem(4, new ItemBuilder(Material.ENDER_EYE)
                .name("&bAllianz-Warps")
                .lore(
                        "&7Freigegebene Warps eurer",
                        "&7verbündeten Clans: &e" + allyWarps.size(),
                        "",
                        "&7Klicke auf einen Warp, um",
                        "&7dorthin zu teleportieren."
                )
                .build());

        int slot = 10;
        for (int i = 0; i < allyWarps.size(); i++) {
            ClanWarp warp = allyWarps.get(i);
            String allyClanName = allyWarpClanNames.get(i);

            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 35) break;

            setItem(slot, new ItemBuilder(Material.ENDER_EYE)
                    .name("&d" + warp.getName())
                    .lore(
                            "&7Clan: &e" + allyClanName,
                            "&7Welt: &e" + warp.getWorldName(),
                            "&7Position: &e" + (int) warp.getX() + ", " + (int) warp.getY() + ", " + (int) warp.getZ(),
                            "",
                            "&a▶ &7Klicke zum Teleportieren"
                    )
                    .build(), () -> {
                player.closeInventory();
                player.performCommand("clan warp " + warp.getName());
            });

            slot++;
        }

        addBackButton(40, () -> new ClanWarpsGUI(plugin, player, clan).open());

        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }
}
