package de.Z7534.ztone.claims.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class GuiUtil {

    private GuiUtil() {
    }

    public static ItemStack backButton(String modelId) {
        return ItemBuilder.of(Material.ARROW)
                .name(Text.line("<gray>« Zurück"))
                .lore(Text.line("<gray>Linksklick, zurück."))
                .model(modelId)
                .build();
    }

    public static int pageCount(int items, int perPage) {
        return Math.max(1, (items + perPage - 1) / perPage);
    }

    public static int clampPage(int page, int items, int perPage) {
        return Math.max(0, Math.min(page, pageCount(items, perPage) - 1));
    }

    public static ItemStack prevPageButton(int page, int pages, String modelId) {
        return ItemBuilder.of(Material.ARROW)
                .name(Text.line("<yellow>« Vorherige Seite"))
                .lore(Text.line("<gray>Seite <white>" + page + "<gray> von <white>" + pages),
                        Text.line("<gray>Linksklick, zurückblättern."))
                .model(modelId)
                .build();
    }

    public static ItemStack nextPageButton(int page, int pages, String modelId) {
        return ItemBuilder.of(Material.ARROW)
                .name(Text.line("<yellow>Nächste Seite »"))
                .lore(Text.line("<gray>Seite <white>" + page + "<gray> von <white>" + pages),
                        Text.line("<gray>Linksklick, weiterblättern."))
                .model(modelId)
                .build();
    }

    public static String toggleSuffix(boolean enabled) {
        return enabled ? " <green>(An)" : " <red>(Aus)";
    }
}
