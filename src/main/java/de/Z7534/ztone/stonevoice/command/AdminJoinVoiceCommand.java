package de.Z7534.ztone.stonevoice.command;

import de.Z7534.ztone.stonevoice.StoneVoice;
import de.Z7534.ztone.stonevoice.session.AdminVoiceManager;
import de.Z7534.ztone.util.SmallCapsUtil;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Handles the /adminjoinvoice command.
 */
public class AdminJoinVoiceCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "adminjoinvoice.use";
    private static final List<String> ALL_FLAGS = List.of("muted", "hidden", "FullMuted");

    private final StoneVoice plugin;

    public AdminJoinVoiceCommand(StoneVoice plugin) {
        this.plugin = plugin;
    }

    private void send(CommandSender sender, Component component) {
        sender.sendMessage(SmallCapsUtil.apply(component));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, Component.text("Dieser Befehl kann nur von Spielern ausgeführt werden.", NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission(PERMISSION)) {
            send(player, Component.text("[StoneVoice] Dazu hast du keine Berechtigung.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            send(player, Component.text("[StoneVoice] ", NamedTextColor.BLUE)
                    .append(Component.text("Verwendung: ", NamedTextColor.GOLD))
                    .append(Component.text("/" + label + " <Spieler|Voicegruppe|leave> [muted] [hidden] [FullMuted]", NamedTextColor.YELLOW)));
            return true;
        }

        VoicechatServerApi api = plugin.getVoicechatPlugin() != null ? plugin.getVoicechatPlugin().getServerApi() : null;
        if (api == null) {
            send(player, Component.text("[StoneVoice] SimpleVoiceChat Server-API ist noch nicht verfügbar.", NamedTextColor.RED));
            return true;
        }

        VoicechatConnection adminConn = api.getConnectionOf(player.getUniqueId());
        if (adminConn == null || !adminConn.isConnected()) {
            send(player, Component.text("[StoneVoice] Du bist nicht mit Simple Voice Chat verbunden.", NamedTextColor.RED));
            return true;
        }

        AdminVoiceManager manager = plugin.getAdminVoiceManager();

        // Handle leave shortcut: /adminjoinvoice leave
        if (args.length == 1 && args[0].equalsIgnoreCase("leave")) {
            manager.leave(player, adminConn);
            return true;
        }

        String target = args[0];
        Group targetGroup = null;

        // 1. Check if target is an online player
        Player targetPlayer = Bukkit.getPlayerExact(target);
        if (targetPlayer == null) {
            targetPlayer = Bukkit.getPlayer(target);
        }

        if (targetPlayer != null) {
            VoicechatConnection targetConn = api.getConnectionOf(targetPlayer.getUniqueId());
            if (targetConn != null && targetConn.isInGroup()) {
                targetGroup = targetConn.getGroup();
            } else {
                send(player, Component.text("[StoneVoice] ", NamedTextColor.BLUE)
                        .append(Component.text("Spieler ", NamedTextColor.RED))
                        .append(Component.text(targetPlayer.getName(), NamedTextColor.GOLD))
                        .append(Component.text(" ist in keiner Voice-Gruppe.", NamedTextColor.RED)));
                return true;
            }
        }

        // 2. If not a player (or player had no group), check if target matches a group name directly
        if (targetGroup == null) {
            for (Group group : api.getGroups()) {
                if (group.getName().equalsIgnoreCase(target)) {
                    targetGroup = group;
                    break;
                }
            }
        }

        if (targetGroup == null) {
            send(player, Component.text("[StoneVoice] ", NamedTextColor.BLUE)
                    .append(Component.text("Voice-Gruppe oder Spieler '", NamedTextColor.RED))
                    .append(Component.text(target, NamedTextColor.GOLD))
                    .append(Component.text("' wurde nicht gefunden.", NamedTextColor.RED)));
            return true;
        }

        // Parse optional flags: muted, hidden, FullMuted
        boolean muted = false;
        boolean hidden = false;
        boolean fullMuted = false;

        for (int i = 1; i < args.length; i++) {
            String flag = args[i].toLowerCase(Locale.ROOT);
            switch (flag) {
                case "muted" -> muted = true;
                case "hidden" -> hidden = true;
                case "fullmuted" -> fullMuted = true;
                default -> {
                    send(player, Component.text("[StoneVoice] ", NamedTextColor.BLUE)
                            .append(Component.text("Unbekannte Option: '", NamedTextColor.RED))
                            .append(Component.text(args[i], NamedTextColor.GOLD))
                            .append(Component.text("'. Verfügbare Optionen: muted, hidden, FullMuted", NamedTextColor.RED)));
                    return true;
                }
            }
        }

        manager.joinGroup(player, adminConn, targetGroup, muted, hidden, fullMuted);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || !player.hasPermission(PERMISSION)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();

            // Online players
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(player)) {
                    suggestions.add(p.getName());
                }
            }

            // Existing voice groups
            VoicechatServerApi api = plugin.getVoicechatPlugin() != null ? plugin.getVoicechatPlugin().getServerApi() : null;
            if (api != null) {
                for (Group g : api.getGroups()) {
                    suggestions.add(g.getName());
                }
            }

            // Leave option
            suggestions.add("leave");

            return StringUtil.copyPartialMatches(args[0], suggestions, new ArrayList<>());
        }

        if (args.length >= 2 && args.length <= 4) {
            // Collect used flags
            Set<String> used = new HashSet<>();
            for (int i = 1; i < args.length - 1; i++) {
                used.add(args[i].toLowerCase(Locale.ROOT));
            }

            List<String> available = new ArrayList<>();
            for (String flag : ALL_FLAGS) {
                if (!used.contains(flag.toLowerCase(Locale.ROOT))) {
                    available.add(flag);
                }
            }

            return StringUtil.copyPartialMatches(args[args.length - 1], available, new ArrayList<>());
        }

        return Collections.emptyList();
    }
}
