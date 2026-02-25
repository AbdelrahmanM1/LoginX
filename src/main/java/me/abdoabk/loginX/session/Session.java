package me.abdoabk.loginX.session;

import java.time.Instant;
import java.util.UUID;

public class Session {

    private final UUID uuid;
    private final String ip;
    private final String fingerprint;
    private Instant expiresAt;

    public Session(UUID uuid, String ip, String fingerprint, Instant expiresAt) {
        this.uuid = uuid;
        this.ip = ip;
        this.fingerprint = fingerprint;
        this.expiresAt = expiresAt;
    }

    public UUID getUuid() { return uuid; }
    public String getIp() { return ip; }
    public String getFingerprint() { return fingerprint; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    @Override
    public String toString() {
        return "Session{uuid=" + uuid + ", ip=" + ip + ", expires=" + expiresAt + '}';
    }
}