package de.Z7534.ztone.listeners;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.managers.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.lang.reflect.Field;
import java.util.*;

public class ModuleCommandFilterListener implements Listener {

    private final Ztone plugin;
    private final Set<String> disabledCommands = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, String> disabledMessages = Collections.synchronizedMap(new HashMap<>());

    public ModuleCommandFilterListener(Ztone plugin) {
        this.plugin = plugin;
        recalculateDisabledCommands();
    }

    public void recalculateDisabledCommands() {
        disabledCommands.clear();
        disabledMessages.clear();

        ConfigManager cfg = plugin.getConfigManager();
        if (cfg == null) return;

        String pluginPrefix = plugin.getName().toLowerCase(Locale.ROOT) + ":";

        // 2. Clan-System Module
        if (!cfg.isClansystemEnabled()) {
            registerDisabled(new String[]{
                    "clan", "clanadmin"
            }, "§8[§6Clan§8] §cDas Clan-System ist auf diesem Server deaktiviert.", pluginPrefix);
        }

        // 3. PvPManager Module
        if (!cfg.isPvpmanagerEnabled()) {
            registerDisabled(new String[]{
                    "pvpmanager", "pmr", "pvpm", "pm", "pvp", "togglepvp", "pvptoggle", "pvpoverride", "pvpo",
                    "pvpinfo", "pvpstatus", "pvplist", "listpvp", "pvptag", "combattag", "tag", "untag",
                    "teleportoverride", "testrace", "announce", "soup", "pvpdimensiontoggle"
            }, "§cDas PvPManager-Modul ist auf diesem Server deaktiviert.", pluginPrefix);
        }

        // 4. Claims Module
        if (!cfg.isClaimsEnabled()) {
            registerDisabled(new String[]{
                    "claim", "staffclaim"
            }, "§cDas Claims-Modul ist auf diesem Server deaktiviert.", pluginPrefix);
        }

        // 5. StoneAttack Module
        if (!cfg.isStoneattackEnabled()) {
            registerDisabled(new String[]{
                    "stoneattack", "playerlimit", "plimit", "performance", "tps", "leaderboard"
            }, "§cDas StoneAttack-Modul ist auf diesem Server deaktiviert.", pluginPrefix);
        }

        // 6. Stonelog Module
        if (!cfg.isStonelogEnabled()) {
            registerDisabled(new String[]{
                    "stonelog"
            }, "§cDas Stonelog-Modul ist auf diesem Server deaktiviert.", pluginPrefix);
        }

        // 7. MapControl Module
        if (!cfg.isMapcontrolEnabled()) {
            registerDisabled(new String[]{
                    "mapcontrol"
            }, "§cDas MapControl-Modul ist auf diesem Server deaktiviert.", pluginPrefix);
        }

        // 8. StoneVoice Module
        if (!cfg.isStonevoiceEnabled()) {
            registerDisabled(new String[]{
                    "adminjoinvoice"
            }, "§cDas StoneVoice-Modul ist auf diesem Server deaktiviert.", pluginPrefix);
        }

        // 9. PlayRewards Module
        if (!cfg.isPlayrewardsEnabled()) {
            registerDisabled(new String[]{
                    "playrewards", "pr", "playreward"
            }, "§cDas PlayRewards-Modul ist auf diesem Server deaktiviert.", pluginPrefix);
        }

        // 10. Deathchest Module
        if (!cfg.isDeathchestEnabled()) {
            registerDisabled(new String[]{
                    "deathchest"
            }, "§cDas Deathchest-Modul ist auf diesem Server deaktiviert.", pluginPrefix);
        }

        // 11. ClearLag Module
        if (!cfg.isClearlagEnabled()) {
            registerDisabled(new String[]{
                    "clearlag"
            }, "§cDas ClearLag-Modul ist auf diesem Server deaktiviert.", pluginPrefix);
        }

        // Unregister disabled commands from Bukkit's CommandMap so Brigadier doesn't register them
        unregisterFromCommandMap();

        // Update command suggestions for all connected players
        for (Player online : Bukkit.getOnlinePlayers()) {
            try {
                online.updateCommands();
            } catch (Exception ignored) {
            }
        }
    }

    private void registerDisabled(String[] commands, String message, String prefix) {
        for (String cmd : commands) {
            String lower = cmd.toLowerCase(Locale.ROOT);
            disabledCommands.add(lower);
            disabledCommands.add(prefix + lower);
            disabledMessages.put(lower, message);
            disabledMessages.put(prefix + lower, message);
        }
    }

    @SuppressWarnings("unchecked")
    private void unregisterFromCommandMap() {
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());

            Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

            for (String disabled : disabledCommands) {
                Command cmd = knownCommands.remove(disabled);
                if (cmd != null) {
                    cmd.unregister(commandMap);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Prevents disabled module commands from ever appearing in client-side chat tab suggestions.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandSend(PlayerCommandSendEvent event) {
        if (!disabledCommands.isEmpty()) {
            event.getCommands().removeAll(disabledCommands);
        }
    }

    /**
     * Blocks execution if a player types a disabled module command manually.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        if (msg.startsWith("/")) {
            msg = msg.substring(1);
        }
        String[] parts = msg.split(" ", 2);
        String label = parts[0].toLowerCase(Locale.ROOT);

        if (disabledCommands.contains(label)) {
            event.setCancelled(true);
            String customMessage = disabledMessages.get(label);
            if (customMessage != null && !customMessage.isEmpty()) {
                event.getPlayer().sendMessage(customMessage);
            }
        }
    }
}
