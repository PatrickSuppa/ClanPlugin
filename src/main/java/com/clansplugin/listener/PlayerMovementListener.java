package com.clansplugin.listener;

import com.clansplugin.config.MessageManager;
import com.clansplugin.service.TeleportService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class PlayerMovementListener implements Listener {
    private final TeleportService teleportService;
    private final MessageManager messages;

    public PlayerMovementListener(TeleportService teleportService, MessageManager messages) {
        this.teleportService = teleportService;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        boolean hadPending = teleportService.hasPending(event.getPlayer().getUniqueId());
        teleportService.cancelIfMoved(event.getPlayer());
        if (hadPending && !teleportService.hasPending(event.getPlayer().getUniqueId())) {
            messages.send(event.getPlayer(), "teleport-cancelled");
        }
    }
}
