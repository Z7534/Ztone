package de.Z7534.ztone.claims.listener;

import de.Z7534.ztone.claims.config.ConfigManager;
import de.Z7534.ztone.claims.manager.BypassManager;
import de.Z7534.ztone.claims.manager.ClaimManager;
import de.Z7534.ztone.claims.manager.ClaimMapManager;
import de.Z7534.ztone.claims.manager.SelectionManager;
import de.Z7534.ztone.claims.model.ChunkPos;
import de.Z7534.ztone.claims.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;

public final class SelectionListener implements Listener {

    private final SelectionManager selectionManager;
    private final ClaimManager claimManager;
    private final ConfigManager config;
    private final BypassManager bypassManager;
    private final ClaimMapManager claimMapManager;

    public SelectionListener(SelectionManager selectionManager, ClaimManager claimManager, ConfigManager config,
                             BypassManager bypassManager, ClaimMapManager claimMapManager) {
        this.bypassManager = bypassManager;
        this.selectionManager = selectionManager;
        this.claimManager = claimManager;
        this.config = config;
        this.claimMapManager = claimMapManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        Player player = e.getPlayer();
        if (!selectionManager.isActive(player.getUniqueId())) return;
        if (e.getClickedBlock() == null) return;

        e.setCancelled(true);
        ChunkPos pos = new ChunkPos(e.getClickedBlock().getWorld().getName(),
                e.getClickedBlock().getX() >> 4, e.getClickedBlock().getZ() >> 4);

        boolean bypass = bypassManager.isActive(player);
        SelectionManager.ClickResult result = selectionManager.handleClick(player, pos, bypass);
        if (result == null) return;

        if (claimMapManager != null) {
            claimMapManager.invalidate(player.getUniqueId());
        }

        switch (result) {
            case MARKED_CLAIM -> player.sendMessage(Text.line(config.prefix() + config.message("claim-marked")));
            case MARKED_UNCLAIM -> player.sendMessage(Text.line(config.prefix() + config.message("unclaim-marked")));
            case UNMARKED -> player.sendMessage(Text.line(config.prefix() + config.message("selection-removed")));
            case FOREIGN -> player.sendMessage(Text.line(config.prefix() + config.message("cannot-select-foreign")));
            case SPAWN_PROTECTED -> player.sendMessage(Text.line(config.prefix() + config.message("spawn-protected")));
            case END_PROTECTED -> player.sendMessage(Text.line(config.prefix() + config.message("end-protected")));
            case DIMENSION_DISABLED -> player.sendMessage(Text.line(config.prefix() + config.message("dimension-disabled")));
            case NOT_ENOUGH_SLOTS -> {
                int used = claimManager.getUsedClaimSlots(player.getUniqueId());
                int max = claimManager.getMaxClaimSlots(player);
                player.sendMessage(Text.line(config.prefix() + config.message("not-enough-slots"),
                        Map.of("belegt", String.valueOf(used), "max", String.valueOf(max))));
            }
        }
    }
}
