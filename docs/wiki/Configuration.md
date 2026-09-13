# Configuration

## Discord

- `webhook`: command/chat audit webhook.
- `sign-webhook`: optional sign-only webhook; empty uses `webhook`.
- `book-webhook`: optional book-only webhook; empty uses `webhook`.
- `username`, `avatar_url`: Discord display overrides.
- `prefix`: prefix placed in text webhook messages.

Only HTTPS Discord webhook URLs are accepted. Invalid values are backed up and reset safely.

## Logging and filtering

- `logged-commands`: root command names, optionally namespaced.
- `exclude-permission`: bypass permission for both logging and filtering.
- `filter.enabled`: global filter switch.
- `filter.check-chat`: enables chat filtering.
- `filter.check-signs`: filters new or edited sign text without affecting ordinary signs.
- `filter.check-books`: filters every edited/signed book page and its metadata without affecting
  ordinary books.
- `filter.smart-detection`: detects separator/Unicode obfuscation in banned words plus common domains,
  Minecraft ad prefixes and IPv4 addresses. Disable it to retain only exact word and regex behavior.
- `filter.words`: case-insensitive banned words or phrases. Smart detection uses token boundaries, so a
  short banned word inside a legitimate longer word is not blocked.
- `filter.whitelisted-words`: removes only allowed fragments before the remaining message is scanned.
- `filter.regex`: defense-in-depth Java regex rules with guards against common catastrophic-backtracking
  structures. The defaults cover direct and fragmented domain/IP forms and may be extended.
- `filter.role-uuid`: 17–20 digit Discord role ID for blocked-content alerts.
- `log-signs`, `log-books`: send normal sign/book edits to Discord for auditing.
- `log-signs-to-console`, `log-books-to-console`: optional control-character-safe console copies.
- `update-check.enabled`: checks GitHub releases first, then uses the GitLab mirror if GitHub cannot be
  reached. It never downloads or installs an update.

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

Missing keys are restored. Required default entries are merged into `filter.words` and `filter.regex`;
custom rules and `filter.whitelisted-words` remain unchanged. Superseded broad defaults such as
`play.` are removed because the address detector replaces them without the same false positives.
Unknown keys, invalid types/ranges, unsafe regex, and malformed YAML are repaired only after a
timestamped copy is written under `backups/`. Correct files are compared semantically and are not
rewritten.

## Türkçe not

Eksik alanlar otomatik eklenir. Fazla, yanlış tipli veya geçersiz alanlar düzeltilmeden önce `backups/` altında yedek oluşturulur. Doğru dosyalar tekrar yazılmaz; reload döngüsü oluşmaz.
