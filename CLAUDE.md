# CLAUDE.md – E.L.I.A.S. (elias-assistant)

Personal Czech voice assistant for Android. Speaks a short morning and evening briefing.
The name is **written E.L.I.A.S.** on screens and in docs, **spoken "Elias"**, and `Elias` in code identifiers.
**Source of truth for scope and rules: [docs/SPEC.md](docs/SPEC.md).** Read it before starting any milestone.

## How we work

- **Plan first, then code.** Propose a milestone plan and wait for approval.
- **One milestone at a time** (M0–M8 and M4.5 in the spec). After each one, stop and report to the user **in Czech**:
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
  so it stays unit-testable on the plain JVM. Allowed in `domain`: the JDK, kotlinx.coroutines and
  `javax.inject` annotations (JSR-330, not Android).
- **Sources return `SourceResult`** (data or a `FailureReason`), they never throw expected errors.
  Network code goes through `loadSafely`. `TodayOverviewLoader` runs all sources in parallel, each with a time limit.
- **Android-only code stays thin.** For example `ContentResolverCalendarRepository` only copies cursor rows;
  the date logic lives in `CalendarInstanceMapper`, which is unit-tested.
- **Navigation 3:** screen keys are `@Serializable` objects implementing `NavKey` in `ui/navigation`.
- **Briefings:** `BriefingInputs` turns the overview into spoken-Czech facts (`domain/speech`), `TemplateBriefingBuilder`
  and `ClaudeBriefingBuilder` write `BriefingText` sections, `BriefingService` picks AI or template.
- **Voice:** a tap or an alarm starts `PlaybackService` (Media3 `MediaSessionService`, ExoPlayer) with
  `PlaybackService.speakIntent(request)`. It goes into the foreground at once, plays a silent "Připravuji…" loop and
  runs `BriefingRunner` (domain): write the briefing when needed, speak one WAV clip per section with
  `SelectedVoice` (the app's voice `PiperTextToSpeech`, "Oldřich 3.0" with sherpa-onnx, or the phone's
  `AndroidTextToSpeech` as the fallback, offline voices only), swap the loop for the clips, record the run.
  sherpa-onnx stays on 1.13.8 (D37); the voice model is a generated asset, never in git (D38). Screens follow the
  shared `SpeechStatus`; the app has no `MediaController`. Only intents with the process token are obeyed.
  Audio focus is our own (`AudioFocusPolicy`), never ExoPlayer's (`handleAudioFocus = false`). User-started playback
  uses `PlaybackStream.MEDIA`, scheduled playback `PlaybackStream.ALARM`. Media3 APIs marked `@UnstableApi` need
  `@OptIn(UnstableApi::class)` (androidx.annotation), or lint fails.
- **Scheduling:** `WeeklySchedule` (DataStore) and `ScheduleCalculator` (sabbath rules, computed from date and clock
  time in Prague) are pure domain code. `ScheduleKeeper` keeps one alarm per task through `AndroidAlarmScheduler`;
  it runs at app start, on schedule changes, after every alarm and in `RescheduleReceiver` (boot, time, time zone,
  update). Receivers get dependencies through `ScheduleEntryPoint`. The evening briefing opens
  `ScheduledBriefingActivity` with a full-screen notification (`EveningBriefingStarter`), which speaks while visible.
  Never play audio after `BOOT_COMPLETED`, only reschedule.
- **Plans and journal (M4.5):** a topic is a phone calendar marked on Plány (`CalendarSettingsRepository`, DataStore);
  `Topics.agenda` splits a day into events and plans by topic. Calendars switched off on Plány and duplicate
  events are dropped by `SelectedCalendars`, which Hilt provides as the `CalendarRepository`: never read the phone's
  calendar around it. The journal (`Journal`, Room table `journal_entries`)
  stays on the phone: briefing prompts never contain it, `BriefingService` adds the journal sections after the AI
  answer, and only "na čem se zlepšit" with the weekly theme goes to the AI on a tap (`SuggestStep`).
- **Morning routine (M5):** `BibleReading` (`domain/reading`) holds the Bible reading queue (`ReadingQueue`: only what
  is left, one JSON value in DataStore) and the chapters marked as read (Room table `read_chapters`). Book names,
  abbreviations and chapter counts are in `BibleBooks` (ČEP, checked against the source; never from memory).
  `MorningRoutine` picks the day's exercises and recipe from `assets/exercises.json` and `assets/recipes.json` by
  date. The reading, exercises and recipe use the routine day that starts at 4:00 (`JournalDay`). The `ROUTINE` and
  `READING_REMINDER` sections are template sections added in `BriefingService` after the AI answer; the AI never gets
  the morning reading. "Cvič se mnou" is `BriefingRequest.Exercises` (`BriefingKind.EXERCISES`) through
  `PlaybackService` and `BriefingRunner`, on the media stream, never in the run log.
- **Room 3:** package `androidx.room3`, platform SQLite driver, schemas in `app/schemas/` (commit them). DAOs stay thin
  and are not tested on the JVM; test the logic around them with fakes (`InMemorySourceCache`, `FakeRunLog`).
- **Claude API:** `POST /v1/messages` via Ktor, header `anthropic-version: 2026-10-21`. Never send `temperature`,
  `top_p` or `top_k` (400 on that version). Prompts are Czech and live in `data/ai/ClaudePrompts.kt`.
  Never log or display the API key; `ClaudeConfig.toString()` hides it.
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
- Network clients are tested with Ktor `MockEngine` via `createHttpClient(engine, ..., maxRetries = 0)`.
  ViewModel tests register `MainDispatcherExtension`. Test fixtures (RSS, JSON) are fictional.
- Voice tests use the shared fakes in `testing/FakeVoice.kt` (`FakeVoicePlayer`, `FakePlaylistPlayer`...),
  `FakeRunLog` and `TestLoaders.failing(clock)`. DataStore is tested for real on a file in a JUnit `@TempDir`.
  The Android-only classes (TTS, service, receivers, alarms, notifications) stay thin and are checked on a device.
- ViewModels created as test properties must be `by lazy`, so they are built after `MainDispatcherExtension`
  has set the main dispatcher.
- Never call real services from committed tests. A one-off manual check is fine, but delete it afterwards.
- Code with a real-time timeout around a `MockEngine` call (like `ClaudeBriefingBuilder`) is tested with
  `runBlocking`: in `runTest` virtual time the timeout would fire instantly.
- The routine content is checked by tests that read the real assets (`ExerciseFileTest`, `RecipeFileTest`: no
  digits or health claims in exercises, no pork, seafood or other forbidden food in recipes). Keep them passing.
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
./gradlew installDebug           # install on a connected phone (USB debugging on), the APK for its processor
```

The first build downloads the voice model (about 67 MB, SHA-256 checked) into the Gradle user home.
`-Pelias.voiceModel=skip` builds without it (CI does); the app then speaks with the phone's voice.
