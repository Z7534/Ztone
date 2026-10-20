package de.Z7534.ztone.stonelog.listener;

import de.Z7534.ztone.stonelog.Stonelog;
import de.Z7534.ztone.stonelog.config.ConfigManager;
import de.Z7534.ztone.stonelog.discord.Embed;
import de.Z7534.ztone.stonelog.util.AvatarUtil;
import de.Z7534.ztone.stonelog.util.CodeBlockUtil;
import de.Z7534.ztone.stonelog.util.DimensionUtil;
import de.Z7534.ztone.stonelog.util.MaterialNameUtil;
import de.Z7534.ztone.stonelog.util.PlaceholderUtil;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Trident;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Loggt Todesfaelle mit vollständigen Killer- und Waffen-Informationen.
 * Der Webhook-Name enthaelt die Todesnachricht (z.B. "Player was killed by Killer..."),
 * darunter folgt das detaillierte Embed mit Killer, Waffe, Welt und Position.
 */
public class DeathListener implements Listener {

    private final Stonelog plugin;

    public DeathListener(Stonelog plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        ConfigManager.WebhookEntry entry = plugin.getConfigManager().getDeaths();
        if (!entry.isEnabled()) return;

        Player victim = event.getPlayer();
        DeathDetails details = resolveDeathDetails(victim, event);

        String dimension = DimensionUtil.of(victim.getWorld());
        Location loc = victim.getLocation();
        String position = loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", victim.getName());
        placeholders.put("uuid", victim.getUniqueId().toString());
        placeholders.put("deathmessage", details.deathMessage());
        placeholders.put("killer", details.killerName());
        placeholders.put("weapon", details.weaponName());
        placeholders.put("world", dimension);
        placeholders.put("position", position);

        String avatarUrl = AvatarUtil.headUrl(plugin.getConfigManager().getAvatarBaseUrl(), victim.getUniqueId());

        Embed embed = new Embed();
        embed.footerText = PlaceholderUtil.apply(entry.msg("footer", "{uuid}"), placeholders);
        embed.color = 0x8b0000;

        embed.addField(entry.msg("field-killer", "Killer"), CodeBlockUtil.wrap(details.killerName()), false);
        embed.addField(entry.msg("field-weapon", "Waffe"), CodeBlockUtil.wrap(details.weaponName()), false);
        embed.addField(entry.msg("field-world", "Welt"), CodeBlockUtil.wrap(dimension), false);
        embed.addField(entry.msg("field-position", "Position"), CodeBlockUtil.wrap(position), false);

        // Webhook-Name = Todesnachricht (maximal 80 Zeichen fuer Discord)
        String webhookUsername = details.deathMessage();
        if (webhookUsername.length() > 80) {
            webhookUsername = webhookUsername.substring(0, 77) + "...";
        }

        plugin.getWebhookSender().send(entry.getUrls(), webhookUsername, avatarUrl, embed);
    }

    private DeathDetails resolveDeathDetails(Player victim, PlayerDeathEvent event) {
        String killerName = null;
        String weaponName = null;

        Player killerPlayer = victim.getKiller();
        if (killerPlayer != null) {
            killerName = killerPlayer.getName();
            var item = killerPlayer.getInventory().getItemInMainHand();
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                weaponName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                        ? PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName())
                        : MaterialNameUtil.prettify(item.getType());
            } else {
                weaponName = "Fäuste";
            }
        } else {
            EntityDamageEvent lastDamage = victim.getLastDamageCause();
            if (lastDamage instanceof EntityDamageByEntityEvent edbe) {
                Entity damager = edbe.getDamager();
                if (damager instanceof Projectile proj) {
                    if (proj.getShooter() instanceof Player shooterPlayer) {
                        killerName = shooterPlayer.getName();
                        weaponName = formatProjectile(proj);
                    } else if (proj.getShooter() instanceof Entity shooterEntity) {
                        killerName = formatEntityName(shooterEntity);
                        weaponName = formatProjectile(proj);
                    } else {
                        killerName = "Projektil (" + formatEntityName(proj) + ")";
                        weaponName = formatProjectile(proj);
                    }
                } else if (damager instanceof TNTPrimed tnt) {
                    if (tnt.getSource() instanceof Player sourcePlayer) {
                        killerName = sourcePlayer.getName();
                        weaponName = "TNT (Explosion)";
                    } else if (tnt.getSource() != null) {
                        killerName = formatEntityName(tnt.getSource());
                        weaponName = "TNT (Explosion)";
                    } else {
                        killerName = "TNT";
                        weaponName = "Explosion";
                    }
                } else if (damager instanceof Player pDamager) {
                    killerName = pDamager.getName();
                    var item = pDamager.getInventory().getItemInMainHand();
                    if (item != null && item.getType() != org.bukkit.Material.AIR) {
                        weaponName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                                ? PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName())
                                : MaterialNameUtil.prettify(item.getType());
                    } else {
                        weaponName = "Fäuste";
                    }
                } else if (damager != null) {
                    killerName = formatEntityName(damager);
                    weaponName = "Nahkampf";
                }
            } else if (lastDamage != null) {
                EntityDamageEvent.DamageCause cause = lastDamage.getCause();
                switch (cause) {
                    case FALL -> { killerName = "Sturz"; weaponName = "Fallschaden"; }
                    case DROWNING -> { killerName = "Ertrinken"; weaponName = "Wasser"; }
                    case LAVA -> { killerName = "Lava"; weaponName = "Lava"; }
                    case FIRE, FIRE_TICK -> { killerName = "Feuer"; weaponName = "Verbrennung"; }
                    case VOID -> { killerName = "Void"; weaponName = "Leere"; }
                    case SUFFOCATION -> { killerName = "Ersticken"; weaponName = "Erstickung"; }
                    case STARVATION -> { killerName = "Hunger"; weaponName = "Verhungert"; }
                    case CONTACT -> { killerName = "Kaktus"; weaponName = "Stacheln"; }
                    case FREEZE -> { killerName = "Kälte"; weaponName = "Erfroren"; }
                    case LIGHTNING -> { killerName = "Blitzschlag"; weaponName = "Blitz"; }
                    case MAGIC, POISON -> { killerName = "Magie"; weaponName = "Trank / Gift"; }
                    case WITHER -> { killerName = "Wither"; weaponName = "Wither-Effekt"; }
                    case FLY_INTO_WALL -> { killerName = "Aufprall"; weaponName = "Kinetische Energie"; }
                    case SONIC_BOOM -> { killerName = "Warden"; weaponName = "Ultraschall"; }
                    default -> { killerName = formatCauseName(cause.name()); weaponName = "Umgebung"; }
                }
            }
        }

        String deathMessage = null;
        if (event.deathMessage() != null) {
            deathMessage = PlainTextComponentSerializer.plainText().serialize(event.deathMessage());
        }

        if (deathMessage == null || deathMessage.isBlank() || deathMessage.equalsIgnoreCase(victim.getName() + " died")) {
            if (killerName != null && weaponName != null) {
                deathMessage = victim.getName() + " wurde von " + killerName + " (" + weaponName + ") getötet";
            } else if (killerName != null) {
                deathMessage = victim.getName() + " wurde von " + killerName + " getötet";
            } else {
                deathMessage = victim.getName() + " ist gestorben";
            }
        }

        return new DeathDetails(
                deathMessage,
                killerName != null ? killerName : "Unbekannt",
                weaponName != null ? weaponName : "Unbekannt"
        );
    }

    private String formatProjectile(Projectile proj) {
        if (proj instanceof Arrow) return "Pfeil / Bogen";
        if (proj instanceof Trident) return "Dreizack";
        if (proj instanceof ThrownPotion) return "Wurftrank";
        return formatEntityName(proj);
    }

    private String formatEntityName(Entity entity) {
        if (entity == null) return "Unbekannt";
        if (entity.getCustomName() != null && !entity.getCustomName().isBlank()) {
            return entity.getCustomName();
        }
        String name = entity.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private String formatCauseName(String cause) {
        if (cause == null) return "Unbekannt";
        String s = cause.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private record DeathDetails(String deathMessage, String killerName, String weaponName) {}
}
