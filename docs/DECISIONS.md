# Decision log

Short records of decisions that shape the project. Newest at the bottom.
Format: **context** – what we had to decide, **decision**, **why**.

## D1 – One Gradle module, layers as packages (2026-10-27)
- **Context:** The spec asks for separate `data`, `domain` and `ui` layers. They can be packages or Gradle modules.
- **Decision:** One `:app` module with packages `core`, `data`, `domain`, `ui`, `di`.
- **Why:** Simpler Gradle setup while learning Kotlin and Android. The dependency rule
  (`domain` imports no Android code) is written in `CLAUDE.md`. Splitting packages into modules later is a mechanical change.

## D2 – Networking: Ktor client with OkHttp engine + kotlinx.serialization (2026-10-27)
- **Context:** We call Open-Meteo (JSON), RSS feeds (XML) and the Claude API (JSON). The spec allows Ktor or OkHttp.
- **Decision:** Ktor client on top of the OkHttp engine, JSON via kotlinx.serialization. Used from M1.
- **Why:** The API is built on `suspend` functions, so it fits coroutines without adapters. `MockEngine` lets us unit-test
  every client without a network or an Android device. OkHttp underneath is the proven HTTP stack on Android.

## D3 – KSP instead of kapt (2026-10-27)
- **Context:** Hilt (and later Room) need an annotation processor.
- **Decision:** KSP.
- **Why:** AGP 9 compiles Kotlin itself ("built-in Kotlin") and kapt is not compatible with it. KSP is also faster.

## D4 – Kotlin version pinned on the buildscript classpath (2026-10-27)
- **Context:** AGP 9 brings its own Kotlin Gradle plugin (2.2.10), which is older than the current stable Kotlin.
- **Decision:** Put `kotlin-gradle-plugin` with the version from the catalog on the root buildscript classpath,
  as the AGP 9 release notes describe.
- **Why:** We want the current stable Kotlin, and the Compose compiler plugin must match the Kotlin version exactly.

## D5 – JUnit 6 on the JUnit Platform for local tests (2026-10-27)
- **Context:** Android projects default to JUnit 4. The spec allows any JUnit.
- **Decision:** JUnit 6 (Jupiter API) for local JVM tests, run with `useJUnitPlatform()`. No extra Gradle plugin.
- **Why:** Parameterized tests suit date and time logic (DST changes, all-day events). The `android-junit` plugin
  would only be needed for JUnit 5/6 in instrumented tests, which we do not have.

## D6 – ktlint with the Android Studio code style (2026-10-27)
- **Context:** A portfolio project needs consistent formatting. Adding a formatter later means reformatting everything.
- **Decision:** ktlint via the `org.jlleitschuh.gradle.ktlint` plugin, `android_studio` code style with trailing commas
  allowed, checked in CI. `./gradlew ktlintFormat` fixes most findings automatically.
- **Why:** The IDE formatter and ktlint read the same `.editorconfig`, so they agree. Trailing commas follow the
  Kotlin coding conventions and keep diffs small when a parameter is added.

## D7 – java.time without desugaring, time through an injected Clock (2026-10-27)
- **Context:** The spec requires `java.time` with the `Europe/Prague` zone.
- **Decision:** Use `java.time` directly (available since API 26, our minSdk is 29). Inject a `java.time.Clock`
  in the Prague zone instead of calling `now()` directly.
- **Why:** No desugaring library needed. Tests can set any instant, including DST transitions and midnight edges.

## D8 – Public repository, private plan outside git (2026-10-27)
- **Context:** The personal plan contains private details, but the repository is a public portfolio.
- **Decision:** The real plan lives in `docs/private/` and `app/src/main/assets/plan.json`, both git-ignored.
  The repository ships only fictional sample data.
- **Why:** Once something is pushed to a public repository, it stays in the history.

## D9 – Android backup disabled (2026-10-27)
- **Context:** The app will store notification metadata and personal plan data.
- **Decision:** `android:allowBackup="false"`.
- **Why:** Privacy. That data should not leave the phone through cloud backup. Nothing in it is worth restoring.

## D10 – One branch and pull request per milestone (2026-10-27)
- **Context:** Each milestone ends with testing on the phone before the next one starts.
- **Decision:** Work happens on a branch such as `m0-foundation`. The pull request into `main` is merged after the phone test.
- **Why:** The pull request is a natural review point. CI runs on it and the history shows the milestones clearly.

## D11 – Open-source Gradle cache in CI (2026-10-27)
- **Context:** `gradle/actions/setup-gradle` v6 uses a proprietary caching library by default.
- **Decision:** `cache-provider: basic`, the MIT-licensed provider built on the standard GitHub Actions cache.
- **Why:** Fully open source with no extra terms, and fast enough for a single-module project.
