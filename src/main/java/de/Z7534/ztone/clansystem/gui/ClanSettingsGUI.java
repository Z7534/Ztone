package de.Z7534.ztone.clansystem.gui;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.models.ClanMember;
import de.Z7534.ztone.clansystem.models.Permission;
import de.Z7534.ztone.clansystem.utils.ClanIcons;
import de.Z7534.ztone.clansystem.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class ClanSettingsGUI extends AbstractGUI {

    private final Clan clan;
    private final ClanMember member;
    private final boolean adminView;

    public ClanSettingsGUI(Ztone plugin, Player player, Clan clan) {
        this(plugin, player, clan, false);
    }

    /**
     * @param adminView true, wenn das Menü von einem Admin über /clanadmin settings
     *                  geöffnet wurde. Der Spieler muss dann kein Mitglied des Clans
     *                  sein und erhält vollen Zugriff auf alle Einstellungen.
     */
    public ClanSettingsGUI(Ztone plugin, Player player, Clan clan, boolean adminView) {
        super(plugin, player, plugin.getMessageManager().getRaw("gui.settings"), 45);
        this.clan = clan;
        this.member = clan.getMember(player.getUniqueId());
        this.adminView = adminView;
    }

    private boolean hasPerm(Permission permission) {
        return adminView || (member != null && member.hasPermission(permission));
    }

    @Override
    public void setup() {
        fillFancyBorder();

        // =========================================================================
        // REIHE 1 (Slots 11 - 15): IDENTITÄT & AUSSEHEN
        // =========================================================================

        // Slot 11: Clan umbenennen
        if (clan.isLeader(player.getUniqueId()) || adminView || hasPerm(Permission.SETTINGS)) {
            setItem(11, new ItemBuilder(Material.NAME_TAG)
                    .name("&6&l➤ Clan umbenennen")
                    .lore(
                            "&7Aktueller Name: &e" + clan.getName(),
                            "&7Ändere den Namen deines Clans.",
                            "",
                            "&a▶ &7Klicke zum Umbenennen"
                    )
                    .build(), () -> {
                plugin.getChatInputManager().request(player, "Bitte gib den neuen Clannamen im Chat ein:", input -> {
                    player.performCommand("clan rename " + input);
                });
            });
        }

        // Slot 12: Clan-Farbe
        if (hasPerm(Permission.SETTINGS)) {
            setItem(12, new ItemBuilder(Material.CYAN_DYE)
                    .name("&b&l➤ Clan-Farbe &8(" + clan.getColoredName() + "&r&8)")
                    .lore(
                            "&7Färbt deinen Clan-Namen in",
                            "&7Nachrichten und deinen",
                            "&7Clan-Suffix ein - wahlweise",
                            "&7einfarbig oder als Gradient.",
                            "",
                            "&a▶ &7Klicke zum Ändern"
                    )
                    .build(), () -> new ClanColorGUI(plugin, player, clan, adminView).open());
        }

        // Slot 13: Clan-Icon (Zentrum der 1. Reihe)
        if (hasPerm(Permission.SETTINGS)) {
            setItem(13, new ItemBuilder(ClanIcons.fromName(clan.getIcon()))
                    .name("&e&l➤ Clan-Icon")
                    .lore(
                            "&7Aktuell: &f" + clan.getIcon(),
                            "&7Wähle ein Icon, das den",
                            "&7Clan in Menüs repräsentiert.",
                            "",
                            "&a▶ &7Klicke zum Ändern"
                    )
                    .build(), () -> new ClanIconGUI(plugin, player, clan, adminView).open());
        }

        // Slot 14: Clan-Suffix
        if (hasPerm(Permission.SET_SUFFIX)) {
            setItem(14, new ItemBuilder(Material.FEATHER)
                    .name("&a&l➤ Clan-Suffix")
                    .lore(
                            "&7Aktuell: &r" + (clan.getSuffix().isEmpty() ? "&8Keiner" : clan.getColoredSuffix()),
                            "&7Passe das Kürzel deines Clans an.",
                            "",
                            "&a▶ &7Klicke zum Bearbeiten"
                    )
                    .build(), () -> new ClanSuffixGUI(plugin, player, clan, adminView).open());
        }

        // Slot 15: Glow-Effekt
        if (hasPerm(Permission.SETTINGS)) {
            setItem(15, new ItemBuilder(clan.isGlowEnabled() ? Material.GLOWSTONE_DUST : Material.GUNPOWDER)
                    .name("&d&l➤ Glow-Effekt")
                    .lore(
                            "&7Status: " + (clan.isGlowEnabled() ? "&aAktiviert" : "&cDeaktiviert"),
                            "&7Lässt Clan-Mitglieder durch",
                            "&7Wände hindurch leuchten.",
                            "",
                            "&a▶ &7Klicke zum Umschalten"
                    )
                    .glow(clan.isGlowEnabled())
                    .build(), () -> new ClanGlowGUI(plugin, player, clan, adminView).open());
        }

        // =========================================================================
        // REIHE 2 (Slots 20, 21 | 22 Trenner | 23, 24): VERWALTUNG & RECHTE
        // =========================================================================

        // Slot 20: Beitrittsart
        if (hasPerm(Permission.SETTINGS)) {
            Material joinMaterial;
            String joinTypeName;
            String joinTypeDesc;

            switch (clan.getJoinType()) {
                case OPEN:
                    joinMaterial = Material.OAK_DOOR;
                    joinTypeName = "&aOffen";
                    joinTypeDesc = "&7Jeder kann beitreten.";
                    break;
                case APPLY:
                    joinMaterial = Material.WRITABLE_BOOK;
                    joinTypeName = "&eBewerbung";
                    joinTypeDesc = "&7Spieler müssen sich bewerben.";
                    break;
                default:
                    joinMaterial = Material.IRON_DOOR;
                    joinTypeName = "&cNur Einladung";
                    joinTypeDesc = "&7Spieler müssen eingeladen werden.";
                    break;
            }

            setItem(20, new ItemBuilder(joinMaterial)
                    .name("&6&l➤ Beitrittsart: " + joinTypeName)
                    .lore(
                            joinTypeDesc,
                            "",
                            "&eKlicke zum Ändern"
                    )
                    .build(), () -> {
                Clan.JoinType[] types = Clan.JoinType.values();
                int currentIndex = clan.getJoinType().ordinal();
                int nextIndex = (currentIndex + 1) % types.length;
                clan.setJoinType(types[nextIndex]);
                plugin.getClanManager().updateClanInDatabase(clan);
                refresh();
                player.updateInventory();
            });
        }

        // Slot 21: Bewerbungen
        if (hasPerm(Permission.INVITE)) {
            int applicationCount = clan.getPendingApplications().size();
            boolean isApply = clan.getJoinType() == Clan.JoinType.APPLY;
            setItem(21, new ItemBuilder(Material.PAPER)
                    .name("&e&l➤ Bewerbungen")
                    .lore(
                            "&7Ausstehende Bewerbungen: &e" + applicationCount,
                            (isApply ? "&7Verwalte Anfragen von Spielern." : "&8(Beitrittsart ist nicht 'Bewerbung')"),
                            "",
                            "&eKlicke zum Öffnen"
                    )
                    .glow(applicationCount > 0)
                    .build(), () -> new ClanApplicationsGUI(plugin, player, clan, adminView).open());
        }

        // Slot 22: Zentrierter Trenner
        setItem(22, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("&8").build());

        // Slot 23: Warp-Sichtbarkeit
        if (hasPerm(Permission.SETTINGS)) {
            int visibleWarps = (int) clan.getWarps().values().stream()
                    .filter(w -> w.isVisibleToAllies())
                    .count();

            setItem(23, new ItemBuilder(Material.ENDER_EYE)
                    .name("&5&l➤ Warp-Sichtbarkeit")
                    .lore(
                            "&7Warps für Allianzen sichtbar:",
                            "&e" + visibleWarps + "&8/&e" + clan.getWarpCount(),
                            "",
                            "&7Bearbeite einzelne Warps",
                            "&7in den Warp-Einstellungen."
                    )
                    .build());
        }

        // Slot 24: Ränge verwalten
        if (hasPerm(Permission.RANK_MANAGE)) {
            setItem(24, new ItemBuilder(Material.EMERALD)
                    .name("&a&l➤ Ränge verwalten")
                    .lore(
                            "&7Erstelle, bearbeite und",
                            "&7lösche Clan-Ränge.",
                            "",
                            "&eKlicke zum Öffnen"
                    )
                    .build(), () -> new ClanRankGUI(plugin, player, clan, adminView).open());
        }

        // =========================================================================
        // REIHE 3 (Slot 31): DANGER ZONE (Zentriert)
        // =========================================================================

        if (clan.isLeader(player.getUniqueId()) || adminView) {
            // Clan auflösen für Leader oder Admins
            setItem(31, new ItemBuilder(Material.TNT)
                    .name("&c&l➤ Clan auflösen")
                    .lore(
                            "&c&lACHTUNG!",
                            "&7Dies kann nicht rückgängig",
                            "&7gemacht werden!",
                            "",
                            "&cKlicke zum Auflösen"
                    )
                    .build(), () -> new ConfirmGUI(plugin, player,
                    "Clan auflösen?",
                    () -> {
                        if (plugin.getConfigManager().isBroadcastClanDisbanded()) {
                            plugin.getMessageManager().broadcastExcept("broadcast.clan-disbanded", player,
                                    de.Z7534.ztone.clansystem.managers.MessageManager.of("clan", clan.getColoredName()));
                        }

                        plugin.getClanManager().disbandClan(clan);
                        plugin.getMessageManager().send(player, "clan.disbanded",
                                de.Z7534.ztone.clansystem.managers.MessageManager.of("clan", clan.getColoredName()));
                        player.closeInventory();
                    },
                    () -> new ClanSettingsGUI(plugin, player, clan, adminView).open()
            ).open());
        } else if (member != null) {
            // Clan verlassen für reguläre Mitglieder
            setItem(31, new ItemBuilder(Material.IRON_DOOR)
                    .name("&c&l➤ Clan verlassen")
                    .lore(
                            "&7Du verlässt den Clan &e" + clan.getName() + "&7.",
                            "&7Danach gilt ein kurzer",
                            "&7Cooldown, bevor du einen",
                            "&7neuen Clan gründen kannst.",
                            "",
                            "&cKlicke zum Verlassen"
                    )
                    .build(), () -> new ConfirmGUI(plugin, player,
                    "Clan wirklich verlassen?",
                    () -> {
                        player.closeInventory();
                        player.performCommand("clan leave");
                    },
                    () -> new ClanSettingsGUI(plugin, player, clan, adminView).open()
            ).open());
        }

        // =========================================================================
        // REIHE 4 (Slot 40): ZURÜCK / SCHLIESSEN (Zentriert)
        // =========================================================================

        if (adminView) {
            addCloseButton(40);
        } else {
            addBackButton(40, () -> new ClanMainGUI(plugin, player, clan).open());
        }

        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }
}
