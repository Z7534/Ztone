package de.Z7534.ztone.clansystem.listeners;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.models.ClanWar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;

import java.time.Duration;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerListener implements Listener {

    private final Ztone plugin;

    public PlayerListener(Ztone plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        plugin.getDatabaseManager().clearExpiredCooldowns();
        notifyIfInActiveWar(player);

        if (plugin.getGlowManager() != null) {
            plugin.getGlowManager().onJoin(player);
            // 2 Ticks Verzoegerung, damit der Client vollstaendig verbunden und das Entity-Tracking aktiv ist
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.getGlowManager().applyToPlayer(player);
                }
            }, 2L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (plugin.getGlowManager() != null) {
            plugin.getGlowManager().onQuit(player);
        }

        ChatListener.clearToggles(player.getUniqueId());
        plugin.getChatInputManager().cancel(player.getUniqueId());

        de.Z7534.ztone.clansystem.gui.AbstractGUI.removeOpenGui(player);
        de.Z7534.ztone.clansystem.gui.ClanChestGUI.removeOpenChest(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // Beim Respawn erhaelt der Spieler in Minecraft eine neue Entity-ID, Cache aktualisieren und verzoegert setzen
        if (plugin.getGlowManager() != null) {
            plugin.getGlowManager().onJoin(player);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.getGlowManager().applyToPlayer(player);
                }
            }, 2L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();

        if (plugin.getGlowManager() != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.getGlowManager().applyToPlayer(player);
                }
            }, 2L);
        }
    }
    private void notifyIfInActiveWar(Player player) {
        // 20 Ticks Verzoegerung, damit der Spieler vollstaendig geladen ist und Chat/Title sieht
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (plugin.getClanManager() == null || plugin.getWarManager() == null) return;

            Clan clan = plugin.getClanManager().getClanByPlayer(player);
            if (clan == null) return;

            List<ClanWar> activeWars = plugin.getWarManager().getActiveWarsForClan(clan.getId());
            if (activeWars.isEmpty()) return;

            for (ClanWar war : activeWars) {
                int enemyClanId = war.getOpponent(clan.getId());
                Clan enemyClan = plugin.getClanManager().getClan(enemyClanId);
                String enemyName = enemyClan != null ? enemyClan.getColoredName() : ("Clan #" + enemyClanId);

                boolean isFighter = war.isParticipant(player.getUniqueId());

                player.sendMessage("§8§m--------------------------------------------------");
                player.sendMessage("§8[§6Krieg§8] §c§lACHTUNG: §eDein Clan befindet sich aktuell im Clankrieg gegen " + enemyName + "§e!");
                if (isFighter) {
                    int deaths = war.getParticipantDeaths(player.getUniqueId());
                    int remainingLives = Math.max(0, 3 - deaths);
                    player.sendMessage("§8[§6Krieg§8] §c§lDu wurdest als KÄMPFER ausgewählt! §7(Verbleibende Leben: §e" + remainingLives + "/3§7)");
                    player.sendMessage("§8[§6Krieg§8] §ePvP gegen die gegnerischen Kämpfer ist außerhalb von Claims aktiv!");
                } else {
                    player.sendMessage("§8[§6Krieg§8] §7Du wurdest nicht als aktiver Kämpfer ausgewählt und nimmst als Zuschauer teil.");
                }
                player.sendMessage("§8§m--------------------------------------------------");

                Title title = Title.title(
                        Component.text("§c§lCLANKRIEG AKTIV!"),
                        Component.text(isFighter ? "§eDu bist als §cKämpfer §eim Krieg!" : "§7Dein Clan ist im Krieg!"),
                        Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3500), Duration.ofMillis(1000))
                );
                player.showTitle(title);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.0f);
            }
        }, 20L);
    }
}
