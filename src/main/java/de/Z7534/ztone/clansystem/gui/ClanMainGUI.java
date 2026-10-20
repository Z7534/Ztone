package de.Z7534.ztone.clansystem.gui;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.managers.LevelManager;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.models.ClanMember;
import de.Z7534.ztone.clansystem.models.Permission;
import de.Z7534.ztone.clansystem.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class ClanMainGUI extends AbstractGUI {

    private final Clan clan;
    private final ClanMember member;

    public ClanMainGUI(Ztone plugin, Player player, Clan clan) {
        super(plugin, player, plugin.getMessageManager().getRaw("gui.main"), 54);
        this.clan = clan;
        this.member = clan.getMember(player.getUniqueId());
    }

    @Override
    public void setup() {
        fillFancyBorder();

        LevelManager levelManager = plugin.getLevelManager();
        int maxMembers = levelManager.getMaxMembers(clan.getLevel());
        int maxHomes = levelManager.getMaxHomes(clan.getLevel());
        int maxWarps = levelManager.getMaxWarps(clan.getLevel()) + clan.getBonusWarps();

        boolean maxLevel = levelManager.isMaxLevel(clan.getLevel());

        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("&7Suffix: &r" + (clan.getSuffix().isEmpty() ? "&8Keiner" : clan.getColoredSuffix()));
        lore.add("");
        if (maxLevel) {
            lore.add("&7Level &e" + clan.getLevel() + " &6&l(MAX)");
            lore.add("&7" + clan.getPoints() + " Punkte gesamt");
        } else {
            long pointsRequired = levelManager.getPointsRequired(clan.getLevel() + 1);
            String bar = ItemBuilder.progressBar(clan.getPoints(), pointsRequired, 24, "&6", "&8");
            lore.add("&7Level &e" + clan.getLevel() + " &8» &e" + (clan.getLevel() + 1));
            lore.add(bar);
            lore.add("&7" + clan.getPoints() + " &8/ &7" + pointsRequired + " Punkte");
        }
        lore.add("");
        lore.add("&7Mitglieder: &a" + clan.getMemberCount() + "&8/&a" + maxMembers);
        ClanMember leaderMember = clan.getMember(clan.getLeaderUuid());
        lore.add("&7Anführer: &e★ " + (leaderMember != null ? leaderMember.getName() : "Unbekannt"));

        setItem(4, new ItemBuilder(de.Z7534.ztone.clansystem.utils.ClanIcons.fromName(clan.getIcon()))
                .name("&6&l✧ " + clan.getName() + " &6&l✧")
                .lore(lore)
                .glow()
                .build());

        String rankName = member != null ? member.getDisplayRankName() : "-";
        setItem(13, ownHead("&b&l✧ Dein Profil",
                "&7Name: &f" + player.getName(),
                "&7Rang: &e" + rankName,
                "",
                "&8Seit " + new java.text.SimpleDateFormat("dd.MM.yyyy").format(new java.util.Date(member != null ? member.getJoinedAt() : System.currentTimeMillis())) + " im Clan"
        ));

        setItem(20, new ItemBuilder(Material.PAPER)
                .name("&a&l➔ Mitglieder")
                .lore(
                        "&7Zeigt alle Clan-Mitglieder",
                        "&7und deren Ränge an.",
                        "",
                        "&a▶ &7Klicke zum Öffnen"
                )
                .build(), () -> new ClanMembersGUI(plugin, player, clan).open());

        if (member != null && member.hasPermission(Permission.CHEST_ACCESS)) {
            setItem(22, new ItemBuilder(Material.CHEST)
                    .name("&6&l➔ Clan-Truhe")
                    .lore(
                            "&7Gemeinsames Lager für",
                            "&7alle Clan-Mitglieder.",
                            "",
                            "&a▶ &7Klicke zum Öffnen"
                    )
                    .glow()
                    .build(), () -> {
                if (clan.isInDebt()) {
                    player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("§8[§6Clan§8] §cDein Clan hat Schulden! Die Clan-Truhe ist gesperrt."));
                    return;
                }
                new ClanChestGUI(plugin, player, clan).open();
            });
        } else {
            setItem(22, new ItemBuilder(Material.CHEST)
                    .name("&6&l➔ Clan-Truhe")
                    .lore(
                            "&c✖ Kein Zugriff"
                    )
                    .build());
        }

        setItem(24, new ItemBuilder(Material.RED_BED)
                .name("&c&l➔ Clan-Homes")
                .lore(
                        "&7Verwalte die Clan-Homes.",
                        "&7Aktuell: &e" + clan.getHomeCount() + "&8/&e" + maxHomes,
                        "",
                        "&a▶ &7Klicke zum Öffnen"
                )
                .build(), () -> {
                if (clan.isInDebt()) {
                    player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("§8[§6Clan§8] §cDein Clan hat Schulden! Clan-Homes sind gesperrt."));
                    return;
                }
                new ClanHomesGUI(plugin, player, clan).open();
            });

        setItem(30, new ItemBuilder(Material.ENDER_PEARL)
                .name("&5&l➔ Clan-Warps")
                .lore(
                        "&7Verwalte die Clan-Warps.",
                        "&7Aktuell: &e" + clan.getWarpCount() + "&8/&e" + maxWarps,
                        "",
                        "&a▶ &7Klicke zum Öffnen"
                )
                .build(), () -> {
                if (clan.isInDebt()) {
                    player.sendMessage(de.Z7534.ztone.clansystem.utils.ColorUtils.colorize("§8[§6Clan§8] §cDein Clan hat Schulden! Clan-Warps sind gesperrt."));
                    return;
                }
                new ClanWarpsGUI(plugin, player, clan).open();
            });

        if (plugin.getConfigManager().isClankasseEnabled()) {
            String formattedBalance = plugin.getEconomyManager() != null ? plugin.getEconomyManager().format(clan.getBalance()) : (clan.getBalance() + " $");
            setItem(31, new ItemBuilder(Material.GOLD_INGOT)
                    .name("&6&l➔ Clankasse")
                    .lore(
                            "&7Guthaben: &e" + formattedBalance,
                            "",
                            "&7Guthaben deines Clans.",
                            "&7Verwalte Ein- und Auszahlungen.",
                            "",
                            "&a▶ &7Klicke für Transaktionen"
                    )
                    .glow()
                    .build(), () -> {
                if (member != null && (clan.isLeader(player.getUniqueId()) || member.hasPermission(Permission.BANK_VIEW_TRANSACTIONS))) {
                    new ClanBankTransactionsGUI(plugin, player, clan).open();
                }
            });
        }

        setItem(32, new ItemBuilder(Material.GOLDEN_SWORD)
                .name("&e&l➔ Allianzen")
                .lore(
                        "&7Verwalte Clan-Allianzen.",
                        "&7Aktuell: &e" + clan.getAllies().size() + " Verbündete",
                        "",
                        "&a▶ &7Klicke zum Öffnen"
                )
                .hideFlags()
                .build(), () -> new ClanAlliesGUI(plugin, player, clan).open());

        boolean atWar = !clan.getActiveWars().isEmpty();
        setItem(38, new ItemBuilder(Material.IRON_SWORD)
                .name((atWar ? "&c&l⚔ Kriege" : "&c&l➔ Kriege"))
                .lore(
                        "&7Zeige aktive Kriege und",
                        "&7Kriegshistorie an.",
                        "&7Aktive Kriege: " + (atWar ? "&c" : "&a") + clan.getActiveWars().size(),
                        "",
                        "&a▶ &7Klicke zum Öffnen"
                )
                .hideFlags()
                .glow(atWar)
                .build(), () -> new ClanWarsGUI(plugin, player, clan).open());

        if (member != null && (member.hasPermission(Permission.SETTINGS) || member.hasPermission(Permission.RANK_MANAGE))) {
            setItem(40, new ItemBuilder(Material.COMPARATOR)
                    .name("&7&l➔ Einstellungen")
                    .lore(
                            "&7Clan-Einstellungen",
                            "&7bearbeiten.",
                            "",
                            "&a▶ &7Klicke zum Öffnen"
                    )
                    .build(), () -> new ClanSettingsGUI(plugin, player, clan).open());
        }

        setItem(42, new ItemBuilder(Material.BOOK)
                .name("&b&l➔ Clan-Liste")
                .lore(
                        "&7Zeige alle Clans",
                        "&7des Servers an.",
                        "",
                        "&a▶ &7Klicke zum Öffnen"
                )
                .build(), () -> new ClanListGUI(plugin, player).open());

        if (!clan.isLeader(player.getUniqueId())) {
            setItem(46, new ItemBuilder(Material.IRON_DOOR)
                    .name("&c&lClan verlassen")
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
                    () -> new ClanMainGUI(plugin, player, clan).open()
            ).open());
        }

        de.Z7534.ztone.clansystem.listeners.ChatListener.ChatMode chatMode =
                de.Z7534.ztone.clansystem.listeners.ChatListener.getChatMode(player.getUniqueId());

        Material chatMaterial;
        String chatModeName;
        switch (chatMode) {
            case CLAN -> {
                chatMaterial = Material.GREEN_BANNER;
                chatModeName = "&aClan";
            }
            case ALLY -> {
                chatMaterial = Material.BLUE_BANNER;
                chatModeName = "&bAllianz";
            }
            default -> {
                chatMaterial = Material.WHITE_BANNER;
                chatModeName = "&fGlobal";
            }
        }

        setItem(48, new ItemBuilder(chatMaterial)
                .name("&e&l➔ Chat-Kanal: " + chatModeName)
                .lore(
                        "&7Aktuell schreibst du in den",
                        "&7" + chatModeName + " &7-Chat.",
                        "",
                        "&7Global &8→ &aClan &8→ &bAllianz &8→ &7Global",
                        "",
                        "&a▶ &7Klicke zum Wechseln"
                )
                .build(), () -> {
            de.Z7534.ztone.clansystem.listeners.ChatListener.ChatMode next = switch (chatMode) {
                case GLOBAL -> de.Z7534.ztone.clansystem.listeners.ChatListener.ChatMode.CLAN;
                case CLAN -> de.Z7534.ztone.clansystem.listeners.ChatListener.ChatMode.ALLY;
                case ALLY -> de.Z7534.ztone.clansystem.listeners.ChatListener.ChatMode.GLOBAL;
            };
            de.Z7534.ztone.clansystem.listeners.ChatListener.setChatMode(player, next);

            String nextName = switch (next) {
                case GLOBAL -> "Global";
                case CLAN -> "Clan";
                case ALLY -> "Allianz";
            };
            plugin.getMessageManager().send(player, "chat.mode-switched",
                    de.Z7534.ztone.clansystem.managers.MessageManager.of("mode", nextName));

            refresh();
            player.updateInventory();
        });

        addCloseButton(49);

        setItem(50, new ItemBuilder(Material.NETHER_STAR)
                .name("&6&l🏆 Clan-Rangliste (Top 10)")
                .lore(
                        "&7Zeige die stärksten Clans",
                        "&7nach Punkten an.",
                        "",
                        "&a▶ &7Klicke zum Öffnen"
                )
                .glow()
                .build(), () -> new ClanTopGUI(plugin, player).open());

        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }
}
