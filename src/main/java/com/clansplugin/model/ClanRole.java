package com.clansplugin.model;

public enum ClanRole {
    LEADER(3),
    OFFICER(2),
    MEMBER(1);

    private final int power;

    ClanRole(int power) {
        this.power = power;
    }

    public int getPower() {
        return power;
    }

    public boolean isHigherThan(ClanRole other) {
        return this.power > other.power;
    }
}
