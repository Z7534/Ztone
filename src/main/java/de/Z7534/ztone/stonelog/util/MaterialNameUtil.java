package de.Z7534.ztone.stonelog.util;

import org.bukkit.Material;

public class MaterialNameUtil {

    public static String prettify(Material material) {
        if (material == null || material == Material.AIR) {
            return "Faust";
        }
        String name = material.name().toLowerCase().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String part : name.split(" ")) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}
