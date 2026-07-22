# Compatibility

## Supported range

- Paper: 1.16.x–26.1.x
- Folia: supported where Folia is available
- Java bytecode: 11

The CI legacy lane compiles the release source against Spigot API 1.16.5 using Java 11 bytecode. The modern lane compiles the same source against the latest selected Paper 26.1.2 API using JDK 25.

## Runtime capability selection

- Modern/Folia server: global and entity schedulers are discovered once and used for server/player operations.
- Legacy Paper: classic Bukkit scheduler is used.
- Modern double-sided signs: the edited side is read through the sign-side API.
- Legacy signs: the original four-line sign API is used.
- Player messages: legacy text remains the common transport; clickable teleport help uses the stable Spigot component bridge with a plain-text fallback.

No modern-only class appears in a mandatory legacy class signature, preventing early class-loader failure.
