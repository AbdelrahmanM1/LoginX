package me.abdoabk.loginX.config;

import me.abdoabk.loginX.LoginX;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Loads and serves all player-facing text from {@code messages.yml}.
 *
 * <p>Features:
 * <ul>
 *   <li>Automatic prefix injection via {@link #get(String)}</li>
 *   <li>Vararg placeholder replacement via {@link #get(String, String...)}</li>
 *   <li>Raw (no prefix) messages for kick screens via {@link #getRaw(String)}</li>
 *   <li>Defaults merged from the bundled JAR resource so missing keys still work</li>
 *   <li>Hot-reload support — called by {@link me.abdoabk.loginX.command.LoginXAdminCommand}</li>
 * </ul>
 *
 * <h2>Consumers (every service/command that sends messages)</h2>
 * {@link me.abdoabk.loginX.auth.AuthService},
 * {@link me.abdoabk.loginX.auth.LoginService},
 * {@link me.abdoabk.loginX.auth.RegisterService},
 * {@link me.abdoabk.loginX.auth.ChangePasswordService},
 * {@link me.abdoabk.loginX.auth.LoginTimeoutService},
 * {@link me.abdoabk.loginX.premium.PremiumService},
 * {@link me.abdoabk.loginX.listener.PlayerRestrictListener},
 * all command classes.
 *
 * @see LoginX#getMessages()
 * @see ConfigService
 */
public class Messages {

    private final LoginX plugin;
    private FileConfiguration messages;
    private String prefix;

    /**
     * Loads {@code messages.yml} immediately on construction.
     *
     * @param plugin the main plugin instance
     */
    public Messages(LoginX plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Re-reads {@code messages.yml} from disk and refreshes the prefix.
     * Called on construction and by {@link me.abdoabk.loginX.command.LoginXAdminCommand} reload.
     */
    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);

        // Merge bundled defaults so missing keys fall back gracefully
        try (var stream = plugin.getResource("messages.yml")) {
            if (stream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                messages.setDefaults(defaults);
            }
        } catch (Exception ignored) {}

        prefix = colorize(messages.getString("prefix", "&8[&bLoginX&8] &r"));
    }

    /**
     * Returns the color-formatted message at {@code path}, prepended with the prefix.
     *
     * <p>Used for regular in-chat messages sent via
     * {@link me.abdoabk.loginX.util.MessageUtil#send(org.bukkit.entity.Player, String)}.</p>
     *
     * @param path YAML path, e.g. {@code "auth.login-success"}
     * @return prefix + colorized message
     */
    public String get(String path) {
        String raw = messages.getString(path, "&cMissing message: " + path);
        return prefix + colorize(raw);
    }

    /**
     * Same as {@link #get(String)} but replaces placeholder tokens before returning.
     *
     * <p>Placeholders are passed as key-value pairs:
     * {@code get("errors.password-too-short", "{min}", "8")}</p>
     *
     * @param path         YAML path
     * @param placeholders alternating key/value pairs: {@code "{key}", "value", ...}
     * @return prefix + colorized message with all tokens replaced
     */
    public String get(String path, String... placeholders) {
        String msg = get(path);
        for (int i = 0; i < placeholders.length - 1; i += 2) {
            msg = msg.replace(placeholders[i], placeholders[i + 1]);
        }
        return msg;
    }

    /**
     * Returns the colorized message <em>without</em> the prefix.
     *
     * <p>Used for kick messages where the prefix would look odd in the
     * disconnect screen (e.g. {@code premium.verified-kick},
     * {@code errors.login-timeout}).</p>
     *
     * @param path YAML path
     * @return colorized message, no prefix
     */
    public String getRaw(String path) {
        return colorize(messages.getString(path, ""));
    }

    /** Translates {@code &} color codes using {@link ChatColor}. */
    private String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}
