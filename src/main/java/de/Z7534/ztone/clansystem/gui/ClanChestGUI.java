package de.Z7534.ztone.clansystem.gui;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.models.ClanMember;
import de.Z7534.ztone.clansystem.models.Permission;
import de.Z7534.ztone.clansystem.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClanChestGUI implements InventoryHolder {

    private static final Map<UUID, ClanChestGUI> openChests = new ConcurrentHashMap<>();
    private static final Map<Integer, Inventory> activeClanInventories = new ConcurrentHashMap<>();

    private final Ztone plugin;
    private final Player player;
    private final Clan clan;
    private final ClanMember member;
    private final boolean adminView;
    private final boolean canWithdraw;
    private Inventory inventory;
    private ItemStack[] originalContents;

    public ClanChestGUI(Ztone plugin, Player player, Clan clan) {
        this(plugin, player, clan, false);
    }

    /**
     * @param adminView true, wenn die Truhe von einem Admin über /clanadmin chest
     *                  geöffnet wurde. Der Spieler muss dann kein Mitglied des Clans
     *                  sein und darf Items entnehmen.
     */
    public ClanChestGUI(Ztone plugin, Player player, Clan clan, boolean adminView) {
        this.plugin = plugin;
        this.player = player;
        this.clan = clan;
        this.adminView = adminView;
        this.member = clan.getMember(player.getUniqueId());
        this.canWithdraw = adminView
                || (member != null && member.hasPermission(Permission.CHEST_WITHDRAW));
    }

    public void open() {
        int expectedSize = clan.getChestSize();
        Inventory shared = activeClanInventories.get(clan.getId());
        if (shared == null || shared.getSize() != expectedSize) {
            String title = plugin.getMessageManager().getRaw("gui.chest");
            shared = Bukkit.createInventory(this, expectedSize, ColorUtils.colorize(title));

            ItemStack[] contents = clan.getChestContents();
            if (contents != null) {
                for (int i = 0; i < Math.min(contents.length, shared.getSize()); i++) {
                    shared.setItem(i, contents[i] != null ? contents[i].clone() : null);
                }
            }
            activeClanInventories.put(clan.getId(), shared);
        }

        this.inventory = shared;

        ItemStack[] liveContents = inventory.getContents();
        this.originalContents = new ItemStack[liveContents.length];
        for (int i = 0; i < liveContents.length; i++) {
            this.originalContents[i] = liveContents[i] != null ? liveContents[i].clone() : null;
        }

        player.openInventory(inventory);
        openChests.put(player.getUniqueId(), this);
    }

    public void handleClick(InventoryClickEvent event) {
        Player clicker = (Player) event.getWhoClicked();
        boolean allowWithdraw = adminView
                || (clan.getMember(clicker.getUniqueId()) != null
                && clan.getMember(clicker.getUniqueId()).hasPermission(Permission.CHEST_WITHDRAW));

        int rawSlot = event.getRawSlot();
        int topSize = inventory.getSize();

        // Verhindern, dass Spieler ohne Entnahme-Rechte Items aus der Truhe ziehen
        if (!allowWithdraw) {
            // 1. Klick in die Clantruhe (oberes Inventar)
            if (rawSlot >= 0 && rawSlot < topSize) {
                ItemStack current = inventory.getItem(rawSlot);
                // Wenn im Slot ein Item liegt und der Spieler es aufheben, bewegen oder tauschen will:
                if (current != null && !current.getType().isAir()) {
                    event.setCancelled(true);
                    plugin.getMessageManager().send(clicker, "chest.no-withdraw-access");
                    return;
                }
                // Mit Number-Key (Hotbar) ein Item in oder aus der Truhe tauschen:
                if (event.getClick() == ClickType.NUMBER_KEY) {
                    event.setCancelled(true);
                    plugin.getMessageManager().send(clicker, "chest.no-withdraw-access");
                    return;
                }
            } else if (event.isShiftClick() && rawSlot >= topSize) {
                // Shift-Klick aus dem Spieler-Inventar in die Truhe ist Einzahlen (erlaubt)
            } else if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                // Doppelklick zieht Items aus der gesamten Truhe auf den Cursor -> blockieren
                event.setCancelled(true);
                plugin.getMessageManager().send(clicker, "chest.no-withdraw-access");
                return;
            }
        }

        // Chest-Logging wenn aktiviert
        if (allowWithdraw && rawSlot >= 0 && rawSlot < topSize) {
            ItemStack clickedItem = inventory.getItem(rawSlot);
            if (clickedItem != null && !clickedItem.getType().isAir()) {
                if (plugin.getConfigManager().isChestLogging()) {
                    plugin.getDatabaseManager().logChestAction(clan.getId(), clicker.getUniqueId(),
                            "WITHDRAW", clickedItem.clone());
                }
            }
        }
    }

    public void handleClose(InventoryCloseEvent event) {
        if (inventory == null) return;

        // Speicher-Snapshot der aktuellen Inhalte
        ItemStack[] contents = new ItemStack[inventory.getSize()];
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack it = inventory.getItem(i);
            contents[i] = it != null ? it.clone() : null;
        }
        clan.setChestContents(contents);
        plugin.getClanManager().saveClanChest(clan);

        long itemsBefore = countItems(originalContents);
        long itemsAfter = countItems(contents);

        if (itemsAfter > itemsBefore) {
            clan.broadcastMessage(plugin.getMessageManager().get("chest.deposit",
                    de.Z7534.ztone.clansystem.managers.MessageManager.of("player", player.getName())));
        } else if (itemsAfter < itemsBefore) {
            clan.broadcastMessage(plugin.getMessageManager().get("chest.withdraw",
                    de.Z7534.ztone.clansystem.managers.MessageManager.of("player", player.getName())));
        }

        openChests.remove(player.getUniqueId());

        // Wenn kein Spieler mehr die Truhe dieses Clans offen hat, Cache leeren
        if (inventory.getViewers().size() <= 1) {
            activeClanInventories.remove(clan.getId());
        }
    }

    public static void closeAllOpenChests() {
        for (Inventory inv : activeClanInventories.values()) {
            new ArrayList<>(inv.getViewers()).forEach(HumanEntity::closeInventory);
        }
        activeClanInventories.clear();
        openChests.clear();
    }

    private long countItems(ItemStack[] items) {
        long total = 0;
        if (items == null) {
            return 0;
        }
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                total += item.getAmount();
            }
        }
        return total;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Clan getClan() {
        return clan;
    }

    public Player getPlayer() {
        return player;
    }

    public static ClanChestGUI getOpenChest(Player player) {
        return openChests.get(player.getUniqueId());
    }

    public static boolean hasOpenChest(Player player) {
        return openChests.containsKey(player.getUniqueId());
    }

    public static void removeOpenChest(Player player) {
        openChests.remove(player.getUniqueId());
    }
}
