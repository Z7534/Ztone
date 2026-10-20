package de.Z7534.ztone.pvpmanager.listeners;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;

public class PotionEffectListener implements Listener {

    private final Pvpmanager plugin;

    public PotionEffectListener(Pvpmanager plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPotionEffect(EntityPotionEffectEvent event) {
        // Combat mode darf NUR bei Player vs Player ausgelöst werden.
        // Potion PvP (Schaden/Schadstränke) wird exklusiv über PotionSplashEvent,
        // AreaEffectCloudApplyEvent und EntityDamageByEntityEvent in CombatListener gehandhabt,
        // damit PvE-Effekte (Hexen, Höhlenspinnen, Wither-Rosen, Essen) keinen Combat-Tag geben.
    }
}
