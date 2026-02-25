package me.abdoabk.loginX.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.abdoabk.loginX.LoginX;
import me.abdoabk.loginX.config.ConfigService;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

public class DatabaseManager {

    private final LoginX plugin;
    private final ConfigService config;
    private HikariDataSource dataSource;

    public DatabaseManager(LoginX plugin, ConfigService config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void init() throws SQLException {
        HikariConfig hikari = new HikariConfig();
        String dbType = config.getDatabaseType().toLowerCase();

        if (dbType.equals("mysql")) {
            hikari.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=false&autoReconnect=true",
                    config.getMysqlHost(), config.getMysqlPort(), config.getMysqlDatabase()));
            hikari.setUsername(config.getMysqlUsername());
            hikari.setPassword(config.getMysqlPassword());
            hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hikari.setMaximumPoolSize(10);
        } else {
            File dbFile = new File(plugin.getDataFolder(), "loginx.db");
            hikari.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hikari.setDriverClassName("org.sqlite.JDBC");
            hikari.setMaximumPoolSize(1);
            hikari.setConnectionTestQuery("SELECT 1");
        }

        hikari.setPoolName("LoginX-DB");
        hikari.setConnectionTimeout(30000);
        hikari.setIdleTimeout(600000);
        hikari.setMaxLifetime(1800000);

        dataSource = new HikariDataSource(hikari);
        createTables();
        plugin.getLogger().info("Database connected (" + dbType + ").");
    }

    private void createTables() throws SQLException {
        try (Connection con = getConnection(); Statement stmt = con.createStatement()) {
            // Players table
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS players (
                    uuid          VARCHAR(36)  PRIMARY KEY,
                    username      VARCHAR(16)  NOT NULL,
                    password_hash TEXT,
                    premium_state VARCHAR(20)  NOT NULL DEFAULT 'CRACKED',
                    created_at    BIGINT       NOT NULL
                )
                """);

            // Sessions table
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sessions (
                    uuid        VARCHAR(36)  NOT NULL,
                    ip          VARCHAR(45)  NOT NULL,
                    fingerprint CHAR(64),
                    expires_at  BIGINT       NOT NULL,
                    PRIMARY KEY (uuid)
                )
                """);

            // Fingerprint changes table (for drift tracking)
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS fingerprint_changes (
                    uuid       VARCHAR(36) NOT NULL,
                    changed_at BIGINT      NOT NULL
                )
                """);

            // Brute force table
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS brute_force (
                    ip         VARCHAR(45) PRIMARY KEY,
                    attempts   INT         NOT NULL DEFAULT 0,
                    banned_until BIGINT    NOT NULL DEFAULT 0
                )
                """);
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}