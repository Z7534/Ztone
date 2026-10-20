package de.Z7534.ztone.stonelog.command;

import de.Z7534.ztone.stonelog.Stonelog;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;

public class StonelogCommand implements CommandExecutor, TabCompleter {

    private final Stonelog plugin;

    public StonelogCommand(Stonelog plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("stonelog.admin")) {
            sender.sendMessage(ChatColor.RED + "Dazu hast du keine Berechtigung.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reload();
            sender.sendMessage(ChatColor.GREEN + "[Stonelog] Konfiguration wurde neu geladen.");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Nutzung: /stonelog reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reload");
        }
        return Collections.emptyList();
    }
}
