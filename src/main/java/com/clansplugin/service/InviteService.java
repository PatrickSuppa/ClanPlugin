package com.clansplugin.service;

import com.clansplugin.ClansPlugin;
import com.clansplugin.db.DatabaseManager;
import com.clansplugin.model.ClanInvite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InviteService {
    private final ClansPlugin plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, ClanInvite> invites = new ConcurrentHashMap<>();

    public InviteService(ClansPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public void loadActiveInvites() {
        invites.clear();
        long now = System.currentTimeMillis();

        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement cleanup = connection.prepareStatement("DELETE FROM clan_invites WHERE expires_at <= ?")) {
                cleanup.setLong(1, now);
                cleanup.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement("SELECT clan_id, invited_uuid, invited_name, invited_by_uuid, expires_at FROM clan_invites WHERE expires_at > ?");) {
                statement.setLong(1, now);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        ClanInvite invite = new ClanInvite(
                                resultSet.getLong("clan_id"),
                                UUID.fromString(resultSet.getString("invited_uuid")),
                                resultSet.getString("invited_name"),
                                UUID.fromString(resultSet.getString("invited_by_uuid")),
                                resultSet.getLong("expires_at")
                        );
                        invites.put(invite.invitedUuid(), invite);
                    }
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Errore caricamento inviti: " + exception.getMessage());
        }
    }

    public void put(ClanInvite invite) {
        invites.put(invite.invitedUuid(), invite);

        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM clan_invites WHERE invited_uuid = ?")) {
                delete.setString(1, invite.invitedUuid().toString());
                delete.executeUpdate();
            }

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO clan_invites (clan_id, invited_uuid, invited_name, invited_by_uuid, expires_at) VALUES (?, ?, ?, ?, ?)")) {
                insert.setLong(1, invite.clanId());
                insert.setString(2, invite.invitedUuid().toString());
                insert.setString(3, invite.invitedName());
                insert.setString(4, invite.invitedByUuid().toString());
                insert.setLong(5, invite.expiresAt());
                insert.executeUpdate();
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Errore salvataggio invito: " + exception.getMessage());
        }
    }

    public ClanInvite get(UUID uuid) {
        ClanInvite invite = invites.get(uuid);
        if (invite != null && invite.isExpired()) {
            remove(uuid);
            return null;
        }
        return invite;
    }

    public void remove(UUID uuid) {
        invites.remove(uuid);

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM clan_invites WHERE invited_uuid = ?")) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Errore rimozione invito: " + exception.getMessage());
        }
    }

    public int expireSeconds() {
        return plugin.getConfig().getInt("invites.expire-seconds", 60);
    }
}
