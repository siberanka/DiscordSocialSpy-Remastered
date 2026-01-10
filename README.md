# DiscordSocialSpy (Async Advanced)

## 🇬🇧 English

DiscordSocialSpy is a fully asynchronous, high-performance command logging plugin for **Paper 1.21.8+**.  
It sends selected in-game commands (e.g., `/msg`, `/tell`, `/w`) to a Discord channel via webhook while keeping the server lag-free and secure.

### Features
- Fully asynchronous webhook delivery (no main-thread lag)
- Configurable command filters
- Permission-based player exclusion
- Built‑in rate‑limit & spam protection
- Customizable message prefix
- Secure JSON formatting
- Simple reload command

### Configuration Example
```yaml
webhook: "YOUR_WEBHOOK_URL"

logged-commands:
  - msg
  - tell
  - w

exclude-permission: "discordspy.bypass"

prefix: "[Spy] "
```

### Command
| Command | Description |
|--------|-------------|
| `/discordsocialspy reload` | Reloads configuration |

### Requirements
- Paper 1.21.8+
- Java 21

---

## 🇹🇷 Türkçe

DiscordSocialSpy, **Paper 1.21.8+** için geliştirilmiş tamamen asenkron, yüksek performanslı bir komut loglama eklentisidir.  
Özel komutları (örn: `/msg`, `/tell`, `/w`) Discord kanalına gönderir ve sunucuyu laglandırmaz.

### Özellikler
- Tamamen asenkron gönderim sistemi
- Config üzerinden komut filtresi ayarlanabilir
- Belirli izinlere sahip oyuncuları hariç tutma
- Dahili rate‑limit & spam koruması
- Özelleştirilebilir prefix
- Güvenli JSON formatlama
- Kolay yeniden yükleme komutu

### Config Örneği
```yaml
webhook: "WEBHOOK_URLINIZ"

logged-commands:
  - msg
  - tell
  - w

exclude-permission: "discordspy.bypass"

prefix: "[Spy] "
```

### Komut
| Komut | Açıklama |
|--------|----------|
| `/discordsocialspy reload` | Ayarları yeniler |

### Gereksinimler
- Paper 1.21.8+
- Java 21

---

## 📥 Download

You can download the latest ZIP build here:

**[DiscordSocialSpyAsyncAdvanced.zip](sandbox:/mnt/data/DiscordSocialSpyAsyncAdvanced.zip)**

