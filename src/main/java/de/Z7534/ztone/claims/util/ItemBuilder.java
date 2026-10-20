package de.Z7534.ztone.claims.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ItemBuilder {

    private final ItemStack stack;
    private Component name;
    private final List<Component> lore = new ArrayList<>();
    private Boolean glintOverride;

    private ItemBuilder(Material material) {
        this.stack = new ItemStack(material);
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material);
    }

    public ItemBuilder name(Component name) {
        this.name = Text.noItalic(name);
        return this;
    }

    public ItemBuilder lore(Component... lines) {
        for (Component c : lines) {
            lore.add(Text.noItalic(c));
        }
        return this;
    }

    public ItemBuilder lore(List<Component> lines) {
        for (Component c : lines) {
            lore.add(Text.noItalic(c));
        }
        return this;
    }

    public ItemBuilder emptyLoreLine() {
        lore.add(Component.empty());
        return this;
    }

    public ItemBuilder amount(int amount) {
        stack.setAmount(Math.max(1, amount));
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        this.glintOverride = glow;
        return this;
    }

    public ItemBuilder model(String modelId) {
        if (modelId != null && !modelId.isBlank()) {
            NamespacedKey key = NamespacedKey.fromString(modelId.toLowerCase());
            if (key != null) {
                ItemMeta meta = stack.getItemMeta();
                meta.setItemModel(key);
                stack.setItemMeta(meta);
            }
        }
        return this;
    }

    public ItemStack build() {
        ItemMeta meta = stack.getItemMeta();
        if (name != null) {
            meta.displayName(name);
        }
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        if (glintOverride != null) {
            meta.setEnchantmentGlintOverride(glintOverride);
        }
        meta.addItemFlags(ItemFlag.values());
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack playerHead(UUID uuid, String name) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(org.bukkit.Bukkit.getOfflinePlayer(uuid));
        meta.displayName(Text.noItalic(Component.text(name)));
        meta.addItemFlags(ItemFlag.values());
        head.setItemMeta(meta);
        return head;
    }
}
