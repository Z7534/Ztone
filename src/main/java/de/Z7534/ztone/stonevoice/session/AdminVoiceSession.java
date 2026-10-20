package de.Z7534.ztone.stonevoice.session;

import java.util.UUID;

/**
 * Represents an active admin voice session for a player.
 */
public class AdminVoiceSession {

    private final UUID playerUuid;
    private final UUID targetGroupId;
    private final String targetGroupName;
    private final boolean muted;
    private final boolean hidden;
    private final boolean fullMuted;

    public AdminVoiceSession(UUID playerUuid, UUID targetGroupId, String targetGroupName, boolean muted, boolean hidden, boolean fullMuted) {
        this.playerUuid = playerUuid;
        this.targetGroupId = targetGroupId;
        this.targetGroupName = targetGroupName;
        this.muted = muted;
        this.hidden = hidden;
        this.fullMuted = fullMuted;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public UUID getTargetGroupId() {
        return targetGroupId;
    }

    public String getTargetGroupName() {
        return targetGroupName;
    }

    public boolean isMuted() {
        return muted;
    }

    public boolean isHidden() {
        return hidden;
    }

    public boolean isFullMuted() {
        return fullMuted;
    }
}
