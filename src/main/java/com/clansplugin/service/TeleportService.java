package com.clansplugin.service;

import com.clansplugin.ClansPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportService {
    private final ClansPlugin plugin;
    private final Map<UUID, PendingTeleport> teleports = new ConcurrentHashMap<>();

    public TeleportService(ClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void schedule(Player player, Location target, Runnable onComplete) {
        int delaySeconds = plugin.getConfig().getInt("homes.teleport-delay-seconds", 3);
        teleports.put(player.getUniqueId(), new PendingTeleport(player.getLocation().clone(), target.clone()));

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            PendingTeleport pending = teleports.remove(player.getUniqueId());
            if (pending == null) {
                return;
            }
            player.teleport(target);
            onComplete.run();
        }, delaySeconds * 20L);
    }

    public void cancelIfMoved(Player player) {
        if (!plugin.getConfig().getBoolean("homes.cancel-on-move", true)) {
            return;
        }

        PendingTeleport pending = teleports.get(player.getUniqueId());
        if (pending == null) {
            return;
        }

        Location origin = pending.origin();
        Location current = player.getLocation();
        if (origin.getBlockX() != current.getBlockX() || origin.getBlockY() != current.getBlockY() || origin.getBlockZ() != current.getBlockZ()) {
            teleports.remove(player.getUniqueId());
        }
    }

    public boolean hasPending(UUID uuid) {
        return teleports.containsKey(uuid);
    }

    private record PendingTeleport(Location origin, Location target) {
    }
}
