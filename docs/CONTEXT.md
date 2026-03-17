# InsightLenz — Project Context Brief

*Share this document at the start of every session to restore full context.*

---

## What InsightLenz Is

An AI-native Android operating system — not an app, not a launcher, but a true OS layer that sits above every application on the device. The reference experience is Jarvis from Iron Man: always present, deeply personal, proactive, voice-first, and calibrated entirely to its user.

InsightLenz controls every app on the device. Before any app is opened, InsightLenz is aware. Every notification passes through it. Every attention decision is mediated by it. The phone stops being a distraction machine and becomes a thinking tool.

---

## The Problem It Solves

For founders and high-performers operating under cognitive load:
- Reactive behavior hijacks intentional work
- Priorities are clear in theory but don't translate to action
- Apps are designed to capture attention, not protect it
- No existing tool knows the user deeply enough to give genuinely personal guidance

InsightLenz is the answer to all four.

---

## Core Architecture Decisions

**The device is a thin client. The brain lives in the cloud.**

- The Android device handles: UI, voice I/O, wake word, app interception, notifications
- The cloud handles: AI orchestration, context engine, memory, workflow logic, integrations
- AI models are fully pluggable — swap Claude/GPT/Gemini at the backend level with zero app changes
- The context engine is the real IP — it compounds in value the longer a user is on the system

**Three OS layers:**
1. Attention Layer — monitors app usage, creates intentional friction gates
2. Context Layer — user profile, values, priorities, patterns, relationship graph
3. Intelligence Layer — AI orchestration, proactive surfacing, workflow engine

---

## Tech Stack

| Layer | Technology | Rationale |
|-------|-----------|-----------|
| Android App | Kotlin | Standard, well-supported |
| System/OS Level | C++ (NDK + AOSP) | Rishi knows C++; AOSP is C++ |
| Cloud Backend | Python + FastAPI | Rishi knows Python; fast iteration |
| Database | PostgreSQL | Rishi knows SQL |
| AI Layer | Model-agnostic Python | Swap models without app changes |
| Wake Word | On-device C++ | Low power, always-on |

---

## Build Phases

**Phase 0 — Living Prototype (Now)**
Configure spare Android phone manually: minimal launcher, Claude with full context, wake word app, single-purpose home screen. Goal: experience the product before building it.

**Phase 1 — Real Product (Months 1–4)**
Android app with Device Owner (DPC) privileges. Kotlin frontend, Python backend, context engine v1, voice interface. First version that goes in someone else's hands.

**Phase 2 — Full OS (Months 4–12+)**
AOSP fork. InsightLenz replaces Android. Every layer of the device is owned by InsightLenz. Ship as a product.

---

## Founder Profile

**Name:** Rishi
**Background:** Quant engineer — strong in C++, Python, MySQL. Not a mobile developer but technically deep and fast-learning.
**Build approach:** Rishi + AI (Claude) as co-founder. Full commitment.
**Current situation:** Growing founder dealing with operational complexity. InsightLenz is both a personal tool and a product he's building.

---

## Product Name

**InsightLenz** — seeing your world through a clearer lens.

---

## What's Been Decided

- Build on Android (spare phone as Phase 0 prototype)
- OS by design, not app by design — controls every app on the device
- Cloud brain, thin device client
- AI layer is pluggable from day one
- Python backend (Rishi's strength)
- C++ for system layer (Rishi's strength)
- Kotlin for Android app layer (to learn with AI support)
- GitHub for version control
- Privacy not a primary concern — cloud-first is fine

---

## What's Still Open

- [x] GitHub repo — github.com/Rishabh-insightlenz/Insightlenz
- [x] Spare Android phone — Samsung Galaxy M07 (Android 15, One UI 7, Helio G99, 4GB RAM)
- [x] Backend architecture — FastAPI + PostgreSQL + Redis + model-agnostic AI
- [x] Context engine — built and tested
- [x] First live InsightLenz response — generated via Groq/llama-3.3-70b
- [ ] PostgreSQL running locally (needs: docker-compose up -d, then bash run.sh)
- [ ] Anthropic API credits (add $5 at console.anthropic.com to switch to Claude)
- [ ] Android app — Kotlin skeleton not started yet
- [ ] ADB wireless debugging — phone ready, waiting for first APK

---

## Next Session — Start Here

**To get the full stack running locally:**
```bash
# 1. Start PostgreSQL + Redis (requires Docker Desktop)
docker-compose up -d

# 2. Start InsightLenz backend (one command)
bash run.sh
```

**To verify everything is working:**
```bash
curl http://localhost:8000/health
# Should show: database: "connected"
```

**What to build next:**
1. Wire up repository layer to the API routes (so context persists across restarts)
2. Start the Android app — Kotlin project in `android/` folder
3. Add `$5` Anthropic credits to switch from Groq to Claude Sonnet

---

## Session Log

| Date | What We Did |
|------|-------------|
| 2026-03-17 | Brainstormed vision, locked architecture, named the product, set up repo structure |
| 2026-03-17 | Built backend v0.1 — Context Engine, AI Orchestrator, Workflow Engine, API routes |
| 2026-03-17 | Added Groq provider. **InsightLenz spoke for the first time.** Morning brief generated with full Rishi context. |
| 2026-03-17 | (Overnight) Built full PostgreSQL persistence layer — DB models, Alembic migrations, repository pattern, conversation history, insight storage, app usage tracking. Added docker-compose.yml and run.sh one-command startup. |
