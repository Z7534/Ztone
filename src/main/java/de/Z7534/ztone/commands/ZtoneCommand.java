package de.Z7534.ztone.commands;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.claims.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ZtoneCommand implements CommandExecutor, TabCompleter {

    private final Ztone plugin;
    private static final String PREFIX = "<gradient:#00C9FF:#92FE9D><bold>ᴢᴛᴏɴᴇ</bold></gradient> <dark_gray>»</dark_gray>";

    public ZtoneCommand(Ztone plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("ztone.admin")) {
            sender.sendMessage(Text.line(PREFIX + " <red>Dazu hast du keine Berechtigung!</red>"));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            long start = System.currentTimeMillis();
            plugin.reload();
            long took = System.currentTimeMillis() - start;
            sender.sendMessage(Text.line(PREFIX + " <green>Alle Konfigurationen und Module wurden erfolgreich neu geladen! <dark_gray>(<yellow>" + took + "ms</yellow>)</dark_gray></green>"));
            return true;
        }

        sender.sendMessage(Text.line(PREFIX + " <gray>Verwendung: <yellow>/ztone reload</yellow></gray>"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("ztone.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> sub = List.of("reload");
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
