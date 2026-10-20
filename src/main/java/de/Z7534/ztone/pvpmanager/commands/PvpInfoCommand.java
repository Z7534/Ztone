package de.Z7534.ztone.pvpmanager.commands;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.permissions.Permissions;
import de.Z7534.ztone.pvpmanager.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.stream.Collectors;

public class PvpInfoCommand extends AbstractCommand {

    private static final String[] EXEMPT_PERMS = {
            Permissions.EXEMPT_BLOCKCOMMANDS, Permissions.EXEMPT_COMBATLOG, Permissions.EXEMPT_COMBATTAG,
            Permissions.EXEMPT_DISABLEACTIONS, Permissions.EXEMPT_KILLABUSE,
            Permissions.EXEMPT_RESPAWNPROTECTION, Permissions.EXEMPT_PVPTOGGLECOOLDOWN
    };

    public PvpInfoCommand(Pvpmanager plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check if command can be executed (not in combat or has bypass)
        if (!canExecuteCommand(sender)) {
            return true;
        }

        Player target;
        if (args.length >= 1) {
            if (!requirePermission(sender, Permissions.CMD_PVPINFO_OTHERS)) {
                return true;
            }
            target = resolveTarget(args[0]);
            if (target == null) {
                notFound(sender, args[0]);
                return true;
            }
        } else {
            if (!requirePermission(sender, Permissions.CMD_PVPINFO)) {
                return true;
            }
            target = requirePlayer(sender);
            if (target == null) {
                return true;
            }
        }

        boolean combat = plugin.getCombatManager().isTagged(target.getUniqueId());
        String opponents = plugin.getCombatManager().getOpponents(target.getUniqueId()).stream()
                .map(Bukkit::getPlayer)
                .filter(java.util.Objects::nonNull)
                .map(Player::getName)
                .collect(Collectors.joining(", "));
        String exemptions = java.util.Arrays.stream(EXEMPT_PERMS)
                .filter(target::hasPermission)
                .collect(Collectors.joining(", "));

        sendLine(sender, "PvPInfo_Header", target.getName());
        sendLabelValue(sender, "PvPInfo_UUID", target.getUniqueId().toString());
        sendLabelValue(sender, "PvPInfo_Combat", combat ? "im Kampf" : "nicht im Kampf");
        sendLabelValue(sender, "PvPInfo_Protection", resolveProtectionStatus(target));
        sendLabelValue(sender, "PvPInfo_World", target.getWorld().getName());
        sendLabelValue(sender, "PvPInfo_Override", String.valueOf(plugin.getOverrideManager().isOverriding(target.getUniqueId())));
        sendLabelValue(sender, "PvPInfo_Enemies", opponents.isEmpty() ? "-" : opponents);
        sendLabelValue(sender, "PvPInfo_Exemptions", exemptions.isEmpty() ? "-" : exemptions);
        sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("PvPInfo_Footer")));
        return true;
    }

    private void sendLine(CommandSender sender, String key, String player) {
        sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw(key).replace("{player}", player)));
    }

    private void sendLabelValue(CommandSender sender, String labelKey, String value) {
        sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw(labelKey) + value));
    }

    private String resolveProtectionStatus(Player target) {
        if (plugin.getRespawnProtectionManager().isProtected(target.getUniqueId())) {
            return "Spawnschutz (" + plugin.getRespawnProtectionManager().remainingSeconds(target.getUniqueId()) + "s)";
        }
        if (!plugin.getPvpToggleManager().isPvpEnabled(target.getUniqueId())) {
            return "PvP deaktiviert";
        }
        return "kein Schutz";
    }
}
