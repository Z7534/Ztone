package de.Z7534.ztone.stoneattack.modules.performance;

import de.Z7534.ztone.stoneattack.Stoneattack;
import de.Z7534.ztone.stoneattack.util.ColorUtil;
import de.Z7534.ztone.stoneattack.util.FormatUtil;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.Map;

public class PerformanceMonitorTask extends BukkitRunnable {

    private final Stoneattack plugin;
    private final PerformanceModule module;

    public PerformanceMonitorTask(Stoneattack plugin, PerformanceModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @Override
    public void run() {
        var config = plugin.getConfigManager().get();

        double tps = Math.min(20.0, Bukkit.getServer().getTPS()[0]);
        double mspt = Bukkit.getServer().getAverageTickTime();

        double tpsThreshold = config.getDouble("performance.tps-threshold", 18.0);
        double msptThreshold = config.getDouble("performance.mspt-threshold", 45.0);
        double recoveryTps = config.getDouble("performance.recovery-tps-threshold", 19.0);
        double recoveryMspt = config.getDouble("performance.recovery-mspt-threshold", 40.0);
        String mode = config.getString("performance.threshold-mode", "OR");

        boolean badTps = tps < tpsThreshold;
        boolean badMspt = mspt > msptThreshold;
        boolean bad = "AND".equalsIgnoreCase(mode) ? (badTps && badMspt) : (badTps || badMspt);

        // Fuer die Entwarnung muessen BEIDE Werte wieder ueber der
        // (staerkeren) Erholungsschwelle liegen - bewusst konservativ,
        // damit die Meldung nicht flackert.
        boolean recovered = tps >= recoveryTps && mspt <= recoveryMspt;

        Map<String, String> placeholders = Map.of(
                "tps", FormatUtil.round2(tps),
                "mspt", FormatUtil.round2(mspt),
                "tps_schwelle", FormatUtil.round2(tpsThreshold),
                "mspt_schwelle", FormatUtil.round2(msptThreshold)
        );

        if (bad) {
            long cooldownMillis = config.getLong("performance.warning-cooldown-seconds", 120) * 1000L;
            long now = System.currentTimeMillis();
            if (now - module.getLastWarningTimestamp() >= cooldownMillis) {
                sendWarning(placeholders);
                module.setLastWarningTimestamp(now);
                module.setCurrentlyWarned(true);

                if (config.getBoolean("performance.log-to-console", true)) {
                    plugin.getLogger().warning(plugin.getMessagesManager().raw("performance.console-log-warning")
                            .replace("%tps%", placeholders.get("tps"))
                            .replace("%mspt%", placeholders.get("mspt")));
                }
            }
        } else if (recovered && module.isCurrentlyWarned() && config.getBoolean("performance.recovery.enabled", true)) {
            sendRecovery(placeholders);
            module.setCurrentlyWarned(false);

            if (config.getBoolean("performance.log-to-console", true)) {
                plugin.getLogger().info(plugin.getMessagesManager().raw("performance.console-log-recovery")
                        .replace("%tps%", placeholders.get("tps"))
                        .replace("%mspt%", placeholders.get("mspt")));
            }
        }
    }

    private void sendWarning(Map<String, String> placeholders) {
        var config = plugin.getConfigManager().get();
        var messages = plugin.getMessagesManager();

        Component titleComponent = ColorUtil.parse(messages.raw("performance.warning-title"), placeholders);
        Component subtitleComponent = ColorUtil.parse(messages.raw("performance.warning-subtitle"), placeholders);

        Title.Times times = Title.Times.times(
                Duration.ofMillis(config.getInt("performance.warning.fade-in-ticks", 10) * 50L),
                Duration.ofMillis(config.getInt("performance.warning.stay-ticks", 60) * 50L),
                Duration.ofMillis(config.getInt("performance.warning.fade-out-ticks", 10) * 50L)
        );
        Title title = Title.title(titleComponent, subtitleComponent, times);

        boolean actionbarEnabled = config.getBoolean("performance.warning.actionbar-enabled", false);
        Component actionbar = actionbarEnabled
                ? ColorUtil.parse(messages.raw("performance.warning-actionbar"), placeholders)
                : null;

        boolean soundEnabled = config.getBoolean("performance.warning.sound-enabled", true);
        Sound sound = null;
        if (soundEnabled) {
            String soundKey = config.getString("performance.warning.sound", "block.note_block.pling");
            sound = Sound.sound(
                    net.kyori.adventure.key.Key.key(soundKey),
                    Sound.Source.MASTER,
                    (float) config.getDouble("performance.warning.sound-volume", 1.0),
                    (float) config.getDouble("performance.warning.sound-pitch", 0.7)
            );
        }

        Component staffMessage = messages.get("performance.staff-message", placeholders);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("performance.bypass")) {
                continue;
            }
            player.showTitle(title);
            if (actionbar != null) {
                player.sendActionBar(actionbar);
            }
            if (sound != null) {
                player.playSound(sound);
            }
            if (player.hasPermission("performance.staff")) {
                player.sendMessage(staffMessage);
            }
        }
    }

    private void sendRecovery(Map<String, String> placeholders) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("performance.bypass")) {
                continue;
            }
            player.sendMessage(plugin.getMessagesManager().get("performance.recovery-message", placeholders));
        }
    }
}
