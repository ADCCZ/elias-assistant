# E.L.I.A.S.

[![CI](https://github.com/ADCCZ/elias-assistant/actions/workflows/ci.yml/badge.svg)](https://github.com/ADCCZ/elias-assistant/actions/workflows/ci.yml)

**E.L.I.A.S.** (spoken "Elias") is a personal voice assistant for Android that speaks Czech. Every morning it
tells you, in about a minute, what happened overnight and what the day holds. In the evening it tells you
what is still left to do and what is coming tomorrow. Then it goes quiet.

> Work in progress. Built step by step as a portfolio project, one milestone at a time.

## Status

| Milestone | Content | State |
|---|---|---|
| M0 – Foundation | Project, Compose, Hilt, CI, docs | Done |
| M1 – Data sources | Calendar, weather, RSS, personal plan, sunset, Data screen | Done |
| M2 – Briefing | Spoken Czech, template builder, Claude API builder with fallback, preview screen | Done |
| M3 – Voice | Offline Czech speech, playback service with notification controls, audio focus, speech rate | Done |
| M4 – Scheduling | Times per day, evening briefing on its own, prefetch and cache, sabbath rules, status screen | In review |
| M5 – Overnight messages | Notification listener (metadata only) | Planned |
| M6 – Alarm clock | Alarm that always rings, then Elias speaks | Planned |
| M7 – Polish | Settings, widget, demo mode, run log, screenshots | Planned |

The full specification is in [docs/SPEC.md](docs/SPEC.md) (Czech). Design decisions are recorded in
[docs/DECISIONS.md](docs/DECISIONS.md).

## What it knows today (M1)

The **Data** screen ("Co E.L.I.A.S. dnes ví") shows everything the briefing will be built from, one card per
source, each with its load status or the reason it failed:

| Source | How | Notes |
|---|---|---|
| Calendar | `CalendarContract.Instances`, read only | Repeating events expanded; all-day events handled in UTC |
| Weather | [Open-Meteo](https://open-meteo.com) forecast for Pilsen | Unix timestamps, so DST days are correct |
| News | RSS: ČT24, iROZHLAS, Root.cz | Last 24 hours, duplicates and repeated stories removed |
| Personal plan | `plan.json` in the app's assets | Phases, weekly themes, readings, recurring items, deadlines |
| Sun and sabbath | NOAA solar formulas, computed on the phone | Within a minute of the US Naval Observatory |

Sources load in parallel. Each has its own time limit, and one failing source never hides the others.

## Briefings (M2)

The **Náhled textu** screen shows the morning and evening briefing as Elias would say them, who wrote them and
how long they take to say.

- **Spoken Czech on the phone.** Numbers, times, dates, temperatures and Bible references are turned into words
  with correct Czech grammar before anything else happens: "ve čtvrt na jedenáct", "v úterý sedmadvacátého
  října", "pětadvacet stupňů", "Marek, první kapitola".
- **Template first.** `TemplateBriefingBuilder` writes both briefings from fixed sentences, follows the order
  from the spec and keeps the morning under 180 words. It never invents anything: when a source is missing,
  it says so in one short sentence.
- **AI as an improvement.** `ClaudeBriefingBuilder` sends the same pre-spoken data to Claude Haiku 5.5
  (`POST /v1/messages`, API version `2026-10-21`, effort `low`, structured output with one string per section).
  The answer is checked for length and for anything not meant for listening. Any problem (no key, no credit,
  timeout after 15 seconds, refusal, invalid output) falls back to the template, and the screen shows why.
- **Cost:** about $0.0006 per briefing with Claude Haiku 5.5, well under a dollar a month for two briefings a day.

## Voice (M3)

E.L.I.A.S. reads the briefing aloud: from the home screen (**Přehrát ranní souhrn**, **Přehrát večerní souhrn**,
written fresh from today's data) or from **Náhled textu** (**Přehrát** reads exactly the text on the screen).

- **Offline Czech voice only.** The phone's text-to-speech engine (Google or Samsung) speaks with a `cs-CZ` voice
  that works without network. A voice that needs the network is never used, because the engine would send the whole
  briefing to a server. The voice check on **Náhled textu** says what is missing, and **Opravit** opens the right
  settings: the voice download, or the text-to-speech settings to choose another engine.
- **Section by section.** Each section of the briefing is spoken into its own audio file (`synthesizeToFile`)
  at the chosen speech rate, and the files play as a playlist with a short pause in between.
- **A real playback service.** A Media3 `MediaSessionService` of type `mediaPlayback` with ExoPlayer plays the
  playlist, so it goes on with the screen off and shows a notification with **Znovu** (repeat the current section),
  **Pause** and **Stop**. The lock screen, Bluetooth buttons and the system media controls work too. The service
  ends with the briefing. Android 17 allows background sound only like this.
- **Shares the sound politely.** While Elias speaks, music gets quieter and comes back afterwards. A phone call
  pauses Elias and it continues when the call ends. Unplugging headphones pauses it. If another app takes the sound
  for good (you start music), Elias stops. ExoPlayer's own audio focus handling would stop music for good, so
  E.L.I.A.S. requests transient focus itself (`AudioFocusPolicy`, unit-tested).
- **Speech rate** from 0.7× to 1.5× on **Náhled textu**, stored with DataStore. The estimated duration follows it.
- **No notification permission needed for playback:** media notifications are exempt from Android 13's
  notification permission.

## Scheduling (M4)

E.L.I.A.S. now works on its own.

- **Your times, for each day.** On **Časy** every day of the week has a morning and an evening time, or none
  (7:00 and 22:00 at first). Weather and headlines are downloaded 30 minutes before the morning time (from M6 the
  alarm will ring at it). At the evening time the evening briefing starts by itself.
- **The evening briefing lights up the screen.** Like an alarm clock, a full-screen notification opens the evening
  briefing screen over the lock screen and Elias starts speaking. The screen closes itself at the end. While you are
  using the phone, or without the full-screen permission, the briefing plays as sound only.
- **On time, also after a restart.** The evening briefing is an alarm clock alarm (`setAlarmClock`), the prefetch
  and the sabbath reminder are exact alarms (`USE_EXACT_ALARM`). They are planned again after a restart, a change of
  the time or the time zone and an app update, and nothing plays then. A briefing that would come more than
  15 minutes late is skipped.
- **Sabbath rules.** No evening briefing on Friday and no prefetch for Saturday morning. An hour before Friday's
  sunset a quiet notification lists what to do before the sabbath. On Saturday the evening briefing never starts
  before sunset and opens with "sabat skončil": a question about the day's study and tomorrow's review of goals.
- **The playback service prepares the briefing itself.** A tap or an alarm starts it in the foreground at once. It
  plays a silent loop titled **Připravuji…** while the briefing is written and spoken into clips. So a briefing keeps
  going when you leave the app, which Android 17 would otherwise mute (found in the M3 phone test).
- **Cache.** The last weather forecast and each RSS feed that worked are kept in a Room database. When a source fails,
  the copy is used and the Data screen says from when.
- **Headlines since the last briefing**, at least 6 and at most 24 hours back, so a replay still has news.
- **Run log.** Every run records when it started, who started it, how each source did, who wrote the briefing and how
  it ended. Never the briefing's text. The status screen shows the latest run, the Log screen comes with M7.
- **Stav** shows what Elias needs: calendar access, notifications, exact alarms, no battery optimization, the
  full-screen notification and the Czech voice, each problem with **Opravit**. It also lists the next runs, the last
  run and a link to [dontkillmyapp.com/samsung](https://dontkillmyapp.com/samsung).

All rules are pure Kotlin with unit tests, including the daylight saving changes of 25 October 2026 and 28 March 2027,
midnight, Friday, Saturday and the late summer sunset.

## Planned features

- **Morning briefing:** weather in Pilsen, today's calendar events, the daily plan, two or three world headlines
  and upcoming deadlines.
- **Evening briefing:** tomorrow's events, one reflection question, a reminder to put the phone away.
- **Written for listening:** times and dates in words, no lists or abbreviations.
- **AI optional:** the Claude API polishes the text, but a local template always works without network.
- **Reliable:** data is prefetched before the morning briefing, every source falls back to its cache,
  and the alarm rings even without network or text-to-speech.
- **Sabbath aware:** quiet from Friday sunset to Saturday sunset.

## Tech stack

| Area | Choice |
|---|---|
| Language and UI | Kotlin, Jetpack Compose, Material 3, Navigation 3 |
| Architecture | MVVM, layers `data` / `domain` / `ui` |
| Dependency injection | Hilt (KSP) |
| Networking | Ktor client (OkHttp engine) + kotlinx.serialization |
| Storage | DataStore (speech rate, weekly schedule), Room 3 (source cache, run log) |
| Scheduling | AlarmManager (`setAlarmClock()`, exact alarms), WorkManager with Hilt workers (prefetch) |
| Voice | Android `TextToSpeech` (`cs-CZ`, offline voice), Media3 ExoPlayer and `MediaSessionService` |
| Time | `java.time`, zone `Europe/Prague`, injected `Clock` |
| Tests | JUnit 6, MockK, kotlinx-coroutines-test, Turbine, Ktor MockEngine |
| Code style and CI | ktlint, Android lint, GitHub Actions, Dependabot |

Min SDK 29 (Android 10), target SDK 37 (Android 17).

## Architecture

```
ui      Compose screens + ViewModels (one StateFlow of UI state per screen)
 │
 ▼
domain  pure Kotlin: models, plan rules, sun and sabbath, headline selection, the loader that combines sources,
        spoken Czech, briefing inputs, template builder, AI-or-template choice, voice check and playlist,
        the briefing runner, schedule rules with the sabbath, the prefetch, the run log
 ▲
 │
data    calendar provider, Open-Meteo, RSS, plan file (each returns a SourceResult: data or a failure reason),
        Claude API client, text-to-speech, playback service with audio focus, settings in DataStore,
        Room (cache, run log), alarms and their receivers, the prefetch worker, notifications
```

`ui` and `data` both depend on `domain`, never the other way round. A full diagram comes with M7.

## Getting started

**Requirements:** Android Studio (current stable version) with the Android SDK, and an Android phone or emulator
with Android 10 or newer. The Gradle daemon runs on Java 25, which Android Studio bundles. From a terminal,
point `JAVA_HOME` to a JDK 25, for example Android Studio's `jbr` folder.

1. Clone the repository and open it in Android Studio.
2. Optional: add your Anthropic API key to `local.properties` in the project root (the file is git-ignored):

   ```properties
   ANTHROPIC_API_KEY=sk-ant-...
   ```

   Without a key (or without credit on the account) the app still builds and runs and uses the local template.
3. Optional: put your own plan into `app/src/main/assets/plan.json` (git-ignored). The format is the same as
   [plan.example.json](app/src/main/assets/plan.example.json). Without it the app uses the fictional sample plan.
4. Build and run:

   ```bash
   ./gradlew assembleDebug        # build the APK
   ./gradlew installDebug         # install on a connected phone
   ./gradlew testDebugUnitTest    # unit tests
   ./gradlew lintDebug ktlintCheck
   ```

   On Windows use `.\gradlew.bat` instead of `./gradlew`.

> [!WARNING]
> **Never share an APK built with your API key.** The key is compiled into the app and anyone with the APK can
> extract it. The same goes for your personal plan. Set a monthly spend limit for the key in the
> [Anthropic Console](https://console.anthropic.com/).

## Privacy

E.L.I.A.S. is built to keep personal data on the phone. This section grows with each milestone.

**What leaves the phone:**

- A weather request to Open-Meteo with the fixed coordinates of Pilsen. Nothing about you.
- Requests for the RSS feeds of ČT24, iROZHLAS and Root.cz. Like any web request, they reveal your IP address.
- **Only if an API key is set:** the briefing data goes to the Claude API (Anthropic): the date, the weather
  summary, titles, times and places of today's and tomorrow's calendar events, today's part of the personal plan,
  the chosen headlines and upcoming deadlines. The key is sent only as the `x-api-key` header and never logged.

**What stays on the phone:**

- Calendar events are read on the device. Without an API key they never leave it. The app never writes to the calendar.
- The personal plan is part of the app and is only read locally.
- Speech is made on the phone with an offline voice. Elias never uses a voice that needs the network, so the
  briefing text does not go to a text-to-speech server.
- The spoken audio files are in the app's cache (`cache/voice/`) while a briefing plays. They are deleted when
  playback ends, at the latest before the next briefing. The speech rate and the weekly schedule are stored in the
  app's private storage.
- The last weather forecast and RSS feeds that worked are kept in the app's database. They are public data.
- The run log keeps, for 30 days, when each briefing or prefetch ran, how each source did and how it ended. It never
  holds the briefing's text.
- Android cloud backup and device-to-device transfer are disabled for this app.

**Rules for later milestones:**

- **Overnight messages (M5):** only the app, the sender or conversation name and the time are stored, never the
  message content. Records are deleted after 48 hours. This data is **never sent to the AI**.
- **Overnight messages and the AI:** the briefing is kept as separate sections, so the overnight messages will be
  inserted locally after the AI answer. Names of people from notifications never reach Anthropic.

**No personal data in this repository:** the real personal plan is git-ignored. The repository ships fictional
sample data only.

## Data sources and credits

- Weather data by [Open-Meteo.com](https://open-meteo.com), licensed under CC BY 4.0.
- Headlines from the public RSS feeds of [ČT24](https://ct24.ceskatelevize.cz), [iROZHLAS](https://www.irozhlas.cz)
  and [Root.cz](https://www.root.cz). Only titles are shown, with their source.
- Sunrise and sunset follow the [NOAA Solar Calculator](https://gml.noaa.gov/grad/solcalc/) formulas.

## About the name

**E.L.I.A.S.** stands for *Everyday Life Intelligent Assistant & Scheduler* (in Czech, *Elektronický Lehký
Inteligentní Asistent Studenta*). The name also points to the prophet Elijah (Elias). In 1 Kings 19:11–12, God
did not speak to him in the great wind, the earthquake or the fire, but in "a still small voice", the sound of
a gentle breeze.

E.L.I.A.S. is meant to be like that. It does not shout, does not interrupt and does not fill the day with noise.
In the morning and in the evening it quietly says what really matters. This shapes the design: short briefings,
no unnecessary notifications, and silence on the sabbath.

## Česky

E.L.I.A.S. (čti „Elias“) je osobní hlasový asistent pro Android. Ráno a večer česky a stručně řekne, co je
důležité: počasí, kalendář, plán dne, titulky ze světa a termíny. Jméno odkazuje na proroka Eliáše a „hlas
tichého vánku“ (1 Kr 19,12), takže Elias mluví málo a jen když to má smysl. Souhrn čte nahlas offline českým
hlasem, při hovoru se zastaví a ovládá se z oznámení (Znovu, Pauza, Stop). Od M4 pracuje i sám: v čase, který si
nastavíš pro každý den, rozsvítí obrazovku a přečte večerní souhrn, před ranním souhrnem stáhne data, v pátek hodinu
před západem slunce připomene sabat a v sobotu večer začne slovy „sabat skončil“. Podrobná specifikace je
v [docs/SPEC.md](docs/SPEC.md).

## License

[MIT](LICENSE)
