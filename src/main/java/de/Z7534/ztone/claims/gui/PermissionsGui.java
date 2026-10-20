package de.Z7534.ztone.claims.gui;

import de.Z7534.ztone.claims.ClaimsContext;
import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.claims.model.ClaimPermission;
import de.Z7534.ztone.claims.model.TrustedPlayer;
import de.Z7534.ztone.claims.util.GuiUtil;
import de.Z7534.ztone.claims.util.ItemBuilder;
import de.Z7534.ztone.claims.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

public final class PermissionsGui extends Gui {

    private static final Material[] ICONS = {
            Material.DIAMOND_PICKAXE, Material.WATER_BUCKET, Material.CHEST, Material.OAK_DOOR,
            Material.LEVER, Material.HOPPER, Material.IRON_SWORD, Material.DIAMOND_SWORD,
            Material.SADDLE, Material.ITEM_FRAME, Material.FLINT_AND_STEEL, Material.BELL,
            Material.SPAWNER
    };

    private static final int SLOT_BACK = 18;
    private static final int SLOT_ALLOW_ALL = 22;
    private static final int SLOT_DENY_ALL = 26;

    private final ClaimsContext ctx;
    private final Claim claim;
    private final java.util.UUID target;

    public PermissionsGui(ClaimsContext ctx, Claim claim, java.util.UUID target) {
        super(27, Text.line("<dark_green>Rechte" + (target == null ? " - Besucher" : "")));
        this.ctx = ctx;
        this.claim = claim;
        this.target = target;
        build();
    }

    private int currentMask() {
        if (target == null) return claim.visitorPermissionMask();
        TrustedPlayer tp = claim.trusted().get(target);
        return tp != null ? tp.permissionMask() : 0;
    }

    private void build() {
        int mask = currentMask();
        ClaimPermission[] perms = ClaimPermission.values();
        for (int i = 0; i < perms.length; i++) {
            ClaimPermission perm = perms[i];
            boolean enabled = ClaimPermission.has(mask, perm);
            String key = "permissions-right-" + perm.name().toLowerCase();
            inventory.setItem(i, ItemBuilder.of(ICONS[i])
                    .name(Text.line((enabled ? "<green>" : "<red>") + perm.description() + de.Z7534.ztone.claims.util.GuiUtil.toggleSuffix(enabled)))
                    .lore(Text.line("<gray>Linksklick, umschalten."))
                    .model(ctx.config().itemModel(key))
                    .glow(enabled)
                    .build());
        }

        inventory.setItem(SLOT_BACK, GuiUtil.backButton(ctx.config().itemModel("permissions-back")));

        inventory.setItem(SLOT_ALLOW_ALL, ItemBuilder.of(Material.LIME_DYE)
                .name(Text.line("<green>Alles aktivieren"))
                .lore(Text.line("<gray>Linksklick, alle Rechte aktivieren."))
                .model(ctx.config().itemModel("permissions-allow-all"))
                .build());

        inventory.setItem(SLOT_DENY_ALL, ItemBuilder.of(Material.RED_DYE)
                .name(Text.line("<red>Alles deaktivieren"))
                .lore(Text.line("<gray>Linksklick, alle Rechte deaktivieren."))
                .model(ctx.config().itemModel("permissions-deny-all"))
                .build());
    }

    @Override
    public void onClick(Player player, int slot, ClickType clickType) {
        Claim current = ctx.claims().getOwned(player.getUniqueId(), claim.id());
        if (current == null) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_BACK) {
            new TrustGui(ctx, current).open(player);
            return;
        }
        if (slot == SLOT_ALLOW_ALL) {
            applyMask(current, ClaimPermission.ALL_MASK);
            new PermissionsGui(ctx, current, target).open(player);
            return;
        }
        if (slot == SLOT_DENY_ALL) {
            applyMask(current, ClaimPermission.NONE_MASK);
            new PermissionsGui(ctx, current, target).open(player);
            return;
        }
        ClaimPermission[] perms = ClaimPermission.values();
        if (slot >= 0 && slot < perms.length) {
            int mask = currentMaskOf(current);
            mask = ClaimPermission.with(mask, perms[slot], !ClaimPermission.has(mask, perms[slot]));
            applyMask(current, mask);
            new PermissionsGui(ctx, current, target).open(player);
        }
    }

    private int currentMaskOf(Claim current) {
        if (target == null) return current.visitorPermissionMask();
        TrustedPlayer tp = current.trusted().get(target);
        return tp != null ? tp.permissionMask() : 0;
    }

    private void applyMask(Claim current, int mask) {
        if (target == null) {
            ctx.claims().setVisitorMask(current, mask);
        } else {
            ctx.claims().setTrust(current, target, mask);
        }
    }
}
