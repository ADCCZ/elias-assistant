# CLAUDE.md – Elias (elias-assistant)

Personal Czech voice assistant for Android. Speaks a short morning and evening briefing.
**Source of truth for scope and rules: [docs/SPEC.md](docs/SPEC.md).** Read it before starting any milestone.

## How we work

- **Plan first, then code.** Propose a milestone plan and wait for approval.
- **One milestone at a time** (M0–M7 in the spec). After each one, stop and report to the user **in Czech**:
  what was done and why (short, the user is learning Kotlin/Android), what to test on the phone and how,
  known limitations and debt. Do not start the next milestone until the user confirms it works.
- **Commit after each logical step.** English messages, Conventional Commits style
  (`feat:`, `fix:`, `test:`, `docs:`, `build:`, `ci:`, `refactor:`, `chore:`).
  **No AI attribution:** no `Co-Authored-By: Claude` trailer in commits and no "Generated with Claude Code"
  line in pull requests (the user's explicit rule).
- **One branch and pull request per milestone** (e.g. `m0-foundation`). The user merges it after testing on the phone.
- **Don't guess APIs.** Check current official docs and the latest stable versions (Android, Claude API,
  Open-Meteo, RSS feeds) before using them. All versions live only in `gradle/libs.versions.toml`.
- **Ask** when a decision is unclear or expensive to change. Decide small things yourself and say why.
- Log notable decisions in `docs/DECISIONS.md` (one short entry each: context, decision, why).
- Changes to the spec only after agreement, recorded at the end of `docs/SPEC.md`.

## Language

- Code, identifiers, comments, commit messages, README: **English**. The README also gets a short Czech section.
- UI strings, spoken text and messages to the user: **Czech**. UI strings go in `res/values/strings.xml`.

## Architecture

Single Gradle module `:app`, layered by package (it can be split into modules later).
Root package and applicationId: `io.github.adccz.elias`.

```
io/github/adccz/elias/
  EliasApp.kt, MainActivity.kt
  core/     shared utilities (time, dispatchers, result types)
  data/     sources (calendar, weather, news, plan, notifications), repositories, Room, DataStore
  domain/   models, briefing builders, use cases – pure Kotlin
  ui/       Compose screens, ViewModels, theme, navigation
  di/       Hilt modules
```

- **Dependency rule:** `ui → domain ← data`. `domain` must not import `android.*` or `androidx.*`,
  so it stays unit-testable on the plain JVM.
- **MVVM + unidirectional data flow:** a ViewModel exposes one `StateFlow<XxxUiState>`, the screen
  sends events up. Composables stay stateless where possible (state hoisting).
- **DI:** Hilt. Constructor injection everywhere; modules only for interfaces and third-party types.
- **Each data source is independent:** timeout, retry, fallback to the last cached value. One failing
  source must never break the briefing.
- **AI is optional:** `TemplateBriefingBuilder` always works without network. `ClaudeBriefingBuilder`
  is tried first and falls back to the template on any error or timeout.

## Non-negotiable rules

- **Time:** `java.time` only, zone `Europe/Prague`. Never call `LocalDate.now()` / `Instant.now()` /
  `System.currentTimeMillis()` directly. Inject `java.time.Clock` so tests can control time.
  Cover DST changes and all-day events (stored in UTC) with tests.
- **Privacy:** from notifications store only the package, the title (sender or conversation) and the time,
  **never the message content**. Overnight messages are **never sent to the AI**. They are rendered
  locally by a template and inserted after the AI response.
- **Secrets:** the API key lives in `local.properties` (git-ignored) and reaches code via `BuildConfig`.
  Never commit keys. CI builds with an empty key.
- **Public repo, private plan:** the real personal plan (`docs/private/`, `app/src/main/assets/plan.json`)
  is git-ignored and must never be committed. Samples, tests and demo data use fictional content only.
- **Quiet by design:** no unnecessary notifications. Follow the sabbath rules in the spec.

## Testing

- Local JVM unit tests: JUnit 6 (Jupiter) + MockK + kotlinx-coroutines-test (+ Turbine for Flows).
  Tests live in `app/src/test/kotlin/`, mirroring the main package.
- Test name style: `` `returns cached weather when network fails`() ``.
- Every parser, date/time calculation and briefing builder gets unit tests. Prefer fakes over mocks for
  our own interfaces.
- CI (GitHub Actions) runs build, unit tests, lint and ktlint on every push.

## Commands

Windows: use `.\gradlew.bat` instead of `./gradlew`. The Gradle daemon needs Java 25
(`gradle/gradle-daemon-jvm.properties`). On this machine use Android Studio's JDK:
`JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` (Git Bash).

```bash
./gradlew assembleDebug          # build the debug APK
./gradlew testDebugUnitTest      # run unit tests
./gradlew lintDebug              # Android lint
./gradlew ktlintCheck            # code style check (ktlintFormat to fix)
./gradlew installDebug           # install on a connected phone (USB debugging on)
```
