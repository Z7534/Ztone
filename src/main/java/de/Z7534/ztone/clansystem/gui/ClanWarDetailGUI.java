package de.Z7534.ztone.clansystem.gui;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.models.ClanMember;
import de.Z7534.ztone.clansystem.models.ClanWar;
import de.Z7534.ztone.clansystem.models.Permission;
import de.Z7534.ztone.clansystem.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ClanWarDetailGUI extends AbstractGUI {

    private final Clan clan;
    private final ClanWar war;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    public ClanWarDetailGUI(Ztone plugin, Player player, Clan clan, ClanWar war) {
        super(plugin, player, "§8⚔ Krieg-Details", 54);
        this.clan = clan;
        this.war = war;
    }

    @Override
    public void setup() {
        fillFancyBorder();

        int opponentId = war.getOpponent(clan.getId());
        Clan opponent = plugin.getClanManager().getClan(opponentId);
        String oppName = opponent != null ? opponent.getName() : "Unbekannt";

        boolean isAttacker = war.isAttacker(clan.getId());
        int ourPoints = isAttacker ? war.getAttackerPoints() : war.getDefenderPoints();
        int theirPoints = isAttacker ? war.getDefenderPoints() : war.getAttackerPoints();

        // 1. Center header item
        List<String> centerLore = new ArrayList<>();
        centerLore.add("§7Gestartet: §e" + dateFormat.format(new Date(war.getStartedAt())));
        long durationMillis = (war.isActive() ? System.currentTimeMillis() : war.getEndedAt()) - war.getStartedAt();
        long minutes = (durationMillis / 1000) / 60;
        long hours = minutes / 60;
        minutes = minutes % 60;
        centerLore.add("§7Kampfdauer: §e" + hours + "h " + minutes + "m");
        centerLore.add("");
        centerLore.add("§7Punktestand:");
        centerLore.add(" §a" + clan.getName() + ": §e" + ourPoints + " Punkte");
        centerLore.add(" §c" + oppName + ": §e" + theirPoints + " Punkte");
        centerLore.add("");
        if (ourPoints > theirPoints) {
            centerLore.add("§a✔ Dein Clan führt diesen Krieg an!");
        } else if (theirPoints > ourPoints) {
            centerLore.add("§c✖ Der gegnerische Clan führt aktuell!");
        } else {
            centerLore.add("§e⚖ Gleichstand der Punkte!");
        }
        centerLore.add("");
        centerLore.add("§6Siegbedingung:");
        centerLore.add("§7Töte jeden Teilnehmer mit PvP-Rechten §c3x§7!");
        centerLore.add("§7Belohnung für den Sieger:");
        centerLore.add("§8» §e+500 Clan-Punkte");
        centerLore.add("§8» §b+3 Clan-Warps permanent");
        centerLore.add("§8» §a+50.000$ Clankasse");

        setItem(4, new ItemBuilder(Material.NETHERITE_SWORD)
                .name("§c§l⚔ KRIEGS-STATISTIK ⚔")
                .lore(centerLore)
                .glow()
                .hideFlags()
                .build());

        // Clan A Header banner/item
        setItem(2, new ItemBuilder(Material.CYAN_BANNER)
                .name("§a§l" + clan.getName() + " §7(Dein Clan)")
                .lore("§7Punkte: §e" + ourPoints, "§7Rolle: " + (isAttacker ? "§cAngreifer" : "§eVerteidiger"))
                .build());

        // Clan B Header banner/item
        setItem(6, new ItemBuilder(Material.RED_BANNER)
                .name("§c§l" + oppName + " §7(Gegner)")
                .lore("§7Punkte: §e" + theirPoints, "§7Rolle: " + (!isAttacker ? "§cAngreifer" : "§eVerteidiger"))
                .build());

        // Middle vertical divider
        int[] dividerSlots = {13, 22, 31, 40};
        for (int ds : dividerSlots) {
            setItem(ds, new ItemBuilder(Material.ORANGE_STAINED_GLASS_PANE).name("§8⚔").build());
        }

        // Left side: Our clan participants
        int[] leftSlots = {19, 20, 21, 28, 29, 30, 37, 38, 39};
        int leftIdx = 0;
        for (ClanMember m : clan.getMembers().values()) {
            if (m.hasPermission(Permission.WAR_ALWAYS_PVP)) {
                if (leftIdx >= leftSlots.length) break;
                int deaths = war.getParticipantDeaths(m.getUuid());
                int lives = Math.max(0, 3 - deaths);
                int kills = war.getKillsForPlayer(m.getUuid());

                String heartBar;
                if (lives == 3) heartBar = "§a❤❤❤ §2(3/3)";
                else if (lives == 2) heartBar = "§e❤❤§8🖤 §6(2/3)";
                else if (lives == 1) heartBar = "§c❤§8🖤🖤 §c(1/3)";
                else heartBar = "§4🖤🖤🖤 §4(0/3 K.O.)";

                List<String> pLore = new ArrayList<>();
                pLore.add("§7Rolle: §e" + m.getDisplayRankName());
                pLore.add("§7Status: " + (lives == 0 ? "§4§l☠ AUSGESCHIEDEN" : "§a§lIM KAMPF"));
                pLore.add("§7Verbleibende Leben: " + heartBar);
                pLore.add("§7Kills im Krieg: §b" + kills);

                setItem(leftSlots[leftIdx++], new ItemBuilder(Material.PLAYER_HEAD)
                        .skullOwner(m.getUuid())
                        .name((lives == 0 ? "§8§m" : "§a") + m.getName())
                        .lore(pLore)
                        .build());
            }
        }

        // Right side: Opponent clan participants
        int[] rightSlots = {23, 24, 25, 32, 33, 34, 41, 42, 43};
        int rightIdx = 0;
        if (opponent != null) {
            for (ClanMember m : opponent.getMembers().values()) {
                if (m.hasPermission(Permission.WAR_ALWAYS_PVP)) {
                    if (rightIdx >= rightSlots.length) break;
                    int deaths = war.getParticipantDeaths(m.getUuid());
                    int lives = Math.max(0, 3 - deaths);
                    int kills = war.getKillsForPlayer(m.getUuid());

                    String heartBar;
                    if (lives == 3) heartBar = "§a❤❤❤ §2(3/3)";
                    else if (lives == 2) heartBar = "§e❤❤§8🖤 §6(2/3)";
                    else if (lives == 1) heartBar = "§c❤§8🖤🖤 §c(1/3)";
                    else heartBar = "§4🖤🖤🖤 §4(0/3 K.O.)";

                    List<String> pLore = new ArrayList<>();
                    pLore.add("§7Rolle: §e" + m.getDisplayRankName());
                    pLore.add("§7Status: " + (lives == 0 ? "§4§l☠ AUSGESCHIEDEN" : "§a§lIM KAMPF"));
                    pLore.add("§7Verbleibende Leben: " + heartBar);
                    pLore.add("§7Kills im Krieg: §b" + kills);

                    setItem(rightSlots[rightIdx++], new ItemBuilder(Material.PLAYER_HEAD)
                            .skullOwner(m.getUuid())
                            .name((lives == 0 ? "§8§m" : "§c") + m.getName())
                            .lore(pLore)
                            .build());
                }
            }
        }

        // Back button to ClanWarsGUI
        addBackButton(49, () -> new ClanWarsGUI(plugin, player, clan).open());

        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }
}
