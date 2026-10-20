package de.Z7534.ztone.pvpmanager.commands;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.permissions.Permissions;
import de.Z7534.ztone.pvpmanager.utils.ColorUtils;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;

public class SoupCommand extends AbstractCommand {

    public SoupCommand(Pvpmanager plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check if command can be executed (not in combat or has bypass)
        if (!canExecuteCommand(sender)) {
            return true;
        }

        if (!requirePermission(sender, Permissions.CMD_SOUP)) {
            return true;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        PlayerInventory inventory = player.getInventory();
        int filled = 0;
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() == Material.BOWL) {
                int amount = item.getAmount();
                if (amount <= 1) {
                    inventory.setItem(i, new ItemStack(Material.MUSHROOM_STEW, 1));
                } else {
                    item.setAmount(amount - 1);
                    inventory.addItem(new ItemStack(Material.MUSHROOM_STEW, 1));
                }
                filled++;
            }
        }

        if (filled > 0) {
            if (sender instanceof Player p) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("filled", Integer.toString(filled));
                plugin.getMessageDispatcher().send(p, "soup_filled", placeholders);
            } else {
                String message = plugin.getLocaleManager().raw("soup_filled").replace("{filled}", Integer.toString(filled));
                sender.sendMessage(ColorUtils.colorize(message));
            }
        } else {
            if (sender instanceof Player p) {
                plugin.getMessageDispatcher().send(p, "soup_no_empty");
            } else {
                sender.sendMessage(ColorUtils.colorize(plugin.getLocaleManager().raw("soup_no_empty")));
            }
        }
        return true;
    }
}