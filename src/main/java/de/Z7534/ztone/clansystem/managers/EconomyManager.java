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

    /**
     * Parst Betrags-Eingaben inklusive gängiger Economy-Suffixe (k, m, b, t)
     * sowie Komma- und Punkt-Dezimalstellen.
     *
     * Beispiele:
     * - "10k"   -> 10.000,0
     * - "1.5m"  -> 1.500.000,0
     * - "1,5m"  -> 1.500.000,0
     * - "2b"    -> 2.000.000.000,0
     * - "1t"    -> 1.000.000.000.000,0
     * - "50000" -> 50.000,0
     *
     * @param input Der eingegebene String
     * @return Der geparste Betrag oder null bei ungültiger Eingabe
     */
    public static Double parseAmount(String input) {
        if (input == null) return null;
        String trimmed = input.trim().replace(" ", "").replace("$", "");
        if (trimmed.isEmpty()) return null;

        char lastChar = trimmed.charAt(trimmed.length() - 1);
        double multiplier = 1.0;
        boolean hasSuffix = true;

        switch (Character.toLowerCase(lastChar)) {
            case 'k' -> multiplier = 1_000.0;
            case 'm' -> multiplier = 1_000_000.0;
            case 'b' -> multiplier = 1_000_000_000.0;
            case 't' -> multiplier = 1_000_000_000_000.0;
            default -> hasSuffix = false;
        }

        String numberPart = hasSuffix ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        if (numberPart.isEmpty()) return null;

        try {
            if (numberPart.contains(".") && numberPart.contains(",")) {
                if (numberPart.lastIndexOf('.') > numberPart.lastIndexOf(',')) {
                    numberPart = numberPart.replace(",", "");
                } else {
                    numberPart = numberPart.replace(".", "").replace(",", ".");
                }
            } else {
                numberPart = numberPart.replace(',', '.');
            }

            double val = Double.parseDouble(numberPart);
            if (Double.isNaN(val) || Double.isInfinite(val) || val < 0) {
                return null;
            }
            double result = val * multiplier;
            if (Double.isInfinite(result) || result < 0) {
                return null;
            }
            return Math.round(result * 100.0) / 100.0;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
