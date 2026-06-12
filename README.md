# Heirloom

A Progress Knight-style incremental life sim about pioneer settlement — work the land,
survive winters, and pass the homestead down through generations.

**Status: in development.** See `DECISIONS.md` for the design log and `SIMULATION.md`
(after milestone m2) for balance playthroughs.

## Modules

- `engine/` — pure Kotlin game engine, zero Android dependencies. Build & test anywhere:
  `./gradlew :engine:test`. Run the balance simulator: `./gradlew :engine:run`.
- `app/` — Android app (Jetpack Compose). Requires the Android SDK; the module is only
  included in the build when one is found (`local.properties` sdk.dir or `ANDROID_HOME`).

Fully offline gameplay; network is used only for optional rewarded ads and Play Billing.
