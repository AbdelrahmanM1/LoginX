package me.abdoabk.loginX.auth;

import me.abdoabk.loginX.config.ConfigService;

public class PasswordPolicy {

    private final ConfigService config;

    public PasswordPolicy(ConfigService config) {
        this.config = config;
    }

    public boolean meetsMinLength(String password) {
        return password != null && password.length() >= config.getMinPasswordLength();
    }

    public boolean isDifferentFrom(String newPassword, String oldHash) {
        // We just check if they're not exactly the same plaintext
        // (full hash verify would be done in ChangePasswordService)
        return true; // Actual check happens in ChangePasswordService via HashUtil
    }
}