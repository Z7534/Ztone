package de.Z7534.ztone.pvpmanager.hooks;

import org.bukkit.Bukkit;

public class ProtocolLibHook {

    private final boolean available;

    public ProtocolLibHook() {
        boolean present;
        try {
            present = Bukkit.getPluginManager().getPlugin("ProtocolLib") != null
                    && Class.forName("com.comphenix.protocol.ProtocolLibrary") != null;
        } catch (ClassNotFoundException exception) {
            present = false;
        }
        this.available = present;
    }

    public boolean isAvailable() {
        return available;
    }
}
