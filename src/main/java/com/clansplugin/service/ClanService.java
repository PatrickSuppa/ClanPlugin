package com.clansplugin.service;

import com.clansplugin.ClansPlugin;
import com.clansplugin.config.MessageManager;
import com.clansplugin.db.DatabaseManager;
import com.clansplugin.model.Clan;
import com.clansplugin.model.ClanInvite;
import com.clansplugin.model.ClanMember;
import com.clansplugin.model.ClanRole;
import com.clansplugin.util.ChunkKey;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class ClanService {
    private final ClansPlugin plugin;
    private final DatabaseManager database;
    private final MessageManager messages;
    private final InviteService inviteService;
    private final ConfirmationService confirmationService;
    private final TeleportService teleportService;
    private final WorldGuardHook worldGuardHook;

    private final Map<Long, Clan> clansById = new ConcurrentHashMap<>();
    private final Map<String, Clan> clansByName = new ConcurrentHashMap<>();
    private final Map<String, Clan> clansByTag = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerClan = new ConcurrentHashMap<>();
    private final Map<String, Long> claims = new ConcurrentHashMap<>();
    private final Set<UUID> clanChatToggled = ConcurrentHashMap.newKeySet();

    public ClanService(ClansPlugin plugin,
                       DatabaseManager database,
                       MessageManager messages,
                       InviteService inviteService,
                       ConfirmationService confirmationService,
                       TeleportService teleportService,
                       WorldGuardHook worldGuardHook) {
        this.plugin = plugin;
        this.database = database;
        this.messages = messages;
        this.inviteService = inviteService;
        this.confirmationService = confirmationService;
        this.teleportService = teleportService;
        this.worldGuardHook = worldGuardHook;
    }

    public void loadAllCaches() {
        claims.clear();
        try (Connection connection = database.getConnection()) {
            Map<Long, Clan> localClans = new HashMap<>();

            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM clans");
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    World world = resultSet.getString("home_world") == null ? null : Bukkit.getWorld(resultSet.getString("home_world"));
                    Location home = world == null ? null : new Location(
                            world,
                            resultSet.getDouble("home_x"),
                            resultSet.getDouble("home_y"),
                            resultSet.getDouble("home_z"),
                            resultSet.getFloat("home_yaw"),
                            resultSet.getFloat("home_pitch")
                    );

                    Clan clan = new Clan(
                            resultSet.getLong("id"),
                            resultSet.getString("name"),
                            resultSet.getString("tag"),
                            UUID.fromString(resultSet.getString("leader_uuid")),
                            home,
                            resultSet.getTimestamp("created_at")
                    );
                    localClans.put(clan.getId(), clan);
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM clan_members");
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Clan clan = localClans.get(resultSet.getLong("clan_id"));
                    if (clan == null) {
                        continue;
                    }
                    clan.addMember(new ClanMember(
                            UUID.fromString(resultSet.getString("player_uuid")),
                            resultSet.getString("player_name"),
                            ClanRole.valueOf(resultSet.getString("role"))
                    ));
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM clan_claims");
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    claims.put(
                            ChunkKey.of(resultSet.getString("world"), resultSet.getInt("chunk_x"), resultSet.getInt("chunk_z")),
                            resultSet.getLong("clan_id")
                    );
                }
            }

            clansById.clear();
            clansByName.clear();
            clansByTag.clear();
            playerClan.clear();
            clansById.putAll(localClans);

            for (Clan clan : localClans.values()) {
                clansByName.put(clan.getName().toLowerCase(Locale.ROOT), clan);
                clansByTag.put(clan.getTag().toLowerCase(Locale.ROOT), clan);
                for (ClanMember member : clan.getMembers()) {
                    playerClan.put(member.getUuid(), clan.getId());
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("Errore caricamento cache clan: " + exception.getMessage());
        }
    }

    public void syncAllWorldGuardClaims() {
        if (!worldGuardHook.isEnabledForClaims()) {
            return;
        }
        for (Map.Entry<String, Long> entry : claims.entrySet()) {
            Clan clan = clansById.get(entry.getValue());
            if (clan == null) {
                continue;
            }
            Chunk chunk = getLoadedChunkFromKey(entry.getKey());
            if (chunk != null) {
                worldGuardHook.createOrUpdateClaim(clan, chunk);
            }
        }
    }

    private Chunk getLoadedChunkFromKey(String key) {
        String[] parts = key.split(":", 3);
        if (parts.length != 3) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            int x = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            return world.getChunkAt(x, z);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private List<Chunk> getClanChunks(long clanId) {
        List<Chunk> result = new ArrayList<>();
        for (Map.Entry<String, Long> entry : claims.entrySet()) {
            if (entry.getValue() == clanId) {
                Chunk chunk = getLoadedChunkFromKey(entry.getKey());
                if (chunk != null) {
                    result.add(chunk);
                }
            }
        }
        return result;
    }

    private void syncWorldGuardForClan(Clan clan) {
        if (clan != null && worldGuardHook.isEnabledForClaims()) {
            for (Chunk chunk : getClanChunks(clan.getId())) {
                worldGuardHook.syncClaimMembers(clan, chunk);
            }
        }
    }

    public Clan getClanByPlayer(UUID uuid) {
        Long clanId = playerClan.get(uuid);
        return clanId == null ? null : clansById.get(clanId);
    }

    public Clan getClanByName(String name) {
        return name == null ? null : clansByName.get(name.toLowerCase(Locale.ROOT));
    }

    public Clan getClanByTag(String tag) {
        return tag == null ? null : clansByTag.get(tag.toLowerCase(Locale.ROOT));
    }

    public Clan getClanById(long id) {
        return clansById.get(id);
    }

    public Clan getClanAt(Chunk chunk) {
        Long clanId = claims.get(ChunkKey.of(chunk));
        return clanId == null ? null : clansById.get(clanId);
    }

    public ClanRole getRole(UUID uuid) {
        Clan clan = getClanByPlayer(uuid);
        if (clan == null) {
            return null;
        }
        ClanMember member = clan.getMember(uuid);
        return member == null ? null : member.getRole();
    }

    public boolean isLeader(UUID uuid) {
        return getRole(uuid) == ClanRole.LEADER;
    }

    public boolean isOfficerOrHigher(UUID uuid) {
        ClanRole role = getRole(uuid);
        return role == ClanRole.LEADER || role == ClanRole.OFFICER;
    }

    public boolean isValidClanName(String name) {
        String regex = plugin.getConfig().getString("validation.clan-name-regex", "^[A-Za-z0-9_]{3,32}$");
        return Pattern.compile(regex).matcher(name).matches();
    }

    public boolean isValidClanTag(String tag) {
        String regex = plugin.getConfig().getString("validation.clan-tag-regex", "^[A-Za-z0-9_]{2,10}$");
        return Pattern.compile(regex).matcher(tag).matches();
    }

    public boolean createClan(Player player, String name, String tag) {
        if (getClanByPlayer(player.getUniqueId()) != null) {
            return false;
        }
        if (!isValidClanName(name) || !isValidClanTag(tag)) {
            return false;
        }
        if (getClanByName(name) != null || getClanByTag(tag) != null) {
            return false;
        }

        try (Connection connection = database.getConnection()) {
            long clanId;
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO clans (name, tag, leader_uuid) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            )) {
                statement.setString(1, name);
                statement.setString(2, tag);
                statement.setString(3, player.getUniqueId().toString());
                statement.executeUpdate();

                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        return false;
                    }
                    clanId = keys.getLong(1);
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO clan_members (clan_id, player_uuid, player_name, role) VALUES (?, ?, ?, ?)"
            )) {
                statement.setLong(1, clanId);
                statement.setString(2, player.getUniqueId().toString());
                statement.setString(3, player.getName());
                statement.setString(4, ClanRole.LEADER.name());
                statement.executeUpdate();
            }

            Clan clan = new Clan(clanId, name, tag, player.getUniqueId(), null, new Timestamp(System.currentTimeMillis()));
            clan.addMember(new ClanMember(player.getUniqueId(), player.getName(), ClanRole.LEADER));
            clansById.put(clanId, clan);
            clansByName.put(name.toLowerCase(Locale.ROOT), clan);
            clansByTag.put(tag.toLowerCase(Locale.ROOT), clan);
            playerClan.put(player.getUniqueId(), clanId);
            return true;
        } catch (SQLException exception) {
            plugin.getLogger().warning("Errore creazione clan: " + exception.getMessage());
            return false;
        }
    }

    public boolean disbandClan(Player player) {
        Clan clan = getClanByPlayer(player.getUniqueId());
        if (clan == null || !isLeader(player.getUniqueId())) {
            return false;
        }

        if (worldGuardHook.isEnabledForClaims()) {
            for (Chunk chunk : getClanChunks(clan.getId())) {
                worldGuardHook.removeClaim(chunk);
            }
        }

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM clans WHERE id = ?")) {
            statement.setLong(1, clan.getId());
            statement.executeUpdate();

            for (ClanMember member : clan.getMembers()) {
                playerClan.remove(member.getUuid());
            }
            claims.entrySet().removeIf(entry -> entry.getValue().equals(clan.getId()));
            clansById.remove(clan.getId());
            clansByName.remove(clan.getName().toLowerCase(Locale.ROOT));
            clansByTag.remove(clan.getTag().toLowerCase(Locale.ROOT));
            return true;
        } catch (SQLException exception) {
            plugin.getLogger().warning("Errore scioglimento clan: " + exception.getMessage());
            return false;
        }
    }

    public boolean sendInvite(Player sender, Player target) {
        Clan clan = getClanByPlayer(sender.getUniqueId());
        if (clan == null || !isOfficerOrHigher(sender.getUniqueId())) {
            return false;
        }
        if (getClanByPlayer(target.getUniqueId()) != null) {
            return false;
        }

        ClanInvite invite = new ClanInvite(
                clan.getId(),
                target.getUniqueId(),
                target.getName(),
                sender.getUniqueId(),
                System.currentTimeMillis() + inviteService.expireSeconds() * 1000L
        );
        inviteService.put(invite);
        return true;
    }

    public boolean acceptInvite(Player player) {
        ClanInvite invite = inviteService.get(player.getUniqueId());
        if (invite == null || getClanByPlayer(player.getUniqueId()) != null) {
            return false;
        }

        Clan clan = clansById.get(invite.clanId());
        if (clan == null) {
            return false;
        }

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO clan_members (clan_id, player_uuid, player_name, role) VALUES (?, ?, ?, ?)"
             )) {
            statement.setLong(1, clan.getId());
            statement.setString(2, player.getUniqueId().toString());
            statement.setString(3, player.getName());
            statement.setString(4, ClanRole.MEMBER.name());
            statement.executeUpdate();

            clan.addMember(new ClanMember(player.getUniqueId(), player.getName(), ClanRole.MEMBER));
            playerClan.put(player.getUniqueId(), clan.getId());
            inviteService.remove(player.getUniqueId());
            syncWorldGuardForClan(clan);
            return true;
        } catch (SQLException exception) {
            plugin.getLogger().warning("Errore accettazione invito: " + exception.getMessage());
            return false;
        }
    }

    public Clan denyInvite(Player player) {
        ClanInvite invite = inviteService.get(player.getUniqueId());
        if (invite == null) {
            return null;
        }
        inviteService.remove(player.getUniqueId());
        return clansById.get(invite.clanId());
    }

    public boolean kickMember(Player actor, OfflinePlayer target) {
        Clan clan = getClanByPlayer(actor.getUniqueId());
        if (clan == null || actor.getUniqueId().equals(target.getUniqueId())) {
            return false;
        }

        ClanMember actorMember = clan.getMember(actor.getUniqueId());
        ClanMember targetMember = clan.getMember(target.getUniqueId());
        if (actorMember == null || targetMember == null) {
            return false;
        }
        if (!actorMember.getRole().isHigherThan(targetMember.getRole())) {
            return false;
        }

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM clan_members WHERE clan_id = ? AND player_uuid = ?"
             )) {
            statement.setLong(1, clan.getId());
            statement.setString(2, target.getUniqueId().toString());
            statement.executeUpdate();

            clan.removeMember(target.getUniqueId());
            playerClan.remove(target.getUniqueId());
            syncWorldGuardForClan(clan);
            return true;
        } catch (SQLException exception) {
            plugin.getLogger().warning("Errore kick membro: " + exception.getMessage());
            return false;
        }
    }

    public boolean promote(Player actor, OfflinePlayer target) {
        Clan clan = getClanByPlayer(actor.getUniqueId());
        if (clan == null || !isLeader(actor.getUniqueId())) {
            return false;
        }
        ClanMember member = clan.getMember(target.getUniqueId());
        if (member == null || member.getRole() != ClanRole.MEMBER) {
            return false;
        }
        member.setRole(ClanRole.OFFICER);
        return updateRole(clan, member, ClanRole.OFFICER);
    }

    public boolean demote(Player actor, OfflinePlayer target) {
        Clan clan = getClanByPlayer(actor.getUniqueId());
        if (clan == null || !isLeader(actor.getUniqueId())) {
            return false;
        }
        ClanMember member = clan.getMember(target.getUniqueId());
        if (member == null || member.getRole() != ClanRole.OFFICER) {
            return false;
        }
        member.setRole(ClanRole.MEMBER);
        return updateRole(clan, member, ClanRole.MEMBER);
    }

    private boolean updateRole(Clan clan, ClanMember member, ClanRole newRole) {
        member.setRole(newRole);
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE clan_members SET role = ? WHERE clan_id = ? AND player_uuid = ?"
             )) {
            statement.setString(1, newRole.name());
            statement.setLong(2, clan.getId());
            statement.setString(3, member.getUuid().toString());
            statement.executeUpdate();
            syncWorldGuardForClan(clan);
            return true;
        } catch (SQLException exception) {
            plugin.getLogger().warning("Errore aggiornamento ruolo: " + exception.getMessage());
            return false;
        }
    }

    public boolean claim(Player player) {
        Clan clan = getClanByPlayer(player.getUniqueId());
        if (clan == null || !isOfficerOrHigher(player.getUniqueId())) {
            return false;
        }

        String key = ChunkKey.of(player.getChunk());
        if (claims.containsKey(key)) {
            return false;
        }
        if (getClaimCount(clan.getId()) >= plugin.getConfig().getInt("territories.max-claims-per-clan", 25)) {
            return false;
        }

        String regionId = worldGuardHook.isEnabledForClaims() ? worldGuardHook.getRegionId(player.getChunk()) : null;
        if (worldGuardHook.isEnabledForClaims() && !worldGuardHook.createOrUpdateClaim(clan, player.getChunk())) {
            return false;
        }

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO clan_claims (clan_id, world, chunk_x, chunk_z, region_id) VALUES (?, ?, ?, ?, ?)"
             )) {
            statement.setLong(1, clan.getId());
            statement.setString(2, player.getWorld().getName());
            statement.setInt(3, player.getChunk().getX());
            statement.setInt(4, player.getChunk().getZ());
            statement.setString(5, regionId);
            statement.executeUpdate();
            claims.put(key, clan.getId());
            return true;
        } catch (SQLException exception) {
            if (worldGuardHook.isEnabledForClaims()) {
                worldGuardHook.removeClaim(player.getChunk());
            }
            plugin.getLogger().warning("Errore claim: " + exception.getMessage());
            return false;
        }
    }

    public boolean unclaim(Player player) {
        Clan clan = getClanByPlayer(player.getUniqueId());
        if (clan == null || !isOfficerOrHigher(player.getUniqueId())) {
            return false;
        }

        String key = ChunkKey.of(player.getChunk());
        Long ownerId = claims.get(key);
        if (ownerId == null || ownerId.longValue() != clan.getId()) {
            return false;
        }

        if (worldGuardHook.isEnabledForClaims()) {
            worldGuardHook.removeClaim(player.getChunk());
        }

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM clan_claims WHERE clan_id = ? AND world = ? AND chunk_x = ? AND chunk_z = ?"
             )) {
            statement.setLong(1, clan.getId());
            statement.setString(2, player.getWorld().getName());
            statement.setInt(3, player.getChunk().getX());
            statement.setInt(4, player.getChunk().getZ());
            statement.executeUpdate();
            claims.remove(key);
            return true;
        } catch (SQLException exception) {
            plugin.getLogger().warning("Errore unclaim: " + exception.getMessage());
            return false;
        }
    }

    public int getClaimCount(long clanId) {
        int count = 0;
        for (Long ownerId : claims.values()) {
            if (ownerId == clanId) {
                count++;
            }
        }
        return count;
    }

    public boolean setHome(Player player) {
        Clan clan = getClanByPlayer(player.getUniqueId());
        if (clan == null || !isOfficerOrHigher(player.getUniqueId())) {
            return false;
        }

        Location location = player.getLocation().clone();
        clan.setHome(location);

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE clans SET home_world = ?, home_x = ?, home_y = ?, home_z = ?, home_yaw = ?, home_pitch = ? WHERE id = ?"
             )) {
            statement.setString(1, location.getWorld().getName());
            statement.setDouble(2, location.getX());
            statement.setDouble(3, location.getY());
            statement.setDouble(4, location.getZ());
            statement.setFloat(5, location.getYaw());
            statement.setFloat(6, location.getPitch());
            statement.setLong(7, clan.getId());
            statement.executeUpdate();
            return true;
        } catch (SQLException exception) {
            plugin.getLogger().warning("Errore sethome: " + exception.getMessage());
            return false;
        }
    }

    public boolean teleportHome(Player player) {
        Clan clan = getClanByPlayer(player.getUniqueId());
        if (clan == null || clan.getHome() == null) {
            return false;
        }
        teleportService.schedule(player, clan.getHome(), () -> messages.send(player, "teleported-home"));
        return true;
    }

    public void toggleClanChat(UUID uuid) {
        if (!clanChatToggled.add(uuid)) {
            clanChatToggled.remove(uuid);
        }
    }

    public boolean isClanChatEnabled(UUID uuid) {
        return clanChatToggled.contains(uuid);
    }

    public void sendClanMessage(Player sender, String text) {
        Clan clan = getClanByPlayer(sender.getUniqueId());
        if (clan == null) {
            return;
        }

        String format = plugin.getConfig().getString("chat.format", "&8[&aClan&8] &7[%clan_tag%] &f%player%&7: %message%");
        String message = format
                .replace("%clan_tag%", clan.getTag())
                .replace("%player%", sender.getName())
                .replace("%message%", text);

        String colored = messages.color(message);
        for (ClanMember member : clan.getMembers()) {
            Player online = Bukkit.getPlayer(member.getUuid());
            if (online != null && online.isOnline()) {
                online.sendMessage(colored);
            }
        }
    }

    public boolean canBuild(UUID uuid, Chunk chunk) {
        Clan owner = getClanAt(chunk);
        return owner == null || owner.hasMember(uuid) || Bukkit.getPlayer(uuid) != null && Bukkit.getPlayer(uuid).hasPermission("clans.admin");
    }

    public boolean canPvp(Player attacker, Player victim) {
        Clan owner = getClanAt(victim.getChunk());
        if (owner == null) {
            return true;
        }
        if (owner.hasMember(attacker.getUniqueId()) && owner.hasMember(victim.getUniqueId())) {
            return true;
        }
        return plugin.getConfig().getBoolean("territories.allow-pvp", false);
    }

    public boolean shouldBlockMobSpawning(Location location) {
        return plugin.getConfig().getBoolean("territories.block-mob-spawning", true) && getClanAt(location.getChunk()) != null;
    }

    public int getOnlineMembersCount(Clan clan) {
        int online = 0;
        for (ClanMember member : clan.getMembers()) {
            Player player = Bukkit.getPlayer(member.getUuid());
            if (player != null && player.isOnline()) {
                online++;
            }
        }
        return online;
    }

    public Collection<Clan> getAllClans() {
        return clansById.values();
    }

    public ConfirmationService getConfirmationService() {
        return confirmationService;
    }

    public InviteService getInviteService() {
        return inviteService;
    }
}
