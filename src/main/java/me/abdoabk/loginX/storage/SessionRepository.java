package me.abdoabk.loginX.storage;

import me.abdoabk.loginX.session.Session;

import java.sql.*;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class SessionRepository {

    private final DatabaseManager db;
    private final Executor async;

    public SessionRepository(DatabaseManager db, Executor async) {
        this.db = db;
        this.async = async;
    }

    public CompletableFuture<Session> findByUuid(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection con = db.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "SELECT * FROM sessions WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return mapRow(rs);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        }, async);
    }

    public CompletableFuture<Void> save(Session session) {
        return CompletableFuture.runAsync(() -> {
            try (Connection con = db.getConnection();
                 PreparedStatement ps = con.prepareStatement("""
                     INSERT INTO sessions (uuid, ip, fingerprint, expires_at)
                     VALUES (?, ?, ?, ?)
                     ON CONFLICT(uuid) DO UPDATE SET
                       ip = excluded.ip,
                       fingerprint = excluded.fingerprint,
                       expires_at = excluded.expires_at
                     """)) {
                ps.setString(1, session.getUuid().toString());
                ps.setString(2, session.getIp());
                ps.setString(3, session.getFingerprint());
                ps.setLong(4, session.getExpiresAt().toEpochMilli());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, async);
    }

    public CompletableFuture<Void> delete(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            try (Connection con = db.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "DELETE FROM sessions WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, async);
    }

    public CompletableFuture<Void> deleteExpired() {
        return CompletableFuture.runAsync(() -> {
            try (Connection con = db.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "DELETE FROM sessions WHERE expires_at < ?")) {
                ps.setLong(1, Instant.now().toEpochMilli());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, async);
    }

    private Session mapRow(ResultSet rs) throws SQLException {
        return new Session(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("ip"),
                rs.getString("fingerprint"),
                Instant.ofEpochMilli(rs.getLong("expires_at"))
        );
    }
}