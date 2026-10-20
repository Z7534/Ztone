package de.Z7534.ztone.clansystem.gui;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.managers.EconomyManager;
import de.Z7534.ztone.clansystem.managers.LevelManager;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public class ClanListGUI extends AbstractGUI {

    private int page;
    private SortType sortType;
    private static final int ITEMS_PER_PAGE = 28;

    public enum SortType {
        LEVEL("Level"),
        POINTS("Punkte"),
        BALANCE("Geld"),
        MEMBERS("Mitglieder");

        private final String displayName;

        SortType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public ClanListGUI(Ztone plugin, Player player) {
        this(plugin, player, 0, SortType.LEVEL);
    }

    public ClanListGUI(Ztone plugin, Player player, int page, SortType sortType) {
        super(plugin, player, plugin.getMessageManager().getRaw("gui.list"), 54);
        this.page = page;
        this.sortType = sortType;
    }

    @Override
    public void setup() {
        fillFancyBorder();

        List<Clan> clans = getSortedClans();
        int totalPages = (int) Math.ceil((double) clans.size() / ITEMS_PER_PAGE);

        setItem(4, new ItemBuilder(Material.HOPPER)
                .name("&e&l⇅ Sortierung: &6" + sortType.getDisplayName())
                .lore(
                        "",
                        "&a▶ &7Klicke zum Wechseln",
                        "&8Verfügbar: Level, Punkte, Geld, Mitglieder"
                )
                .glow()
                .build(), () -> {
            SortType[] types = SortType.values();
            int nextIndex = (sortType.ordinal() + 1) % types.length;
            sortType = types[nextIndex];
            page = 0;
            refresh();
            player.updateInventory();
        });

        int startIndex = page * ITEMS_PER_PAGE;
        int slot = 10;
        EconomyManager eco = plugin.getEconomyManager();

        for (int i = startIndex; i < Math.min(startIndex + ITEMS_PER_PAGE, clans.size()); i++) {
            Clan clan = clans.get(i);

            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 44) break;

            LevelManager levelManager = plugin.getLevelManager();
            int maxMembers = levelManager.getMaxMembers(clan.getLevel());

            String medal = switch (i) {
                case 0 -> "&6★ ";
                case 1 -> "&7★ ";
                case 2 -> "&c★ ";
                default -> "";
            };

            String formattedBalance = eco != null ? eco.format(clan.getBalance()) : (clan.getBalance() + " $");

            final int clanId = clan.getId();
            setItem(slot, new ItemBuilder(de.Z7534.ztone.clansystem.utils.ClanIcons.fromName(clan.getIcon()))
                    .name(medal + "&6&l" + clan.getName())
                    .lore(
                            "&7Suffix: &r" + (clan.getSuffix().isEmpty() ? "&8-" : clan.getColoredSuffix()),
                            "&7Level: &e" + clan.getLevel(),
                            "&7Punkte: &e" + clan.getPoints(),
                            "&7Clangeld: &e" + formattedBalance,
                            "&7Mitglieder: &e" + clan.getMemberCount() + "&8/&e" + maxMembers,
                            "",
                            "&a▶ &7Klicke für Details"
                    )
                    .glow(i < 3)
                    .build(), () -> {
                Clan clickedClan = plugin.getClanManager().getClan(clanId);
                if (clickedClan != null) {
                    new ClanInfoGUI(plugin, player, clickedClan).open();
                }
            });

            slot++;
        }

        if (page > 0) {
            setItem(45, ItemBuilder.previousPage(), () -> {
                page--;
                refresh();
                player.updateInventory();
            });
        }

        if (page < totalPages - 1) {
            setItem(53, ItemBuilder.nextPage(), () -> {
                page++;
                refresh();
                player.updateInventory();
            });
        }

        setItem(49, new ItemBuilder(Material.PAPER)
                .name("&7Seite &e" + (page + 1) + "&8/&e" + Math.max(1, totalPages))
                .lore("&7Gesamt: &e" + clans.size() + " Clans")
                .build());

        addCloseButton(48);

        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }

    private List<Clan> getSortedClans() {
        return switch (sortType) {
            case POINTS -> plugin.getClanManager().getClansSortedByPoints();
            case MEMBERS -> plugin.getClanManager().getClansSortedByMembers();
            case BALANCE -> plugin.getClanManager().getClansSortedByBalance();
            default -> plugin.getClanManager().getClansSortedByLevel();
        };
    }
}
