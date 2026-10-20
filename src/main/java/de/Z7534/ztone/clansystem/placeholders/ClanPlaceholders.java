package de.Z7534.ztone.clansystem.placeholders;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.managers.LevelManager;
import de.Z7534.ztone.clansystem.models.Clan;
import de.Z7534.ztone.clansystem.models.ClanMember;
import de.Z7534.ztone.clansystem.utils.ColorUtils;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class ClanPlaceholders extends PlaceholderExpansion {

    private final Ztone plugin;
    private final String identifier;

    public ClanPlaceholders(Ztone plugin) {
        this(plugin, "clansystem");
    }

    public ClanPlaceholders(Ztone plugin, String identifier) {
        this.plugin = plugin;
        this.identifier = (identifier != null && !identifier.isBlank()) ? identifier : "clansystem";
    }

    @Override
    public @NotNull String getIdentifier() {
        return identifier;
    }

    @Override
    public @NotNull String getAuthor() {
        return "Z7534";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        String lower = params.toLowerCase();

        // PvP & Dimensions-Platzhalter
        if (lower.equals("dimension_pvp") || lower.equals("world_pvp")) {
            if (offlinePlayer instanceof Player player) {
                return String.valueOf(plugin.isDimensionPvPEnabled(player.getWorld()));
            }
            return String.valueOf(plugin.isDimensionPvPEnabled("minecraft:overworld"));
        }

        if (lower.startsWith("dimension_pvp_") || lower.startsWith("world_pvp_")) {
            String dim = params.substring(params.indexOf('_', params.indexOf('_') + 1) + 1);
            return String.valueOf(plugin.isDimensionPvPEnabled(dim));
        }

        if (lower.equals("dimension_forced") || lower.equals("world_forced")
                || lower.equals("dimension_force") || lower.equals("world_force")
                || lower.equals("force") || lower.equals("is_forced")) {
            if (offlinePlayer instanceof Player player) {
                return String.valueOf(plugin.isDimensionPvPForced(player.getWorld()));
            }
            return "false";
        }

        if (lower.startsWith("dimension_forced_") || lower.startsWith("world_forced_")
                || lower.startsWith("dimension_force_") || lower.startsWith("world_force_")) {
            String dim = params.substring(params.indexOf('_', params.indexOf('_') + 1) + 1);
            return String.valueOf(plugin.isDimensionPvPForced(dim));
        }

        if (lower.equals("can_pvp")) {
            if (offlinePlayer instanceof Player player) {
                return String.valueOf(plugin.canPvP(player));
            }
            return "false";
        }

        if (offlinePlayer == null) {
            return "";
        }

        Clan clan = plugin.getClanManager() != null ? plugin.getClanManager().getClanByPlayer(offlinePlayer.getUniqueId()) : null;

        return switch (lower) {
            // Suffix mit Farbe (Standard bei ohne _raw)
            case "clan_suffix", "suffix", "clan_tag", "tag",
                 "suffix_colored", "clan_suffix_colored", "tag_colored", "clan_tag_colored" -> {
                if (clan == null || clan.getSuffix() == null || clan.getSuffix().isEmpty()) {
                    yield "";
                }
                yield ColorUtils.translateColors(clan.getColoredSuffix());
            }

            // Suffix mit Farbe & Klammern [&r...&8] 
            case "suffix_formatted", "clan_suffix_formatted", "tag_formatted", "clan_tag_formatted" -> {
                if (clan == null || clan.getSuffix() == null || clan.getSuffix().isEmpty()) {
                    yield "";
                }
                yield ColorUtils.translateColors("&8[&r" + clan.getColoredSuffix() + "&8] ");
            }

            // Suffix als reiner Rohtext (nur bei expliziter _raw Angabe)
            case "clan_suffix_raw", "suffix_raw", "clan_tag_raw", "tag_raw" -> {
                if (clan == null || clan.getSuffix() == null) {
                    yield "";
                }
                yield clan.getSuffix();
            }

            // Clan-Name mit Farbe (Standard bei ohne _raw)
            case "clan_name", "name", "clan",
                 "clan_name_colored", "name_colored", "clan_colored" -> {
                if (clan == null) {
                    yield "";
                }
                yield ColorUtils.translateColors(clan.getColoredName());
            }

            // Clan-Name als reiner Rohtext (nur bei expliziter _raw Angabe)
            case "clan_name_raw", "name_raw", "clan_raw" -> {
                if (clan == null) {
                    yield "";
                }
                yield clan.getName();
            }

            case "clan_level", "level" -> {
                yield clan != null ? String.valueOf(clan.getLevel()) : "0";
            }
            case "clan_points", "points" -> {
                yield clan != null ? String.valueOf(clan.getPoints()) : "0";
            }

            // Rang mit Farbe (Standard)
            case "clan_rank", "rank", "clan_rank_colored", "rank_colored" -> {
                if (clan == null) {
                    yield "";
                }
                ClanMember member = clan.getMember(offlinePlayer.getUniqueId());
                yield member != null ? ColorUtils.translateColors(member.getDisplayRankName()) : "";
            }

            // Rang als Rohtext (nur bei explizitem _raw)
            case "clan_rank_raw", "rank_raw" -> {
                if (clan == null) {
                    yield "";
                }
                ClanMember member = clan.getMember(offlinePlayer.getUniqueId());
                yield member != null ? member.getDisplayRankName() : "";
            }

            case "clan_membercount", "membercount", "members", "clan_members" -> {
                yield clan != null ? String.valueOf(clan.getMemberCount()) : "0";
            }
            case "clan_maxmembers", "maxmembers", "max_members" -> {
                if (clan == null) {
                    yield "0";
                }
                LevelManager levelManager = plugin.getLevelManager();
                yield String.valueOf(levelManager.getMaxMembers(clan.getLevel()));
            }
            case "balance", "clan_balance", "kasse", "clankasse" -> {
                if (clan == null) {
                    yield "0.00";
                }
                yield String.format(Locale.US, "%.2f", clan.getBalance());
            }
            case "debt", "clan_debt", "schulden", "clan_schulden" -> {
                if (clan == null) {
                    yield "0.00";
                }
                yield String.format(Locale.US, "%.2f", clan.getDebt());
            }
            case "in_clan", "has_clan" -> {
                yield clan != null ? "true" : "false";
            }
            case "leader", "clan_leader" -> {
                if (clan == null || clan.getLeaderUuid() == null) {
                    yield "";
                }
                ClanMember leaderMember = clan.getMember(clan.getLeaderUuid());
                if (leaderMember != null) {
                    yield leaderMember.getName();
                }
                String offlineName = Bukkit.getOfflinePlayer(clan.getLeaderUuid()).getName();
                yield offlineName != null ? offlineName : "";
            }
            case "allies", "allies_count", "clan_allies" -> {
                yield clan != null ? String.valueOf(clan.getAllies().size()) : "0";
            }
            case "wars", "wars_count", "clan_wars" -> {
                yield (clan != null && plugin.getWarManager() != null)
                        ? String.valueOf(plugin.getWarManager().getActiveWarsForClan(clan.getId()).size()) : "0";
            }
            case "color", "clan_color" -> {
                yield clan != null ? clan.getNameColor() : "";
            }
            default -> null;
        };
    }
}
