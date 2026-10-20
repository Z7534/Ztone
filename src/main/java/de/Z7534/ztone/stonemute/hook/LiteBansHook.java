package de.Z7534.ztone.stonemute.hook;

import de.Z7534.ztone.stonemute.Stonemute;
import de.Z7534.ztone.stonemute.mute.MuteService;
import litebans.api.Entry;
import litebans.api.Events;
import org.bukkit.Bukkit;

/**
 * Reagiert sofort auf neue bzw. aufgehobene LiteBans-Mutes.
 * Mutes von anderen Servern fangen zusaetzlich die periodischen Pruefungen im MuteService auf.
 */
public final class LiteBansHook {

    private final Stonemute plugin;
    private final MuteService muteService;

    private final Events.Listener listener = new Events.Listener() {
        @Override
        public void entryAdded(Entry entry) {
            handle(entry);
        }

        @Override
        public void entryRemoved(Entry entry) {
            handle(entry);
        }
    };

    public LiteBansHook(Stonemute plugin, MuteService muteService) {
        this.plugin = plugin;
        this.muteService = muteService;
    }

    public void register() {
        Events.get().register(listener);
    }

    public void unregister() {
        try {
            Events.get().unregister(listener);
        } catch (Exception ignored) {
            // LiteBans faehrt eventuell gerade selbst herunter
        }
    }

    private void handle(Entry entry) {
        if (!"mute".equals(entry.getType())) {
            return;
        }
        // Events koennen auf einem Async-Thread ankommen -> zurueck auf den Main-Thread
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin.getPlugin(), muteService::refreshAll);
        }
    }
}
