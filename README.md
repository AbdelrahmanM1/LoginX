# 🔐 LoginX

**LoginX** is a secure, feature-rich authentication plugin for Minecraft servers running **Paper/Spigot 1.20+**. It supports both cracked (offline-mode) and premium (Mojang-authenticated) players, persistent sessions, brute-force protection, device fingerprinting, and much more.

---

## ✨ Features

| Feature | Description |
|---|---|
| 🔑 **Registration & Login** | Players register/login with a password hashed using **Argon2id** |
| 🔄 **Persistent Sessions** | Players auto-login on reconnect if their IP/fingerprint matches |
| 🌟 **Premium Support** | Players can lock their account to their Mojang UUID via `/premium` |
| 🛡️ **Brute-Force Protection** | IP is temporarily banned after repeated failed login attempts |
| 🖥️ **Device Fingerprinting** | Tracks client brand + protocol version to detect suspicious logins |
| 🚫 **Alt Account Limiting** | Limits how many accounts can register per IP |
| ⏱️ **Login Timeout** | Kicks players who don't authenticate within a configurable time |
| 🎭 **Restriction System** | Unauthenticated players cannot move, chat, interact, or use commands |
| 🗃️ **SQLite & MySQL** | Choose between embedded SQLite or a full MySQL server |
| 🔁 **Async I/O** | All database operations run off the main thread |
| 🛠️ **Admin Commands** | Reload config, view accounts, invalidate sessions, force-premium, unban IPs |
| 🔌 **Developer API** | Other plugins can check login state, fetch accounts, and invalidate sessions |
| ⚡ **Bypass Permission** | Trusted admins with `loginx.bypass` skip authentication entirely |

---

## 📦 Installation

1. Download `LoginX-x.x.x.jar` and place it in your server's `plugins/` folder.
2. Start your server once to generate the default config files.
3. Edit `plugins/LoginX/config.yml` to your needs (database, security thresholds, etc.).
4. Restart the server (or `/loginx reload` for config-only changes).

**Requirements:**
- Java 17+
- Paper or Spigot 1.20+

---

## ⚙️ Configuration (`config.yml`)

```yaml
auth:
  allow-cracked: true          # Allow offline-mode players
  min-password-length: 8       # Minimum password character count
  login-timeout-seconds: 30    # Seconds before unauthenticated players are kicked

session:
  enabled: true
  timeout-minutes: 30          # Session lifetime
  rolling: true                # Extend session on each reconnect
  invalidate-on-ip-change: true
  invalidate-on-fingerprint-change: true

fingerprint:
  enabled: true
  strict-for-premium: true     # Always re-login on fingerprint change for premium accounts
  max-changes-per-7d: 1        # How many fingerprint drifts are tolerated per week

premium:
  enabled: true
  auto-login-premium: true
  kick-cracked-on-premium-name: true  # Kick cracked players using a premium username

security:
  max-accounts-per-ip: 3
  brute-force:
    max-attempts: 5
    temp-ban-minutes: 10

database:
  type: sqlite    # sqlite | mysql
  mysql:
    host: localhost
    port: 3306
    database: loginx
    username: root
    password: password

performance:
  async-database: true
  cache-sessions: true
  cleanup-task-minutes: 10
```

---

## 💬 Player Commands

| Command | Permission | Description |
|---|---|---|
| `/register <password> <confirm>` | `loginx.player` | Create an account |
| `/login <password>` | `loginx.player` | Log in to your account |
| `/logout` | `loginx.player` | Log out (re-login required) |
| `/changepass <old> <new> <confirm>` | `loginx.player` | Change your password |
| `/premium` | `loginx.premium` | Lock account to your Mojang profile |

---

## 🛠️ Admin Commands (`/loginx`)

Requires permission: `loginx.admin` (default: OP)

| Sub-command | Description |
|---|---|
| `/loginx reload` | Reload `config.yml` and `messages.yml` |
| `/loginx info <player>` | View UUID, premium state, and login status |
| `/loginx session invalidate <player>` | Force a player to re-authenticate |
| `/loginx premium force <player>` | Manually mark a player as premium-locked |
| `/loginx unban <ip>` | Lift a brute-force IP ban immediately |

---

## 🔐 Permissions

| Permission | Default | Description |
|---|---|---|
| `loginx.player` | Everyone | Access to `/register`, `/login`, `/logout`, `/changepass` |
| `loginx.premium` | Everyone | Access to `/premium` |
| `loginx.admin` | OP | Access to `/loginx` admin commands |
| `loginx.bypass` | Nobody | Skips authentication entirely (for trusted admins) |

---

## 🏗️ Architecture Overview

```
LoginX (main plugin)
├── config/          ConfigService, Messages
├── storage/         DatabaseManager, PlayerRepository, SessionRepository
├── model/           PlayerAccount
├── session/         Session, SessionService, SessionValidator, SessionCleanupTask
├── auth/            AuthService, LoginService, RegisterService,
│                    ChangePasswordService, PasswordPolicy, LoginTimeoutservice
├── fingerprint/     Fingerprint, FingerprintService, FingerprintPolicy
├── security/        BruteForceService, AltLimitService, AntiReplayService
├── premium/         PremiumService, PremiumState
├── listener/        PlayerJoinListener, PlayerQuitListener, PlayerRestrictListener
├── command/         LoginCommand, RegisterCommand, LogoutCommand,
│                    ChangePassCommand, PremiumCommand, LoginXAdminCommand
├── util/            HashUtil, IpUtil, MessageUtil, TimeUtil
└── api/             LoginXAPI
```

### Flow: Player Joins

```
PlayerJoinEvent
  └── Has loginx.bypass? → setLoggedIn(true), skip all checks
  └── applyRestrictions()  ← blindness + title loop + actionbar loop
  └── [1s delay] isPremiumLocked?
        YES → createSession() → removeRestrictions()
        NO  → AuthService.handleJoin()
                  └── No account? → startTimeout() + prompt /register
                  └── Has account? → getSession()
                        └── VALID session → auto-login, removeRestrictions()
                        └── EXPIRED/MISMATCH/NONE → startTimeout() + prompt /login
```

### Flow: Player Logs In (`/login <password>`)

```
LoginService.login()
  └── Already logged in? → error
  └── IP banned? (async DB check) → kick with remaining time
  └── findByUuid() → account not found? → prompt register
  └── verifyPassword(Argon2id)
        FAIL → recordFailure() → warn / kick & ban IP
        PASS → clearAttempts() → createSession() → removeRestrictions()
```

---

## 🔌 Developer API

Other plugins can depend on LoginX and use the static `LoginXAPI`:

```java
// Check if a player is logged in
boolean loggedIn = LoginXAPI.isLoggedIn(player.getUniqueId());

// Fetch a player's account asynchronously
LoginXAPI.getAccount(uuid).thenAccept(account -> {
    if (account != null) {
        System.out.println(account.getPremiumState());
    }
});

// Force-invalidate a session
LoginXAPI.invalidateSession(uuid);
```

---

## 🗄️ Database Schema

```sql
-- Player accounts
CREATE TABLE players (
    uuid          VARCHAR(36)  PRIMARY KEY,
    username      VARCHAR(16)  NOT NULL,
    password_hash TEXT,                      -- Argon2id hash
    premium_state VARCHAR(20)  NOT NULL DEFAULT 'CRACKED',
    created_at    BIGINT       NOT NULL
);

-- Active sessions
CREATE TABLE sessions (
    uuid        VARCHAR(36)  PRIMARY KEY,
    ip          VARCHAR(45)  NOT NULL,
    fingerprint CHAR(64),                    -- SHA-256 of client brand+protocol
    expires_at  BIGINT       NOT NULL
);

-- Fingerprint drift tracking
CREATE TABLE fingerprint_changes (
    uuid       VARCHAR(36) NOT NULL,
    changed_at BIGINT      NOT NULL
);

-- IP brute-force bans
CREATE TABLE brute_force (
    ip           VARCHAR(45) PRIMARY KEY,
    attempts     INT         NOT NULL DEFAULT 0,
    banned_until BIGINT      NOT NULL DEFAULT 0
);
```

---

## 🔄 Changelog

### Improvements (vs. original)

- **`PlayerJoinListener`** — Now applies restrictions *immediately* on join, before the async account lookup. Previously, players had a window where they could act freely. Also properly handles the `loginx.bypass` permission.
- **`LogoutCommand`** — After logout, restrictions are re-applied and a new login timeout is started, preventing players from remaining in a free-roam state after logging out.
- **`PlayerRestrictListener`** — All event handlers now check `loginx.bypass` so trusted admins are never blocked.
- **`BruteForceService`** — Added `isBannedAsync()` + `unbanIp()` for async-safe usage and admin IP unban support.
- **`LoginService`** — Ban check is now fully asynchronous instead of blocking an async thread with a synchronous DB call.
- **`LoginXAdminCommand`** — `info` subcommand now dispatches messages back to the main thread (was sending from async thread). Added `/loginx unban <ip>` sub-command.
- **`PremiumCommand`** — Removed incorrect `@UnknownNullability` annotation, replaced with `@NotNull`.
- **`messages.yml`** — Added `admin.ip-unbanned` message key.

---

## 👤 Author

**abdoabk** — [GitHub](https://github.com/abdoabk/LoginX)
