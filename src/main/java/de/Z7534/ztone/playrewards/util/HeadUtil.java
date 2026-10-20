package de.Z7534.ztone.playrewards.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public final class HeadUtil {

    private HeadUtil() {}

    /**
     * Erstellt einen Spielerkopf mit einer Textur von minecraft-heads.com.
     * Unterstützt direkte Base64-Strings, Texture-URLs oder Texture-Hashes.
     */
    public static ItemStack createHeadFromBase64(String textureInput) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (textureInput == null || textureInput.trim().isEmpty()) {
            return head;
        }

        String raw = textureInput.trim();
        String base64Value = raw;

        // Falls eine reine URL übergeben wurde
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + raw + "\"}}}";
            base64Value = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } else if (raw.matches("^[0-9a-fA-F]{64}$")) {
            // Falls ein reiner 64-stelliger Minecraft-Texture-Hash übergeben wurde
            String url = "http://textures.minecraft.net/texture/" + raw;
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
            base64Value = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        }

        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            try {
                PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
                profile.setProperty(new ProfileProperty("textures", base64Value));
                meta.setPlayerProfile(profile);
                head.setItemMeta(meta);
            } catch (Exception ignored) {
                // Fallback, falls die Textur ungültig ist
            }
        }
        return head;
    }
}
