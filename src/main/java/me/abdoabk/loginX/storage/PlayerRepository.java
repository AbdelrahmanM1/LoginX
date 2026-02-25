package me.abdoabk.loginX.storage;

import me.abdoabk.loginX.model.PlayerAccount;
import me.abdoabk.loginX.premium.PremiumState;

import java.sql.*;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class PlayerRepository {

    private final DatabaseManager db;
    private final Executor async;

    public PlayerRepository(DatabaseManager db, Executor async) {
        this.db = db;
        this.async = async;
    }

    public CompletableFuture<PlayerAccount> findByUuid(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection con = db.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "SELECT * FROM players WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return mapRow(rs);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        }, async);
    }

    public CompletableFuture<PlayerAccount> findByUsername(String username) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection con = db.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "SELECT * FROM players WHERE LOWER(username) = LOWER(?)")) {
                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return mapRow(rs);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        }, async);
    }

    public CompletableFuture<Void> save(PlayerAccount account) {
        return CompletableFuture.runAsync(() -> {
            try (Connection con = db.getConnection();
                 PreparedStatement ps = con.prepareStatement("""
                     INSERT INTO players (uuid, username, password_hash, premium_state, created_at)
                     VALUES (?, ?, ?, ?, ?)
                     ON CONFLICT(uuid) DO UPDATE SET
                       username = excluded.username,
                       password_hash = excluded.password_hash,
                       premium_state = excluded.premium_state
                     """)) {
                ps.setString(1, account.getUuid().toString());
                ps.setString(2, account.getUsername());
                ps.setString(3, account.getPasswordHash());
                ps.setString(4, account.getPremiumState().name());
                ps.setLong(5, account.getCreatedAt().toEpochMilli());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, async);
    }

    public CompletableFuture<Integer> countByIp(String ip) {
        // We join with sessions since that's where IP is tracked at login time
        return CompletableFuture.supplyAsync(() -> {
            try (Connection con = db.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "SELECT COUNT(DISTINCT uuid) FROM sessions WHERE ip = ?")) {
                ps.setString(1, ip);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getInt(1);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return 0;
        }, async);
    }

    private PlayerAccount mapRow(ResultSet rs) throws SQLException {
        return new PlayerAccount(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("username"),
                rs.getString("password_hash"),
                PremiumState.valueOf(rs.getString("premium_state")),
                Instant.ofEpochMilli(rs.getLong("created_at"))
        );
    }
}