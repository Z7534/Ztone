package de.Z7534.ztone.claims.manager;

import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.claims.model.ChunkPos;
import de.Z7534.ztone.claims.model.SelectionSession;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class SelectionManager {

    private final ClaimManager claimManager;
    private final Map<UUID, SelectionSession> sessions = new HashMap<>();
    private Consumer<UUID> onEnd;

    public SelectionManager(ClaimManager claimManager) {
        this.claimManager = claimManager;
    }

    public void setOnEnd(Consumer<UUID> onEnd) {
        this.onEnd = onEnd;
    }

    public boolean isActive(UUID player) {
        return sessions.containsKey(player);
    }

    public SelectionSession start(UUID player) {
        SelectionSession session = new SelectionSession(player);
        sessions.put(player, session);
        return session;
    }

    public SelectionSession get(UUID player) {
        return sessions.get(player);
    }

    public void end(UUID player) {
        SelectionSession removed = sessions.remove(player);
        if (removed != null && onEnd != null) {
            onEnd.accept(player);
        }
    }

    public enum ClickResult {
        MARKED_CLAIM, MARKED_UNCLAIM, UNMARKED, FOREIGN, SPAWN_PROTECTED, END_PROTECTED, DIMENSION_DISABLED, NOT_ENOUGH_SLOTS
    }

    public ClickResult handleClick(Player player, ChunkPos pos, boolean bypass) {
        UUID playerUuid = player.getUniqueId();
        SelectionSession session = sessions.get(playerUuid);
        if (session == null) return null;

        if (session.toClaim().contains(pos)) {
            session.toClaim().remove(pos);
            return ClickResult.UNMARKED;
        }
        if (session.toUnclaim().contains(pos)) {
            session.toUnclaim().remove(pos);
            return ClickResult.UNMARKED;
        }

        Claim owner = claimManager.getClaimAt(pos);
        if (owner != null && owner.owner().equals(playerUuid)) {
            session.toUnclaim().add(pos);
            return ClickResult.MARKED_UNCLAIM;
        }
        if (owner != null) {
            return ClickResult.FOREIGN;
        }

        if (!bypass) {
            if (!claimManager.isDimensionEnabled(pos.world())) {
                return ClickResult.DIMENSION_DISABLED;
            }
            if (claimManager.isSpawnProtected(pos)) {
                return ClickResult.SPAWN_PROTECTED;
            }
            if (claimManager.isEndProtected(pos)) {
                return ClickResult.END_PROTECTED;
            }
        }

        if (!hasFreeSlot(player, session)) {
            return ClickResult.NOT_ENOUGH_SLOTS;
        }
        session.toClaim().add(pos);
        return ClickResult.MARKED_CLAIM;
    }

    private boolean hasFreeSlot(Player player, SelectionSession session) {
        int used = claimManager.getUsedClaimSlots(player.getUniqueId());
        int max = claimManager.getMaxClaimSlots(player);
        int pending = used - session.toUnclaim().size() + session.toClaim().size();
        return pending < max;
    }
}
