package com.clansplugin.listener;

import com.clansplugin.ClansPlugin;
import com.clansplugin.config.MessageManager;
import com.clansplugin.service.ClanService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class ProtectionListener implements Listener {
    private final ClansPlugin plugin;
    private final ClanService service;
    private final MessageManager messages;

    public ProtectionListener(ClansPlugin plugin, ClanService service, MessageManager messages) {
        this.plugin = plugin;
        this.service = service;
        this.messages = messages;
    }

    private boolean canBypass(Player player) {
        return player.hasPermission("clans.admin");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("territories.protect-build", true) || canBypass(event.getPlayer())) {
            return;
        }
        if (!service.canBuild(event.getPlayer().getUniqueId(), event.getBlock().getChunk())) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "territory-break-denied");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("territories.protect-build", true) || canBypass(event.getPlayer())) {
            return;
        }
        if (!service.canBuild(event.getPlayer().getUniqueId(), event.getBlock().getChunk())) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "territory-build-denied");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || canBypass(event.getPlayer())) {
            return;
        }
        if (!plugin.getConfig().getBoolean("territories.protect-containers", true)) {
            return;
        }
        if (!service.canBuild(event.getPlayer().getUniqueId(), event.getClickedBlock().getChunk())) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "territory-interact-denied");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (canBypass(event.getPlayer())) {
            return;
        }
        if (!service.canBuild(event.getPlayer().getUniqueId(), event.getBlock().getChunk())) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "territory-build-denied");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (canBypass(event.getPlayer())) {
            return;
        }
        if (!service.canBuild(event.getPlayer().getUniqueId(), event.getBlock().getChunk())) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "territory-break-denied");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (canBypass(attacker)) {
            return;
        }
        if (!service.canPvp(attacker, victim)) {
            event.setCancelled(true);
            messages.send(attacker, "territory-pvp-denied");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (service.shouldBlockMobSpawning(event.getLocation())) {
            event.setCancelled(true);
        }
    }
}
