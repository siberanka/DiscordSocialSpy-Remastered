# Installation

## Requirements

Use the Java runtime required by the installed Paper version. The plugin artifact itself uses Java 11 bytecode.

| Paper | Required/recommended Java |
|---|---:|
| 1.16.1–1.16.4 | 11 |
| 1.16.5 | 16 |
| 1.17–1.19.x | 17 |
| 1.20–1.21.11 | 21 |
| 26.1.x | 25 |

## Steps

1. Stop the server.
2. Copy `DiscordSocialSpy-2.0.0.jar` into `plugins/`.
3. Start the server and wait for `Enabled v2.0.0` in the console.
4. Edit `plugins/DiscordSocialSpy/config.yml` and set the Discord webhook.
5. Use `/dss reload`.

Do not use plugin managers to hot-load or hot-unload the JAR. A normal server restart guarantees listener, scheduler, and executor lifecycle integrity.

## Türkçe

Sunucunuzun Paper sürümüne uygun Java sürümünü kullanın. JAR’ı `plugins/` klasörüne kopyalayın, sunucuyu başlatın, oluşan `config.yml` dosyasına webhook adresini yazın ve `/dss reload` çalıştırın. PlugMan benzeri araçlarla hot-load/hot-unload yapmayın.
