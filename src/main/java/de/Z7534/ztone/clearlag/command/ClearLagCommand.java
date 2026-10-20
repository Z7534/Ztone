package de.Z7534.ztone.clearlag.command;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.claims.util.Text;
import de.Z7534.ztone.clearlag.config.ClearLagConfig;
import de.Z7534.ztone.clearlag.manager.ClearLagManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ClearLagCommand implements CommandExecutor, TabCompleter {

    private final Ztone plugin;
    private final ClearLagManager manager;
    private final ClearLagConfig config;

    public ClearLagCommand(Ztone plugin, ClearLagManager manager, ClearLagConfig config) {
        this.plugin = plugin;
        this.manager = manager;
        this.config = config;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("clearlag.admin")) {
            sender.sendMessage(Text.line(config.getPrefix() + " <red>Dazu hast du keine Berechtigung!</red>"));
            return true;
        }

        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("clear")) {
                int cleared = manager.clearGroundItems();
                manager.resetTimer();
                Bukkit.broadcast(config.getMessage("cleared", Map.of("count", String.valueOf(cleared))));
                return true;
            }
            if (args[0].equalsIgnoreCase("time")) {
                sender.sendMessage(Text.line(config.getPrefix() + " <gray>Nächster Item-Clear in: <yellow><bold>"
                        + manager.getCountdownDigital() + "</bold></yellow> (<gold>" + manager.getCountdownSmallCaps() + "</gold>)</gray>"));
                return true;
            }
            if (args[0].equalsIgnoreCase("reload")) {
                config.load();
                sender.sendMessage(Text.line(config.getPrefix() + " <green>Konfiguration wurde neu geladen.</green>"));
                return true;
            }
        }

        sender.sendMessage(Text.line(config.getPrefix() + " <gray>Verwendung: <yellow>/clearlag <clear|time|reload></yellow></gray>"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("clearlag.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> sub = List.of("clear", "time", "reload");
            List<String> result = new ArrayList<>();
            for (String s : sub) {
                if (s.toLowerCase().startsWith(args[0].toLowerCase())) {
                    result.add(s);
                }
            }
            return result;
        }
        return List.of();
    }
}
