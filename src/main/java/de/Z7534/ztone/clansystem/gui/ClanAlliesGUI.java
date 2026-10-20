package de.Z7534.ztone.clansystem.gui;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.models.ClanMember;
import de.Z7534.ztone.clansystem.models.Permission;
import de.Z7534.ztone.clansystem.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ClanAlliesGUI extends AbstractGUI {

    private final Clan clan;
    private final ClanMember member;

    public ClanAlliesGUI(Ztone plugin, Player player, Clan clan) {
        super(plugin, player, plugin.getMessageManager().getRaw("gui.allies"), 45);
        this.clan = clan;
        this.member = clan.getMember(player.getUniqueId());
    }

    @Override
    public void setup() {
        fillFancyBorder();

        boolean canManage = member != null && member.hasPermission(Permission.ALLY_MANAGE);

        setItem(4, new ItemBuilder(Material.GOLDEN_SWORD)
                .name("&eAllianzen")
                .lore(
                        "&7Verbündete: &e" + clan.getAllies().size(),
                        "",
                        "&7Alliierte Clans können:",
                        "&8- &7Sich nicht gegenseitig angreifen",
                        "&8- &7Im Allianz-Chat schreiben",
                        "&8- &7Warps teilen (wenn aktiviert)"
                )
                .hideFlags()
                .build());

        List<Integer> allyIds = new ArrayList<>(clan.getAllies());
        int slot = 10;

        for (int allyId : allyIds) {

            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 35) break;

            Clan ally = plugin.getClanManager().getClan(allyId);
            if (ally == null) continue;

            List<String> lore = new ArrayList<>();
            lore.add("&7Level: &e" + ally.getLevel());
            lore.add("&7Mitglieder: &e" + ally.getMemberCount());
            lore.add("&7Online: &a" + ally.getOnlineMembers().size());

            if (canManage) {
                lore.add("");
                lore.add("&cKlicke zum Auflösen der Allianz");
            }

            final int finalAllyId = allyId;
            setItem(slot, new ItemBuilder(Material.GOLDEN_CHESTPLATE)
                    .name("&a" + ally.getName())
                    .lore(lore)
                    .hideFlags()
                    .build(), () -> {
                if (canManage) {
                    new ConfirmGUI(plugin, player,
                            "Allianz mit " + ally.getName() + " auflösen?",
                            () -> {
                                plugin.getAllianceManager().breakAlliance(clan.getId(), finalAllyId);
                                plugin.getMessageManager().send(player, "alliance.broken",
                                        de.Z7534.ztone.clansystem.managers.MessageManager.of("ally", ally.getColoredName()));
                                new ClanAlliesGUI(plugin, player, clan).open();
                            },
                            () -> new ClanAlliesGUI(plugin, player, clan).open()
                    ).open();
                }
            });

            slot++;
        }

        if (canManage) {
            setItem(42, new ItemBuilder(Material.PAPER)
                    .name("&e&l➤ Allianz einladen")
                    .lore(
                            "&7Lade einen anderen Clan",
                            "&7zu einer Allianz ein.",
                            "",
                            "&a▶ &7Klicke zum Einladen"
                    )
                    .build(), () -> plugin.getChatInputManager().request(player,
                    "Bitte gib den Namen des Clans ein, den du zur Allianz einladen möchtest:",
                    input -> {
                        player.performCommand("clan ally invite " + input.trim());
                        new ClanAlliesGUI(plugin, player, clan).open();
                    }));
        }

        addBackButton(40, () -> new ClanMainGUI(plugin, player, clan).open());

        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }
}
