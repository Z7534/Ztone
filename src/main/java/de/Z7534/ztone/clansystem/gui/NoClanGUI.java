package de.Z7534.ztone.clansystem.gui;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.managers.MessageManager;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.utils.ColorUtils;
import de.Z7534.ztone.clansystem.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class NoClanGUI extends AbstractGUI {

    public NoClanGUI(Ztone plugin, Player player) {
        super(plugin, player, "&8&l✦ &6Willkommen im Ztone", 27);
    }

    @Override
    public void setup() {
        fillFancyBorder();

        setItem(11, new ItemBuilder(Material.NETHER_STAR)
                .name("&a&l➤ Clan erstellen")
                .lore(
                        "&7Du bist noch in keinem Clan.",
                        "&7Gründe jetzt deinen eigenen!",
                        "",
                        "&7Du wirst automatisch",
                        "&7Eigentümer (Leader).",
                        "",
                        "&a▶ &7Klicke, um den Namen einzugeben"
                )
                .glow()
                .build(), this::startCreateFlow);

        setItem(15, new ItemBuilder(Material.BOOK)
                .name("&e&l➤ Clans durchsuchen")
                .lore(
                        "&7Sieh dir alle Clans des",
                        "&7Servers an und tritt einem",
                        "&7offenen Clan bei oder",
                        "&7bewirb dich bei einem.",
                        "",
                        "&a▶ &7Klicke zum Öffnen"
                )
                .build(), () -> new ClanListGUI(plugin, player).open());

        addCloseButton(22);

        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }

    private void startCreateFlow() {
        if (!player.hasPermission("clansystem.create")) {
            player.closeInventory();
            plugin.getMessageManager().send(player, "general.no-permission");
            return;
        }

        if (!player.hasPermission("clansystem.bypass.cooldown")) {
            long cooldownExpires = plugin.getDatabaseManager().getCooldown(player.getUniqueId(), "CREATE");
            if (cooldownExpires > System.currentTimeMillis()) {
                long remaining = (cooldownExpires - System.currentTimeMillis()) / 1000;
                player.closeInventory();
                plugin.getMessageManager().send(player, "clan.create-cooldown",
                        MessageManager.of("time", MessageManager.formatTime(remaining)));
                return;
            }
        }

        plugin.getChatInputManager().request(player,
                "Bitte gib den gewünschten Clan-Namen im Chat ein:",
                this::handleNameInput);
    }

    private void handleNameInput(String name) {

        if (plugin.getClanManager().isInClan(player)) {
            plugin.getMessageManager().send(player, "clan.already-in-clan");
            return;
        }

        if (!plugin.getClanManager().isValidClanName(name)) {
            if (name.length() < plugin.getConfigManager().getClanNameMinLength()) {
                plugin.getMessageManager().send(player, "clan.name-too-short",
                        MessageManager.of("count", String.valueOf(plugin.getConfigManager().getClanNameMinLength())));
            } else if (name.length() > plugin.getConfigManager().getClanNameMaxLength()) {
                plugin.getMessageManager().send(player, "clan.name-too-long",
                        MessageManager.of("count", String.valueOf(plugin.getConfigManager().getClanNameMaxLength())));
            } else {
                plugin.getMessageManager().send(player, "clan.name-invalid");
            }

            plugin.getChatInputManager().request(player,
                    "Bitte gib den gewünschten Clan-Namen im Chat ein:",
                    this::handleNameInput);
            return;
        }

        if (plugin.getClanManager().clanExists(name)) {
            plugin.getMessageManager().send(player, "clan.name-taken");
            plugin.getChatInputManager().request(player,
                    "Bitte gib den gewünschten Clan-Namen im Chat ein:",
                    this::handleNameInput);
            return;
        }

        Clan clan = plugin.getClanManager().createClan(name, player);
        if (clan == null) {
            player.sendMessage(ColorUtils.colorize("&cDer Clan konnte nicht erstellt werden."));
            return;
        }

        plugin.getMessageManager().send(player, "clan.created",
                MessageManager.of("clan", clan.getColoredName()));

        if (plugin.getConfigManager().isBroadcastClanCreated()) {
            plugin.getMessageManager().broadcastExcept("broadcast.clan-created", player,
                    MessageManager.of("clan", clan.getColoredName(), "player", player.getName()));
        }

        new ClanMainGUI(plugin, player, clan).open();
    }
}
