# Troubleshooting

## Plugin does not enable

Confirm the server is Paper/Folia 1.16.x–26.1.x and uses the Java runtime required by that Paper release. Do not run Paper 26.1.x on Java 21; it requires Java 25.

## Discord receives nothing

1. Confirm `webhook` is a full HTTPS Discord `/api/webhooks/...` URL.
2. Check that the player does not have the configured bypass permission.
3. Confirm the root command is present under `logged-commands`.
4. Inspect console warnings for HTTP 401/403/404 or queue overflow.
5. Regenerate a webhook that was ever exposed publicly.

## Configuration keeps changing

Look under `plugins/DiscordSocialSpy/backups/` and compare the newest backup. Unknown keys, invalid types/ranges, unsafe regex, and malformed YAML are intentionally corrected. A correct file is not rewritten on later reloads.

## Folia scheduling warning

Use an official supported Folia build. The plugin detects scheduler methods at startup. If reflection fails, it reports one warning; include that warning and the exact Folia build in a bug report.

## Türkçe

Eklenti açılmıyorsa Paper–Java eşleşmesini kontrol edin. Discord mesajı gitmiyorsa webhook biçimini, bypass yetkisini ve `logged-commands` listesini inceleyin. Yapılandırma düzeltmelerinin önceki hali `backups/` klasöründedir.
