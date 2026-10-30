package de.Z7534.ztone.stonesit.util;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;

public final class PassengerUtil {

    private PassengerUtil() {
    }

    public static long getEntityVehicleCount(Entity entity) {
        long vehicleCount = 0;
        Entity currentVehicle = entity.getVehicle();
        while (currentVehicle != null) {
            if (currentVehicle instanceof Player) {
                vehicleCount++;
            }
            currentVehicle = currentVehicle.getVehicle();
        }
        return vehicleCount;
    }

    public static long getEntityPassengerCount(Entity entity) {
        long passengerCount = 0;
        for (Entity currentPassenger : entity.getPassengers()) {
            if (currentPassenger instanceof Player) {
                passengerCount++;
            }
            passengerCount += getEntityPassengerCount(currentPassenger);
        }
        return passengerCount;
    }

    public static boolean isEntityInPassengerList(Entity entity, Entity passenger) {
        List<Entity> currentPassengers = entity.getPassengers();
        if (currentPassengers.contains(passenger)) {
            return true;
        }
        for (Entity currentPassenger : currentPassengers) {
            if (isEntityInPassengerList(currentPassenger, passenger)) {
                return true;
            }
        }
        return false;
    }

    public static Entity getTopEntityPassenger(Entity entity) {
        Entity current = entity;
        while (current != null && !current.getPassengers().isEmpty()) {
            current = current.getPassengers().getFirst();
        }
        return current;
    }

    public static Entity getBottomEntityVehicle(Entity entity) {
        Entity current = entity;
        while (current != null && current.getVehicle() != null) {
            current = current.getVehicle();
        }
        return current;
    }
}
