package de.Z7534.ztone.pvpmanager.managers;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class BorderHoppingManager {

    private final Pvpmanager plugin;

    public BorderHoppingManager(Pvpmanager plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfigManager().config().getBoolean("Anti Border Hopping.Vulnerable.Enabled", false)
                || plugin.getConfigManager().config().getBoolean("Anti Border Hopping.Barrier.Enabled", false)
                || plugin.getConfigManager().config().getBoolean("Anti Border Hopping.Push Back.Enabled", false);
    }

    public boolean handleZoneEntry(Player player, Location from, Location to) {
        if (!isEnabled() || !plugin.getCombatManager().isTagged(player.getUniqueId())) {
            return false;
        }

        boolean vulnerable = plugin.getConfigManager().config().getBoolean("Anti Border Hopping.Vulnerable.Enabled", false);
        if (vulnerable && plugin.getConfigManager().config().getBoolean("Anti Border Hopping.Vulnerable.Renew Combat Tag", true)) {
            plugin.getCombatManager().tag(player, null);
        }

        if (plugin.getConfigManager().config().getBoolean("Anti Border Hopping.Push Back.Enabled", false)) {
            pushBack(player, from, to);
            return true;
        }

        return false;
    }

    private void pushBack(Player player, Location from, Location to) {
        double force = plugin.getConfigManager().config().getDouble("Anti Border Hopping.Push Back.Force", 1.2);
        Vector direction = from.toVector().subtract(to.toVector()).normalize();
        if (direction.lengthSquared() == 0) {
            direction = player.getLocation().getDirection().multiply(-1);
        }
        player.setVelocity(direction.multiply(force));

        plugin.getMessageDispatcher().send(player, "Pushback_Warning");
    }
}
