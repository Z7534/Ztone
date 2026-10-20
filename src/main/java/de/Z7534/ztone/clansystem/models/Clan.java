package de.Z7534.ztone.clansystem.models;

import de.Z7534.ztone.clansystem.utils.ColorUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class Clan {

    public enum JoinType {
        OPEN,
        INVITE,
        APPLY
    }

    private int id;
    private String name;
    private String suffix;
    private int level;
    private int points;
    private UUID leaderUuid;
    private long createdAt;
    private JoinType joinType;
    private String icon;
    private boolean glowEnabled;
    private String glowColor;
    private String nameColor;
    private boolean nameStrikethrough;
    private boolean gradientEnabled;
    private String gradientColorStart;
    private String gradientColorEnd;
    private double balance = 0.0;
    private int bonusWarps = 0;

    private Map<UUID, ClanMember> members;

    private Map<Integer, ClanRank> ranks;

    private Map<String, ClanHome> homes;
    private Map<String, ClanWarp> warps;

    private ItemStack[] chestContents;

    private Set<Integer> allies;
    private Set<Integer> activeWars;

    private Map<UUID, Long> pendingInvites;
    private Map<UUID, Long> pendingApplications;

    public Clan(int id, String name, UUID leaderUuid) {
        this.id = id;
        this.name = name;
        this.suffix = "";
        this.level = 1;
        this.points = 0;
        this.leaderUuid = leaderUuid;
        this.createdAt = System.currentTimeMillis();
        this.joinType = JoinType.INVITE;

        this.icon = "SHIELD";
        this.glowEnabled = false;
        this.glowColor = "WHITE";
        this.nameColor = "&f";
        this.nameStrikethrough = false;
        this.gradientEnabled = false;
        this.gradientColorStart = "#FF0000";
        this.gradientColorEnd = "#0000FF";

        this.members = new HashMap<>();
        this.ranks = new HashMap<>();
        this.homes = new HashMap<>();
        this.warps = new HashMap<>();

        this.chestContents = new ItemStack[9];
        this.allies = new HashSet<>();
        this.activeWars = new HashSet<>();
        this.pendingInvites = new HashMap<>();
        this.pendingApplications = new HashMap<>();
    }

    public Clan(String name, UUID leaderUuid) {
        this(-1, name, leaderUuid);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSuffix() {
        return suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix != null ? suffix : "";
    }

    public String getNameColor() {
        return nameColor;
    }

    public void setNameColor(String nameColor) {
        this.nameColor = nameColor != null ? nameColor : "&f";
    }

    public boolean isNameStrikethrough() {
        return nameStrikethrough;
    }

    public void setNameStrikethrough(boolean nameStrikethrough) {
        this.nameStrikethrough = nameStrikethrough;
    }

    public boolean isGradientEnabled() {
        return gradientEnabled;
    }

    public void setGradientEnabled(boolean gradientEnabled) {
        this.gradientEnabled = gradientEnabled;
    }

    public String getGradientColorStart() {
        return gradientColorStart;
    }

    public void setGradientColorStart(String gradientColorStart) {
        this.gradientColorStart = ColorUtils.isValidHex(gradientColorStart)
                ? ColorUtils.normalizeHex(gradientColorStart) : "#FF0000";
    }

    public String getGradientColorEnd() {
        return gradientColorEnd;
    }

    public void setGradientColorEnd(String gradientColorEnd) {
        this.gradientColorEnd = ColorUtils.isValidHex(gradientColorEnd)
                ? ColorUtils.normalizeHex(gradientColorEnd) : "#0000FF";
    }

    /**
     * Liefert den vollständig eingefärbten Clan-Namen. Ist ein Gradient aktiviert,
     * wird der Name mit einem Farbverlauf von der Start- zur Endfarbe dargestellt,
     * andernfalls mit der einfachen Clan-Farbe (nameColor).
     */
    public String getColoredName() {
        if (gradientEnabled) {
            String formatting = nameStrikethrough ? "&m" : "";
            return ColorUtils.applyGradient(name, gradientColorStart, gradientColorEnd, formatting);
        }
        return nameColor + (nameStrikethrough ? "&m" : "") + name;
    }

    /**
     * Liefert den vollständig eingefärbten Clan-Suffix. Ist ein Gradient aktiviert,
     * wird der Suffix-Text (unter Beibehaltung seiner eigenen Formatierung, z.B. fett)
     * mit einem Farbverlauf dargestellt, andernfalls mit der einfachen Clan-Farbe
     * (nameColor). Durchgestrichen wird - genau wie beim Clan-Namen - zentral über
     * die Clan-Farbe (nameStrikethrough) für beide gemeinsam gesteuert.
     */
    public String getColoredSuffix() {
        if (suffix == null || suffix.isEmpty()) {
            return "";
        }
        if (gradientEnabled) {
            String formatting = ColorUtils.extractFormatCodes(suffix) + (nameStrikethrough ? "&m" : "");
            String plainText = ColorUtils.stripColors(suffix);
            return ColorUtils.applyGradient(plainText, gradientColorStart, gradientColorEnd, formatting);
        }
        return nameColor + (nameStrikethrough ? "&m" : "") + suffix;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public void addPoints(int amount) {
        this.points += amount;
    }

    public UUID getLeaderUuid() {
        return leaderUuid;
    }

    public void setLeaderUuid(UUID leaderUuid) {
        this.leaderUuid = leaderUuid;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public JoinType getJoinType() {
        return joinType;
    }

    public void setJoinType(JoinType joinType) {
        this.joinType = joinType;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public boolean isGlowEnabled() {
        return glowEnabled;
    }

    public void setGlowEnabled(boolean glowEnabled) {
        this.glowEnabled = glowEnabled;
    }

    public String getGlowColor() {
        return glowColor;
    }

    public void setGlowColor(String glowColor) {
        this.glowColor = glowColor != null ? glowColor : "WHITE";
    }

    public Map<UUID, ClanMember> getMembers() {
        return members;
    }

    public ClanMember getMember(UUID uuid) {
        return members.get(uuid);
    }

    public void addMember(ClanMember member) {
        members.put(member.getUuid(), member);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.containsKey(uuid);
    }

    public boolean isLeader(UUID uuid) {
        return leaderUuid.equals(uuid);
    }

    public int getMemberCount() {
        return members.size();
    }

    public List<ClanMember> getOnlineMembers() {
        List<ClanMember> online = new ArrayList<>();
        for (ClanMember member : members.values()) {
            if (member.isOnline()) {
                online.add(member);
            }
        }
        return online;
    }

    public void broadcastMessage(String message) {
        broadcastMessage(message, null);
    }

    public void broadcastMessage(String message, java.util.UUID excludeUuid) {

        net.kyori.adventure.text.Component component = ColorUtils.colorize(message);
        for (ClanMember member : members.values()) {
            if (excludeUuid != null && member.getUuid().equals(excludeUuid)) {
                continue;
            }
            Player player = member.getPlayer();
            if (player != null) {
                player.sendMessage(component);
            }
        }
    }

    public Map<Integer, ClanRank> getRanks() {
        return ranks;
    }

    public ClanRank getRank(int id) {
        return ranks.get(id);
    }

    public ClanRank getRankByName(String name) {
        for (ClanRank rank : ranks.values()) {
            if (rank.getName().equalsIgnoreCase(name)) {
                return rank;
            }
        }
        return null;
    }

    public void addRank(ClanRank rank) {
        ranks.put(rank.getId(), rank);
    }

    public void removeRank(int id) {
        ranks.remove(id);
    }

    public ClanRank getHighestRank() {
        ClanRank highest = null;
        for (ClanRank rank : ranks.values()) {
            if (highest == null || rank.getPriority() > highest.getPriority()) {
                highest = rank;
            }
        }
        return highest;
    }

    public ClanRank getLowestRank() {
        ClanRank lowest = null;
        for (ClanRank rank : ranks.values()) {
            if (lowest == null || rank.getPriority() < lowest.getPriority()) {
                lowest = rank;
            }
        }
        return lowest;
    }

    public List<ClanRank> getSortedRanks() {
        List<ClanRank> sorted = new ArrayList<>(ranks.values());
        sorted.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        return sorted;
    }

    public Map<String, ClanHome> getHomes() {
        return homes;
    }

    public ClanHome getHome(String name) {
        return homes.get(name.toLowerCase());
    }

    public void addHome(ClanHome home) {
        homes.put(home.getName().toLowerCase(), home);
    }

    public void removeHome(String name) {
        homes.remove(name.toLowerCase());
    }

    public int getHomeCount() {
        return homes.size();
    }

    public Map<String, ClanWarp> getWarps() {
        return warps;
    }

    public ClanWarp getWarp(String name) {
        return warps.get(name.toLowerCase());
    }

    public void addWarp(ClanWarp warp) {
        warps.put(warp.getName().toLowerCase(), warp);
    }

    public void removeWarp(String name) {
        warps.remove(name.toLowerCase());
    }

    public int getWarpCount() {
        return warps.size();
    }

    public ItemStack[] getChestContents() {
        return chestContents;
    }

    public void setChestContents(ItemStack[] contents) {
        this.chestContents = contents;
    }

    public int getChestSize() {
        return chestContents != null ? chestContents.length : 0;
    }

    public void resizeChest(int newSize) {
        if (chestContents == null) {
            chestContents = new ItemStack[newSize];
            return;
        }

        ItemStack[] newContents = new ItemStack[newSize];
        int copyLength = Math.min(chestContents.length, newSize);
        System.arraycopy(chestContents, 0, newContents, 0, copyLength);
        chestContents = newContents;
    }

    public Set<Integer> getAllies() {
        return allies;
    }

    public boolean isAlliedWith(int clanId) {
        return allies.contains(clanId);
    }

    public void addAlly(int clanId) {
        allies.add(clanId);
    }

    public void removeAlly(int clanId) {
        allies.remove(clanId);
    }

    public Set<Integer> getActiveWars() {
        return activeWars;
    }

    public boolean isAtWarWith(int clanId) {
        return activeWars.contains(clanId);
    }

    public void addWar(int clanId) {
        activeWars.add(clanId);
    }

    public void removeWar(int clanId) {
        activeWars.remove(clanId);
    }

    public Map<UUID, Long> getPendingInvites() {
        return pendingInvites;
    }

    public boolean hasInvite(UUID uuid) {
        Long time = pendingInvites.get(uuid);
        if (time == null) return false;

        if (System.currentTimeMillis() - time > 300000) {
            pendingInvites.remove(uuid);
            return false;
        }
        return true;
    }

    public void addInvite(UUID uuid) {
        pendingInvites.put(uuid, System.currentTimeMillis());
    }

    public void removeInvite(UUID uuid) {
        pendingInvites.remove(uuid);
    }

    public Map<UUID, Long> getPendingApplications() {
        return pendingApplications;
    }

    public boolean hasApplication(UUID uuid) {
        return pendingApplications.containsKey(uuid);
    }

    public void addApplication(UUID uuid) {
        pendingApplications.put(uuid, System.currentTimeMillis());
    }

    public void removeApplication(UUID uuid) {
        pendingApplications.remove(uuid);
    }

    public boolean hasPermission(UUID uuid, Permission permission) {
        ClanMember member = getMember(uuid);
        if (member == null) return false;
        return member.hasPermission(permission);
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public boolean isInDebt() {
        return this.balance < 0;
    }

    public double getDebt() {
        return this.balance < 0 ? -this.balance : 0.0;
    }

    public int getBonusWarps() {
        return bonusWarps;
    }

    public void setBonusWarps(int bonusWarps) {
        this.bonusWarps = Math.max(0, bonusWarps);
    }

    public boolean hasAnyMemberWithPermission(Permission permission) {
        for (ClanMember member : members.values()) {
            if (member.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasBalance(double amount) {
        return amount > 0 && this.balance >= amount;
    }

    public boolean deposit(double amount) {
        if (amount > 0) {
            boolean wasInDebt = isInDebt();
            this.balance += amount;
            return wasInDebt && !isInDebt();
        }
        return false;
    }

    public boolean withdraw(double amount) {
        if (amount > 0 && this.balance >= amount) {
            this.balance -= amount;
            return true;
        }
        return false;
    }
}
