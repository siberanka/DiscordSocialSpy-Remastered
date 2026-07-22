# Configuration

## Discord

- `webhook`: command/chat audit webhook.
- `sign-webhook`: optional sign-only webhook; empty uses `webhook`.
- `username`, `avatar_url`: Discord display overrides.
- `prefix`: prefix placed in text webhook messages.

Only HTTPS Discord webhook URLs are accepted. Invalid values are backed up and reset safely.

## Logging and filtering

- `logged-commands`: root command names, optionally namespaced.
- `exclude-permission`: bypass permission for both logging and filtering.
- `filter.enabled`: global filter switch.
- `filter.check-chat`: enables chat filtering.
- `filter.words`: case-insensitive literal fragments.
- `filter.whitelisted-words`: removes only allowed fragments before the remaining message is scanned.
- `filter.regex`: Java regex with guards against common catastrophic-backtracking structures.
- `filter.role-uuid`: 17–20 digit Discord role ID for blocked-content alerts.

## Resource limits

- `async.sender_threads`: `-1` selects two threads; otherwise 1–4.
- `async.queue_size`: 32–10,000 queued deliveries.
- `async.max_retries`: 0–10 retry attempts.
- `async.retry_interval`: base retry delay in seconds.
- `async.rate_limit_wait`: fallback Discord 429 delay.
- `async.request_timeout`: request timeout in seconds.
- `async.shutdown_timeout`: graceful executor shutdown timeout.
- `rate-limit.capacity`: per-player burst size.
- `rate-limit.refill-per-second`: sustained per-player logging rate.

## YAML repair

Missing keys are restored. Unknown keys, invalid types/ranges, unsafe regex, and malformed YAML are repaired only after a timestamped copy is written under `backups/`. Correct files are compared semantically and are not rewritten.

## Türkçe not

Eksik alanlar otomatik eklenir. Fazla, yanlış tipli veya geçersiz alanlar düzeltilmeden önce `backups/` altında yedek oluşturulur. Doğru dosyalar tekrar yazılmaz; reload döngüsü oluşmaz.
