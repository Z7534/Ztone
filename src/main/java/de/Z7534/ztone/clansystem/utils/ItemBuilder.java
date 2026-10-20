package de.Z7534.ztone.clansystem.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder(Material material, int amount) {
        this.item = new ItemStack(material, amount);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder(ItemStack item) {
        this.item = item.clone();
        this.meta = this.item.getItemMeta();
    }

    public ItemBuilder name(String name) {
        meta.displayName(ColorUtils.colorize(name));
        return this;
    }

    public ItemBuilder name(Component name) {
        meta.displayName(name);
        return this;
    }

    public ItemBuilder lore(String... lines) {
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(ColorUtils.colorize(line));
        }
        meta.lore(lore);
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(ColorUtils.colorize(line));
        }
        meta.lore(lore);
        return this;
    }

    public ItemBuilder addLore(String line) {
        List<Component> lore = meta.lore();
        if (lore == null) {
            lore = new ArrayList<>();
        }
        lore.add(ColorUtils.colorize(line));
        meta.lore(lore);
        return this;
    }

    public ItemBuilder addLore(String... lines) {
        List<Component> lore = meta.lore();
        if (lore == null) {
            lore = new ArrayList<>();
        }
        for (String line : lines) {
            lore.add(ColorUtils.colorize(line));
        }
        meta.lore(lore);
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    public ItemBuilder enchant(Enchantment enchantment, int level) {
        meta.addEnchant(enchantment, level, true);
        return this;
    }

    public ItemBuilder glow() {
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        if (glow) {
            return glow();
        }
        return this;
    }

    public ItemBuilder hideFlags() {
        meta.addItemFlags(ItemFlag.values());
        return this;
    }

    public ItemBuilder addFlag(ItemFlag... flags) {
        meta.addItemFlags(flags);
        return this;
    }

    public ItemBuilder unbreakable() {
        meta.setUnbreakable(true);
        return this;
    }

    public ItemBuilder skullOwner(UUID uuid) {
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(org.bukkit.Bukkit.getOfflinePlayer(uuid));
        }
        return this;
    }

    public ItemBuilder skullOwner(org.bukkit.OfflinePlayer offlinePlayer) {
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(offlinePlayer);
        }
        return this;
    }

    public ItemBuilder customHead(String base64Texture) {
        if (meta instanceof SkullMeta skullMeta) {
            try {

                com.destroystokyo.paper.profile.PlayerProfile profile =
                        (com.destroystokyo.paper.profile.PlayerProfile) org.bukkit.Bukkit.createProfile(UUID.randomUUID());
                profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", base64Texture));
                skullMeta.setPlayerProfile(profile);
            } catch (Exception ignored) {

            }
        }
        return this;
    }

    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack placeholder() {
        return placeholder(Material.GRAY_STAINED_GLASS_PANE);
    }

    public static ItemStack placeholder(Material material) {
        return new ItemBuilder(material)
                .name(" ")
                .build();
    }

    public static ItemStack back() {
        return new ItemBuilder(Material.ARROW)
                .name("&f« &7Zurück")
                .lore("&8Klicke um zurückzugehen")
                .build();
    }

    public static ItemStack close() {
        return new ItemBuilder(Material.BARRIER)
                .name("&c&lSchließen")
                .lore("&8Klicke um zu schließen")
                .build();
    }

    public static ItemStack nextPage() {
        return new ItemBuilder(Material.ARROW)
                .name("&a&lNächste Seite &f»")
                .build();
    }

    public static ItemStack previousPage() {
        return new ItemBuilder(Material.ARROW)
                .name("&f« &a&lVorherige Seite")
                .build();
    }

    public static ItemStack confirm() {
        return new ItemBuilder(Material.LIME_DYE)
                .name("&a&l✔ Bestätigen")
                .lore("&8Klicke um zu bestätigen")
                .glow()
                .build();
    }

    public static ItemStack cancel() {
        return new ItemBuilder(Material.RED_DYE)
                .name("&c&l✘ Abbrechen")
                .lore("&8Klicke um abzubrechen")
                .build();
    }

    public static String progressBar(long current, long max, int length, String filledColor, String emptyColor) {
        if (max <= 0) max = 1;
        double ratio = Math.max(0, Math.min(1.0, (double) current / max));
        int filled = (int) Math.round(ratio * length);

        StringBuilder bar = new StringBuilder();
        bar.append(filledColor);
        for (int i = 0; i < length; i++) {
            if (i == filled) {
                bar.append(emptyColor);
            }
            bar.append("■");
        }
        return bar.toString();
    }

    public static String progressBar(long current, long max) {
        return progressBar(current, max, 20, "&6", "&8");
    }
}
