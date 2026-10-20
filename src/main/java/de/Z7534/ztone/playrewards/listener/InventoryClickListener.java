package de.Z7534.ztone.playrewards.listener;

import de.Z7534.ztone.playrewards.PlayReward;
import de.Z7534.ztone.playrewards.menu.DailyRewardMenu;
import de.Z7534.ztone.playrewards.menu.MainRewardMenu;
import de.Z7534.ztone.playrewards.menu.PlaytimeRewardMenu;
import de.Z7534.ztone.playrewards.menu.StreakRewardMenu;
import de.Z7534.ztone.playrewards.model.RewardItem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

public class InventoryClickListener implements Listener {

    private final PlayReward plugin;

    public InventoryClickListener(PlayReward plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof MainRewardMenu ||
              holder instanceof DailyRewardMenu ||
              holder instanceof StreakRewardMenu ||
              holder instanceof PlaytimeRewardMenu)) {
            return;
        }

        // Klick in einem Belohnungs-Inventar wird immer abgebrochen!
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }

        // Anti-Spam / Anti-Dupe Cooldown
        if (!plugin.getRewardClaimService().checkCooldown(player)) {
            return;
        }

        // 1. Hauptmenü
        if (holder instanceof MainRewardMenu) {
            handleMainMenuClick(player, slot);
            return;
        }

        // 2. Daily Rewards Menü (54 Slots mit Schlangenlinien-Layout)
        if (holder instanceof DailyRewardMenu) {
            DailyRewardMenu menu = (DailyRewardMenu) holder;
            if (slot == 48 && menu.getPage() > 1) {
                plugin.getRewardClaimService().playClickSound(player);
                new DailyRewardMenu(plugin, player, menu.getPage() - 1).open();
                return;
            }
            if (slot == 49) {
                plugin.getRewardClaimService().playClickSound(player);
                new MainRewardMenu(plugin, player).open();
                return;
            }
            if (slot == 50 && menu.getPage() < menu.getMaxPage()) {
                plugin.getRewardClaimService().playClickSound(player);
                new DailyRewardMenu(plugin, player, menu.getPage() + 1).open();
                return;
            }

            RewardItem item = menu.getRewardAtSlot(slot);
            if (item != null) {
                if (plugin.getRewardClaimService().claimDailyReward(player, item)) {
                    new DailyRewardMenu(plugin, player, menu.getPage()).open();
                }
            }
            return;
        }

        // 3. Streak Rewards Menü (54 Slots mit Schlangenlinien-Layout)
        if (holder instanceof StreakRewardMenu) {
            StreakRewardMenu menu = (StreakRewardMenu) holder;
            if (slot == 48 && menu.getPage() > 1) {
                plugin.getRewardClaimService().playClickSound(player);
                new StreakRewardMenu(plugin, player, menu.getPage() - 1).open();
                return;
            }
            if (slot == 49) {
                plugin.getRewardClaimService().playClickSound(player);
                new MainRewardMenu(plugin, player).open();
                return;
            }
            if (slot == 50 && menu.getPage() < menu.getMaxPage()) {
                plugin.getRewardClaimService().playClickSound(player);
                new StreakRewardMenu(plugin, player, menu.getPage() + 1).open();
                return;
            }

            RewardItem item = menu.getRewardAtSlot(slot);
            if (item != null) {
                if (plugin.getRewardClaimService().claimStreakReward(player, item)) {
                    new StreakRewardMenu(plugin, player, menu.getPage()).open();
                }
            }
            return;
        }

        // 4. Playtime Rewards Menü (54 Slots mit Schlangenlinien-Layout)
        if (holder instanceof PlaytimeRewardMenu) {
            PlaytimeRewardMenu menu = (PlaytimeRewardMenu) holder;
            if (slot == 48 && menu.getPage() > 1) {
                plugin.getRewardClaimService().playClickSound(player);
                new PlaytimeRewardMenu(plugin, player, menu.getPage() - 1).open();
                return;
            }
            if (slot == 49) {
                plugin.getRewardClaimService().playClickSound(player);
                new MainRewardMenu(plugin, player).open();
                return;
            }
            if (slot == 50 && menu.getPage() < menu.getMaxPage()) {
                plugin.getRewardClaimService().playClickSound(player);
                new PlaytimeRewardMenu(plugin, player, menu.getPage() + 1).open();
                return;
            }

            RewardItem item = menu.getRewardAtSlot(slot);
            if (item != null) {
                if (plugin.getRewardClaimService().claimPlaytimeReward(player, item)) {
                    new PlaytimeRewardMenu(plugin, player, menu.getPage()).open();
                }
            }
        }
    }

    private void handleMainMenuClick(Player player, int slot) {
        plugin.getRewardClaimService().playClickSound(player);
        if (slot == 11) {
            new DailyRewardMenu(plugin, player).open();
        } else if (slot == 13 || slot == 22) {
            new StreakRewardMenu(plugin, player).open();
        } else if (slot == 15) {
            new PlaytimeRewardMenu(plugin, player, 1).open();
        }
    }
}
