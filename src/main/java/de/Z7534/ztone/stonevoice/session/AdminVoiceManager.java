package de.Z7534.ztone.stonevoice.session;

import de.Z7534.ztone.stonevoice.StoneVoice;
import de.Z7534.ztone.util.SmallCapsUtil;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active admin voice sessions.
 */
public class AdminVoiceManager {

    private final StoneVoice plugin;
    private final Map<UUID, AdminVoiceSession> activeSessions = new ConcurrentHashMap<>();

    public AdminVoiceManager(StoneVoice plugin) {
        this.plugin = plugin;
    }

    private void send(Player player, Component component) {
        if (player != null && player.isOnline()) {
            player.sendMessage(SmallCapsUtil.apply(component));
        }
    }

    /**
     * Joins an admin to a voice group with specific flags.
     */
    public void joinGroup(Player player, VoicechatConnection connection, Group group, boolean muted, boolean hidden, boolean fullMuted) {
        UUID uuid = player.getUniqueId();

        // Clear any previous session
        stopSessionSilently(uuid, connection);

        AdminVoiceSession session = new AdminVoiceSession(uuid, group.getId(), group.getName(), muted, hidden, fullMuted);
        activeSessions.put(uuid, session);

        if (hidden) {
            // In hidden mode, leave the voice group officially so no icon / player entry is visible in the client UI
            if (connection.isInGroup()) {
                connection.setGroup(null);
            }
        } else {
            // Join the group openly without password
            connection.setGroup(group);
        }

        // Build nice status feedback
        List<Component> modeBadges = new ArrayList<>();
        if (hidden) {
            modeBadges.add(Component.text("Hidden", NamedTextColor.LIGHT_PURPLE));
        }
        if (fullMuted) {
            modeBadges.add(Component.text("FullMuted", NamedTextColor.RED));
        } else if (muted) {
            modeBadges.add(Component.text("Muted", NamedTextColor.GOLD));
        }

        if (modeBadges.isEmpty()) {
            modeBadges.add(Component.text("Normal", NamedTextColor.AQUA));
        }

        Component modesJoined = Component.join(net.kyori.adventure.text.JoinConfiguration.separator(Component.text(", ", NamedTextColor.GRAY)), modeBadges);

        send(player, Component.text("[StoneVoice] ", NamedTextColor.BLUE)
                .append(Component.text("Voice-Gruppe beigetreten: ", NamedTextColor.GREEN))
                .append(Component.text(group.getName(), NamedTextColor.GOLD))
                .append(Component.text(" (", NamedTextColor.GRAY))
                .append(modesJoined)
                .append(Component.text(")", NamedTextColor.GRAY)));
    }

    /**
     * Leaves the current group / terminates admin session.
     */
    public boolean leave(Player player, VoicechatConnection connection) {
        UUID uuid = player.getUniqueId();
        AdminVoiceSession session = activeSessions.remove(uuid);
        boolean wasInGroup = false;

        if (connection != null && connection.isInGroup()) {
            connection.setGroup(null);
            wasInGroup = true;
        }

        if (session != null || wasInGroup) {
            send(player, Component.text("[StoneVoice] ", NamedTextColor.BLUE)
                    .append(Component.text("Du hast die Voice-Gruppe / Admin-Session verlassen.", NamedTextColor.GREEN)));
            return true;
        } else {
            send(player, Component.text("[StoneVoice] ", NamedTextColor.BLUE)
                    .append(Component.text("Du bist aktuell in keiner Admin-Voice-Session oder Gruppe.", NamedTextColor.YELLOW)));
            return false;
        }
    }

    /**
     * Silently stops an admin session without sending chat messages.
     */
    public void stopSessionSilently(UUID playerUuid, VoicechatConnection connection) {
        AdminVoiceSession session = activeSessions.remove(playerUuid);
        if (session != null && !session.isHidden() && connection != null && connection.isInGroup()) {
            connection.setGroup(null);
        }
    }

    public AdminVoiceSession getSession(UUID playerUuid) {
        return activeSessions.get(playerUuid);
    }

    public boolean isSessionActive(UUID playerUuid) {
        return activeSessions.containsKey(playerUuid);
    }

    /**
     * Returns all hidden listeners currently listening to the given group.
     */
    public Collection<AdminVoiceSession> getHiddenListenersForGroup(UUID groupId) {
        List<AdminVoiceSession> result = new ArrayList<>();
        for (AdminVoiceSession session : activeSessions.values()) {
            if (session.isHidden() && session.getTargetGroupId().equals(groupId)) {
                result.add(session);
            }
        }
        return result;
    }

    /**
     * Called when a group is removed from SimpleVoiceChat.
     */
    public void onGroupRemoved(UUID groupId) {
        for (AdminVoiceSession session : activeSessions.values()) {
            if (session.getTargetGroupId().equals(groupId)) {
                activeSessions.remove(session.getPlayerUuid());
                Player player = Bukkit.getPlayer(session.getPlayerUuid());
                if (player != null && player.isOnline()) {
                    send(player, Component.text("[StoneVoice] ", NamedTextColor.BLUE)
                            .append(Component.text("Die Voice-Gruppe ", NamedTextColor.RED))
                            .append(Component.text(session.getTargetGroupName(), NamedTextColor.GOLD))
                            .append(Component.text(" wurde gelöscht.", NamedTextColor.RED)));
                }
            }
        }
    }

    public void onPlayerQuit(UUID playerUuid) {
        activeSessions.remove(playerUuid);
    }

    public void clearAll() {
        activeSessions.clear();
    }
}
