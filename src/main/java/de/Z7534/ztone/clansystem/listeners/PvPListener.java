package de.Z7534.ztone.clansystem.listeners;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.managers.ConfigManager;
import de.Z7534.ztone.clansystem.managers.MessageManager;
import de.Z7534.ztone.clansystem.models.Clan;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

public class PvPListener implements Listener {

    private final Ztone plugin;

    public PvPListener(Ztone plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = resolveAttacker(event);
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        Clan victimClan = plugin.getClanManager().getClanByPlayer(victim);
        Clan attackerClan = plugin.getClanManager().getClanByPlayer(attacker);

        if (victimClan == null || attackerClan == null) {
            return;
        }

        ConfigManager config = plugin.getConfigManager();

        if (victimClan.getId() == attackerClan.getId() && config.isDisableFriendlyFire()) {
            event.setCancelled(true);
            return;
        }

        if (config.isDisableAllyFire() && attackerClan.isAlliedWith(victimClan.getId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        Clan killerClan = plugin.getClanManager().getClanByPlayer(killer);
        Clan victimClan = plugin.getClanManager().getClanByPlayer(victim);

        if (killerClan == null || victimClan == null || killerClan.getId() == victimClan.getId()) {
            return;
        }

        ConfigManager config = plugin.getConfigManager();
        MessageManager messages = plugin.getMessageManager();

        if (plugin.getWarManager().areAtWar(killerClan.getId(), victimClan.getId())) {

            plugin.getWarManager().recordKill(killer.getUniqueId(), victim.getUniqueId(),
                    killerClan.getId(), victimClan.getId());

            messages.send(killer, "war.kill", MessageManager.of(
                    "player", killer.getName(),
                    "target", victim.getName(),
                    "points", String.valueOf(config.getWarKillPoints())));
        } else if (config.isCountNormalKills()) {

            int points = config.getNormalKillPoints();
            plugin.getClanManager().addPointsToClan(killerClan, points);

            messages.send(killer, "level.points-gained", MessageManager.of("points", String.valueOf(points)));
        }
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }

        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }

        return null;
    }
}
