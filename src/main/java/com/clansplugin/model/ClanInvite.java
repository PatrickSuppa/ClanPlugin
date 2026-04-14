package com.clansplugin.model;

import java.util.UUID;

public record ClanInvite(long clanId, UUID invitedUuid, String invitedName, UUID invitedByUuid, long expiresAt) {
    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
}
