package de.Z7534.ztone.pvpmanager.commands;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.permissions.Permissions;
import de.Z7534.ztone.pvpmanager.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public class PvpListCommand extends AbstractCommand {

    public PvpListCommand(Pvpmanager plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check if command can be executed (not in combat or has bypass)
        if (!canExecuteCommand(sender)) {
            return true;
        }

        if (!requirePermission(sender, Permissions.CMD_PVPLIST)) {
            return true;
        }

        boolean includeOffline = args.length >= 1 && args[0].equalsIgnoreCase("offline");
        if (includeOffline && !requirePermission(sender, Permissions.CMD_PVPLIST_OFFLINE)) {
            return true;
        }

        sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("PvPList_Title")));
        int shown = 0;
        for (org.bukkit.entity.Player online : Bukkit.getOnlinePlayers()) {
            boolean enabled = plugin.getPvpToggleManager().isPvpEnabled(online.getUniqueId());
            String key = enabled ? "PvPList_Enabled" : "PvPList_Disabled";
            sender.sendMessage(ColorUtils.colorize(online.getName() + " - " + plugin.getLocaleManager().raw(key)));
            shown++;
        }

        if (includeOffline) {
            java.util.Set<java.util.UUID> onlineIds = Bukkit.getOnlinePlayers().stream()
                    .map(org.bukkit.entity.Player::getUniqueId)
                    .collect(java.util.stream.Collectors.toSet());

            for (de.Z7534.ztone.pvpmanager.database.models.PlayerData data : plugin.getPlayerDataDAO().findAll()) {
                if (onlineIds.contains(data.getUuid())) {
                    continue;
                }
                String name = Bukkit.getOfflinePlayer(data.getUuid()).getName();
                if (name == null) {
                    continue;
                }
                String key = data.isPvpEnabled() ? "PvPList_Enabled" : "PvPList_Disabled";
                sender.sendMessage(ColorUtils.colorize(name + " - " + plugin.getLocaleManager().raw(key)));
                shown++;
            }
        }

        if (shown == 0) {
            sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("PvPList_Nothing_Found")));
        }
        return true;
    }
}
