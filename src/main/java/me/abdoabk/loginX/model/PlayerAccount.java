package me.abdoabk.loginX.model;

import me.abdoabk.loginX.premium.PremiumState;

import java.time.Instant;
import java.util.UUID;

public class PlayerAccount {

    private final UUID uuid;
    private String username;
    private String passwordHash;
    private PremiumState premiumState;
    private final Instant createdAt;

    public PlayerAccount(UUID uuid, String username, String passwordHash,
                         PremiumState premiumState, Instant createdAt) {
        this.uuid = uuid;
        this.username = username;
        this.passwordHash = passwordHash;
        this.premiumState = premiumState;
        this.createdAt = createdAt;
    }

    public UUID getUuid() { return uuid; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public PremiumState getPremiumState() { return premiumState; }
    public void setPremiumState(PremiumState premiumState) { this.premiumState = premiumState; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isPremiumLocked() {
        return premiumState == PremiumState.PREMIUM_LOCKED;
    }
    public boolean isCracked() {
        return premiumState == PremiumState.CRACKED;
    }

    @Override
    public String toString() {
        return "PlayerAccount{uuid=" + uuid + ", username='" + username + "', premiumState=" + premiumState + '}';
    }
}