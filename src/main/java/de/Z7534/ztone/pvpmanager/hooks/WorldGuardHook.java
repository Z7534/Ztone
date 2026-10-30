package de.Z7534.ztone.pvpmanager.hooks;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import org.bukkit.Location;

import java.lang.reflect.Array;
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

    // ---- Reflection-Cache --------------------------------------------------------------------
    // Alle Reflection-Lookups (Class.forName, getMethod, getMethods, getField, valueOf) werden nur
    // EINMAL beim ersten Aufruf aufgeloest und danach wiederverwendet. Vorher lief z. B.
    // Class.getMethods() bei JEDEM Schaden-Event (mehrfach pro Hit), was unnoetig teuer war.
    private volatile boolean reflectionResolved;
    private volatile boolean reflectionBroken;
    private Object regionContainer;
    private Method createQueryMethod;
    private Method adaptLocationMethod;
    private Method queryStateMethod;
    private Class<?> flagComponentType;
    private Object pvpFlag;
    private Object allowState;
    private Object denyState;

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
     * Loest alle benoetigten WorldGuard-Klassen/Methoden einmalig auf und cached sie.
     * Wird lazy beim ersten Query aufgerufen (WorldGuard ist dann sicher fertig geladen).
     */
    private synchronized void resolveReflection() {
        if (reflectionResolved || reflectionBroken) {
            return;
        }
        try {
            Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object worldGuard = worldGuardClass.getMethod("getInstance").invoke(null);
            Object platform = worldGuardClass.getMethod("getPlatform").invoke(worldGuard);
            Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Method createQuery = container.getClass().getMethod("createQuery");

            Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Method adapt = adapterClass.getMethod("adapt", Location.class);

            Class<?> flagsClass = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
            Object flag = flagsClass.getField("PVP").get(null);

            // queryState-Methode einmalig ueber den Rueckgabetyp von createQuery() suchen
            Method queryState = findQueryStateMethod(createQuery.getReturnType());
            // WICHTIG: queryState(Location, RegionAssociable, StateFlag...) ist eine varargs-Methode.
            // Bei Method.invoke() muss das Array fuer den letzten (varargs-)Parameter selbst gebaut werden.
            Class<?> componentType = queryState.getParameterTypes()[2].getComponentType();

            Class<?> stateClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag$State");
            Method valueOf = stateClass.getMethod("valueOf", String.class);

            this.regionContainer = container;
            this.createQueryMethod = createQuery;
            this.adaptLocationMethod = adapt;
            this.pvpFlag = flag;
            this.queryStateMethod = queryState;
            this.flagComponentType = componentType;
            this.allowState = valueOf.invoke(null, "ALLOW");
            this.denyState = valueOf.invoke(null, "DENY");
            this.reflectionResolved = true;
        } catch (Throwable throwable) {
            this.reflectionBroken = true;
            logQueryFailureOnce(throwable);
        }
    }

    /**
     * Fragt den pvp-Flag von WorldGuard an der angegebenen Position ab.
     * Liefert UNDEFINED zurueck, wenn WorldGuard nicht verfuegbar ist, kein Flag gesetzt ist,
     * oder die Reflection-Abfrage fehlschlaegt (z. B. wegen einer API-Aenderung) - in dem Fall
     * soll PvPManager NIE selbststaendig PvP verbieten, sondern einfach sein normales Verhalten
     * beibehalten.
     */
    public PvpState queryPvpState(Location location) {
        if (!available || location == null) {
            return PvpState.UNDEFINED;
        }
        if (!reflectionResolved) {
            resolveReflection();
            if (!reflectionResolved) {
                return PvpState.UNDEFINED;
            }
        }
        try {
            Object query = createQueryMethod.invoke(regionContainer);
            Object adaptedLocation = adaptLocationMethod.invoke(null, location);

            Object flagsArray = Array.newInstance(flagComponentType, 1);
            Array.set(flagsArray, 0, pvpFlag);
            Object state = queryStateMethod.invoke(query, adaptedLocation, null, flagsArray);

            if (state == null) {
                // Kein Flag an dieser Stelle gesetzt (weder ALLOW noch DENY) -> undefiniert
                return PvpState.UNDEFINED;
            }
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

    private Method findQueryStateMethod(Class<?> queryClass) {
        for (Method method : queryClass.getMethods()) {
            if (method.getName().equals("queryState") && method.getParameterCount() == 3
                    && method.getParameterTypes()[2].isArray()) {
                return method;
            }
        }
        throw new IllegalStateException("queryState-Methode auf WorldGuard RegionQuery nicht gefunden");
    }
}
