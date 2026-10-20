package de.Z7534.ztone.clansystem.gui;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.utils.ColorUtils;
import de.Z7534.ztone.clansystem.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public abstract class AbstractGUI implements InventoryHolder {

    protected final Ztone plugin;
    protected final Player player;
    protected Inventory inventory;
    protected final Map<Integer, Runnable> clickActions;
    protected final Map<Integer, Runnable> rightClickActions;

    private static final Map<UUID, AbstractGUI> openGuis = new HashMap<>();

    public AbstractGUI(Ztone plugin, Player player, String title, int size) {
        this.plugin = plugin;
        this.player = player;
        this.clickActions = new HashMap<>();
        this.rightClickActions = new HashMap<>();
        this.inventory = Bukkit.createInventory(this, size, ColorUtils.colorize(title));
    }

    public abstract void setup();

    public void open() {
        setup();
        player.openInventory(inventory);
        openGuis.put(player.getUniqueId(), this);
    }

    protected void refresh() {
        inventory.clear();
        clickActions.clear();
        rightClickActions.clear();
        setup();
    }

    public void forceRefresh() {
        refresh();
    }

    public void close() {
        openGuis.remove(player.getUniqueId());
        player.closeInventory();
    }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }

        Runnable action = (event.isRightClick() && rightClickActions.containsKey(slot))
                ? rightClickActions.get(slot)
                : clickActions.get(slot);

        if (action != null) {
            action.run();
        }
    }

    protected void setItem(int slot, ItemStack item) {
        inventory.setItem(slot, item);
    }

    protected void setItem(int slot, ItemStack item, Runnable onClick) {
        inventory.setItem(slot, item);
        if (onClick != null) {
            clickActions.put(slot, onClick);
        }
    }

    /**
     * Registriert für einen Slot getrennte Aktionen für Links- und Rechtsklick.
     * Ohne diese Überladung wird jeder Klick (egal ob links oder rechts) als
     * "normaler" Klick behandelt - siehe {@link #handleClick(InventoryClickEvent)}.
     */
    protected void setItem(int slot, ItemStack item, Runnable onLeftClick, Runnable onRightClick) {
        inventory.setItem(slot, item);
        if (onLeftClick != null) {
            clickActions.put(slot, onLeftClick);
        }
        if (onRightClick != null) {
            rightClickActions.put(slot, onRightClick);
        }
    }

    protected void fillBorder(Material material) {
        ItemStack filler = new ItemBuilder(material).name(" ").build();
        int size = inventory.getSize();
        int rows = size / 9;

        for (int i = 0; i < 9; i++) {
            setItem(i, filler);
            setItem(size - 9 + i, filler);
        }

        for (int i = 1; i < rows - 1; i++) {
            setItem(i * 9, filler);
            setItem(i * 9 + 8, filler);
        }
    }

    protected void fillFancyBorder(Material edgeMaterial, Material accentMaterial) {
        ItemStack edge = new ItemBuilder(edgeMaterial).name(" ").build();
        ItemStack accent = new ItemBuilder(accentMaterial).name(" ").glow().build();

        int size = inventory.getSize();
        int rows = size / 9;

        for (int i = 0; i < 9; i++) {
            setItem(i, edge);
            setItem(size - 9 + i, edge);
        }

        for (int i = 1; i < rows - 1; i++) {
            setItem(i * 9, edge);
            setItem(i * 9 + 8, edge);
        }

        setItem(0, accent);
        setItem(8, accent);
        setItem(size - 9, accent);
        setItem(size - 1, accent);
    }

    protected void fillFancyBorder() {
        fillFancyBorder(Material.BLACK_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE);
    }

    protected void fillEmpty(Material material) {
        ItemStack filler = new ItemBuilder(material).name(" ").build();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                setItem(i, filler);
            }
        }
    }

    protected void addBackButton(int slot, Runnable onClick) {
        setItem(slot, ItemBuilder.back(), onClick);
    }

    protected void addCloseButton(int slot) {
        setItem(slot, ItemBuilder.close(), this::close);
    }

    protected ItemStack ownHead(String name, String... lore) {
        return new ItemBuilder(Material.PLAYER_HEAD)
                .skullOwner(player)
                .name(name)
                .lore(lore)
                .build();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }

    public static AbstractGUI getOpenGui(Player player) {
        return openGuis.get(player.getUniqueId());
    }

    public static void removeOpenGui(Player player) {
        openGuis.remove(player.getUniqueId());
    }

    public static boolean hasOpenGui(Player player) {
        return openGuis.containsKey(player.getUniqueId());
    }
}
