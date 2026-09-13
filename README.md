<!-- DOWNLOAD_BADGES_START -->
<p align="center">
  <a href="https://gitlab.com/siberanka/DiscordSocialSpy-Remastered/-/releases/permalink/latest/downloads/plugin.jar"><img alt="Download Paper" src="https://img.shields.io/badge/Download-Paper-2c2f33?logo=gitlab&logoColor=white"></a>
  <a href="https://gitlab.com/siberanka/DiscordSocialSpy-Remastered/-/releases/permalink/latest/downloads/plugin.jar"><img alt="Download Folia" src="https://img.shields.io/badge/Download-Folia-87c540?logo=gitlab&logoColor=white"></a>
</p>
<!-- DOWNLOAD_BADGES_END -->

# DiscordSocialSpy Remastered

[![Paper](https://img.shields.io/badge/Paper-1.16.x--26.1.x-2c2f33)](https://papermc.io/software/paper)
[![Folia](https://img.shields.io/badge/Folia-supported-87c540)](https://papermc.io/software/folia)
[![Java bytecode](https://img.shields.io/badge/bytecode-Java%2011-f89820)](https://docs.papermc.io/paper/getting-started/)

A lightweight Paper/Folia plugin that audits selected player commands and book/sign edits, filters
configured chat and writable content, and delivers events to Discord webhooks without blocking a
server tick thread.

## Highlights

- One JAR for **Paper/Folia 1.16.x through 26.1.x**.
- Runtime scheduler detection: Folia entity/global schedulers on modern servers, Bukkit scheduler fallback on legacy Paper.
- Bounded asynchronous webhook queue with configurable concurrency, retry backoff, Discord `429` handling, timeouts, and graceful shutdown.
- Command, chat, front/back sign, and writable-book coverage with permission-based bypass.
- Unicode-aware banned-word filtering, whitelist exceptions, guarded regex rules, and obfuscated
  domain/IPv4 detection.
- Safe Discord JSON encoding, payload limits, disabled implicit mentions, and validated role mentions.
- Self-healing `config.yml` and language YAML files with atomic writes and timestamped backups.
- Startup update checks use GitHub first and the public GitLab mirror as a fallback.
- English and Turkish localization.

## Compatibility

The release artifact is compiled to Java 11 bytecode and tested against both API edges. Use the Java runtime required by your Paper version:

| Paper version | Server Java | Validation |
|---|---:|---|
| 1.16.1–1.16.4 | 11 | Legacy API/build line |
| 1.16.5 | 16 | Legacy API/build line |
| 1.17–1.19.x | 17 | Compatible bytecode/API surface |
| 1.20–1.21.11 | 21 | Compatible bytecode/API surface |
| 26.1.x | 25 | Modern API/build line |

Paper’s current JVM requirements are documented in the [Paper getting-started guide](https://docs.papermc.io/paper/getting-started/). Other Bukkit-derived implementations are not release-tested.

## Installation

1. Download `DiscordSocialSpy-2.0.1.jar` from the [latest release](https://gitlab.com/siberanka/DiscordSocialSpy-Remastered/-/releases/permalink/latest).
2. Place it in the server’s `plugins/` directory.
3. Start the server once.
4. Set `webhook` in `plugins/DiscordSocialSpy/config.yml`.
5. Run `/dss reload` or restart the server.

Never publish a configured webhook URL. Discord webhook URLs contain a secret token.

## Commands and permissions

| Command | Permission | Purpose |
|---|---|---|
| `/dss reload` | `discordsocialspy.reload` | Validate and reload configuration/languages asynchronously |
| `/dss cmd add <command>` | `discordsocialspy.cmd` | Add a command to the audit list |
| `/dss cmd remove <command>` | `discordsocialspy.cmd` | Remove a command from the audit list |
| `/dss sign toggle` | `discordsocialspy.notify.sign` | Toggle personal in-game sign notifications |

`discordsocialspy.use` gates the base command. Players with the configured `exclude-permission` (default: `discordspy.bypass`) are excluded from logging and filtering.

## Configuration recovery

On startup and `/dss reload`, the plugin compares YAML values with the bundled schema:

- missing entries are added;
- required default word/regex protections are merged into legacy filter lists while custom entries and
  the whitelist are preserved;
- unknown entries and incompatible values are corrected;
- malformed YAML is replaced with a safe default;
- every correction to an existing file first creates `plugins/DiscordSocialSpy/backups/*.bak`;
- a semantically correct file is not rewritten, preventing save/reload loops.

Configuration reference and migration details are available in the [wiki](https://gitlab.com/siberanka/DiscordSocialSpy-Remastered/-/wikis/home).

## Building

```bash
# Release artifact: Java 11 bytecode, Spigot 1.16.5 API baseline
./gradlew clean build

# Source/API compatibility check: Paper 26.1.x on JDK 25
./gradlew clean build -PapiLine=modern
```

The release JAR is written to `build/libs/DiscordSocialSpy-2.0.1.jar`.

## Security and support

Review the [security audit](SECURITY-AUDIT.md), [security policy](SECURITY.md), and [contribution guide](CONTRIBUTING.md) before reporting a problem. Please use the structured [issue forms](https://gitlab.com/siberanka/DiscordSocialSpy-Remastered/-/issues/new) and include the exact Paper build, Java version, plugin version, and a redacted configuration.

## Türkçe

Kurulum, yapılandırma ve sorun giderme belgelerinin Türkçe sürümleri [GitLab Wiki](https://gitlab.com/siberanka/DiscordSocialSpy-Remastered/-/wikis/home) içinde yer alır. Webhook adresinizi loglarda, issue içinde veya ekran görüntülerinde paylaşmayın.
