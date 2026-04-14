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
);

CREATE TABLE IF NOT EXISTS clan_members (
    clan_id BIGINT NOT NULL,
    player_uuid CHAR(36) NOT NULL,
    player_name VARCHAR(16) NOT NULL,
    role VARCHAR(16) NOT NULL,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (clan_id, player_uuid),
    INDEX idx_clan_members_player (player_uuid),
    CONSTRAINT fk_clan_members_clan FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
);

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
);

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
);
