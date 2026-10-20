package de.Z7534.ztone.clearlag.manager;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clearlag.config.ClearLagConfig;
import de.Z7534.ztone.util.SmallCapsUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;

public class ClearLagManager {

    private final Ztone plugin;
    private final ClearLagConfig config;

    private int remainingSeconds;
    private BukkitTask task;

    public ClearLagManager(Ztone plugin, ClearLagConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.remainingSeconds = config.getIntervalSeconds();
    }

    public void start() {
        stop();
        this.remainingSeconds = config.getIntervalSeconds();
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        remainingSeconds--;

        if (remainingSeconds == config.getWarning5mSeconds()) {
            Bukkit.broadcast(config.getMessage("warning-5m"));
        } else if (remainingSeconds == config.getWarning1mSeconds()) {
            Bukkit.broadcast(config.getMessage("warning-1m"));
        } else if (remainingSeconds <= 0) {
            int cleared = clearGroundItems();
            Bukkit.broadcast(config.getMessage("cleared", Map.of("count", String.valueOf(cleared))));
            remainingSeconds = config.getIntervalSeconds();
        }
    }

    public int clearGroundItems() {
        int count = 0;
        int minAge = config.getMinItemAgeTicks(); // 200 Ticks = 10 Sekunden

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item item) {
                    // Wichtig: Keine Items, die vor weniger als 10 Sekunden (200 Ticks) gedroppt wurden!
                    if (item.getTicksLived() < minAge) {
                        continue;
                    }
                    item.remove();
                    count++;
                }
            }
        }
        return count;
    }

    public void resetTimer() {
        this.remainingSeconds = config.getIntervalSeconds();
    }

    public int getRemainingSeconds() {
        return Math.max(0, remainingSeconds);
    }

    public String getCountdownSmallCaps() {
        int sec = getRemainingSeconds();
        int mins = sec / 60;
        int secs = sec % 60;
        String raw = String.format("%02dm %02ds", mins, secs);
        return SmallCapsUtil.toSmallCaps(raw);
    }

    public String getCountdownDigital() {
        int sec = getRemainingSeconds();
        int mins = sec / 60;
        int secs = sec % 60;
        return String.format("%02d:%02d", mins, secs);
    }
}
