package de.Z7534.ztone.stoneattack.commands;

import de.Z7534.ztone.stoneattack.Stoneattack;
import de.Z7534.ztone.stoneattack.util.FormatUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Map;

public class PerformanceCommand implements CommandExecutor {

    private final Stoneattack plugin;

    public PerformanceCommand(Stoneattack plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("performance.use")) {
            plugin.getMessagesManager().send(sender, "general.no-permission");
            return true;
        }

        var config = plugin.getConfigManager().get();
        var messages = plugin.getMessagesManager();

        double[] tps = Bukkit.getServer().getTPS();
        double msptAvg = Bukkit.getServer().getAverageTickTime();
        double msptMax = maxTickTime();

        String goodColor = config.getString("performance.command.good-color", "<green>");
        String warnColor = config.getString("performance.command.warn-color", "<yellow>");
        String badColor = config.getString("performance.command.bad-color", "<red>");

        sender.sendMessage(messages.get("performance.command.header"));
        sender.sendMessage(messages.get("performance.command.tps-line", Map.of(
                "color", tpsColor(tps[0], goodColor, warnColor, badColor),
                "tps1", FormatUtil.round2(Math.min(20, tps[0])),
                "tps5", FormatUtil.round2(Math.min(20, tps[1])),
                "tps15", FormatUtil.round2(Math.min(20, tps[2]))
        )));
        sender.sendMessage(messages.get("performance.command.mspt-line", Map.of(
                "color", msptColor(msptAvg, goodColor, warnColor, badColor),
                "mspt_avg", FormatUtil.round2(msptAvg),
                "mspt_max", FormatUtil.round2(msptMax)
        )));

        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long max = runtime.maxMemory() / 1024 / 1024;
        long percent = max == 0 ? 0 : (used * 100 / max);
        sender.sendMessage(messages.get("performance.command.ram-line", Map.of(
                "used", String.valueOf(used),
                "max", String.valueOf(max),
                "percent", String.valueOf(percent)
        )));

        for (World world : Bukkit.getWorlds()) {
            sender.sendMessage(messages.get("performance.command.world-line", Map.of(
                    "world", world.getName(),
                    "chunks", String.valueOf(world.getLoadedChunks().length),
                    "entities", String.valueOf(world.getEntities().size())
            )));
        }
        return true;
    }

    /**
     * Paper stellt ueber getTickTimes() die letzten Ticklaengen in
     * Nanosekunden bereit - daraus errechnen wir das aktuelle Maximum.
     */
    private double maxTickTime() {
        long[] times = Bukkit.getServer().getTickTimes();
        long max = 0;
        for (long t : times) {
            if (t > max) {
                max = t;
            }
        }
        return max / 1_000_000.0;
    }

    private String tpsColor(double tps, String good, String warn, String bad) {
        double threshold = plugin.getConfigManager().get().getDouble("performance.tps-threshold", 18.0);
        if (tps >= 19.5) return good;
        if (tps >= threshold) return warn;
        return bad;
    }

    private String msptColor(double mspt, String good, String warn, String bad) {
        double threshold = plugin.getConfigManager().get().getDouble("performance.mspt-threshold", 45.0);
        if (mspt <= 40) return good;
        if (mspt <= threshold) return warn;
        return bad;
    }
}
