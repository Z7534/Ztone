package de.Z7534.ztone.clansystem.gui;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.managers.EconomyManager;
import de.Z7534.ztone.clansystem.managers.MessageManager;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.models.ClanBankTransaction;
import de.Z7534.ztone.clansystem.models.ClanMember;
import de.Z7534.ztone.clansystem.models.Permission;
import de.Z7534.ztone.clansystem.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class ClanBankTransactionsGUI extends AbstractGUI {

    private final Clan clan;
    private final ClanMember member;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    public ClanBankTransactionsGUI(Ztone plugin, Player player, Clan clan) {
        super(plugin, player, plugin.getMessageManager().getRaw("gui.bank-transactions"), 54);
        this.clan = clan;
        this.member = clan.getMember(player.getUniqueId());
    }

    @Override
    public void setup() {
        fillFancyBorder();

        EconomyManager eco = plugin.getEconomyManager();
        String formattedBalance = eco != null ? eco.format(clan.getBalance()) : (clan.getBalance() + " $");

        setItem(4, new ItemBuilder(Material.GOLD_BLOCK)
                .name("&6&l✦ Clankasse ✦")
                .lore(
                        "&7Aktuelles Guthaben: &e" + formattedBalance,
                        "",
                        "&7Hier siehst du die letzten",
                        "&7Transaktionen des Clans."
                )
                .glow()
                .build());

        List<ClanBankTransaction> transactions = plugin.getDatabaseManager().loadBankTransactions(clan.getId(), 21);
        if (transactions.isEmpty()) {
            setItem(22, new ItemBuilder(Material.BARRIER)
                    .name("&cKeine Transaktionen")
                    .lore("&7Es wurden noch keine Ein- oder Auszahlungen getätigt.")
                    .build());
        } else {
            int slot = 10;
            for (ClanBankTransaction tx : transactions) {
                while (slot % 9 == 0 || slot % 9 == 8) {
                    slot++;
                }
                if (slot >= 35) break;

                boolean isDeposit = tx.getType() == ClanBankTransaction.Type.DEPOSIT;
                Material mat = isDeposit ? Material.LIME_DYE : Material.RED_DYE;
                String sign = isDeposit ? "&a+" : "&c-";
                String typeName = isDeposit ? "&aEinzahlung" : "&cAuszahlung";
                String formattedAmount = eco != null ? eco.format(tx.getAmount()) : (tx.getAmount() + " $");

                setItem(slot, new ItemBuilder(mat)
                        .name(sign + " " + formattedAmount + " &8(" + typeName + "&8)")
                        .lore(
                                "&7Spieler: &f" + tx.getPlayerName(),
                                "&7Datum: &e" + dateFormat.format(new Date(tx.getTimestamp()))
                        )
                        .build());
                slot++;
            }
        }

        boolean canDeposit = member != null && (clan.isLeader(player.getUniqueId()) || member.hasPermission(Permission.BANK_DEPOSIT));
        boolean canWithdraw = member != null && (clan.isLeader(player.getUniqueId()) || member.hasPermission(Permission.BANK_WITHDRAW));

        // Deposit button
        setItem(47, new ItemBuilder(Material.EMERALD)
                .name("&a&l✦ Geld Einzahlen")
                .lore(
                        "&7Zahle Geld von deinem Konto",
                        "&7in die Clankasse ein.",
                        "",
                        canDeposit ? "&a▶ &7Klicke zum Einzahlen" : "&c✖ Keine Berechtigung"
                )
                .build(), () -> {
            if (!canDeposit) {
                plugin.getMessageManager().send(player, "general.no-permission");
                return;
            }
            if (eco == null || !eco.isAvailable()) {
                plugin.getMessageManager().send(player, "bank.not-available");
                return;
            }

            plugin.getChatInputManager().request(player, "Gib den Betrag ein, den du einzahlen möchtest:", input -> {
                double amount;
                try {
                    amount = Double.parseDouble(input.replace(",", "."));
                } catch (NumberFormatException e) {
                    plugin.getMessageManager().send(player, "bank.invalid-amount");
                    return;
                }

                if (amount <= 0) {
                    plugin.getMessageManager().send(player, "bank.invalid-amount");
                    return;
                }

                if (!eco.hasBalance(player, amount)) {
                    plugin.getMessageManager().send(player, "bank.insufficient-player-funds");
                    return;
                }

                if (!eco.withdrawPlayer(player, amount)) {
                    plugin.getMessageManager().send(player, "bank.error");
                    return;
                }

                clan.deposit(amount);
                plugin.getDatabaseManager().updateClan(clan);
                ClanBankTransaction tx = new ClanBankTransaction(
                        clan.getId(),
                        player.getUniqueId(),
                        player.getName(),
                        ClanBankTransaction.Type.DEPOSIT,
                        amount,
                        System.currentTimeMillis()
                );
                plugin.getDatabaseManager().logBankTransaction(tx);

                plugin.getMessageManager().send(player, "bank.deposit-success",
                        MessageManager.of("amount", eco.format(amount), "balance", eco.format(clan.getBalance())));
                clan.broadcastMessage(plugin.getMessageManager().get("bank.broadcast-deposit",
                        MessageManager.of("player", player.getName(), "amount", eco.format(amount))));

                new ClanBankTransactionsGUI(plugin, player, clan).open();
            });
        });

        // Back button
        addBackButton(49, () -> new ClanMainGUI(plugin, player, clan).open());

        // Withdraw button
        setItem(51, new ItemBuilder(Material.REDSTONE)
                .name("&c&l✦ Geld Auszahlen")
                .lore(
                        "&7Hebe Geld aus der Clankasse",
                        "&7auf dein Konto ab.",
                        "",
                        canWithdraw ? "&a▶ &7Klicke zum Auszahlen" : "&c✖ Keine Berechtigung"
                )
                .build(), () -> {
            if (!canWithdraw) {
                plugin.getMessageManager().send(player, "general.no-permission");
                return;
            }
            if (eco == null || !eco.isAvailable()) {
                plugin.getMessageManager().send(player, "bank.not-available");
                return;
            }

            plugin.getChatInputManager().request(player, "Gib den Betrag ein, den du auszahlen möchtest:", input -> {
                double amount;
                try {
                    amount = Double.parseDouble(input.replace(",", "."));
                } catch (NumberFormatException e) {
                    plugin.getMessageManager().send(player, "bank.invalid-amount");
                    return;
                }

                if (amount <= 0) {
                    plugin.getMessageManager().send(player, "bank.invalid-amount");
                    return;
                }

                if (!clan.hasBalance(amount)) {
                    plugin.getMessageManager().send(player, "bank.insufficient-clan-funds");
                    return;
                }

                if (!eco.depositPlayer(player, amount)) {
                    plugin.getMessageManager().send(player, "bank.error");
                    return;
                }

                clan.withdraw(amount);
                plugin.getDatabaseManager().updateClan(clan);
                ClanBankTransaction tx = new ClanBankTransaction(
                        clan.getId(),
                        player.getUniqueId(),
                        player.getName(),
                        ClanBankTransaction.Type.WITHDRAW,
                        amount,
                        System.currentTimeMillis()
                );
                plugin.getDatabaseManager().logBankTransaction(tx);

                plugin.getMessageManager().send(player, "bank.withdraw-success",
                        MessageManager.of("amount", eco.format(amount), "balance", eco.format(clan.getBalance())));
                clan.broadcastMessage(plugin.getMessageManager().get("bank.broadcast-withdraw",
                        MessageManager.of("player", player.getName(), "amount", eco.format(amount))));

                new ClanBankTransactionsGUI(plugin, player, clan).open();
            });
        });

        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }
}
