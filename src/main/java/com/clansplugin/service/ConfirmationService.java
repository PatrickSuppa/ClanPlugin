package com.clansplugin.service;

import com.clansplugin.ClansPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ConfirmationService {
    private final ClansPlugin plugin;
    private final Map<String, Long> confirmations = new ConcurrentHashMap<>();

    public ConfirmationService(ClansPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean confirmRequired(UUID playerUuid, String action) {
        String key = key(playerUuid, action);
        Long expiresAt = confirmations.get(key);
        long now = System.currentTimeMillis();

        if (expiresAt != null && expiresAt > now) {
            confirmations.remove(key);
            return false;
        }

        confirmations.put(key, now + expireSeconds() * 1000L);
        return true;
    }

    public int expireSeconds() {
        return plugin.getConfig().getInt("confirmations.expire-seconds", 15);
    }

    private String key(UUID uuid, String action) {
        return uuid + ":" + action.toLowerCase();
    }
}
