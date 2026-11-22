package de.Z7534.ztone.stonesit.util;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PassengerUtil {

    // Maximale Stack-Tiefe zur Vorbeugung von Schleifen und StackOverflowErrors
    public static final int MAX_STACK_DEPTH = 16;

    private PassengerUtil() {
    }

    public static long getEntityVehicleCount(Entity entity) {
        if (entity == null) return 0;
        long vehicleCount = 0;
        Set<UUID> visited = new HashSet<>();
        Entity currentVehicle = entity.getVehicle();
        while (currentVehicle != null && visited.add(currentVehicle.getUniqueId()) && visited.size() <= MAX_STACK_DEPTH) {
            if (currentVehicle instanceof Player) {
                vehicleCount++;
            }
            currentVehicle = currentVehicle.getVehicle();
        }
        return vehicleCount;
    }

    public static long getEntityPassengerCount(Entity entity) {
        return getEntityPassengerCount(entity, new HashSet<>());
    }

    private static long getEntityPassengerCount(Entity entity, Set<UUID> visited) {
        if (entity == null || !visited.add(entity.getUniqueId()) || visited.size() > MAX_STACK_DEPTH) {
            return 0;
        }
        long passengerCount = 0;
        for (Entity currentPassenger : entity.getPassengers()) {
            if (currentPassenger instanceof Player) {
                passengerCount++;
            }
            passengerCount += getEntityPassengerCount(currentPassenger, visited);
        }
        return passengerCount;
    }

    public static boolean isEntityInPassengerList(Entity entity, Entity passenger) {
        return isEntityInPassengerList(entity, passenger, new HashSet<>());
    }

    private static boolean isEntityInPassengerList(Entity entity, Entity passenger, Set<UUID> visited) {
        if (entity == null || passenger == null || !visited.add(entity.getUniqueId()) || visited.size() > MAX_STACK_DEPTH) {
            return false;
        }
        List<Entity> currentPassengers = entity.getPassengers();
        if (currentPassengers.contains(passenger)) {
            return true;
        }
        for (Entity currentPassenger : currentPassengers) {
            if (isEntityInPassengerList(currentPassenger, passenger, visited)) {
                return true;
            }
        }
        return false;
    }

    public static Entity getTopEntityPassenger(Entity entity) {
        if (entity == null) return null;
        Entity current = entity;
        Set<UUID> visited = new HashSet<>();
        while (current != null && visited.add(current.getUniqueId()) && visited.size() <= MAX_STACK_DEPTH) {
            List<Entity> passengers = current.getPassengers();
            if (passengers.isEmpty()) {
                return current;
            }
            current = passengers.getFirst();
        }
        return current;
    }

    public static Entity getBottomEntityVehicle(Entity entity) {
        if (entity == null) return null;
        Entity current = entity;
        Set<UUID> visited = new HashSet<>();
        while (current != null && visited.add(current.getUniqueId()) && visited.size() <= MAX_STACK_DEPTH) {
            Entity vehicle = current.getVehicle();
            if (vehicle == null) {
                return current;
            }
            current = vehicle;
        }
        return current;
    }

    /**
     * Prüft rigoros, ob rider sicher auf vehicle aufsitzen kann, ohne eine Schleife
     * (A sitzt auf B sitzt auf A) oder eine Überschreitung der maximalen Tiefe zu erzeugen.
     */
    public static boolean canMount(Entity rider, Entity vehicle) {
        if (rider == null || vehicle == null || rider.equals(vehicle)) {
            return false;
        }

        // 1. Prüfe, ob rider bereits irgendwo im Fahrzeug-Baum von vehicle vorkommt (vehicle reitet direkt oder indirekt auf rider)
        Set<UUID> visitedVehicles = new HashSet<>();
        int depth = 0;
        for (Entity e = vehicle; e != null; e = e.getVehicle()) {
            if (e.equals(rider)) {
                return false;
            }
            if (!visitedVehicles.add(e.getUniqueId()) || ++depth > MAX_STACK_DEPTH) {
                return false;
            }
        }

        // 2. Prüfe, ob vehicle bereits irgendwo in den Passagieren von rider vorkommt
        if (isEntityInPassengerList(rider, vehicle)) {
            return false;
        }

        // 3. Prüfe, ob rider bereits in den Passagieren von vehicle vorkommt
        if (isEntityInPassengerList(vehicle, rider)) {
            return false;
        }

        return true;
    }
}
