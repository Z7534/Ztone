package de.Z7534.ztone.pvpmanager.hooks;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import org.bukkit.Bukkit;

public class ThirdPartyHook {

    private final Pvpmanager plugin;
    private final String pluginName;

    public ThirdPartyHook(Pvpmanager plugin, String pluginName) {
        this.plugin = plugin;
        this.pluginName = pluginName;
    }

    public boolean isPresent() {
        return Bukkit.getPluginManager().getPlugin(pluginName) != null;
    }

    public String getPluginName() {
        return pluginName;
    }

    public boolean configFlag(String path) {
        return plugin.getConfigManager().hooks().getBoolean("Plugin Hooks." + pluginName + "." + path, false);
    }
}
