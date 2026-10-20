package de.Z7534.ztone.stoneattack.modules.performance;

import de.Z7534.ztone.stoneattack.Stoneattack;
import org.bukkit.scheduler.BukkitTask;

/**
 * Haelt den Zustand von Modul 2 (laufender Task, Warn-/Cooldown-
 * Status) und startet/stoppt den wiederholenden Ueberwachungs-Task.
 */
public class PerformanceModule {

    private final Stoneattack plugin;
    private BukkitTask task;

    // Hysterese-Zustand: true, solange zuletzt gewarnt und noch keine
    // Entwarnung gesendet wurde.
    private volatile boolean currentlyWarned = false;
    private volatile long lastWarningTimestamp = 0L;

    public PerformanceModule(Stoneattack plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        long intervalTicks = plugin.getConfigManager().get().getLong("performance.check-interval-seconds", 30) * 20L;
        task = new PerformanceMonitorTask(plugin, this).runTaskTimer(plugin.getPlugin(), intervalTicks, intervalTicks);
    }

    public void disable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void reload() {
        disable();
        enable();
    }

    public boolean isCurrentlyWarned() {
        return currentlyWarned;
    }

    public void setCurrentlyWarned(boolean warned) {
        this.currentlyWarned = warned;
    }

    public long getLastWarningTimestamp() {
        return lastWarningTimestamp;
    }

    public void setLastWarningTimestamp(long timestamp) {
        this.lastWarningTimestamp = timestamp;
    }
}
