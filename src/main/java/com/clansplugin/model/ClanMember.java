package com.clansplugin.model;

import java.util.UUID;

public class ClanMember {
    private final UUID uuid;
    private String name;
    private ClanRole role;

    public ClanMember(UUID uuid, String name, ClanRole role) {
        this.uuid = uuid;
        this.name = name;
        this.role = role;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ClanRole getRole() {
        return role;
    }

    public void setRole(ClanRole role) {
        this.role = role;
    }
}
