package de.Z7534.ztone.deathchest.model;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class DeathChestHolder implements InventoryHolder {

    private DeathChestData chestData;
    private Inventory inventory;

    public DeathChestHolder() {
    }

    public DeathChestHolder(DeathChestData chestData) {
        this.chestData = chestData;
    }

    public DeathChestData getChestData() {
        return chestData;
    }

    public void setChestData(DeathChestData chestData) {
        this.chestData = chestData;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory != null ? inventory : Bukkit.createInventory(null, 54);
    }
}
