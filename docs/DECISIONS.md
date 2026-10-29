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

## D12 – Gradle daemon runs on JDK 25 everywhere (2026-10-27)
- **Context:** Android Studio bundles JDK 25 and generated `gradle/gradle-daemon-jvm.properties` asking for Java 25.
  CI and the command line used JDK 21.
- **Decision:** Keep the file in git and use Java 25 in CI. On the command line, point `JAVA_HOME` to a JDK 25
  (for example Android Studio's `jbr` folder).
- **Why:** The IDE, the terminal and CI build with the same Java, so "works in Studio, fails in CI" surprises
  are less likely. The app's bytecode target stays Java 17.

## D13 – Own RSS parser on the javax.xml SAX parser (2026-10-27)
- **Context:** We only need title, link, guid and date from three RSS 2.0 feeds. Android's `XmlPullParser`
  is not available in plain JVM unit tests.
- **Decision:** A small parser on `javax.xml.parsers.SAXParser`, with external entities blocked (XXE).
- **Why:** The same code runs on the phone and in tests, without an extra library or Robolectric.
  It also handles real-world quirks: CDATA, missing guid, numeric offsets and wrong day names.

## D14 – Data cache waits for M4 (2026-10-27)
- **Context:** The spec wants every source to fall back to its last cached value.
- **Decision:** No cache in M1. Each source reports a clear failure instead. The Room cache comes in M4
  together with the prefetch 30 minutes before the morning briefing.
- **Why:** The cache only pays off with scheduled prefetching. Building it now would make M1 twice as big.

## D15 – The personal plan is an app asset (2026-10-27)
- **Context:** The plan must be replaceable, but it is also private.
- **Decision:** `app/src/main/assets/plan.json` (git-ignored), falling back to the fictional `plan.example.json`.
  Changing the plan means rebuilding the app for now.
- **Why:** Simple and works offline. Importing a plan file on the phone can come with the settings in M7.

## D16 – Repeated stories detected by shared word stems (2026-10-27)
- **Context:** ČT24 and iROZHLAS often report the same story with different titles, so the briefing would repeat it.
- **Decision:** Two titles are one story when they share at least three significant words (four or more letters,
  compared by their first five letters) that make up at least 60 % of the shorter title.
- **Why:** A cheap heuristic that works for Czech word endings without a language library. Tests pin the
  behaviour on both a matching and a non-matching pair.

## D17 – Displayed name E.L.I.A.S., spoken "Elias" (2026-10-27)
- **Context:** The user wants it obvious that the name is an acronym.
- **Decision:** Screens and README write **E.L.I.A.S.**. Speech uses "Elias". Code identifiers stay `Elias`.
- **Why:** Text-to-speech would spell the dotted form letter by letter. Kotlin naming conventions do not allow dots.

## D18 – Claude API called directly with Ktor, not through the Java SDK (2026-10-27)
- **Context:** The official `com.anthropic:anthropic-java` SDK works from Kotlin, but does not list Android support
  and adds Jackson. We make one non-streaming request.
- **Decision:** `POST /v1/messages` with the shared Ktor client, as the spec describes (user's choice).
- **Why:** One HTTP stack and one error-handling path for all sources, tests with `MockEngine`, no extra library.
  The pinned `anthropic-version` header keeps the API stable until we change it on purpose.

## D19 – Consistent AI text without a temperature (2026-10-27)
- **Context:** API version `2026-10-21` rejects `temperature`, `top_p` and `top_k` on every model.
- **Decision:** `output_config.effort: "low"`, structured output (one string per section), a strict prompt,
  and `BriefingValidator` (length, no lists, headings or markup). Anything else falls back to the template.
- **Why:** Structure and validation give more reliable output than a low temperature ever did.

## D20 – Spoken Czech is computed on the phone (2026-10-27)
- **Context:** Czech numerals and dates change form with case and gender ("dvě události", "pětadvacet stupňů",
  "v půl čtvrté"). Language models and text-to-speech both get these wrong now and then.
- **Decision:** `domain/speech` turns numbers, times, dates and Bible references into words. The template uses them,
  and the AI receives them ready-made and is told to copy them.
- **Why:** The grammar is unit-tested once, and the AI only has to connect sentences.

## D21 – Briefings are kept as sections (2026-10-27)
- **Context:** Overnight messages (M5) must appear second in the morning briefing but must never be sent to the AI.
- **Decision:** Both builders return `BriefingText` made of `BriefingSection`s in a fixed speaking order.
- **Why:** The overnight messages section can be inserted locally after the AI answer without parsing free text.

## D22 – The AI output check is looser than the spec's word counts (2026-10-27)
- **Context:** The spec targets 120–180 words in the morning and 60–100 in the evening.
- **Decision:** The validator accepts 100–210 and 50–125 words. The template aims at the spec's ranges.
- **Why:** A slightly long AI text is better than throwing it away. Real limits on silence or length come from
  the spec's intent (about a minute), not from an exact count.

## D23 – Speech goes into files, a Media3 service plays them (2026-10-28)
- **Context:** `TextToSpeech.speak()` plays in the engine's own process: no exact pause and resume, no media
  notification, no control over audio focus. Android 17 also lets sound go on in the background only from a
  foreground service started by the user (or on the alarm stream).
- **Decision:** Each section is spoken into a WAV file (`synthesizeToFile`) in `cache/voice/`. A Media3
  `MediaSessionService` (`mediaPlayback`) with ExoPlayer plays the files as a playlist with short silent clips
  in between. The app connects with a `MediaController` only while a briefing plays. The voice code, including the
  service, lives in `data/voice`.
- **Why:** Pause, resume, "Znovu" and the notification work on whole sections, and Media3 handles the foreground
  service and the system media controls, as Google recommends for Android 17. M4 can reuse it on the alarm stream.

## D24 – Elias handles audio focus itself (2026-10-28)
- **Context:** ExoPlayer handles audio focus only for `USAGE_MEDIA` and `USAGE_GAME` and then asks for permanent
  focus (checked in the Media3 1.11.1 sources): a music app would stop and not come back. With `USAGE_ALARM` it
  throws an `IllegalArgumentException`.
- **Decision:** ExoPlayer gets `handleAudioFocus = false`. `AudioFocusPolicy` (pure, unit-tested) with a thin
  `PlatformAudioFocus` requests `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` and pauses instead of being ducked. A call pauses
  Elias and it resumes within 10 minutes, a permanent loss stops it, and focus is given back on pause and at the end.
- **Why:** Music gets quieter and comes back, as the spec asks ("ztlumit hudbu, při hovoru pauza"), on the media and
  the alarm stream alike. After 10 minutes Media3 leaves the foreground and Android 17 would silence a resume anyway.

## D25 – Offline Czech voices only (2026-10-28)
- **Context:** Some engines offer Czech only as a network voice, which sends the text to a server.
- **Decision:** `VoiceStatusEvaluator` picks an installed offline Czech voice (the one chosen in the phone settings
  first, then the best quality). Without one, Elias does not speak and explains what to fix.
- **Why:** The briefing holds calendar events and the personal plan, and from M5 the names of people who wrote
  overnight. They must not leave the phone through text-to-speech.

## D26 – No notification permission request in M3 (2026-10-28)
- **Context:** Android 13 and newer ask for `POST_NOTIFICATIONS`, but notifications of a media session are exempt.
- **Decision:** M3 does not ask. The request comes in M4 with the first ordinary notifications (sabbath reminder).
- **Why:** Quiet by design: no dialog the user does not need yet.

## D27 – Settings in Preferences DataStore (2026-10-28)
- **Context:** The speech rate is the first setting. More follow in M7.
- **Decision:** One Preferences DataStore file (`settings`), one instance from Hilt, wrapped in repositories such as
  `DataStoreVoiceSettingsRepository`. Values are normalized when read (`SpeechRate.of`).
- **Why:** The recommended replacement for SharedPreferences: asynchronous, observable as a Flow and testable on
  the JVM with a temporary file. Room is kept for the data cache and logs (M4).

## D28 – Room 3 for the cache and the run log (2026-10-29)
- **Context:** M4 needs a database. Room 3.0.3 has been stable since September 2026 (new package `androidx.room3`),
  the last 2.x is 2.8.5.
- **Decision:** Room 3 with the platform SQLite driver (`AndroidSQLiteDriver`), KSP, schemas exported to `app/schemas/`.
- **Why:** There was no database yet, so the newest major version costs no migration. Its DAOs are coroutines-first,
  which fits the code. The platform driver adds no native library.

## D29 – USE_EXACT_ALARM for exact alarms (2026-10-29)
- **Context:** `setAlarmClock()` needs the exact alarm permission. `SCHEDULE_EXACT_ALARM` is denied by default since
  Android 14 and can be revoked. `USE_EXACT_ALARM` is granted at install, but Google Play allows it only for alarm and
  calendar apps.
- **Decision:** `USE_EXACT_ALARM`, plus `SCHEDULE_EXACT_ALARM` up to Android 12L (`maxSdkVersion="32"`).
- **Why:** Elias is not on Google Play and becomes an alarm clock in M6. The briefing must not depend on a switch the
  system can turn off. The status screen still checks `canScheduleExactAlarms()`.

## D30 – The prefetch: an exact alarm starts expedited work (2026-10-29)
- **Context:** The spec wants the data 30 minutes before the morning briefing, with WorkManager. At night the phone is in
  Doze, and WorkManager alone can wait for hours.
- **Decision:** An exact alarm (`setExactAndAllowWhileIdle`, no alarm icon) enqueues an expedited `PrefetchWorker` with a
  network constraint. It retries up to three times when every source failed.
- **Why:** The alarm wakes the app on time and lets it out of Doze for a moment, and WorkManager does the work with
  network constraints and retries. Spec change recorded.

## D31 – The playback service prepares the briefing (2026-10-29)
- **Context:** The M3 phone test showed that Android 17 would mute a briefing when the user leaves the app during
  "Připravuji…", because the service then starts from the background. Scheduled briefings have no screen at all.
- **Decision:** A tap and an alarm both start `PlaybackService` with the request (`speakIntent`). It goes into the
  foreground at once, plays a silent loop titled "Připravuji…", and `BriefingRunner` writes the briefing, speaks it into
  clips and swaps the loop for them. Screens follow the state in the shared `SpeechStatus`; the app no longer connects
  a `MediaController`. Only intents with the process's random token are obeyed, because the service is exported.
- **Why:** One path for every briefing, in the foreground before the first audio focus request. Verified on the
  Android 17 emulator: no hardening warnings for a briefing started just before leaving the app.

## D32 – Run log from M4 (2026-10-29)
- **Context:** The spec has a run log and a Log screen in M7. M4 adds runs nobody watches.
- **Decision:** Record every run in Room now: start, end, trigger, each source's outcome, AI or template and the result.
  Never the briefing's text. Runs are kept for 30 days. The status screen shows the latest one.
- **Why:** Scheduled runs are exactly what needs a log for debugging, and the M7 screen only has to read it.

## D33 – The evening briefing lights up the screen (2026-10-29)
- **Context:** A background service on the alarm stream played on the Android 17 emulator, but it logged
  `AudioHardening … would be muted … reason: 4` for it, with `USE_EXACT_ALARM` and even with `SCHEDULE_EXACT_ALARM`
  granted. The alarm waiver did not apply. The user also wanted to see something on the screen.
- **Decision:** At the evening time the service prepares the briefing silently, with the screen dark and no audio
  focus. When it is ready, a full-screen notification (`USE_FULL_SCREEN_INTENT`, channel of high importance) opens
  `ScheduledBriefingActivity` over the lock screen, and the activity starts the first word. The screen stays lit
  while Elias speaks, not while it prepares (the user's wish). Without the screen within 15 seconds, or while the
  phone is in use, the briefing plays as sound only.
- **Why:** A visible app may always play sound on Android 17, and the user sees what is happening, like with an alarm
  clock. The decision is pure (`EveningStart.choose`) and unit-tested.

## D34 – Headlines since the last briefing, 6 to 24 hours (2026-10-29)
- **Context:** The spec wants only headlines since the last briefing. Played again a minute later, a briefing would have
  none.
- **Decision:** Headlines go back to the start of the last briefing that played (from the run log), at least 6 and at
  most 24 hours.
- **Why:** The morning hears what happened since the evening, and a replay still has news.

## D35 – A morning and an evening time for each day (2026-10-29)
- **Context:** The user wants to set the times freely in the app, separately for each day.
- **Decision:** `WeeklySchedule` with a morning and an evening time per day, each of them can be off, stored in DataStore
  as minutes after midnight. The **Časy** screen edits it. The sabbath rules apply on top. Every run is computed from
  its date and clock time in Prague, never by adding 24 hours.
- **Why:** Free times without extra settings screens, and daylight saving changes cannot shift a run (tested).

## D36 – "Sabat skončil" replaces the evening greeting on Saturday (2026-10-29)
- **Context:** The user wanted "sabat skončil" as the start of Saturday's evening briefing. The evening greeting is part
  of the checklist section (AI and template alike), and a checklist with expenses does not fit the end of the sabbath.
- **Decision:** On Saturday from sunset on, the template's "sabat skončil" section (greeting, a question about the
  study, tomorrow's review) takes the place of the checklist section, after the AI answer.
- **Why:** One greeting, no work list on the sabbath, and the AI needs no special case.

## D37 – sherpa-onnx 1.13.8 for the Piper voice (2026-10-29)
- **Context:** The user wanted a male, more natural Czech voice than Google's. The only ready-made male Czech Piper voice
  is "Jirka" (CC0 data). sherpa-onnx runs Piper voices on Android, but its 2.0 (October 2026) removed espeak-ng, so
  Piper voices would need our own GPL text-to-phoneme library built for Android.
- **Decision:** sherpa-onnx 1.13.8 (September 2026), the last release with Piper's text-to-phoneme built in. It comes
  from JitPack, limited to that one group, and the file was checked against the GitHub release's SHA-256.
- **Why:** A working voice with one dependency instead of building native code. An older version is acceptable here:
  the library only turns text into audio files on the phone, it never touches the network.

## D38 – The voice model is an app asset, downloaded at build time (2026-10-29)
- **Context:** The model is about 63 MB. It must work offline and right after installing, but it does not belong in git.
- **Decision:** A Gradle task downloads `vits-piper-cs_CZ-jirka-medium` from the sherpa-onnx release, checks the
  published SHA-256, keeps the archive in the Gradle user home and unpacks it into generated assets (stored
  uncompressed). Only the Czech and English pronunciation data stay; without the English one espeak-ng crashed. One APK
  per processor type (arm64-v8a, x86_64) without sherpa-onnx's C and C++ API libraries. CI builds with
  `-Pelias.voiceModel=skip`.
- **Why:** The user chose the APK over a download in the app: it works at once and offline. The phone's APK grows by
  about 90 MB (model and ONNX Runtime), which is fine for an app that is not distributed.

## D39 – The app's own voice falls back to the phone's voice (2026-10-29)
- **Context:** Native code can crash the whole app, as the first test on the emulator showed. A scheduled briefing would
  then fail every evening.
- **Decision:** `SelectedVoice` uses the chosen voice and falls back to the phone's voice whenever the app's voice is
  missing or reports a failure. A marker file exists only while the app's voice speaks; found at the next start, it
  switches the voice off until the app is updated, and Náhled textu says so.
- **Why:** A new voice must never cost the user a briefing. The rule is pure and unit-tested; the marker covers what no
  try/catch can.

## D40 – A topic is a calendar on the phone (2026-10-29)
- **Context:** The user wants to plan the next days by topic (school, self-development, Spanish) in Google Calendar,
  and Elias should read the plans grouped by topic.
- **Decision:** Each topic is its own Google calendar. The **Plány** screen lists the phone's calendars and the user
  marks the topics; their ids are kept in DataStore. `Topics.agenda` splits a day into ordinary events and the plans of
  each topic, sorted by the Czech alphabet. Elias only reads the calendar; **Přidat do kalendáře** opens the calendar
  app with the day filled in (the insert intent cannot choose a calendar, the user picks it there).
- **Why:** The user's choice. No prefixes to type and no parsing, each topic keeps its color, and the calendar id is
  already in `CalendarContract.Instances`. No new permission.

## D41 – A journal day starts at 4:00 (2026-10-29)
- **Context:** The journal is written in the evening, which often ends after midnight.
- **Decision:** `JournalDay` counts anything written before 4:00 to the day before. One entry per day; an entry emptied
  of all text is deleted instead of kept blank.
- **Why:** A thought written at 0:30 belongs to the day it is about. The rule is pure and tested, including the night
  the clocks go back.

## D42 – Only "na čem se zlepšit" goes to the AI, and only on a tap (2026-10-29)
- **Context:** The user wanted the AI to suggest a step from the journal. The journal is the most private data in the app.
- **Decision:** **Navrhni krok** sends `StepRequest`: the text of "na čem se zlepšit" and the weekly theme (not the
  theme of the fictional sample plan). The answer is one sentence of at most 40 words (`StepValidator`), shown as a
  suggestion that the user may use or close. The briefing prompts never contain the journal: yesterday's step and the
  evening reminder are template sections added after the AI answer, like the overnight messages (D21).
- **Why:** The user's choice from three options. Sending data is an explicit act, the screen says what leaves the phone,
  and the rest of the entry never does.

## D43 – The journal text lives in the screen, saving in the app's scope (2026-10-29)
- **Context:** Text fields lose characters or the cursor when their state comes through an asynchronous stream, which
  matters for Czech letters typed through the keyboard's composition.
- **Decision:** The editor keeps the typed text in Compose state (`rememberSaveable`, per day) and reports every change.
  The ViewModel saves 0.8 s after typing stops, at once when the screen pauses or another day opens, and in its
  `onCleared`. The save itself runs in the `@ApplicationScope`, so it finishes after the screen is gone.
- **Why:** Google's advice for text field state, and nothing typed is lost when the user leaves the screen.

## D44 – One Claude client for every request (2026-10-29)
- **Context:** The step suggestion needs the same request, timeout and error handling as the briefings.
- **Decision:** `ClaudeClient` sends one structured request and turns every API or network problem into a reason
  (`ClaudeAnswer.Failed`). `ClaudeBriefingBuilder` and `ClaudeStepSuggester` only build the prompt and check the answer.
- **Why:** One tested error path (no credit, bad key, timeouts...) instead of two copies.

## D45 – Calendars can be switched off, in one place (2026-10-29)
- **Context:** While testing Plány the user wanted to switch off any calendar of the account, not only mark topics.
  The spec had the choice of calendars in M7.
- **Decision:** Each calendar on Plány has **Číst** and **Téma**. Switched-off ids are stored in DataStore, so a new
  calendar is read until the user switches it off. `SelectedCalendars` wraps the phone's calendar and drops those
  events; Hilt gives it to everything that reads the calendar. A switched-off calendar cannot be a topic. The topic
  picker stays at the top of the screen and collapses only on Hotovo, so a switch never moves under the finger.
- **Why:** One filter makes the choice hold for the briefings, Plány, Data and the AI alike, with no place forgotten.
  Spec change recorded.

## D46 – An event in several calendars is kept once (2026-10-29)
- **Context:** The user's account has two family calendars, and the same event can be in both. The briefing would say
  it twice.
- **Decision:** `SelectedCalendars` keeps one event per title (ignoring case and extra spaces) and exact start and end.
  The first copy keeps its place; a copy in a topic calendar takes it over, so a plan stays with its topic.
- **Why:** The user's wish. Comparing the exact time keeps a repeated activity at another time (morning and evening
  run) as two events, and doing it in the one filter applies it everywhere.

## D47 – Calendars by account, no add button (2026-10-29)
- **Context:** On the user's phone Plány listed 31 calendars from three Google accounts, the Samsung account and the
  phone itself, with names like "České státní svátky" three times. **Přidat do kalendáře** (D40) could not choose the
  topic calendar, so it saved only picking the date, and the day items ran together.
- **Decision:** `PhoneCalendar.account` (from `Calendars.ACCOUNT_TYPE`: Google and other online accounts by e-mail,
  `LOCAL` as phone only, Samsung's sign-in as Samsung) and `CalendarAccounts.group` put the picker under account
  headers, each with **Číst vše**; an account's main calendar shows as "Hlavní kalendář", unsynced calendars go faded
  to the end. The add button is gone. Every day item shows its calendar's color and its time in a column of its own,
  with a line between the topics and the other events.
- **Why:** The user's choices after the phone test. The account explains the duplicates, and a whole work or school
  account can be switched off in one tap.

## D48 – Synchronizovat asks Android, Plány follow the calendar (2026-10-29)
- **Context:** Calendars made on the web reach the phone only when its Google account syncs. The user missed a sync
  button in Plány, and the screen did not notice new events until it was opened again.
- **Decision:** **Synchronizovat** calls `ContentResolver.requestSync` (manual, expedited) for every online account
  that owns calendars on the phone, read from `CalendarContract.Calendars`; phone-only (`LOCAL`) calendars are
  skipped. `CalendarRepository.changes()` listens to the calendar provider with a `ContentObserver`, and Plány read
  the calendar again 1 s after the changes stop.
- **Why:** A public API that calendar apps use for "refresh", no new permission and no change to any sync setting.
  Whether a calendar syncs at all stays the user's choice in the calendar app. Whether Samsung honours the request
  at once is checked on the phone.
