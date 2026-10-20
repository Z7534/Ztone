package de.Z7534.ztone.stoneattack.commands;

import de.Z7534.ztone.stoneattack.Stoneattack;
import de.Z7534.ztone.stoneattack.modules.playerlimit.PlayerLimitModule;
import de.Z7534.ztone.stoneattack.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlayerLimitCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("set", "get", "off", "list", "reload");

    private final Stoneattack plugin;

    public PlayerLimitCommand(Stoneattack plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("playerlimit.use")) {
            plugin.getMessagesManager().send(sender, "general.no-permission");
            return true;
        }

        PlayerLimitModule module = plugin.getPlayerLimitModule();
        if (module == null) {
            sender.sendMessage(ColorUtil.parse("<red>Das Spielerlimit-Modul ist deaktiviert (siehe config.yml).</red>"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ColorUtil.parse("<red>Nutzung: /playerlimit <set|get|off|list|reload></red>"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "set" -> handleSet(sender, module, args);
            case "get" -> handleGet(sender, module);
            case "off" -> handleOff(sender, module);
            case "list" -> handleList(sender, module);
            case "reload" -> handleReload(sender, module);
            default -> plugin.getMessagesManager().send(sender, "general.unknown-subcommand",
                    Map.of("usage", "/playerlimit <set|get|off|list|reload>"));
        }
        return true;
    }

    private void handleSet(CommandSender sender, PlayerLimitModule module, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.parse("<red>Nutzung: /playerlimit set <zahl></red>"));
            return;
        }
        if (!CommandUtil.isInt(args[1])) {
            plugin.getMessagesManager().send(sender, "general.invalid-number", Map.of("input", args[1]));
            return;
        }
        int newLimit = Integer.parseInt(args[1]);
        if (newLimit < -1) {
            plugin.getMessagesManager().send(sender, "playerlimit.invalid-limit");
            return;
        }

        List<Player> toKick = module.calculatePlayersToKick(newLimit);
        module.setLimit(newLimit);

        String kickTemplate = plugin.getMessagesManager().raw("playerlimit.kick-message");
        for (Player kicked : toKick) {
            int position = module.getJoinOrder().positionOf(kicked.getUniqueId());
            // Position ist zu diesem Zeitpunkt noch die alte Position,
            // da der Spieler erst mit dem tatsaechlichen Kick entfernt wird.
            String finalKickMessage = kickTemplate
                    .replace("%limit%", String.valueOf(newLimit))
                    .replace("%position%", String.valueOf(position));
            kicked.kick(ColorUtil.parse(finalKickMessage));

            plugin.getLogger().info(plugin.getMessagesManager().raw("playerlimit.console-log-kick")
                    .replace("%executor%", sender.getName())
                    .replace("%player%", kicked.getName())
                    .replace("%position%", String.valueOf(position))
                    .replace("%limit%", String.valueOf(newLimit)));
        }

        if (!toKick.isEmpty()) {
            String staffPermission = plugin.getConfigManager().get().getString("playerlimit.staff-notify-permission", "");
            if (staffPermission != null && !staffPermission.isBlank()) {
                Map<String, String> placeholders = Map.of(
                        "executor", sender.getName(),
                        "amount", String.valueOf(toKick.size()),
                        "limit", String.valueOf(newLimit)
                );
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.hasPermission(staffPermission)) {
                        online.sendMessage(plugin.getMessagesManager().get("playerlimit.staff-notify", placeholders));
                    }
                }
            }
        }

        plugin.getMessagesManager().send(sender, "playerlimit.set-success", Map.of("limit", String.valueOf(newLimit)));
    }

    private void handleGet(CommandSender sender, PlayerLimitModule module) {
        int current = Bukkit.getOnlinePlayers().size();
        if (module.isEnabled()) {
            plugin.getMessagesManager().send(sender, "playerlimit.get-info",
                    Map.of("limit", String.valueOf(module.getLimit()), "current", String.valueOf(current)));
        } else {
            plugin.getMessagesManager().send(sender, "playerlimit.get-info-unlimited",
                    Map.of("current", String.valueOf(current)));
        }
    }

    private void handleOff(CommandSender sender, PlayerLimitModule module) {
        module.disableLimit();
        plugin.getMessagesManager().send(sender, "playerlimit.off-success");
    }

    private void handleList(CommandSender sender, PlayerLimitModule module) {
        List<Map.Entry<UUID, String>> ordered = module.getJoinOrder().snapshot();
        plugin.getMessagesManager().send(sender, "playerlimit.list-header", Map.of("count", String.valueOf(ordered.size())));

        int position = 1;
        for (Map.Entry<UUID, String> entry : ordered) {
            sender.sendMessage(plugin.getMessagesManager().get("playerlimit.list-entry",
                    Map.of("position", String.valueOf(position), "player", entry.getValue())));
            position++;
        }

        // Bypass-Spieler getrennt auflisten, da sie nicht Teil der
        // eigentlichen Join-Reihenfolge sind.
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("playerlimit.bypass")) {
                sender.sendMessage(plugin.getMessagesManager().get("playerlimit.list-entry-bypass",
                        Map.of("position", "-", "player", online.getName())));
            }
        }
    }

    private void handleReload(CommandSender sender, PlayerLimitModule module) {
        plugin.getConfigManager().reload();
        plugin.getMessagesManager().reload();
        module.reload();
        plugin.getMessagesManager().send(sender, "playerlimit.reload-success");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return CommandUtil.filterStartingWith(SUBCOMMANDS, args[0]);
        }
        return List.of();
    }
}
