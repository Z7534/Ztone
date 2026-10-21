package de.Z7534.ztone.pvpmanager.listeners;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.hooks.WorldGuardHook;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

public class CombatListener implements Listener {

    private final Pvpmanager plugin;

    public CombatListener(Pvpmanager plugin) {
        this.plugin = plugin;
    }

    public boolean isPvpAllowedAtLocations(Player attacker, Player victim) {
        if (attacker == null || victim == null) return false;
        if (plugin.getOverrideManager().isOverriding(attacker.getUniqueId())) return true;
        if (plugin.getPlugin().areWarOpponentsWithPvP(attacker.getUniqueId(), victim.getUniqueId())) return true;

        if (plugin.getPlugin().getConfigManager().isClaimsEnabled() && plugin.getPlugin().getClaims() != null) {
            var claimsPlugin = plugin.getPlugin().getClaims();
            var claimManager = claimsPlugin.getClaimManager();
            var victimClaim = claimManager.getClaimAt(victim.getLocation());
            var attackerClaim = claimManager.getClaimAt(attacker.getLocation());

            var pvpClaimBypass = claimsPlugin.getPvPClaimBypass();
            boolean playerBypassed = pvpClaimBypass != null && (
                    pvpClaimBypass.isPlayerBypassed(attacker) || pvpClaimBypass.isPlayerBypassed(victim));
            boolean staffBypass = claimsPlugin.getBypassManager() != null && claimsPlugin.getBypassManager().isActive(attacker);

            if (playerBypassed || staffBypass) {
                return true;
            }

            // In Claims ist PvP komplett deaktiviert, AUSSER ein Admin hat den Claim mit /claimspvpoverride freigegeben
            boolean victimInProtectedClaim = victimClaim != null && (pvpClaimBypass == null || !pvpClaimBypass.isClaimBypassed(victimClaim.id()));
            boolean attackerInProtectedClaim = attackerClaim != null && (pvpClaimBypass == null || !pvpClaimBypass.isClaimBypassed(attackerClaim.id()));

            if (victimInProtectedClaim || attackerInProtectedClaim) {
                return false;
            }

            return true;
        }

        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHitExplosive(EntityDamageByEntityEvent event) {
        Player hitter = resolveDirectOrShooter(event.getDamager());
        if (hitter == null) {
            return;
        }
        if (event.getEntity() instanceof TNTPrimed tnt) {
            tnt.setMetadata("ztone_tnt_hitter", new FixedMetadataValue(plugin.getPlugin(), hitter.getUniqueId()));
        } else if (event.getEntity() instanceof ExplosiveMinecart em) {
            em.setMetadata("ztone_tnt_hitter", new FixedMetadataValue(plugin.getPlugin(), hitter.getUniqueId()));
        } else if (event.getEntity() instanceof EnderCrystal crystal) {
            crystal.setMetadata("ztone_crystal_hitter", new FixedMetadataValue(plugin.getPlugin(), hitter.getUniqueId()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = resolveAttacker(event);

        // PvP-Override: Darf jeden ueberall angreifen (setzt Cancelled zurueck und taggt)
        if (attacker != null && !attacker.equals(victim) && plugin.getOverrideManager().isOverriding(attacker.getUniqueId())) {
            event.setCancelled(false);
            applyTagAndMessages(attacker, victim);
            return;
        }

        // Wurde das Event bereits durch Claim-Schutz, WorldGuard etc. abgebrochen -> KEIN COMBAT TAG!
        if (event.isCancelled()) {
            return;
        }

        // TNT und Explosionsminenwagen-Schutz:
        // Verhindert, dass Spieler mit PvP aus durch geschlagene TNTs/Speere/Knockback getoetet werden
        if (event.getDamager() instanceof TNTPrimed || event.getDamager() instanceof ExplosiveMinecart) {
            Player sourceOrHitter = resolveAttacker(event);
            if (sourceOrHitter != null && sourceOrHitter.equals(victim)) {
                // Eigener TNT-Schaden bleibt wie im Standard-Survival erhalten
                return;
            }
            if (!plugin.canPvP(victim) || (sourceOrHitter != null && !isPvpAllowedAtLocations(sourceOrHitter, victim))) {
                event.setCancelled(true);
                return;
            }
        }

        if (event.getDamager() instanceof EnderCrystal) {
            Player sourceOrHitter = resolveAttacker(event);
            if (sourceOrHitter != null && sourceOrHitter.equals(victim)) {
                return;
            }
            if (!plugin.canPvP(victim) || (sourceOrHitter != null && !isPvpAllowedAtLocations(sourceOrHitter, victim))) {
                event.setCancelled(true);
                return;
            }
        }

        if (attacker == null || attacker.equals(victim)) {
            return;
        }

        if (plugin.getConfigManager().config().getStringList("General Settings.World Exclusions").contains(victim.getWorld().getName())) {
            event.setCancelled(true);
            plugin.getMessageDispatcher().send(attacker, "World_Protection");
            return;
        }

        WorldGuardHook.PvpState worldGuardState =
                plugin.getHookManager().worldGuard().queryPvpState(victim.getLocation());

        if (worldGuardState == WorldGuardHook.PvpState.DENY) {
            event.setCancelled(true);
            return;
        }

        // Claims PvP-Pruefung: In Claims ist PvP komplett deaktiviert, ausser durch /claimspvpoverride freigegeben
        if (!isPvpAllowedAtLocations(attacker, victim)) {
            event.setCancelled(true);
            return; // KEIN COMBAT TAG! KEIN COMBAT MODE!
        }

        // Respawn Protection Checks
        if (plugin.getRespawnProtectionManager().isProtected(victim.getUniqueId())) {
            event.setCancelled(true);
            long left = plugin.getRespawnProtectionManager().remainingSeconds(victim.getUniqueId());
            plugin.getMessageDispatcher().send(attacker, "Respawn_Protection_Attacker",
                    Map.of("player", victim.getName(), "time", String.valueOf(left)));
            plugin.getMessageDispatcher().send(victim, "Respawn_Protection_Victim",
                    Map.of("time", String.valueOf(left)));
            return;
        }

        if (plugin.getRespawnProtectionManager().isProtected(attacker.getUniqueId())) {
            event.setCancelled(true);
            long left = plugin.getRespawnProtectionManager().remainingSeconds(attacker.getUniqueId());
            plugin.getMessageDispatcher().send(attacker, "Respawn_Protection_Cannot_Attack",
                    Map.of("time", String.valueOf(left)));
            return;
        }

        // Clankrieg: PvP ist zielgerichtet zwischen den Kriegsgegnern aktiv (nicht global gegen unbeteiligte Spieler)
        boolean atWar = plugin.getPlugin().areWarOpponentsWithPvP(attacker.getUniqueId(), victim.getUniqueId());
        boolean attackerCanPvP = atWar || plugin.canPvP(attacker);
        boolean victimCanPvP = atWar || plugin.canPvP(victim);

        if (!victimCanPvP) {
            event.setCancelled(true);
            plugin.getMessageDispatcher().send(victim, "Attack_Denied_You");
            plugin.getMessageDispatcher().send(attacker, "Attack_Denied_Other", Map.of("player", victim.getName()));
            return;
        }
        if (!attackerCanPvP) {
            event.setCancelled(true);
            plugin.getMessageDispatcher().send(attacker, "Attack_Denied_You");
            return;
        }

        applyTagAndMessages(attacker, victim);

        if (plugin.getConfigManager().config().getBoolean("Other Settings.PvP Blood", true)) {
            victim.getWorld().spawnParticle(Particle.BLOCK, victim.getLocation().add(0, 1, 0),
                    10, 0.3, 0.5, 0.3, Material.REDSTONE_BLOCK.createBlockData());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPetDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Tameable tameable)) {
            return;
        }
        if (!tameable.isTamed() || tameable.getOwner() == null) {
            return;
        }
        UUID ownerUuid = tameable.getOwner().getUniqueId();
        Player attacker = resolveAttacker(event);
        if (attacker == null || attacker.getUniqueId().equals(ownerUuid)) {
            return;
        }

        boolean ownerPvP = plugin.canPvP(ownerUuid);
        boolean attackerPvP = plugin.canPvP(attacker);

        if (!ownerPvP || !attackerPvP) {
            event.setCancelled(true);
            if (!ownerPvP) {
                attacker.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(
                        "&c\u1d05\u026a\u1d07\ua731\u1d07\ua731 \u029c\u1d00\u1d1c\ua731\u1d1b\u026a\u1d07\u0280 \u0262\u1d07\u029c\u00f6\u0280\u1d1b \u1d07\u026a\u0274\u1d07\u1d0d \ua731\u1d18\u026a\u1d07\u029f\u1d07\u0280 \u1d0d\u026a\u1d1b \u1d05\u1d07\u1d00\u1d0b\u1d1b\u026a\u1d20\u026a\u1d07\u0280\u1d1b\u1d07\u1d0d \u1d18\u1d20\u1d18!"));
            } else {
                attacker.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(
                        "&c\u1d1c\u029c\u1d00\ua731\u1d1b \u1d18\u1d20\u1d18 \u1d05\u1d07\u1d00\u1d0b\u1d1b\u026a\u1d20\u026a\u1d07\u0280\u1d1b \u1d1c\u0274\u1d05 \u1d0b\u1d00\u0274\u0274\ua731\u1d1b \u1d0b\u1d07\u026a\u0274\u1d07 \u029c\u1d00\u1d1c\ua731\u1d1b\u026a\u1d07\u0280\u1d07 \u1d00\u0274\u0262\u0280\u1d07\u026a\ua730\u1d07\u0274!"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getCaught() instanceof Player victim && !victim.equals(event.getPlayer())) {
            Player fisher = event.getPlayer();
            boolean atWar = plugin.getPlugin().areWarOpponentsWithPvP(fisher.getUniqueId(), victim.getUniqueId());
            if (!atWar && (!isPvpAllowedAtLocations(fisher, victim) || !plugin.canPvP(victim) || !plugin.canPvP(fisher))) {
                event.setCancelled(true);
                event.getHook().remove();
                plugin.getMessageDispatcher().send(fisher, "Attack_Denied_Other", Map.of("player", victim.getName()));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player attacker)) {
            return;
        }
        boolean hasHarmful = false;
        for (PotionEffect effect : event.getPotion().getEffects()) {
            if (isHarmfulPotion(effect.getType())) {
                hasHarmful = true;
                break;
            }
        }
        if (!hasHarmful) {
            return;
        }

        for (LivingEntity entity : event.getAffectedEntities()) {
            if (entity instanceof Player victim && !victim.equals(attacker)) {
                boolean atWar = plugin.getPlugin().areWarOpponentsWithPvP(attacker.getUniqueId(), victim.getUniqueId());
                if (!atWar && (!isPvpAllowedAtLocations(attacker, victim) || !plugin.canPvP(victim) || !plugin.canPvP(attacker))) {
                    event.setIntensity(victim, 0.0);
                } else {
                    applyTagAndMessages(attacker, victim);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAreaCloud(AreaEffectCloudApplyEvent event) {
        if (!(event.getEntity().getSource() instanceof Player attacker)) {
            return;
        }
        boolean hasHarmful = false;
        for (PotionEffect effect : event.getEntity().getCustomEffects()) {
            if (isHarmfulPotion(effect.getType())) {
                hasHarmful = true;
                break;
            }
        }
        if (event.getEntity().getBasePotionType() != null) {
            for (PotionEffect effect : event.getEntity().getBasePotionType().getPotionEffects()) {
                if (isHarmfulPotion(effect.getType())) {
                    hasHarmful = true;
                    break;
                }
            }
        }
        if (!hasHarmful) {
            return;
        }

        for (LivingEntity entity : new ArrayList<>(event.getAffectedEntities())) {
            if (entity instanceof Player victim && !victim.equals(attacker)) {
                boolean atWar = plugin.getPlugin().areWarOpponentsWithPvP(attacker.getUniqueId(), victim.getUniqueId());
                if (!atWar && (!isPvpAllowedAtLocations(attacker, victim) || !plugin.canPvP(victim) || !plugin.canPvP(attacker))) {
                    event.getAffectedEntities().remove(victim);
                } else {
                    applyTagAndMessages(attacker, victim);
                }
            }
        }
    }

    private boolean isHarmfulPotion(PotionEffectType type) {
        if (type == null) return false;
        return type == PotionEffectType.INSTANT_DAMAGE
                || type == PotionEffectType.POISON
                || type == PotionEffectType.SLOWNESS
                || type == PotionEffectType.WEAKNESS
                || type == PotionEffectType.WITHER
                || type == PotionEffectType.BLINDNESS
                || type == PotionEffectType.MINING_FATIGUE
                || type == PotionEffectType.LEVITATION
                || type == PotionEffectType.NAUSEA
                || type == PotionEffectType.DARKNESS
                || type == PotionEffectType.WIND_CHARGED
                || type == PotionEffectType.INFESTED
                || type == PotionEffectType.OOZING
                || type == PotionEffectType.WEAVING;
    }

    private void applyTagAndMessages(Player attacker, Player victim) {
        boolean attackerNewlyTagged = plugin.getCombatManager().tag(attacker, victim);
        boolean victimNewlyTagged = plugin.getCombatManager().tag(victim, attacker);

        if (attackerNewlyTagged) {
            plugin.getMessageDispatcher().send(attacker, "Tagged_Attacker", Map.of("player", victim.getName()));
        }
        if (victimNewlyTagged) {
            plugin.getMessageDispatcher().send(victim, "Tagged_Defender", Map.of("player", attacker.getName()));
        }
    }

    private Player resolveDirectOrShooter(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        if (damager instanceof TNTPrimed tnt) {
            if (tnt.hasMetadata("ztone_tnt_hitter")) {
                UUID id = (UUID) tnt.getMetadata("ztone_tnt_hitter").get(0).value();
                Player p = Bukkit.getPlayer(id);
                if (p != null) return p;
            }
            if (tnt.getSource() instanceof Player player) {
                return player;
            }
        }
        if (damager instanceof ExplosiveMinecart em) {
            if (em.hasMetadata("ztone_tnt_hitter")) {
                UUID id = (UUID) em.getMetadata("ztone_tnt_hitter").get(0).value();
                Player p = Bukkit.getPlayer(id);
                if (p != null) return p;
            }
        }
        if (damager instanceof EnderCrystal crystal) {
            if (crystal.hasMetadata("ztone_crystal_hitter")) {
                UUID id = (UUID) crystal.getMetadata("ztone_crystal_hitter").get(0).value();
                Player p = Bukkit.getPlayer(id);
                if (p != null) return p;
            }
        }
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player player) {
            return player;
        }
        if (damager instanceof Firework fr && fr.getShooter() instanceof Player player) {
            return player;
        }
        if (damager instanceof org.bukkit.entity.LightningStrike lightning) {
            if (lightning.getCausingEntity() instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
