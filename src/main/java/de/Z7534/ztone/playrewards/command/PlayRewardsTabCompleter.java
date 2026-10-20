package de.Z7534.ztone.playrewards.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlayRewardsTabCompleter implements TabCompleter {

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("playrewards.admin")) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            for (String sub : new String[]{"sync", "reload", "setplaytime", "resetstreak"}) {
                if (sub.startsWith(input)) {
                    completions.add(sub);
                }
            }
            return completions;
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("resetstreak") || args[0].equalsIgnoreCase("sync") || args[0].equalsIgnoreCase("setplaytime"))) {
            String input = args[1].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(input)) {
                    completions.add(player.getName());
                }
            }
            return completions;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("setplaytime")) {
            String input = args[2].toLowerCase();
            for (String suggestion : new String[]{"1h", "5h", "1d", "3d", "7d", "86400s"}) {
                if (suggestion.startsWith(input)) {
                    completions.add(suggestion);
                }
            }
            return completions;
        }

        return Collections.emptyList();
    }
}
