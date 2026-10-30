package de.Z7534.ztone.dragonegg.command;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.claims.util.Text;
import de.Z7534.ztone.dragonegg.config.DragonEggConfig;
import de.Z7534.ztone.dragonegg.manager.DragonEggBypassManager;
import de.Z7534.ztone.dragonegg.manager.DragonEggManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DragonEggCommand implements CommandExecutor, TabCompleter {

    private final Ztone plugin;
    private final DragonEggConfig config;
    private final DragonEggManager manager;
    private final DragonEggBypassManager bypassManager;

    public DragonEggCommand(Ztone plugin, DragonEggConfig config, DragonEggManager manager,
                            DragonEggBypassManager bypassManager) {
        this.plugin = plugin;
        this.config = config;
        this.manager = manager;
        this.bypassManager = bypassManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "bypass" -> {
                handleBypass(sender, args);
                return true;
            }
            case "give" -> {
                if (!sender.hasPermission("dragonegg.admin")) {
                    sender.sendMessage(config.getMessage("no-permission"));
                    return true;
                }
                Player target;
                if (args.length >= 2) {
                    target = Bukkit.getPlayer(args[1]);
                    if (target == null) {
                        sender.sendMessage(config.getMessage("player-not-found", Map.of("player", args[1])));
                        return true;
                    }
                } else if (sender instanceof Player p) {
                    target = p;
                } else {
                    sender.sendMessage(Text.line(config.getPrefix() + " <red>Verwendung: /dragonegg give <spieler></red>"));
                    return true;
                }

                ItemStack egg = config.createSecuredEgg(target);
                target.getInventory().addItem(egg).values().forEach(remaining -> {
                    target.getWorld().dropItemNaturally(target.getLocation(), remaining);
                });

                sender.sendMessage(config.getMessage("given", Map.of("player", target.getName())));
                if (!target.equals(sender)) {
                    target.sendMessage(config.getMessage("received"));
                }
                manager.updatePlayer(target);
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("dragonegg.admin")) {
                    sender.sendMessage(config.getMessage("no-permission"));
                    return true;
                }
                config.load();
                manager.scanAllOnlinePlayers();
                sender.sendMessage(config.getMessage("reloaded"));
                return true;
            }
            default -> {
                sendHelp(sender);
                return true;
            }
        }
    }

    private void handleBypass(CommandSender sender, String[] args) {
        // /dragonegg bypass [on|off] [spieler]
        if (args.length >= 3) {
            if (!sender.hasPermission(DragonEggBypassManager.PERMISSION_OTHER)) {
                sender.sendMessage(config.getMessage("no-permission"));
                return;
            }
            String modeStr = args[1].toLowerCase(Locale.ROOT);
            Boolean enable = parseToggleState(modeStr);
            if (enable == null) {
                sender.sendMessage(config.getMessage("bypass-usage"));
                return;
            }
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(config.getMessage("player-not-found", Map.of("player", args[2])));
                return;
            }
            bypassManager.setExplicitActive(target.getUniqueId(), enable);
            manager.updatePlayer(target);
            sender.sendMessage(config.getMessage(enable ? "bypass-enabled-other" : "bypass-disabled-other",
                    Map.of("player", target.getName())));
            target.sendMessage(config.getMessage(enable ? "bypass-enabled" : "bypass-disabled"));
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getMessage("bypass-usage"));
            return;
        }

        if (!player.hasPermission(DragonEggBypassManager.PERMISSION)) {
            player.sendMessage(config.getMessage("no-permission"));
            return;
        }

        boolean enable;
        if (args.length == 2) {
            Boolean state = parseToggleState(args[1].toLowerCase(Locale.ROOT));
            if (state == null) {
                Player target = Bukkit.getPlayer(args[1]);
                if (target != null && player.hasPermission(DragonEggBypassManager.PERMISSION_OTHER)) {
                    boolean newToggled = !bypassManager.isActive(target);
                    bypassManager.setExplicitActive(target.getUniqueId(), newToggled);
                    manager.updatePlayer(target);
                    player.sendMessage(config.getMessage(newToggled ? "bypass-enabled-other" : "bypass-disabled-other",
                            Map.of("player", target.getName())));
                    target.sendMessage(config.getMessage(newToggled ? "bypass-enabled" : "bypass-disabled"));
                    return;
                }
                player.sendMessage(config.getMessage("bypass-usage"));
                return;
            }
            enable = state;
            bypassManager.setActive(player.getUniqueId(), enable);
        } else {
            enable = bypassManager.toggle(player);
        }

        manager.updatePlayer(player);
        player.sendMessage(config.getMessage(enable ? "bypass-enabled" : "bypass-disabled"));
    }

    private Boolean parseToggleState(String s) {
        return switch (s) {
            case "on", "an", "true", "1", "enable" -> true;
            case "off", "aus", "false", "0", "disable" -> false;
            default -> null;
        };
    }

    private void sendHelp(CommandSender sender) {
        if (sender.hasPermission("dragonegg.admin")) {
            sender.sendMessage(Text.line(config.getPrefix() + " <gray>Verwendung: <yellow>/dragonegg <bypass|give|reload></yellow></gray>"));
        } else if (sender.hasPermission(DragonEggBypassManager.PERMISSION)) {
            sender.sendMessage(config.getMessage("bypass-usage"));
        } else {
            sender.sendMessage(config.getMessage("no-permission"));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            List<String> sub = new ArrayList<>();
            if (sender.hasPermission(DragonEggBypassManager.PERMISSION)) {
                sub.add("bypass");
            }
            if (sender.hasPermission("dragonegg.admin")) {
                sub.add("give");
                sub.add("reload");
            }
            for (String s : sub) {
                if (s.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(s);
                }
            }
            return result;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("give") && sender.hasPermission("dragonegg.admin")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    result.add(p.getName());
                }
            }
            return result;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("bypass")) {
            if (sender.hasPermission(DragonEggBypassManager.PERMISSION)) {
                for (String s : List.of("on", "off")) {
                    if (s.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        result.add(s);
                    }
                }
            }
            if (sender.hasPermission(DragonEggBypassManager.PERMISSION_OTHER)) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        result.add(p.getName());
                    }
                }
            }
            return result;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("bypass")
                && sender.hasPermission(DragonEggBypassManager.PERMISSION_OTHER)) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))) {
                    result.add(p.getName());
                }
            }
            return result;
        }

        return List.of();
    }
}
