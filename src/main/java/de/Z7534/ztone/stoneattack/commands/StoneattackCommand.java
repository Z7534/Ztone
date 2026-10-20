package de.Z7534.ztone.stoneattack.commands;

import de.Z7534.ztone.stoneattack.Stoneattack;
import de.Z7534.ztone.stoneattack.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public class StoneattackCommand implements CommandExecutor, TabCompleter {

    private final Stoneattack plugin;

    public StoneattackCommand(Stoneattack plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("stoneattack.admin")) {
            plugin.getMessagesManager().send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 0 || (!args[0].equalsIgnoreCase("reload") && !args[0].equalsIgnoreCase("rl"))) {
            sender.sendMessage(ColorUtil.parse("<gray>Stoneattack v" + plugin.getDescription().getVersion() + "</gray>"));
            sender.sendMessage(ColorUtil.parse("<red>Nutzung: /stoneattack reload</red>"));
            return true;
        }

        try {
            plugin.reloadAll();
            plugin.getMessagesManager().send(sender, "general.reload-success");
        } catch (Throwable t) {
            plugin.getLogger().severe("Fehler beim Ausfuehren von /stoneattack reload: " + t.getMessage());
            t.printStackTrace();
            sender.sendMessage(ColorUtil.parse("<red>Fehler beim Neuladen: " + t.getMessage() + "</red>"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return CommandUtil.filterStartingWith(List.of("reload", "rl"), args[0]);
        }
        return List.of();
    }
}
