package de.Z7534.ztone.playrewards.menu;

import de.Z7534.ztone.playrewards.PlayReward;
import de.Z7534.ztone.playrewards.model.PlayerData;
import de.Z7534.ztone.playrewards.util.HeadUtil;
import de.Z7534.ztone.playrewards.util.MessageUtil;
import de.Z7534.ztone.playrewards.util.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public class MainRewardMenu implements InventoryHolder {

    public static final String TITLE = "<gradient:#FFAA00:#FF5500><bold>Belohnungs-Zentrum</bold></gradient>";

    // Wunderschöne Player Heads für die Hauptmenü-Kategorien (minecraft-heads.com)
    public static final String HEAD_DAILY = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOThjOGY4MmNjZjhkMmFjYjc5MDA4ZTE3ODM0MDAyZThkYzU2ZTczMmVhODljYzFkYjNiZGU0MjA2NjU4ZWY3ZCJ9fX0="; // Goldenes Geschenk
    public static final String HEAD_STREAK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzFiNzUzNjhkYjlhNzYzMzJkYzZkOWU1YzAwZTIzMmZkNDIzOGNhMWEzMDE5ZTdiNTIxZjE0ZmZjODMzMGU3YiJ9fX0="; // Feuerball / Flamme
    public static final String HEAD_PLAYTIME = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzgxZGNkYjVhMDE3MzhiN2JjYzc0YTNjNWU3ZWQwMThhYjgyMTFkYTJhNTBlMWI2ZWRkNGQwYzgwNzUyNDJkMSJ9fX0="; // Goldene Uhr

    private final PlayReward plugin;
    private final Player player;
    private final Inventory inventory;

    public MainRewardMenu(PlayReward plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, 27, MessageUtil.parse(TITLE));
        buildMenu();
    }

    private void buildMenu() {
        ItemStack filler = MenuItemBuilder.createPane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, filler);
        }

        PlayerData data = plugin.getDatabaseManager().getCachedData(player.getUniqueId());
        long playtime = plugin.getPlaytimeService().getCachedPlaytimeSeconds(player.getUniqueId());
        int availableDaily = plugin.getRewardClaimService().getAvailableDailyRewardsCount(player.getUniqueId());
        int streak = data != null ? data.getCurrentStreak() : 0;
        long hours = TimeUtil.getHours(playtime);
        String formattedDays = TimeUtil.formatDaysAndHours(playtime);

        // Slot 4: Spieler-Kopf mit Gesamt-Übersicht (Stunden & formatierte Tage)
        ItemStack profileHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta headMeta = (SkullMeta) profileHead.getItemMeta();
        if (headMeta != null) {
            headMeta.setOwningPlayer(player);
            headMeta.displayName(MessageUtil.parse("<gradient:#33AAFF:#0055FF><bold>Deine Spielzeit & Statistiken</bold></gradient>"));
            List<Component> lore = new ArrayList<>();
            lore.add(MessageUtil.parse("<gray>Spieler: <yellow><bold>" + player.getName() + "</bold></yellow>"));
            lore.add(Component.empty());
            lore.add(MessageUtil.parse("<gray>Aktuelle Spielzeit: <gold><bold>" + hours + " Stunden</bold></gold>"));
            lore.add(MessageUtil.parse("<gray>Formatiert in Tage: <aqua><bold>" + formattedDays + "</bold></aqua>"));
            lore.add(Component.empty());
            lore.add(MessageUtil.parse("<gray>Aktuelle Serie: <green><bold>" + streak + " Tage Streak</bold></green>"));
            lore.add(MessageUtil.parse("<gray>Offene Tagesbelohnungen: <yellow><bold>" + availableDaily + "</bold></yellow>"));
            headMeta.lore(lore);
            headMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            profileHead.setItemMeta(headMeta);
        }
        inventory.setItem(4, profileHead);

        // Slot 11: Daily Rewards (Wunderschöner Geschenk-Kopf)
        ItemStack dailyItem = HeadUtil.createHeadFromBase64(HEAD_DAILY);
        ItemMeta dailyMeta = dailyItem.getItemMeta();
        if (dailyMeta != null) {
            dailyMeta.displayName(MessageUtil.parse("<gold><bold>Tägliche Belohnungen</bold></gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(MessageUtil.parse("<gray>Hole dir jeden Tag tolle Geschenke ab!"));
            lore.add(MessageUtil.parse("<gray>Automatischer Reset: <yellow>00:00 Uhr</yellow>"));
            lore.add(Component.empty());
            if (availableDaily > 0) {
                lore.add(MessageUtil.parse("<green><bold>" + availableDaily + " Belohnung(en) abholbereit!</bold></green>"));
            } else {
                lore.add(MessageUtil.parse("<gray>Nächster Reset in: <yellow>" + TimeUtil.getTimeUntilMidnight() + "</yellow>"));
            }
            lore.add(Component.empty());
            lore.add(MessageUtil.parse("<yellow>➔ Klicke zum Öffnen"));
            dailyMeta.lore(lore);
            dailyMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            dailyItem.setItemMeta(dailyMeta);
        }
        inventory.setItem(11, dailyItem);

        // Slot 15: Playtime Rewards (Wunderschöner goldener Uhr-Kopf)
        ItemStack playtimeItem = HeadUtil.createHeadFromBase64(HEAD_PLAYTIME);
        ItemMeta playtimeMeta = playtimeItem.getItemMeta();
        if (playtimeMeta != null) {
            playtimeMeta.displayName(MessageUtil.parse("<aqua><bold>Spielzeit-Belohnungen</bold></aqua>"));
            List<Component> lore = new ArrayList<>();
            lore.add(MessageUtil.parse("<gray>Erreiche Meilensteine durch deine"));
            lore.add(MessageUtil.parse("<gray>reine, aktive Spielzeit auf dem Server."));
            lore.add(Component.empty());
            lore.add(MessageUtil.parse("<gray>Spielzeit in Stunden: <gold><bold>" + hours + " Stunden</bold></gold>"));
            lore.add(MessageUtil.parse("<gray>Formatiert in Tage: <aqua><bold>" + formattedDays + "</bold></aqua>"));
            lore.add(Component.empty());
            lore.add(MessageUtil.parse("<yellow>➔ Klicke zum Belohnungspfad"));
            playtimeMeta.lore(lore);
            playtimeMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            playtimeItem.setItemMeta(playtimeMeta);
        }
        inventory.setItem(15, playtimeItem);

        // Slot 22: Streak Rewards (Einen Slot nach unten gesetzt, zentriert in Zeile 3 mit Feuerball-Kopf)
        ItemStack streakItem = HeadUtil.createHeadFromBase64(HEAD_STREAK);
        ItemMeta streakMeta = streakItem.getItemMeta();
        if (streakMeta != null) {
            streakMeta.displayName(MessageUtil.parse("<red><bold>Tägliche Streak</bold></red>"));
            List<Component> lore = new ArrayList<>();
            lore.add(MessageUtil.parse("<gray>Logge dich täglich ein, um deine"));
            lore.add(MessageUtil.parse("<gray>Serie nicht zu verlieren!"));
            lore.add(Component.empty());
            lore.add(MessageUtil.parse("<gray>Aktuelle Streak: <gold><bold>" + streak + " Tage</bold></gold>"));
            if (player.hasPermission("playrewards.streak.saver")) {
                int saver = data != null ? data.getProtectionPointsLeft() : 0;
                lore.add(MessageUtil.parse("<gray>Streak-Saver Schutzpunkte: <aqua>" + saver + "/1</aqua>"));
            }
            lore.add(Component.empty());
            lore.add(MessageUtil.parse("<yellow>➔ Klicke zum Öffnen"));
            streakMeta.lore(lore);
            streakMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            streakItem.setItemMeta(streakMeta);
        }
        inventory.setItem(22, streakItem);
    }

    public void open() {
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
