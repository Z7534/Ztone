package de.Z7534.ztone.stoneattack.listeners.playerlimit;

import de.Z7534.ztone.stoneattack.Stoneattack;
import de.Z7534.ztone.stoneattack.modules.playerlimit.PlayerLimitModule;
import de.Z7534.ztone.stoneattack.util.ColorUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

/**
 * Die eigentliche Durchsetzung des Limits passiert bewusst im
 * PlayerLoginEvent (nicht PlayerJoinEvent): Nur hier kann der Join
 * noch sauber mit einem konfigurierbaren Grund abgelehnt werden,
 * BEVOR der Spieler tatsaechlich in die Welt geladen wird, und die
 * Bypass-Permission ist an dieser Stelle bereits verfuegbar.
 */
public class PlayerLoginListener implements Listener {

    private final Stoneattack plugin;

    public PlayerLoginListener(Stoneattack plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onLogin(PlayerLoginEvent event) {
        PlayerLimitModule module = plugin.getPlayerLimitModule();
        if (module == null || !module.isEnabled()) {
            // Modul aus oder Limit deaktiviert (-1) -> Vanilla-Verhalten
            // fuer den Join-full-Fall, aber wir tragen trotzdem in die
            // Join-Reihenfolge ein, falls das Modul aktiv ist.
            if (module != null && event.getResult() == PlayerLoginEvent.Result.ALLOWED) {
                module.canJoin(event.getPlayer());
            }
            return;
        }

        boolean allowed = module.canJoin(event.getPlayer());
        if (!allowed) {
            String message = plugin.getMessagesManager().raw("playerlimit.join-full-message")
                    .replace("%limit%", String.valueOf(module.getLimit()));
            event.disallow(PlayerLoginEvent.Result.KICK_FULL, ColorUtil.parse(message));
            return;
        }

        // Erlaubt (Bypass ODER unter dem Limit) -> vorherige eventuelle
        // Vanilla-"voll"-Entscheidung ausdruecklich ueberschreiben,
        // damit der Bypass auch bei technisch vollem Server funktioniert.
        event.allow();

        if (!event.getPlayer().hasPermission("playerlimit.bypass")
                && plugin.getConfigManager().get().getBoolean("playerlimit.join-order-logging.enabled", true)) {
            int position = module.positionOf(event.getPlayer());
            module.getLogger().logJoin(event.getPlayer().getUniqueId(), event.getPlayer().getName(), position);
        }
    }
}
