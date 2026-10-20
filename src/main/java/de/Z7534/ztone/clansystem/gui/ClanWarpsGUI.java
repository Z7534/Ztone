package de.Z7534.ztone.clansystem.gui;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.managers.LevelManager;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.models.ClanMember;
import de.Z7534.ztone.clansystem.models.ClanWarp;
import de.Z7534.ztone.clansystem.models.Permission;
import de.Z7534.ztone.clansystem.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ClanWarpsGUI extends AbstractGUI {

    private final Clan clan;
    private final ClanMember member;

    public ClanWarpsGUI(Ztone plugin, Player player, Clan clan) {
        super(plugin, player, plugin.getMessageManager().getRaw("gui.warps"), 45);
        this.clan = clan;
        this.member = clan.getMember(player.getUniqueId());
    }

    @Override
    public void setup() {
        fillFancyBorder();

        LevelManager levelManager = plugin.getLevelManager();
        int maxWarps = levelManager.getMaxWarps(clan.getLevel()) + clan.getBonusWarps();

        setItem(4, new ItemBuilder(Material.ENDER_PEARL)
                .name("&5Clan-Warps")
                .lore(
                        "&7Warps: &e" + clan.getWarpCount() + "&8/&e" + maxWarps,
                        "",
                        "&7Klicke auf einen Warp, um ihn",
                        "&7zu nutzen, freizugeben oder",
                        "&7zu bearbeiten."
                )
                .build());

        List<ClanWarp> warps = new ArrayList<>(clan.getWarps().values());
        int slot = 10;

        for (ClanWarp warp : warps) {

            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 35) break;

            List<String> lore = new ArrayList<>();
            lore.add("&7Welt: &e" + warp.getWorldName());
            lore.add("&7Position: &e" + (int) warp.getX() + ", " + (int) warp.getY() + ", " + (int) warp.getZ());
            lore.add("&7Für Allianzen: " + (warp.isVisibleToAllies() ? "&aJa" : "&cNein"));
            lore.add("");
            lore.add("&a▶ &7Klicke zum Öffnen");

            setItem(slot, new ItemBuilder(Material.ENDER_PEARL)
                    .name("&d" + warp.getName())
                    .glow(warp.isVisibleToAllies())
                    .lore(lore)
                    .build(), () -> new ClanWarpActionsGUI(plugin, player, clan, warp).open());

            slot++;
        }

        if (member != null && member.hasPermission(Permission.SET_WARP) && clan.getWarpCount() < maxWarps) {
            setItem(40, new ItemBuilder(Material.EMERALD)
                    .name("&a&l➤ Neuen Warp setzen")
                    .lore(
                            "&7Setze einen neuen Clan-Warp",
                            "&7an deiner aktuellen Position.",
                            "",
                            "&a▶ &7Klicke zum Setzen"
                    )
                    .build(), () -> plugin.getChatInputManager().request(player,
                    "Bitte gib den Namen für den neuen Warp im Chat ein:",
                    input -> {
                        player.performCommand("clan setwarp " + input.trim().replace(" ", "_"));
                        new ClanWarpsGUI(plugin, player, clan).open();
                    }));
        }

        setItem(44, new ItemBuilder(Material.ENDER_EYE)
                .name("&b&l➤ Allianz-Warps")
                .lore(
                        "&7Zeigt alle Warps, die eure",
                        "&7verbündeten Clans für",
                        "&7Allianzen freigegeben haben.",
                        "",
                        "&a▶ &7Klicke zum Öffnen"
                )
                .build(), () -> new ClanAllyWarpsGUI(plugin, player, clan).open());

        addBackButton(36, () -> new ClanMainGUI(plugin, player, clan).open());

        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }
}
