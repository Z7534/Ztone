package de.Z7534.ztone.pvpmanager.hooks;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import org.bukkit.Location;

import java.lang.reflect.Method;

public class WorldGuardHook {

    /**
     * ALLOW  -> Region hat den pvp-Flag explizit auf ALLOW gesetzt.
     * DENY   -> Region hat den pvp-Flag explizit auf DENY gesetzt (WorldGuard cancelt den Schaden selbst).
     * UNDEFINED -> Kein Flag an dieser Position gesetzt (oder WorldGuard/die Abfrage nicht verfuegbar) -
     *              PvPManager soll sich hier wie gewohnt verhalten (eigene Toggles/Dimension-Settings).
     */
    public enum PvpState {
        ALLOW, DENY, UNDEFINED
    }

    private final Pvpmanager plugin;
    private boolean available;

    public WorldGuardHook(Pvpmanager plugin) {
        this.plugin = plugin;
        try {
            Class.forName("com.sk89q.worldguard.WorldGuard");
            this.available = true;
        } catch (ClassNotFoundException exception) {
            this.available = false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Fragt den pvp-Flag von WorldGuard an der angegebenen Position ab.
     * Liefert UNDEFINED zurueck, wenn WorldGuard nicht verfuegbar ist, kein Flag gesetzt ist,
     * oder die Reflection-Abfrage fehlschlaegt (z. B. wegen einer API-Aenderung) - in dem Fall
     * soll PvPManager NIE selbststaendig PvP verbieten, sondern einfach sein normales Verhalten
     * beibehalten.
     */
    public PvpState queryPvpState(Location location) {
        if (!available) {
            return PvpState.UNDEFINED;
        }
        try {
            Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object worldGuard = worldGuardClass.getMethod("getInstance").invoke(null);
            Object platform = worldGuardClass.getMethod("getPlatform").invoke(worldGuard);
            Object regionContainer = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Object query = regionContainer.getClass().getMethod("createQuery").invoke(regionContainer);

            Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object adaptedLocation = adapterClass.getMethod("adapt", Location.class).invoke(null, location);

            Class<?> flagsClass = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
            Object pvpFlag = flagsClass.getField("PVP").get(null);

            Method queryStateMethod = findQueryStateMethod(query);
            // WICHTIG: queryState(Location, RegionAssociable, StateFlag...) ist eine varargs-Methode.
            // Bei Method.invoke() wird das NICHT automatisch in ein Array gepackt wie bei normalem Java-Code -
            // man muss das Array fuer den letzten (varargs-)Parameter selbst bauen, sonst wirft invoke() ein
            // IllegalArgumentException ("argument type mismatch"), das hier vorher lautlos im catch-Block
            // verschluckt wurde und die Abfrage IMMER auf UNDEFINED fallen liess (weder ALLOW noch DENY
            // wurden je erkannt).
            Class<?> varargsArrayType = queryStateMethod.getParameterTypes()[2];
            Object flagsArray = java.lang.reflect.Array.newInstance(varargsArrayType.getComponentType(), 1);
            java.lang.reflect.Array.set(flagsArray, 0, pvpFlag);
            Object state = queryStateMethod.invoke(query, adaptedLocation, null, flagsArray);

            if (state == null) {
                // Kein Flag an dieser Stelle gesetzt (weder ALLOW noch DENY) -> undefiniert
                return PvpState.UNDEFINED;
            }

            Class<?> stateClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag$State");
            Object allowState = stateClass.getMethod("valueOf", String.class).invoke(null, "ALLOW");
            Object denyState = stateClass.getMethod("valueOf", String.class).invoke(null, "DENY");

            if (state == allowState) {
                return PvpState.ALLOW;
            }
            if (state == denyState) {
                return PvpState.DENY;
            }
            return PvpState.UNDEFINED;
        } catch (Throwable throwable) {
            logQueryFailureOnce(throwable);
            return PvpState.UNDEFINED;
        }
    }

    private boolean queryFailureLogged;

    /** Loggt den ersten Fehler sichtbar (WARNING), damit eine kaputte Abfrage nicht mehr lautlos auf UNDEFINED faellt. */
    private void logQueryFailureOnce(Throwable throwable) {
        if (queryFailureLogged) {
            return;
        }
        queryFailureLogged = true;
        plugin.getLogger().warning("WorldGuard-pvp-Flag-Abfrage fehlgeschlagen (" + throwable + ") - "
                + "PvPManager verhaelt sich bis zum Neustart so, als waere an der jeweiligen Position kein Flag gesetzt "
                + "(weitere Fehler dieser Art werden nicht mehr geloggt).");
    }

    /**
     * Bequemlichkeitsmethode fuer Aufrufer, die nur wissen wollen, ob PvP an dieser Stelle
     * per Flag erzwungen ALLOW ist (bestehendes Verhalten, z. B. fuer "PvP Toggle.WorldGuard Overrides").
     */
    public boolean isPvpForceAllowed(Location location) {
        return queryPvpState(location) == PvpState.ALLOW;
    }

    /**
     * true, wenn WorldGuard an dieser Stelle PvP per Flag explizit VERBIETET.
     * WorldGuard cancelt den Schaden in diesem Fall bereits selbst - PvPManager darf hier
     * unter keinen Umstaenden trotzdem einen Combat-Tag setzen (ausser bei /pvpoverride).
     */
    public boolean isPvpExplicitlyDenied(Location location) {
        return queryPvpState(location) == PvpState.DENY;
    }

    private Method findQueryStateMethod(Object query) {
        for (Method method : query.getClass().getMethods()) {
            if (method.getName().equals("queryState") && method.getParameterCount() == 3) {
                return method;
            }
        }
        throw new IllegalStateException("queryState-Methode auf WorldGuard RegionQuery nicht gefunden");
    }
}
