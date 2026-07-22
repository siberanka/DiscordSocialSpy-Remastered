# Changelog

All notable changes follow [Semantic Versioning](https://semver.org/).

## [2.0.0] - 2026-07-22

### Added

- Paper/Folia support range from 1.16.x through 26.1.x in one Java 11-bytecode JAR.
- Runtime scheduler compatibility layer for legacy Bukkit and modern Folia execution models.
- Deterministic YAML schema reconciliation for configuration and language files.
- Timestamped backups, UTF-8 I/O, and atomic replacement for corrected YAML.
- Bounded webhook delivery, retry/backoff, rate-limit handling, and player-level flood control.
- Legacy and modern API CI lanes, unit tests, CodeQL, dependency review, issue forms, and release automation.
- English/Turkish documentation and wiki pages.

### Changed

- Rebuilt command completion without unsafe async Bukkit API access.
- Reworked sign handling to use modern sign sides when available and the legacy sign API otherwise.
- Moved runtime reload and command-list persistence off tick threads.
- Restricted regular expressions that exhibit common catastrophic-backtracking patterns.

### Security

- Disabled unsolicited Discord mentions and validated explicit role IDs.
- Added payload length limits, control-character-safe logging, webhook validation, and bounded resource use.
- Corrected whitelist behavior so an allowed fragment cannot bypass filtering for the rest of a message.

### Removed

- Direct dependencies on modern-only Adventure and Folia classes from the legacy class path.
- Silent exception swallowing, unbounded async work, and unsafe Bukkit API calls from worker threads.
