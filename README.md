# Heirloom

A Progress Knight-style incremental life sim about pioneer settlement — work the land,
survive winters, and pass the homestead down through generations.

One **Activity** (work) and one **Skill** (training) at a time. Time flows in game-days;
seasons cycle and winter tests your stores. The pioneer ages, dies, and the heir inherits
through three prestige layers: **Heirlooms** (one-time permanent unlocks), **Posterity**
(the scaling currency, spent on Family Ventures), and **Monuments** (deep resets that
grow the Town Vista from wagon camp to future city). Eras gate content:
Trail → Claim → Homestead → Village → Railroad Town → City & Statehood.

This is a **check-in game**: 2–5 short sessions a day for 6–10 weeks. Offline progress
is the primary production engine (capped, extendable via the Wagon Train venture);
sessions are for collecting, choosing the next work, spending Posterity, and prestiging.
Gameplay is fully offline — network is used only for optional rewarded ads and Play Billing.

## Repository layout

```
engine/   Pure Kotlin game engine — ZERO Android dependencies.
          model/ sim/ balance/ serialization/ runner/
app/      Android app (Jetpack Compose, Material 3, Hilt, DataStore).
          Included in the build only when an Android SDK is found.
```

- `DECISIONS.md` — every judgment call (balance numbers, scope cuts) with rationale.
- `SIMULATION.md` — scripted multi-week check-in playthroughs from the headless runner.
- `ATTRIBUTION.md` — art sourcing and license rules (CC0 packs swap in via `assets/vista/`).

## Building

**Engine (any JVM, no Android needed):**

```sh
./gradlew :engine:test     # 125 unit tests against the real balance values
./gradlew :engine:run      # regenerate SIMULATION.md (args: [out] [days] [seed])
```

**App (requires Android SDK):** put `sdk.dir` in `local.properties` (or set
`ANDROID_HOME`), then:

```sh
./gradlew :app:assembleDebug
```

Release builds minify with the rules in `app/proguard-rules.pro`. Before shipping:
replace the AdMob test IDs (`AndroidManifest.xml`, `AdMobRewardedAds.kt`) and create the
`supporter_family_legacy` in-app product in Play Console (see `PlayBillingSupporterStore.kt`).

## Architecture

The engine exposes pure functions — `TickEngine.tick(state, days, config)`,
`PlayerActions.*`, `OfflineProgressCalculator.apply(...)` — over an immutable,
serializable `GameState`. Every tunable constant lives in `BalanceConfig`. The ViewModel
owns the loop (1-second ticks while foregrounded; the same tick fast-forwards offline
time on resume) and `StateFlow<GameState>` is the single source of truth. Saves are
versioned JSON in DataStore with a last-good backup slot and a migration walk.

Monetization (one $4.99 Supporter Pack, rewarded ads only) sits behind two interfaces;
the engine knows nothing but a `supporter` flag and a daily boost counter.
