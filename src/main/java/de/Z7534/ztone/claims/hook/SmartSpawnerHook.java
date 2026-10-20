package de.Z7534.ztone.claims.hook;

import de.Z7534.ztone.claims.config.ConfigManager;
import de.Z7534.ztone.claims.manager.BypassManager;
import de.Z7534.ztone.claims.manager.ClaimManager;
import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.claims.model.ClaimPermission;
import de.Z7534.ztone.claims.model.PlayerData;
import de.Z7534.ztone.claims.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Dynamischer Hook fuer SmartSpawner (https://docs.smartspawner.site/developer-api/events).
 * <p>
 * Blockiert saemtliche Spawner-Aktionen (GUI oeffnen, Stacking, Abholen von EXP/Drops,
 * Platzieren, Abbauen etc.) auf fremden Claims, wenn der Spieler nicht ueber die
 * Berechtigung {@link ClaimPermission#SPAWNER} verfuegt.
 */
public final class SmartSpawnerHook implements Listener {

    private final Plugin plugin;
    private final ClaimManager claimManager;
    private final ConfigManager config;
    private final BypassManager bypass;

    private static final String[] EVENT_CLASSES = {
            "github.nighter.smartspawner.api.events.SpawnerOpenGUIEvent",
            "github.nighter.smartspawner.api.events.SpawnerPlaceEvent",
            "github.nighter.smartspawner.api.events.SpawnerPlayerBreakEvent",
            "github.nighter.smartspawner.api.events.SpawnerBreakEvent",
            "github.nighter.smartspawner.api.events.SpawnerStackEvent",
            "github.nighter.smartspawner.api.events.SpawnerSellEvent",
            "github.nighter.smartspawner.api.events.SpawnerExpClaimEvent",
            "github.nighter.smartspawner.api.events.SpawnerEggChangeEvent",
            "github.nighter.smartspawner.api.events.SpawnerRemoveEvent",
            "github.nighter.smartspawner.api.events.SpawnerDropAllEvent",
            "github.nighter.smartspawner.api.events.SpawnerTakeAllEvent",
            "github.nighter.smartspawner.api.events.SpawnerExplodeEvent"
    };

    public SmartSpawnerHook(Plugin plugin, ClaimManager claimManager, ConfigManager config, BypassManager bypass) {
        this.plugin = plugin;
        this.claimManager = claimManager;
        this.config = config;
        this.bypass = bypass;
    }

    @SuppressWarnings("unchecked")
    public boolean register() {
        PluginManager pm = Bukkit.getPluginManager();
        int registeredCount = 0;

        for (String className : EVENT_CLASSES) {
            try {
                Class<?> clazz = Class.forName(className);
                if (Event.class.isAssignableFrom(clazz)) {
                    Class<? extends Event> eventClass = (Class<? extends Event>) clazz;
                    pm.registerEvent(eventClass, this, EventPriority.HIGH, (l, event) -> handleEvent(event), plugin, true);
                    registeredCount++;
                }
            } catch (ClassNotFoundException ignored) {
                // SmartSpawner nicht installiert oder Klasse existiert in dieser Version nicht
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Fehler beim Registrieren von " + className + ": " + e.getMessage());
            }
        }

        if (registeredCount > 0) {
            plugin.getLogger().info("SmartSpawner-Hook erfolgreich aktiviert (" + registeredCount + " Events registriert).");
            return true;
        }
        return false;
    }

    private void handleEvent(Event event) {
        try {
            Player player = resolvePlayer(event);
            Location loc = resolveLocation(event);

            if (player != null) {
                if (loc == null) {
                    loc = player.getLocation();
                }
                Claim claim = claimManager.getClaimAt(loc);
                if (claim == null) {
                    return;
                }
                if (bypass.isActive(player)) {
                    return;
                }
                if (claim.isBanned(player.getUniqueId()) || !claim.hasPermission(player.getUniqueId(), ClaimPermission.SPAWNER)) {
                    cancel(event);
                    player.sendMessage(Text.line(config.prefix() + config.message("action-denied")));
                }
                return;
            }

            // Kein Spieler (z. B. SpawnerExplodeEvent)
            if (loc != null) {
                Claim claim = claimManager.getClaimAt(loc);
                if (claim != null) {
                    boolean allowsExplosions = claimManager.findPlayerData(claim.owner())
                            .map(PlayerData::explosionsEnabled)
                            .orElse(false);
                    if (!allowsExplosions) {
                        cancel(event);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Fehler in SmartSpawnerHook", e);
        }
    }

    private Player resolvePlayer(Event event) {
        try {
            Method m = event.getClass().getMethod("getPlayer");
            Object res = m.invoke(event);
            if (res instanceof Player p) return p;
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            return null;
        }

        try {
            Method m = event.getClass().getMethod("getEntity");
            Object res = m.invoke(event);
            if (res instanceof Player p) return p;
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            return null;
        }

        return null;
    }

    private Location resolveLocation(Event event) {
        try {
            Method m = event.getClass().getMethod("getLocation");
            Object res = m.invoke(event);
            if (res instanceof Location l) return l;
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private void cancel(Event event) {
        if (event instanceof Cancellable cancellable) {
            cancellable.setCancelled(true);
            return;
        }
        try {
            Method m = event.getClass().getMethod("setCancelled", boolean.class);
            m.invoke(event, true);
        } catch (Exception ignored) {
        }
    }
}
