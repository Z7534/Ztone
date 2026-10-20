package de.Z7534.ztone.clansystem.managers;

import de.Z7534.ztone.Ztone;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class EconomyManager {

    private final Ztone plugin;
    private Economy economy;
    private final DecimalFormat format;

    public EconomyManager(Ztone plugin) {
        this.plugin = plugin;
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.GERMANY);
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        this.format = new DecimalFormat("#,##0.00", symbols);
        setupEconomy();
    }

    public boolean setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        this.economy = rsp.getProvider();
        if (this.economy != null) {
            plugin.getLogger().info("Vault-Economy erfolgreich angebunden (" + economy.getName() + ").");
        }
        return this.economy != null;
    }

    public boolean hasEconomy() {
        if (economy == null) {
            setupEconomy();
        }
        return economy != null;
    }

    public boolean isAvailable() {
        return hasEconomy();
    }

    public Economy getEconomy() {
        if (economy == null) {
            setupEconomy();
        }
        return economy;
    }

    public double getBalance(OfflinePlayer player) {
        if (!hasEconomy()) return 0.0;
        return economy.getBalance(player);
    }

    public boolean has(OfflinePlayer player, double amount) {
        if (!hasEconomy()) return false;
        return economy.has(player, amount);
    }

    public boolean hasBalance(OfflinePlayer player, double amount) {
        return has(player, amount);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (!hasEconomy()) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean withdrawPlayer(OfflinePlayer player, double amount) {
        return withdraw(player, amount);
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (!hasEconomy()) return false;
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    public boolean depositPlayer(OfflinePlayer player, double amount) {
        return deposit(player, amount);
    }

    public String format(double amount) {
        return format.format(amount) + " $";
    }
}
