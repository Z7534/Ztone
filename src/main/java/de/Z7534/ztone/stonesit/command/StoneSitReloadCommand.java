package de.Z7534.ztone.stonesit.command;

import de.Z7534.ztone.stonesit.config.StoneSitConfig;
import de.Z7534.ztone.stonesit.util.EnvironmentUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class StoneSitReloadCommand implements CommandExecutor, TabCompleter {

    private final StoneSitConfig config;

    public StoneSitReloadCommand(StoneSitConfig config) {
        this.config = config;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!EnvironmentUtil.hasPermission(sender, "stonesit.reload", "GSit.Reload")) {
            config.sendMessage(sender, "no-permission");
            return true;
        }
        config.load();
        config.sendMessage(sender, "reloaded");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }
}
