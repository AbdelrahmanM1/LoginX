package me.abdoabk.loginX.security;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks recently used session tokens to prevent session replay attacks.
 */
public class AntiReplayService {

    private final Set<String> usedTokens = ConcurrentHashMap.newKeySet();

    /**
     * Returns true if the token was already used (replay attack detected).
     */
    public boolean isReplay(String token) {
        return !usedTokens.add(token);
    }

    public void invalidateToken(String token) {
        usedTokens.remove(token);
    }

    public void clear() {
        usedTokens.clear();
    }
}