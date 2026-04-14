package com.clansplugin.listener;

import com.clansplugin.service.ClanService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

@SuppressWarnings("deprecation")
public class ChatListener implements Listener {
    private final ClanService service;

    public ChatListener(ClanService service) {
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!service.isClanChatEnabled(player.getUniqueId())) {
            return;
        }
        if (service.getClanByPlayer(player.getUniqueId()) == null) {
            return;
        }
        event.setCancelled(true);
        service.sendClanMessage(player, event.getMessage());
    }
}
