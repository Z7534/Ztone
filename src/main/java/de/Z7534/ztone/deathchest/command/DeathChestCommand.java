package de.Z7534.ztone.deathchest.command;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.claims.util.Text;
import de.Z7534.ztone.deathchest.config.DeathChestConfig;
import de.Z7534.ztone.deathchest.manager.DeathChestManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DeathChestCommand implements CommandExecutor, TabCompleter {

    private final Ztone plugin;
    private final DeathChestManager manager;
    private final DeathChestConfig config;

    public DeathChestCommand(Ztone plugin, DeathChestManager manager, DeathChestConfig config) {
        this.plugin = plugin;
        this.manager = manager;
        this.config = config;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("deathchest.admin")) {
            sender.sendMessage(Text.line(config.getPrefix() + " <red>Dazu hast du keine Berechtigung!</red>"));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("clear")) {
            int count = manager.clearAll();
            sender.sendMessage(config.getMessage("all-chests-cleared", Map.of("count", String.valueOf(count))));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            config.load();
            sender.sendMessage(Text.line(config.getPrefix() + " <green>Konfiguration wurde neu geladen.</green>"));
            return true;
        }

        sender.sendMessage(Text.line(config.getPrefix() + " <gray>Verwendung: <yellow>/deathchest clear</yellow></gray>"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("deathchest.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> sub = List.of("clear", "reload");
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
