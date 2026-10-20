package de.Z7534.ztone.playrewards.menu;

import de.Z7534.ztone.playrewards.model.RewardItem;
import de.Z7534.ztone.playrewards.model.RewardStatus;
import de.Z7534.ztone.playrewards.util.HeadUtil;
import de.Z7534.ztone.playrewards.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class MenuItemBuilder {

    private MenuItemBuilder() {}

    // =========================================================================
    // Einheitliche Player Head Texturen (minecraft-heads.com) für Belohnungen
    // =========================================================================

    // Bereits eingelöste Belohnung (Moderner leuchtend-grüner Haken / Check Mark)
    public static final String HEAD_CLAIMED = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTc5YTVjOTVlZTE3YWJmZWY0NWM4ZGMyMjQxODk5NjQ5NDRkNTYwZjE5YTQ0ZjE5ZjhhNDZhZWYzZmVlNDc1NiJ9fX0=";

    // Standard-Kopf für normale Belohnungen: Wunderschönes rotes Geschenkpaket mit Goldschleife (ID: 49163)
    public static final String HEAD_BASIC = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTcyNmQ5ZDA2MzJlNDBiZGE1YmNmNjU4MzliYTJjYzk4YTg3YmQ2MTljNTNhZGYwMDMxMGQ2ZmM3MWYwNDJiNSJ9fX0=";

    // Kopf für besondere Belohnungen (Meilensteine): Glänzender Goldpokal / Trophy Cup (ID: 42035)
    public static final String HEAD_SPECIAL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjk3ZTJjOGI4Mjc2Mjc2ZTM4ZGVjYTg4NTA4NzJkNTllY2M5YzFmMzhmMmFkY2U0MDg1OGVkYjllNjM0ZDdiYSJ9fX0=";

    // Kopf für besonders besondere Belohnungen (Epischer Abschluss / Finale): Majestätische Königskrone (ID: 16815)
    public static final String HEAD_EPIC = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjI2ZTM4ZDhjMWRhOGQ0NTFkNzE5NGYzYTdmYWQ5MGExNDFkZmU2MTY1YjY2NjdmN2E4Y2JhMDkxOTEzOSJ9fX0=";

    // =========================================================================
    // Schlangenlinien-Slot-Reihenfolge (19 innere Slots pro Seite):
    // Oben links starten -> straight runter -> 2 nach rechts -> straight hoch ->
    // 2 nach rechts -> straight runter -> 2 nach rechts -> straight hoch zum Ende
    // =========================================================================
    public static final int[] SNAKE_SLOTS = {
            10, 19, 28, 37, // Spalte 1: straight ganz runter
            38, 39,         // 2 nach rechts (zu Spalte 3)
            30, 21, 12,     // Spalte 3: straight wieder ganz nach oben
            13, 14,         // 2 nach rechts (zu Spalte 5)
            23, 32, 41,     // Spalte 5: straight wieder ganz nach unten
            42, 43,         // 2 nach rechts (zu Spalte 7)
            34, 25, 16      // Spalte 7: straight wieder ganz nach oben zum Ende
    };

    public static final int ITEMS_PER_PAGE = SNAKE_SLOTS.length; // 19

    /**
     * Füllt das gesamte 54er Menü mit Glasscheiben:
     * - Alle freien Slots werden mit dezentem grauem Glas (GRAY_STAINED_GLASS_PANE) gefüllt.
     * - Der äußere Rahmen wird mit der spezifischen Themen-Glasfarbe (glassMat) hervorgehoben.
     * Somit gibt es nirgends leere Luftlöcher.
     */
    public static void fillFrame(Inventory inventory, Material glassMat) {
        ItemStack fillerPane = createPane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, fillerPane);
        }

        ItemStack framePane = createPane(glassMat != null ? glassMat : Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, framePane);
            inventory.setItem(45 + i, framePane);
        }
        int[] sideSlots = {9, 17, 18, 26, 27, 35, 36, 44};
        for (int slot : sideSlots) {
            inventory.setItem(slot, framePane);
        }
    }

    /**
     * Platziert die Navigations- und Zurück-Buttons in der unteren Leiste eines 54er Menüs.
     */
    public static void setNavigationButtons(Inventory inventory, int page, int maxPage) {
        if (page > 1) {
            inventory.setItem(48, createNavigationButton("<yellow>◀ Vorherige Seite (" + (page - 1) + ")", false));
        }
        inventory.setItem(49, createBackToMainMenuButton());
        if (page < maxPage) {
            inventory.setItem(50, createNavigationButton("<yellow>Nächste Seite (" + (page + 1) + ") ▶", true));
        }
    }

    /**
     * Erstellt eine Glas-Scheibe für den GUI-Rahmen bzw. Hintergrund.
     */
    public static ItemStack createPane(Material material) {
        ItemStack pane = new ItemStack(material != null ? material : Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            pane.setItemMeta(meta);
        }
        return pane;
    }

    /**
     * Erstellt das Belohnungs-Item für ein GUI inklusive Status-Köpfen, Glint und Lore.
     */
    public static ItemStack buildRewardItem(RewardItem item, RewardStatus status) {
        ItemStack stack;
        if (status == RewardStatus.CLAIMED) {
            // Bereits eingelöst: Immer der einheitliche Abgehakt-Kopf (Grüner Haken)!
            stack = HeadUtil.createHeadFromBase64(HEAD_CLAIMED);
        } else if (item.hasCustomHeadTexture()) {
            String texture = item.getCustomHeadTexture();
            // Automatische Aufwertung veralteter/hässlicher Texturen auf die modernen Köpfe
            if (texture.contains("d5c6dc2bbf51c36cfc7714585a6a5683ef2b14d47d8ff714654a893f5da622") ||
                texture.contains("6f68d509b5d1669b971dd1d4df2e47e19bcb1b33bf1a7ff1dda29bfc6f9ebf")) {
                stack = HeadUtil.createHeadFromBase64(HEAD_BASIC);
            } else if (texture.contains("9169e85a8a4fca5c595a9d293f5ddf1068dc6db99f0700dd4d5b673865501a67")) {
                stack = HeadUtil.createHeadFromBase64(HEAD_SPECIAL);
            } else if (texture.contains("45587da7fe7336e8ab9f791ea5e2cfc8a827ca959567eb9d53a647babf948d5")) {
                stack = HeadUtil.createHeadFromBase64(HEAD_EPIC);
            } else {
                stack = HeadUtil.createHeadFromBase64(texture);
            }
        } else {
            stack = HeadUtil.createHeadFromBase64(HEAD_BASIC);
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageUtil.parse(item.getName()));

            List<Component> lore = new ArrayList<>();
            for (String line : item.getLorePreview()) {
                lore.add(MessageUtil.parse(line));
            }
            lore.add(Component.empty());

            // Status-Anzeige
            switch (status) {
                case AVAILABLE:
                    lore.add(MessageUtil.parse("<green><bold>✔ BEREIT ZUM ABHOLEN!</bold></green>"));
                    lore.add(MessageUtil.parse("<yellow>➔ Klicke, um deine Belohnung einzulösen!"));
                    // Optisches Leuchten (Enchantment-Glint)
                    meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    break;
                case CLAIMED:
                    lore.add(MessageUtil.parse("<gray><bold>✔ BEREITS EINGESAMMELT</bold></gray>"));
                    lore.add(MessageUtil.parse("<dark_gray>Du hast diese Belohnung bereits erhalten."));
                    break;
                case LOCKED:
                default:
                    lore.add(MessageUtil.parse("<red><bold>✖ NOCH NICHT ERREICHT</bold></red>"));
                    lore.add(MessageUtil.parse("<gray>Sammle mehr aktive Spielzeit!"));
                    break;
            }

            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * Erstellt einen Navigations-Button (Pfeil).
     */
    public static ItemStack createNavigationButton(String name, boolean isNext) {
        ItemStack item = new ItemStack(isNext ? Material.ARROW : Material.SPECTRAL_ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageUtil.parse(name));
            List<Component> lore = new ArrayList<>();
            lore.add(MessageUtil.parse("<gray>Klicke zum Blättern"));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Erstellt den 'Zurück zum Hauptmenü'-Button.
     */
    public static ItemStack createBackToMainMenuButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageUtil.parse("<red><bold>Zurück zum Hauptmenü</bold></red>"));
            List<Component> lore = new ArrayList<>();
            lore.add(MessageUtil.parse("<gray>Klicke, um zur Übersicht zurückzukehren."));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }
}
