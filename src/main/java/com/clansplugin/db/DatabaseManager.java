package com.clansplugin.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.clansplugin.ClansPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private final ClansPlugin plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(ClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        HikariConfig config = new HikariConfig();
        String host = plugin.getConfig().getString("database.host", "localhost");
        int port = plugin.getConfig().getInt("database.port", 3306);
        String database = plugin.getConfig().getString("database.name", "clans_plugin");
        boolean ssl = plugin.getConfig().getBoolean("database.use-ssl", false);
        String type = plugin.getConfig().getString("database.type", host.contains("rlwy") ? "mysql" : "mariadb").toLowerCase();
        String username = plugin.getConfig().getString("database.username", "root");
        String password = plugin.getConfig().getString("database.password", "password");

        String jdbcUrl;
        String driverClass;

        if (type.equals("mysql")) {
            driverClass = "com.mysql.cj.jdbc.Driver";
            jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useUnicode=true&characterEncoding=utf8"
                    + "&useSSL=" + ssl
                    + "&allowPublicKeyRetrieval=true"
                    + "&serverTimezone=UTC";
        } else {
            driverClass = "org.mariadb.jdbc.Driver";
            jdbcUrl = "jdbc:mariadb://" + host + ":" + port + "/" + database
                    + "?useUnicode=true&characterEncoding=utf8"
                    + "&useSsl=" + ssl;
        }

        config.setDriverClassName(driverClass);
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(plugin.getConfig().getInt("database.pool-size", 10));
        config.setPoolName("ClansPool");
        config.setConnectionTimeout(10000);
        config.setInitializationFailTimeout(15000);

        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException exception) {
            throw new RuntimeException("Driver database non trovato nel jar del plugin: " + driverClass, exception);
        }

        this.dataSource = new HikariDataSource(config);
        createTables();
    }

    private void createTables() {
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clans (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        name VARCHAR(32) NOT NULL UNIQUE,
                        tag VARCHAR(10) NOT NULL UNIQUE,
                        leader_uuid CHAR(36) NOT NULL,
                        home_world VARCHAR(64) NULL,
                        home_x DOUBLE NULL,
                        home_y DOUBLE NULL,
                        home_z DOUBLE NULL,
                        home_yaw FLOAT NULL,
                        home_pitch FLOAT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clan_members (
                        clan_id BIGINT NOT NULL,
                        player_uuid CHAR(36) NOT NULL,
                        player_name VARCHAR(16) NOT NULL,
                        role VARCHAR(16) NOT NULL,
                        joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (clan_id, player_uuid),
                        INDEX idx_clan_members_player (player_uuid),
                        CONSTRAINT fk_clan_members_clan FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clan_invites (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        clan_id BIGINT NOT NULL,
                        invited_uuid CHAR(36) NOT NULL,
                        invited_name VARCHAR(16) NOT NULL,
                        invited_by_uuid CHAR(36) NOT NULL,
                        expires_at BIGINT NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        INDEX idx_clan_invites_player (invited_uuid),
                        CONSTRAINT fk_clan_invites_clan FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clan_claims (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        clan_id BIGINT NOT NULL,
                        world VARCHAR(64) NOT NULL,
                        chunk_x INT NOT NULL,
                        chunk_z INT NOT NULL,
                        region_id VARCHAR(120) NULL,
                        claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE KEY uk_claim (world, chunk_x, chunk_z),
                        INDEX idx_clan_claims_clan (clan_id),
                        CONSTRAINT fk_clan_claims_clan FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                    )
                    """);

            try {
                statement.executeUpdate("ALTER TABLE clan_claims ADD COLUMN region_id VARCHAR(120) NULL");
            } catch (SQLException ignored) {
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("Errore inizializzazione database: " + exception.getMessage());
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
