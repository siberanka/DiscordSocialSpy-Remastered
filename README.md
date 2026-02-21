# DiscordSocialSpy (Async Advanced)

## 🇬🇧 English

DiscordSocialSpy is a fully asynchronous, high-performance command logging plugin for **Paper 1.21.8+**.  
It sends selected in-game commands (e.g., `/msg`, `/tell`, `/w`) to a Discord channel via webhook while keeping the server lag-free and secure.

### Features
- Fully asynchronous webhook delivery (no main-thread lag)
- **Advanced Filtering:** Configurable regex patterns and plaintext word lists
- **Comprehensive Coverage:** Logs and filters `commands`, `signs`, and regular `chat`
- Permission-based player exclusion (e.g. `discordspy.bypass`)
- Built‑in rate‑limit & spam protection
- **Localization:** Fully customizable messages via `tr.yml` and `en.yml`
- Secure JSON formatting
- Simple reload command

### Configuration Example
```yaml
webhook: "YOUR_WEBHOOK_URL"
sign-webhook: "" # Optional specific webhook for signs

logged-commands:
  - msg
  - tell

exclude-permission: "discordspy.bypass"

filter:
  enabled: true
  check-chat: true
  regex:
    - "\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?::[0-9]{1,5})?\\b"
  words:
    - "badword1"
```

### Command
| Command | Description |
|--------|-------------|
| `/discordsocialspy reload` | Reloads configuration |

### Requirements
- Paper 1.16+
- Java 21

---

## 🇹🇷 Türkçe

DiscordSocialSpy, **Paper 1.21.8+** için geliştirilmiş tamamen asenkron, yüksek performanslı bir komut loglama eklentisidir.  
Özel komutları (örn: `/msg`, `/tell`, `/w`) Discord kanalına gönderir ve sunucuyu laglandırmaz.

### Özellikler
- Tamamen asenkron gönderim sistemi (sunucuyu yormaz)
- **Gelişmiş Filtreleme:** Regex (düzenli ifade) desenleri ve düz metin kelime listesi desteği
- **Kapsamlı Dinleme:** `komut`,`tabela` ve standart `sohbet` için filtreleme yeteneği
- Belirli izinlere sahip oyuncuları hariç tutma (`discordspy.bypass`)
- Dahili rate‑limit & spam koruması
- **Dil Desteği:** `tr.yml` ve `en.yml` üzerinden uyarı mesajlarını ve prefixleri özelleştirme
- Güvenli JSON formatlama
- Kolay yeniden yükleme komutu

### Config Örneği
```yaml
webhook: "WEBHOOK_URLINIZ"
sign-webhook: "" # Opsiyonel: Sadece tabelalar için ayrı webhook

logged-commands:
  - msg
  - tell

exclude-permission: "discordspy.bypass"

filter:
  enabled: true
  check-chat: true
  regex:
    - "\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?::[0-9]{1,5})?\\b"
  words:
    - "kufur1"
```

### Komut
| Komut | Açıklama |
|--------|----------|
| `/discordsocialspy reload` | Ayarları yeniler |

### Gereksinimler
- Paper 1.16+
- Java 21
