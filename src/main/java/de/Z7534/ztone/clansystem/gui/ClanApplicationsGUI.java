package de.Z7534.ztone.clansystem.gui;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.managers.LevelManager;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ClanApplicationsGUI extends AbstractGUI {

    private final Clan clan;
    private final boolean adminView;

    public ClanApplicationsGUI(Ztone plugin, Player player, Clan clan) {
        this(plugin, player, clan, false);
    }

    public ClanApplicationsGUI(Ztone plugin, Player player, Clan clan, boolean adminView) {
        super(plugin, player, plugin.getMessageManager().getRaw("gui.applications"), 45);
        this.clan = clan;
        this.adminView = adminView;
    }

    @Override
    public void setup() {
        fillFancyBorder();

        Map<UUID, Long> applications = clan.getPendingApplications();
        LevelManager levelManager = plugin.getLevelManager();
        int maxMembers = levelManager.getMaxMembers(clan.getLevel());

        setItem(4, new ItemBuilder(Material.PAPER)
                .name("&eBewerbungen")
                .lore(
                        "&7Ausstehend: &e" + applications.size(),
                        "",
                        "&7Mitglieder: &e" + clan.getMemberCount() + "&8/&e" + maxMembers
                )
                .build());

        List<UUID> applicants = new ArrayList<>(applications.keySet());
        int slot = 10;

        for (UUID applicantUuid : applicants) {
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 35) break;

            OfflinePlayer applicant = Bukkit.getOfflinePlayer(applicantUuid);
            String name = applicant.getName() != null ? applicant.getName() : applicantUuid.toString();

            List<String> lore = new ArrayList<>();
            lore.add("&7Status: " + (applicant.isOnline() ? "&aOnline" : "&cOffline"));
            lore.add("");
            lore.add("&a▶ &7Klicke zum Öffnen");

            final UUID finalApplicantUuid = applicantUuid;
            final String finalName = name;
            setItem(slot, new ItemBuilder(Material.PLAYER_HEAD)
                    .skullOwner(applicantUuid)
                    .name("&e" + name)
                    .lore(lore)
                    .build(), () -> new ClanApplicationActionsGUI(plugin, player, clan, finalApplicantUuid, finalName, adminView).open());

            slot++;
        }

        if (applications.isEmpty()) {
            setItem(22, new ItemBuilder(Material.BARRIER)
                    .name("&7Keine Bewerbungen")
                    .lore(
                            "&7Es gibt derzeit keine",
                            "&7ausstehenden Bewerbungen."
                    )
                    .build());
        }

        addBackButton(40, () -> new ClanSettingsGUI(plugin, player, clan, adminView).open());

        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }
}
