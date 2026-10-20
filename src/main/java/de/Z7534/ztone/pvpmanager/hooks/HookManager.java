package de.Z7534.ztone.pvpmanager.hooks;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import org.bukkit.Bukkit;

public class HookManager {

    private final Pvpmanager plugin;

    private WorldGuardHook worldGuardHook;
    private ProtocolLibHook protocolLibHook;
    private PlaceholderApiHook placeholderApiHook;
    private final ThirdPartyHook cmiHook;
    private final ThirdPartyHook essentialsHook;
    private final ThirdPartyHook bountyHuntersHook;
    private final ThirdPartyHook notBountiesHook;
    private final ThirdPartyHook landsHook;
    private final ThirdPartyHook kingdomsHook;
    private final ThirdPartyHook simpleClansHook;
    private final ThirdPartyHook townyHook;
    private final ThirdPartyHook huskTownsHook;
    private final ThirdPartyHook tabHook;
    private final ThirdPartyHook libsDisguisesHook;

    public HookManager(Pvpmanager plugin) {
        this.plugin = plugin;
        this.cmiHook = new ThirdPartyHook(plugin, "CMI");
        this.essentialsHook = new ThirdPartyHook(plugin, "Essentials");
        this.bountyHuntersHook = new ThirdPartyHook(plugin, "BountyHunters");
        this.notBountiesHook = new ThirdPartyHook(plugin, "NotBounties");
        this.landsHook = new ThirdPartyHook(plugin, "Lands");
        this.kingdomsHook = new ThirdPartyHook(plugin, "Kingdoms");
        this.simpleClansHook = new ThirdPartyHook(plugin, "SimpleClans");
        this.townyHook = new ThirdPartyHook(plugin, "Towny");
        this.huskTownsHook = new ThirdPartyHook(plugin, "HuskTowns");
        this.tabHook = new ThirdPartyHook(plugin, "TAB");
        this.libsDisguisesHook = new ThirdPartyHook(plugin, "LibsDisguises");
    }

    public void setupAll() {
        this.worldGuardHook = new WorldGuardHook(plugin);
        if (worldGuardHook.isAvailable()) {
            plugin.getLogger().info("WorldGuard gefunden - Hook aktiviert.");
        }
        this.protocolLibHook = new ProtocolLibHook();
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            this.placeholderApiHook = new PlaceholderApiHook(plugin);
            this.placeholderApiHook.register();
            plugin.getLogger().info("PlaceholderAPI gefunden - Platzhalter registriert.");
        }
        logIfPresent(cmiHook);
        logIfPresent(essentialsHook);
        logIfPresent(bountyHuntersHook);
        logIfPresent(notBountiesHook);
        logIfPresent(landsHook);
        logIfPresent(kingdomsHook);
        logIfPresent(simpleClansHook);
        logIfPresent(townyHook);
        logIfPresent(huskTownsHook);
        logIfPresent(tabHook);
        logIfPresent(libsDisguisesHook);
    }

    private void logIfPresent(ThirdPartyHook hook) {
        if (hook.isPresent()) {
            plugin.getLogger().info(hook.getPluginName() + " gefunden - Hook aktiv (" + hook.getPluginName() + ").");
        }
    }

    public WorldGuardHook worldGuard() {
        return worldGuardHook;
    }

    public ProtocolLibHook protocolLib() {
        return protocolLibHook;
    }

    public PlaceholderApiHook placeholderApi() {
        return placeholderApiHook;
    }

    public ThirdPartyHook cmi() {
        return cmiHook;
    }

    public ThirdPartyHook essentials() {
        return essentialsHook;
    }

    public ThirdPartyHook bountyHunters() {
        return bountyHuntersHook;
    }

    public ThirdPartyHook notBounties() {
        return notBountiesHook;
    }

    public ThirdPartyHook lands() {
        return landsHook;
    }

    public ThirdPartyHook kingdoms() {
        return kingdomsHook;
    }

    public ThirdPartyHook simpleClans() {
        return simpleClansHook;
    }

    public ThirdPartyHook towny() {
        return townyHook;
    }

    public ThirdPartyHook huskTowns() {
        return huskTownsHook;
    }

    public ThirdPartyHook tab() {
        return tabHook;
    }

    public ThirdPartyHook libsDisguises() {
        return libsDisguisesHook;
    }
}
