package com.clansplugin.model;

import org.bukkit.Location;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Clan {
    private final long id;
    private final String name;
    private final String tag;
    private UUID leaderUuid;
    private Location home;
    private final Timestamp createdAt;
    private final Map<UUID, ClanMember> members = new ConcurrentHashMap<>();

    public Clan(long id, String name, String tag, UUID leaderUuid, Location home, Timestamp createdAt) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.leaderUuid = leaderUuid;
        this.home = home;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTag() {
        return tag;
    }

    public UUID getLeaderUuid() {
        return leaderUuid;
    }

    public void setLeaderUuid(UUID leaderUuid) {
        this.leaderUuid = leaderUuid;
    }

    public Location getHome() {
        return home;
    }

    public void setHome(Location home) {
        this.home = home;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public Collection<ClanMember> getMembers() {
        return members.values();
    }

    public ClanMember getMember(UUID uuid) {
        return members.get(uuid);
    }

    public boolean hasMember(UUID uuid) {
        return members.containsKey(uuid);
    }

    public void addMember(ClanMember member) {
        members.put(member.getUuid(), member);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }
}
