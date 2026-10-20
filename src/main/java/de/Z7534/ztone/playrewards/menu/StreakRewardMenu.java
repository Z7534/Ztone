package de.Z7534.ztone.playrewards.menu;

import de.Z7534.ztone.playrewards.PlayReward;
import de.Z7534.ztone.playrewards.model.RewardItem;
import de.Z7534.ztone.playrewards.model.RewardStatus;
import de.Z7534.ztone.playrewards.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StreakRewardMenu implements InventoryHolder {

    private final PlayReward plugin;
    private final Player player;
    private final int page;
    private final int maxPage;
    private final Inventory inventory;
    private final Map<Integer, RewardItem> slotRewardMap = new HashMap<>();

    public StreakRewardMenu(PlayReward plugin, Player player) {
        this(plugin, player, 1);
    }

    public StreakRewardMenu(PlayReward plugin, Player player, int page) {
        this.plugin = plugin;
        this.player = player;
        this.page = Math.max(1, page);

        List<RewardItem> allRewards = new ArrayList<>(plugin.getConfigManager().getStreakConfig().getStreakRewards().values());
        this.maxPage = Math.max(1, (int) Math.ceil((double) allRewards.size() / MenuItemBuilder.ITEMS_PER_PAGE));

        String titleTemplate = plugin.getConfigManager().getStreakConfig().getTitle();
        if (maxPage > 1 && !titleTemplate.contains("%page%")) {
            titleTemplate += " <gray>(Seite %page%/%max_page%)";
        }
        String parsedTitle = titleTemplate
                .replace("%page%", String.valueOf(this.page))
                .replace("%max_page%", String.valueOf(this.maxPage));

        this.inventory = Bukkit.createInventory(this, 54, MessageUtil.parse(parsedTitle));
        buildMenu(allRewards);
    }

    private void buildMenu(List<RewardItem> allRewards) {
        Material glassMat = plugin.getConfigManager().getStreakConfig().getFrameGlass();
        MenuItemBuilder.fillFrame(inventory, glassMat);

        int startIndex = (page - 1) * MenuItemBuilder.ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + MenuItemBuilder.ITEMS_PER_PAGE, allRewards.size());

        int snakeIndex = 0;
        for (int i = startIndex; i < endIndex; i++) {
            RewardItem item = allRewards.get(i);
            int slot = (item.getSlot() >= 0 && item.getSlot() < 54)
                    ? item.getSlot()
                    : MenuItemBuilder.SNAKE_SLOTS[snakeIndex];
            snakeIndex++;

            RewardStatus status = plugin.getRewardClaimService().getStreakStatus(player, item);
            ItemStack itemStack = MenuItemBuilder.buildRewardItem(item, status);
            inventory.setItem(slot, itemStack);
            slotRewardMap.put(slot, item);
        }

        MenuItemBuilder.setNavigationButtons(inventory, page, maxPage);
    }

    public void open() {
        player.openInventory(inventory);
    }

    public int getPage() {
        return page;
    }

    public int getMaxPage() {
        return maxPage;
    }

    public RewardItem getRewardAtSlot(int slot) {
        return slotRewardMap.get(slot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
