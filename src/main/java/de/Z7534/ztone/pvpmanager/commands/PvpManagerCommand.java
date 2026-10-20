package de.Z7534.ztone.pvpmanager.commands;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.permissions.Permissions;
import de.Z7534.ztone.pvpmanager.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PvpManagerCommand extends AbstractCommand {

    public PvpManagerCommand(Pvpmanager plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!requirePermission(sender, Permissions.ADMIN)) {
                return true;
            }
            plugin.getConfigManager().reload();
            plugin.getLocaleManager().reload();
            if (sender instanceof Player player) {
                plugin.getMessageDispatcher().send(player, "pvpm_reload_success");
            } else {
                sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("pvpm_reload_success")));
            }
            return true;
        }

        if (sender instanceof Player player) {
            plugin.getMessageDispatcher().send(player, "pvpm_help_header");
            plugin.getMessageDispatcher().send(player, "pvpm_help_pvp");
            plugin.getMessageDispatcher().send(player, "pvpm_help_pvpinfo");
            plugin.getMessageDispatcher().send(player, "pvpm_help_pvpstatus");
            plugin.getMessageDispatcher().send(player, "pvpm_help_pvplist");
            plugin.getMessageDispatcher().send(player, "pvpm_help_pvptag");
            if (sender.hasPermission(Permissions.ADMIN)) {
                plugin.getMessageDispatcher().send(player, "pvpm_help_reload");
                plugin.getMessageDispatcher().send(player, "pvpm_help_pvpdimensiontoggle");
                plugin.getMessageDispatcher().send(player, "pvpm_help_pvpdimensiontoggleall");
                plugin.getMessageDispatcher().send(player, "pvpm_help_pvpoverride");
                plugin.getMessageDispatcher().send(player, "pvpm_help_announce");
            }
            plugin.getMessageDispatcher().send(player, "pvpm_help_footer");
        } else {
            sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("pvpm_help_header")));
            sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("pvpm_help_pvp")));
            sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("pvpm_help_pvpinfo")));
            sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("pvpm_help_pvpstatus")));
            sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("pvpm_help_pvplist")));
            sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("pvpm_help_pvptag")));
            if (sender.hasPermission(Permissions.ADMIN)) {
                sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("pvpm_help_reload")));
                sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("pvpm_help_pvpdimensiontoggle")));
                sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("pvpm_help_pvpdimensiontoggleall")));
                sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("pvpm_help_pvpoverride")));
                sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("pvpm_help_announce")));
            }
            sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("pvpm_help_footer")));
        }
        return true;
    }
}