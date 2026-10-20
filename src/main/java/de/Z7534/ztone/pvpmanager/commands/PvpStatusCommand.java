package de.Z7534.ztone.pvpmanager.commands;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.permissions.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class PvpStatusCommand extends AbstractCommand {

    public PvpStatusCommand(Pvpmanager plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check if command can be executed (not in combat or has bypass)
        if (!canExecuteCommand(sender)) {
            return true;
        }

        if (args.length >= 1) {
            if (!requirePermission(sender, Permissions.CMD_PVPSTATUS_OTHERS)) {
                return true;
            }
            Player target = resolveTarget(args[0]);
            if (target == null) {
                notFound(sender, args[0]);
                return true;
            }
            boolean enabled = plugin.getPvpToggleManager().isPvpEnabled(target.getUniqueId());
            Player receiver = sender instanceof Player p ? p : target;

            plugin.getMessageDispatcher().send(receiver, enabled ? "Other_Status_Enabled" : "Others_Status_Disabled",
                    Map.of("player", target.getName()));
            return true;
        }

        if (!requirePermission(sender, Permissions.CMD_PVPSTATUS)) {
            return true;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        boolean enabled = plugin.getPvpToggleManager().isPvpEnabled(player.getUniqueId());
        plugin.getMessageDispatcher().send(player, enabled ? "Self_Status_Enabled" : "Self_Status_Disabled");
        return true;
    }
}
