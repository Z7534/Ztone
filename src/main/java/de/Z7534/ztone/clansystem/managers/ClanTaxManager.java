package de.Z7534.ztone.clansystem.managers;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.models.ClanBankTransaction;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;

public class ClanTaxManager {

    private final Ztone plugin;
    private BukkitTask task;

    public static class TaxResult {
        private final int taxedClans;
        private final int exemptGraceClans;
        private final double totalAmount;

        public TaxResult(int taxedClans, int exemptGraceClans, double totalAmount) {
            this.taxedClans = taxedClans;
            this.exemptGraceClans = exemptGraceClans;
            this.totalAmount = totalAmount;
        }

        public int getTaxedClans() {
            return taxedClans;
        }

        public int getExemptGraceClans() {
            return exemptGraceClans;
        }

        public double getTotalAmount() {
            return totalAmount;
        }
    }

    public ClanTaxManager(Ztone plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.getConfigManager().isClansystemEnabled()) {
            return;
        }

        // Alle 60 Sekunden (1200 Ticks) prüfen, ob das weltweite Steuer-Intervall fällig ist
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::checkTaxes, 1200L, 1200L);
    }

    public void stop() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
            task = null;
        }
    }

    public void checkTaxes() {
        if (!plugin.getConfigManager().isClansystemEnabled() || !plugin.getConfigManager().isTaxesEnabled()) {
            return;
        }

        long lastRun = plugin.getDatabaseManager().getLastTaxRun();
        long now = System.currentTimeMillis();
        long intervalMillis = plugin.getConfigManager().getTaxesIntervalDays() * 86400000L;

        if (lastRun <= 0) {
            // Initialer Start: ersten Ausführungszeitpunkt auf jetzt festlegen
            plugin.getDatabaseManager().setLastTaxRun(now);
            return;
        }

        if (now - lastRun >= intervalMillis) {
            collectTaxes(null);
        }
    }

    public TaxResult collectTaxes(CommandSender initiator) {
        if (!plugin.getConfigManager().isTaxesEnabled()) {
            if (initiator != null) {
                plugin.getMessageManager().send(initiator, "admin.taxes-disabled");
            }
            return new TaxResult(0, 0, 0.0);
        }

        Collection<Clan> clans = plugin.getClanManager().getAllClans();
        int taxed = 0;
        int exempt = 0;
        double totalCollected = 0.0;
        long now = System.currentTimeMillis();
        long graceMillis = plugin.getConfigManager().getTaxesGracePeriodDays() * 86400000L;

        EconomyManager eco = plugin.getEconomyManager();

        for (Clan clan : clans) {
            long age = now - clan.getCreatedAt();
            if (graceMillis > 0 && age < graceMillis) {
                exempt++;
                continue;
            }

            double taxAmount = plugin.getConfigManager().calculateTotalTax(clan.getLevel(), clan.getBalance());
            if (taxAmount <= 0) {
                continue;
            }

            taxed++;
            totalCollected += taxAmount;

            // Betrag aus der Clankasse abbuchen
            clan.setBalance(clan.getBalance() - taxAmount);
            plugin.getDatabaseManager().updateClan(clan);

            // Transaktion im Kassenbuch festhalten
            ClanBankTransaction tx = new ClanBankTransaction(
                    clan.getId(),
                    null,
                    "Steuern",
                    ClanBankTransaction.Type.TAX,
                    taxAmount,
                    now
            );
            plugin.getDatabaseManager().logBankTransaction(tx);

            // Benachrichtigung an den Clan senden
            String formattedTax = eco != null ? eco.format(taxAmount) : (taxAmount + " $");
            String formattedBalance = eco != null ? eco.format(clan.getBalance()) : (clan.getBalance() + " $");

            clan.broadcastMessage(plugin.getMessageManager().get("bank.taxes-deducted",
                    MessageManager.of("amount", formattedTax, "balance", formattedBalance)));

            // Prüfen, ob der Clan durch die Steuern ins Minus geraten ist
            if (clan.isInDebt()) {
                String formattedDebt = eco != null ? eco.format(clan.getDebt()) : (clan.getDebt() + " $");
                clan.broadcastMessage(plugin.getMessageManager().get("bank.taxes-debt-warning",
                        MessageManager.of("debt", formattedDebt)));
            }
        }

        plugin.getDatabaseManager().setLastTaxRun(now);
        plugin.getLogger().info(String.format("Clan-Steuern eingezogen: %d Clans besteuert, %d in Schonfrist, Gesamteinnahmen: %.2f $",
                taxed, exempt, totalCollected));

        if (initiator != null) {
            String formattedTotal = eco != null ? eco.format(totalCollected) : (totalCollected + " $");
            plugin.getMessageManager().send(initiator, "admin.taxes-run-success",
                    MessageManager.of("count", String.valueOf(taxed), "exempt", String.valueOf(exempt), "total", formattedTotal));
        }

        return new TaxResult(taxed, exempt, totalCollected);
    }

    public boolean isClanInGracePeriod(Clan clan) {
        if (clan == null) return false;
        long age = System.currentTimeMillis() - clan.getCreatedAt();
        long grace = plugin.getConfigManager().getTaxesGracePeriodDays() * 86400000L;
        return grace > 0 && age < grace;
    }

    public long getRemainingGraceDays(Clan clan) {
        if (clan == null) return 0;
        long age = System.currentTimeMillis() - clan.getCreatedAt();
        long grace = plugin.getConfigManager().getTaxesGracePeriodDays() * 86400000L;
        if (age >= grace) return 0;
        return Math.max(1, (grace - age) / 86400000L);
    }

    public long getNextTaxRunTimestamp() {
        long lastRun = plugin.getDatabaseManager().getLastTaxRun();
        if (lastRun <= 0) {
            return System.currentTimeMillis();
        }
        return lastRun + (plugin.getConfigManager().getTaxesIntervalDays() * 86400000L);
    }

    public double calculateClanTax(Clan clan) {
        if (clan == null) return 0.0;
        return plugin.getConfigManager().calculateTotalTax(clan.getLevel(), clan.getBalance());
    }
}
