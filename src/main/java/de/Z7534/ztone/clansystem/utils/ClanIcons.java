package de.Z7534.ztone.clansystem.utils;

import org.bukkit.Material;

public class ClanIcons {

    public static Material fromName(String name) {
        if (name == null) {
            return Material.SHIELD;
        }
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Material.SHIELD;
        }
    }
}
