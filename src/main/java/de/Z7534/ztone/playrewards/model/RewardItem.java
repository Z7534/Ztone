package de.Z7534.ztone.playrewards.model;

import org.bukkit.Material;

import java.util.Collections;
import java.util.List;

public class RewardItem {

    private final String id;
    private final String name;
    private final Material material;
    private final String customHeadTexture;
    private final int slot;
    private final long requiredPlaytimeSeconds;
    private final int requiredStreakDays;
    private final List<String> lorePreview;
    private final List<String> commands;

    public RewardItem(String id,
                      String name,
                      Material material,
                      String customHeadTexture,
                      int slot,
                      long requiredPlaytimeSeconds,
                      int requiredStreakDays,
                      List<String> lorePreview,
                      List<String> commands) {
        this.id = id;
        this.name = name != null ? name : "Belohnung";
        this.material = material != null ? material : Material.CHEST;
        this.customHeadTexture = customHeadTexture != null ? customHeadTexture : "";
        this.slot = slot;
        this.requiredPlaytimeSeconds = requiredPlaytimeSeconds;
        this.requiredStreakDays = requiredStreakDays;
        this.lorePreview = lorePreview != null ? lorePreview : Collections.emptyList();
        this.commands = commands != null ? commands : Collections.emptyList();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Material getMaterial() {
        return material;
    }

    public String getCustomHeadTexture() {
        return customHeadTexture;
    }

    public boolean hasCustomHeadTexture() {
        return !customHeadTexture.trim().isEmpty();
    }

    public int getSlot() {
        return slot;
    }

    public long getRequiredPlaytimeSeconds() {
        return requiredPlaytimeSeconds;
    }

    public int getRequiredStreakDays() {
        return requiredStreakDays;
    }

    public List<String> getLorePreview() {
        return lorePreview;
    }

    public List<String> getCommands() {
        return commands;
    }
}
