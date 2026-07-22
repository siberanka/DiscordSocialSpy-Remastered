# Security, stability, and performance audit

Audit date: 2026-07-22
Release: 2.0.0

## Findings and mitigations

| Area | Risk and trigger | Applied correction | Why the correction does not add a new exposure |
|---|---|---|---|
| Version linkage | Direct references to modern Folia, Adventure, and sign-side classes caused class-loading failure on 1.16.x. | Compile against the 1.16.5 API and detect modern schedulers/sign sides at runtime. | Reflection is cached and guarded; a missing API follows a tested legacy path rather than disabling validation. |
| Thread safety | Help-map reads, online-player iteration, and player messages were performed from arbitrary async threads. | Global/entity scheduler bridge routes Bukkit work to the owning thread; network/config I/O stays off tick threads. | Each callback checks plugin/player lifecycle state and fails closed on scheduling errors. |
| Webhook flooding | Every event could create async work and overwhelm CPU, memory, sockets, or Discord. | Fixed-capacity queue, fixed worker count, in-flight cap, player token buckets, timeouts, and retry ceilings. | Overflow drops only audit delivery—not gameplay—and emits a throttled warning; no unbounded retry queue is introduced. |
| Discord injection | Player text could break JSON or trigger unwanted mentions. | Full JSON control-character escaping, strict payload limits, empty `allowed_mentions`, and numeric role-ID validation. | Role mentions are opt-in and restricted to the configured role; raw user text cannot expand mention scope. |
| SSRF/config error | A malformed or non-Discord webhook could cause exceptions or contact an unintended host. | Require HTTPS Discord webhook hosts and `/api/webhooks/` paths before runtime use. | Empty/placeholder webhooks disable delivery safely and never affect gameplay events. |
| Regex denial of service | Catastrophic-backtracking expressions could be triggered repeatedly by a client. | Cap expressions/input and reject nested unbounded quantifiers, risky overlapping alternatives, backreferences, and lookbehind. | Bundled bounded expressions remain accepted; invalid expressions are backed up and removed deterministically. |
| Filter bypass | Including one whitelisted fragment bypassed filtering for the entire message. | Remove only whitelisted fragments from the scan copy, then evaluate remaining content. | The original message is retained for audit; only matching semantics change. |
| YAML corruption | Invalid YAML was overwritten silently; unknown/type-invalid values persisted; language files were not merged. | Schema reconciliation, semantic no-op detection, backup-before-repair, atomic UTF-8 writes, and all managed language files. | A failed backup or write aborts reload and keeps the prior in-memory snapshot; defaults do not enter rewrite loops. |
| Reload blocking | Config save/reload ran on an event thread. | Serialize runtime configuration I/O on one daemon executor and atomically publish validated snapshots. | Updates are ordered, bounded to one worker, and callbacks return through server schedulers. |
| Memory lifecycle | Per-player spam and notification maps were never cleared. | Remove all player state on quit and clear registries on disable/reconfigure. | UUIDs—not Player objects—are retained; no entity/world reference survives a session. |
| Sign API drift | Modern-only sign methods prevented legacy use; legacy-only reads mishandled back sides. | Use reflective modern side selection when available and `Sign#getLine` on old servers. | Both paths read server state; client-rendered state is never trusted. |
| Error handling | Broad empty catch blocks hid failed writes, loads, and requests. | Fail startup safely for invalid core state; report sanitized operational errors; retain the last valid snapshot on reload failure. | Error messages strip control characters and never contain the configured webhook. |

## Scope notes

The plugin does not manage inventories, items, economy, databases, custom packets, or GUI sessions, so item-duplication and transaction double-spend paths are not present. It observes and may cancel command/chat/sign events only. Client input is bounded before expensive work, and no player-controlled value is dispatched as a server or operating-system command.

## Validation

- Java 11 release bytecode compiled against Spigot API 1.16.5.
- Source/API compatibility compiled against Paper API 26.1.2 on JDK 25.
- Automated tests cover filtering, regex guards, rate limiting, JSON safety, and repeat-safe YAML repair.
- GitHub Actions runs both API lanes; CodeQL and dependency review cover repository changes.
