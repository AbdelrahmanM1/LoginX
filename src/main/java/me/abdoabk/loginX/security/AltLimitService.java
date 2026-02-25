package me.abdoabk.loginX.security;

import me.abdoabk.loginX.config.ConfigService;
import me.abdoabk.loginX.storage.PlayerRepository;

import java.util.concurrent.CompletableFuture;

public class AltLimitService {

    private final PlayerRepository playerRepository;
    private final ConfigService config;

    public AltLimitService(PlayerRepository playerRepository, ConfigService config) {
        this.playerRepository = playerRepository;
        this.config = config;
    }

    public CompletableFuture<Boolean> isOverLimit(String ip) {
        return playerRepository.countByIp(ip).thenApply(count ->
                count >= config.getMaxAccountsPerIp());
    }
}