# Elias

[![CI](https://github.com/ADCCZ/elias-assistant/actions/workflows/ci.yml/badge.svg)](https://github.com/ADCCZ/elias-assistant/actions/workflows/ci.yml)

**Elias** is a personal voice assistant for Android that speaks Czech. Every morning it tells you, in about
a minute, what happened overnight and what the day holds. In the evening it tells you what is still left
to do and what is coming tomorrow. Then it goes quiet.

> Work in progress. Built step by step as a portfolio project, one milestone at a time.

## Status

| Milestone | Content | State |
|---|---|---|
| M0 – Foundation | Project, Compose, Hilt, CI, docs | In progress |
| M1 – Data sources | Calendar, weather, RSS, personal plan, sunset | Planned |
| M2 – Briefing | Template builder, Claude API builder with fallback | Planned |
| M3 – Voice | Text-to-speech in a foreground service, audio focus | Planned |
| M4 – Scheduling | Exact alarms, prefetch, sabbath rules, status screen | Planned |
| M5 – Overnight messages | Notification listener (metadata only) | Planned |
| M6 – Alarm clock | Alarm that always rings, then Elias speaks | Planned |
| M7 – Polish | Settings, widget, demo mode, run log, screenshots | Planned |

The full specification is in [docs/SPEC.md](docs/SPEC.md) (Czech). Design decisions are recorded in
[docs/DECISIONS.md](docs/DECISIONS.md).

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
| Language and UI | Kotlin, Jetpack Compose, Material 3 |
| Architecture | MVVM, layers `data` / `domain` / `ui` |
| Dependency injection | Hilt (KSP) |
| Networking | Ktor client + kotlinx.serialization (from M1) |
| Storage | Room and DataStore (from M1) |
| Scheduling | AlarmManager `setAlarmClock()`, WorkManager (from M4) |
| Voice | Android `TextToSpeech`, `cs-CZ` (from M3) |
| Time | `java.time`, zone `Europe/Prague`, injected `Clock` |
| Tests | JUnit 6, MockK, kotlinx-coroutines-test, Turbine |
| Code style and CI | ktlint, Android lint, GitHub Actions, Dependabot |

Min SDK 29 (Android 10), target SDK 37 (Android 17).

## Architecture

```
ui      Compose screens + ViewModels (one StateFlow of UI state per screen)
 │
 ▼
domain  pure Kotlin: models, briefing builders, use cases (no Android imports)
 ▲
 │
data    data sources, repositories, Room, DataStore
```

`ui` and `data` both depend on `domain`, never the other way round. A full diagram comes with M7.

## Getting started

**Requirements:** Android Studio (current stable version) with the Android SDK, and an Android phone or emulator
with Android 10 or newer.

1. Clone the repository and open it in Android Studio.
2. Optional: add your Anthropic API key to `local.properties` in the project root (the file is git-ignored):

   ```properties
   ANTHROPIC_API_KEY=sk-ant-...
   ```

   Without a key the app still builds and runs. From M2 on it then uses the local template only.
3. Build and run:

   ```bash
   ./gradlew assembleDebug        # build the APK
   ./gradlew installDebug         # install on a connected phone
   ./gradlew testDebugUnitTest    # unit tests
   ./gradlew lintDebug ktlintCheck
   ```

   On Windows use `.\gradlew.bat` instead of `./gradlew`.

> [!WARNING]
> **Never share an APK built with your API key.** The key is compiled into the app and anyone with the APK can
> extract it. Set a monthly spend limit for the key in the [Anthropic Console](https://console.anthropic.com/).

## Privacy

Elias is built to keep personal data on the phone. This section will describe exactly what is stored and what
leaves the device, and it grows with each milestone. The design rules are:

- **Overnight messages:** only the app, the sender or conversation name and the time are stored, never the
  message content. Records are deleted after 48 hours. This data is **never sent to the AI**.
- **What leaves the phone:** requests for weather (Open-Meteo, fixed coordinates of Pilsen) and news (RSS),
  and, if an API key is set, the structured briefing data sent to the Claude API.
- **No backups:** Android cloud backup and device-to-device transfer are disabled for this app.
- **No personal data in this repository:** the real personal plan is git-ignored. The repository ships
  fictional sample data only.

## About the name

**Elias** stands for *Everyday Life Intelligent Assistant & Scheduler*. The name also points to the prophet
Elijah (Elias). In 1 Kings 19:11–12, God did not speak to him in the great wind, the earthquake or the fire,
but in "a still small voice", the sound of a gentle breeze.

Elias is meant to be like that. It does not shout, does not interrupt and does not fill the day with noise.
In the morning and in the evening it quietly says what really matters. This shapes the design: short briefings,
no unnecessary notifications, and silence on the sabbath.

## Česky

Elias je osobní hlasový asistent pro Android. Ráno a večer česky a stručně řekne, co je důležité: počasí, kalendář,
plán dne, titulky ze světa a termíny. Jméno odkazuje na proroka Eliáše a „hlas tichého vánku“ (1 Kr 19,12),
takže Elias mluví málo a jen když to má smysl. Podrobná specifikace je v [docs/SPEC.md](docs/SPEC.md).

## License

[MIT](LICENSE)
