package de.Z7534.ztone.playrewards.service;

import de.Z7534.ztone.playrewards.PlayReward;
import de.Z7534.ztone.playrewards.model.PlayerData;
import de.Z7534.ztone.playrewards.model.RewardItem;
import de.Z7534.ztone.playrewards.model.RewardStatus;
import de.Z7534.ztone.playrewards.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RewardClaimService {

    private final PlayReward plugin;
    private final Map<UUID, Long> lastClickTime = new ConcurrentHashMap<>();

    public RewardClaimService(PlayReward plugin) {
        this.plugin = plugin;
    }

    /**
     * Prüft den Klick-Cooldown gegen schnelles Doppelklicken oder Makros.
     */
    public boolean checkCooldown(Player player) {
        long now = System.currentTimeMillis();
        long cooldownMs = plugin.getConfig().getLong("gui.click_cooldown_ms", 500);
        Long last = lastClickTime.get(player.getUniqueId());

        if (last != null && (now - last) < cooldownMs) {
            return false;
        }
        lastClickTime.put(player.getUniqueId(), now);
        return true;
    }

    public RewardStatus getDailyStatus(Player player, RewardItem item) {
        PlayerData data = plugin.getDatabaseManager().getCachedData(player.getUniqueId());
        if (data == null) return RewardStatus.LOCKED;

        if (data.getClaimedDailyTiers().contains(item.getId())) {
            return RewardStatus.CLAIMED;
        }
        long dailyPlaytime = plugin.getPlaytimeService().getDailyPlaytimeSeconds(player.getUniqueId());
        if (dailyPlaytime >= item.getRequiredPlaytimeSeconds()) {
            return RewardStatus.AVAILABLE;
        }
        return RewardStatus.LOCKED;
    }

    public RewardStatus getStreakStatus(Player player, RewardItem item) {
        PlayerData data = plugin.getDatabaseManager().getCachedData(player.getUniqueId());
        if (data == null) return RewardStatus.LOCKED;

        if (data.getClaimedStreakMilestones().contains(item.getRequiredStreakDays())) {
            return RewardStatus.CLAIMED;
        }
        if (data.getCurrentStreak() >= item.getRequiredStreakDays()) {
            return RewardStatus.AVAILABLE;
        }
        return RewardStatus.LOCKED;
    }

    public RewardStatus getPlaytimeStatus(Player player, RewardItem item) {
        PlayerData data = plugin.getDatabaseManager().getCachedData(player.getUniqueId());
        if (data == null) return RewardStatus.LOCKED;

        if (data.getClaimedPlaytimeTiers().contains(item.getId())) {
            return RewardStatus.CLAIMED;
        }
        long playtime = plugin.getPlaytimeService().getCachedPlaytimeSeconds(player.getUniqueId());
        if (playtime >= item.getRequiredPlaytimeSeconds()) {
            return RewardStatus.AVAILABLE;
        }
        return RewardStatus.LOCKED;
    }

    public int getAvailableDailyRewardsCount(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return 0;
        int count = 0;
        for (RewardItem item : plugin.getConfigManager().getDailyConfig().getRewards().values()) {
            if (getDailyStatus(player, item) == RewardStatus.AVAILABLE) {
                count++;
            }
        }
        return count;
    }

    public boolean claimDailyReward(Player player, RewardItem item) {
        RewardStatus status = getDailyStatus(player, item);
        if (status != RewardStatus.AVAILABLE) {
            playLockedSound(player);
            if (status == RewardStatus.CLAIMED) {
                MessageUtil.sendMessage(player, plugin.getPrefix(), "<red>Du hast diese tägliche Belohnung heute bereits abgeholt!");
            } else {
                MessageUtil.sendMessage(player, plugin.getPrefix(), "<red>Diese Belohnung ist noch gesperrt. Sammle heute mehr aktive Online-Spielzeit!");
            }
            return false;
        }

        if (!handleInventoryCheck(player)) {
            return false;
        }

        PlayerData data = plugin.getDatabaseManager().getCachedData(player.getUniqueId());
        if (data != null) {
            data.getClaimedDailyTiers().add(item.getId());
            plugin.getDatabaseManager().savePlayerDataAsync(data);
        }

        executeRewardCommands(player, item);
        playSuccessSound(player);
        MessageUtil.sendMessage(player, plugin.getPrefix(), "<green>Tägliche Belohnung erfolgreich abgeholt: " + item.getName());
        return true;
    }

    public boolean claimStreakReward(Player player, RewardItem item) {
        RewardStatus status = getStreakStatus(player, item);
        if (status != RewardStatus.AVAILABLE) {
            playLockedSound(player);
            if (status == RewardStatus.CLAIMED) {
                MessageUtil.sendMessage(player, plugin.getPrefix(), "<red>Diesen Streak-Meilenstein hast du bereits eingelöst!");
            } else {
                MessageUtil.sendMessage(player, plugin.getPrefix(), "<red>Dieser Meilenstein ist noch nicht erreicht! Halte deine Serie aufrecht.");
            }
            return false;
        }

        if (!handleInventoryCheck(player)) {
            return false;
        }

        PlayerData data = plugin.getDatabaseManager().getCachedData(player.getUniqueId());
        if (data != null) {
            data.getClaimedStreakMilestones().add(item.getRequiredStreakDays());
            plugin.getDatabaseManager().savePlayerDataAsync(data);
        }

        executeRewardCommands(player, item);
        playSuccessSound(player);
        MessageUtil.sendMessage(player, plugin.getPrefix(), "<green>Streak-Meilenstein erfolgreich abgeholt: " + item.getName());
        return true;
    }

    public boolean claimPlaytimeReward(Player player, RewardItem item) {
        RewardStatus status = getPlaytimeStatus(player, item);
        if (status != RewardStatus.AVAILABLE) {
            playLockedSound(player);
            if (status == RewardStatus.CLAIMED) {
                MessageUtil.sendMessage(player, plugin.getPrefix(), "<red>Diese Spielzeit-Belohnung hast du bereits beansprucht!");
            } else {
                MessageUtil.sendMessage(player, plugin.getPrefix(), "<red>Du hast die erforderliche Spielzeit für diese Stufe noch nicht erreicht!");
            }
            return false;
        }

        if (!handleInventoryCheck(player)) {
            return false;
        }

        PlayerData data = plugin.getDatabaseManager().getCachedData(player.getUniqueId());
        if (data != null) {
            data.getClaimedPlaytimeTiers().add(item.getId());
            plugin.getDatabaseManager().savePlayerDataAsync(data);
        }

        executeRewardCommands(player, item);
        playSuccessSound(player);
        MessageUtil.sendMessage(player, plugin.getPrefix(), "<green>Spielzeit-Belohnung erfolgreich freigeschaltet: " + item.getName());
        return true;
    }

    private boolean handleInventoryCheck(Player player) {
        if (player.getInventory().firstEmpty() == -1) {
            String handling = plugin.getConfig().getString("gui.full_inventory_handling", "BLOCK");
            if ("BLOCK".equalsIgnoreCase(handling)) {
                MessageUtil.sendMessage(player, plugin.getPrefix(), "<red>Dein Inventar ist voll! Schaffe Platz, um deine Belohnung abzuholen.");
                playLockedSound(player);
                return false;
            }
        }
        return true;
    }

    private void executeRewardCommands(Player player, RewardItem item) {
        for (String command : item.getCommands()) {
            String parsed = command.replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }

    public void playSuccessSound(Player player) {
        playSound(player, "gui.sounds.claim_success.sound", Sound.UI_TOAST_CHALLENGE_COMPLETE);
    }

    public void playLockedSound(Player player) {
        playSound(player, "gui.sounds.claim_locked.sound", Sound.ENTITY_VILLAGER_NO);
    }

    public void playClickSound(Player player) {
        playSound(player, "gui.sounds.click.sound", Sound.UI_BUTTON_CLICK);
    }

    private void playSound(Player player, String configPath, Sound fallback) {
        try {
            String soundName = plugin.getConfig().getString(configPath);
            Sound sound = fallback;
            if (soundName != null) {
                NamespacedKey key = NamespacedKey.minecraft(soundName.toLowerCase().replace('.', '_'));
                Sound found = Registry.SOUNDS.get(key);
                if (found != null) {
                    sound = found;
                }
            }
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (Exception ignored) {
            player.playSound(player.getLocation(), fallback, 1.0f, 1.0f);
        }
    }
}
