# Security and performance

## Resource model

Webhook delivery uses a fixed-capacity queue, fixed executor size, bounded in-flight requests, connect/request timeouts, capped retries, and exponential backoff. Queue overflow drops only the Discord audit copy and emits a throttled server warning.

Player-level token buckets prevent one client from filling the global queue. UUID state is removed on quit and all executors/maps are closed on plugin disable.

## Input handling

- JSON control characters are escaped.
- Discord payload fields are truncated to official limits.
- Implicit mentions are disabled.
- Explicit role mention IDs are numeric and bounded.
- Sign/chat/command scan inputs are bounded.
- Unsafe regex structures are rejected during config repair.
- Log messages strip control characters.

## Thread model

HTTP and runtime YAML I/O never execute on a tick thread. Bukkit/Paper operations return through the global or player-owning scheduler. Reload failure retains the last valid in-memory snapshot.

See [SECURITY-AUDIT.md](https://github.com/siberanka/DiscordSocialSpy-Remastered/blob/main/SECURITY-AUDIT.md) for the full threat analysis.
