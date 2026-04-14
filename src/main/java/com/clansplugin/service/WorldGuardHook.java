package com.clansplugin.service;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.clansplugin.ClansPlugin;
import com.clansplugin.model.Clan;
import com.clansplugin.model.ClanMember;
import com.clansplugin.model.ClanRole;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.Locale;
import java.util.UUID;

public class WorldGuardHook {
    private final ClansPlugin plugin;
    private final boolean available;

    public WorldGuardHook(ClansPlugin plugin) {
        this.plugin = plugin;
        this.available = Bukkit.getPluginManager().getPlugin("WorldGuard") instanceof WorldGuardPlugin;
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isEnabledForClaims() {
        return available && plugin.getConfig().getString("territories.mode", "WORLDGUARD").equalsIgnoreCase("WORLDGUARD");
    }

    public String getRegionId(Chunk chunk) {
        return regionId(chunk);
    }

    public boolean createOrUpdateClaim(Clan clan, Chunk chunk) {
        if (!isEnabledForClaims()) {
            return false;
        }

        RegionManager regionManager = getRegionManager(chunk.getWorld());
        if (regionManager == null) {
            return false;
        }

        int minY = chunk.getWorld().getMinHeight();
        int maxY = chunk.getWorld().getMaxHeight() - 1;
        int minX = chunk.getX() << 4;
        int minZ = chunk.getZ() << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;

        ProtectedCuboidRegion region = new ProtectedCuboidRegion(
                regionId(chunk),
                BlockVector3.at(minX, minY, minZ),
                BlockVector3.at(maxX, maxY, maxZ)
        );

        applyFlags(region);
        syncMembers(clan, region);
        regionManager.addRegion(region);
        try {
            regionManager.save();
        } catch (Exception exception) {
            plugin.getLogger().warning("Impossibile salvare i dati di WorldGuard: " + exception.getMessage());
        }
        return true;
    }

    public boolean removeClaim(Chunk chunk) {
        if (!isEnabledForClaims()) {
            return false;
        }

        RegionManager regionManager = getRegionManager(chunk.getWorld());
        if (regionManager == null) {
            return false;
        }

        try {
            regionManager.removeRegion(regionId(chunk));
            regionManager.save();
            return true;
        } catch (Exception exception) {
            plugin.getLogger().warning("Errore rimozione regione WorldGuard: " + exception.getMessage());
            return false;
        }
    }

    public void syncClaimMembers(Clan clan, Chunk chunk) {
        if (!isEnabledForClaims()) {
            return;
        }
        RegionManager regionManager = getRegionManager(chunk.getWorld());
        if (regionManager == null) {
            return;
        }
        ProtectedRegion region = regionManager.getRegion(regionId(chunk));
        if (region == null) {
            createOrUpdateClaim(clan, chunk);
            return;
        }
        syncMembers(clan, region);
        applyFlags(region);
        try {
            regionManager.save();
        } catch (Exception exception) {
            plugin.getLogger().warning("Impossibile salvare i dati di WorldGuard: " + exception.getMessage());
        }
    }

    private void syncMembers(Clan clan, ProtectedRegion region) {
        region.getMembers().clear();
        region.getOwners().clear();
        for (ClanMember member : clan.getMembers()) {
            UUID uuid = member.getUuid();
            region.getMembers().addPlayer(uuid);
            if (member.getRole() == ClanRole.LEADER) {
                region.getOwners().addPlayer(uuid);
            }
        }
    }

    private void applyFlags(ProtectedRegion region) {
        if (plugin.getConfig().getBoolean("territories.protect-build", true)) {
            region.setFlag(Flags.BUILD, com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);
            region.setFlag(Flags.BLOCK_BREAK, com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);
            region.setFlag(Flags.BLOCK_PLACE, com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);
        }
        if (plugin.getConfig().getBoolean("territories.protect-containers", true)) {
            region.setFlag(Flags.CHEST_ACCESS, com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);
            region.setFlag(Flags.USE, com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);
        }
        region.setFlag(Flags.PVP, plugin.getConfig().getBoolean("territories.allow-pvp", false)
                ? com.sk89q.worldguard.protection.flags.StateFlag.State.ALLOW
                : com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);
        if (plugin.getConfig().getBoolean("territories.block-mob-spawning", true)) {
            region.setFlag(Flags.MOB_SPAWNING, com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);
        }
        region.setPriority(plugin.getConfig().getInt("territories.worldguard-priority", 10));
    }

    private RegionManager getRegionManager(World world) {
        return WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
    }

    public static String regionId(Chunk chunk) {
        return String.format(Locale.ROOT, "clan_%s_%d_%d", sanitizeWorld(chunk.getWorld().getName()), chunk.getX(), chunk.getZ());
    }

    private static String sanitizeWorld(String worldName) {
        return worldName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }
}
