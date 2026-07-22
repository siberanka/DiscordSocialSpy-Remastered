# Contributing

## Development setup

- JDK 21 for the legacy/release build.
- JDK 25 for the Paper 26.1.x compatibility build.
- Use the checked-in Gradle wrapper.

Run both validation lanes before submitting a pull request:

```bash
./gradlew clean build
./gradlew clean build -PapiLine=modern
```

## Pull requests

- Keep changes focused and explain player/server impact.
- Add or update tests for behavior changes.
- Preserve Java 11 source compatibility in release code.
- Never call Bukkit APIs from arbitrary executor threads.
- Use bounded queues/caches and clean per-player state on quit.
- Do not log webhook URLs, secrets, or unbounded player-controlled text.
- Update `CHANGELOG.md`, README, and wiki sources when behavior changes.

Use Conventional Commit-style subjects such as `fix:`, `feat:`, `perf:`, `security:`, `docs:`, or `build:`.
