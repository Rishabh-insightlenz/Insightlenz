# InsightLenz OS

> *"Not an app. Not a launcher. An operating system that knows you."*

InsightLenz is an AI-native Android operating system designed to act as your personal Jarvis — proactively surfacing insights, controlling your attention, and helping you make better decisions at every step of your day.

## Vision

Every app on your phone passes through InsightLenz. It knows your values, your priorities, your patterns, and your commitments. It doesn't wait to be asked — it thinks ahead, surfaces what matters, and creates intelligent friction between you and your worst habits.

## Architecture

```
DEVICE LAYER (Android / AOSP)
├── InsightLenz Launcher — replaces home screen entirely
├── Attention Layer — monitors all app usage, creates friction gates
├── Always-On Voice — wake word → instant voice interface
└── Notification Controller — everything passes through IL first

        ↕ Encrypted API

CLOUD BRAIN
├── Context Engine — knows everything about the user
├── AI Orchestration — model-agnostic (Claude / GPT / Gemini)
├── Workflow Engine — morning ritual, decisions, reviews
├── Memory & Learning — gets smarter about you over time
└── Integration Layer — calendar, tasks, communications
```

## Repository Structure

```
insightlenz/
├── android/          # Android app layer (Kotlin) — Phase 1
├── aosp-layer/       # AOSP fork modifications (C++) — Phase 2
├── backend/          # Cloud brain (Python/FastAPI)
├── docs/             # Architecture, decisions, specs
└── scripts/          # Dev tooling and automation
```

## Phases

| Phase | What | When |
|-------|------|------|
| 0 | Living prototype on spare Android phone | Now |
| 1 | Android app with Device Owner privileges | Months 1–4 |
| 2 | Full AOSP fork — InsightLenz IS the OS | Months 4–12 |

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Android App | Kotlin |
| System / OS | C++ (NDK + AOSP) |
| Cloud Backend | Python (FastAPI) |
| Database | PostgreSQL |
| AI Orchestration | Python + model-agnostic API layer |
| Wake Word | On-device C++ engine |

## Founder

Built by Rishi — with AI as co-founder.
