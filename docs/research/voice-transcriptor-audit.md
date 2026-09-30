---
type: research
title: Voice-Transcriptor audit — desktop STT, not an Android/on-device fit
project: AndroidOS
status: provisional
timestamp: 2026-09-30
---

# Voice-Transcriptor audit (leoerdman/Voice-Transcriptor)

Reference supplied by the user: "best transcriber, huge recording window and
understanding; runs a paid service, $200/month quota, used heavily without
hitting it." Audit for whether it belongs in Pip-Boy.

## What it is

- **Desktop Electron app** (macOS Apple Silicon / Windows x64 / Linux x64).
  Not a mobile library; no Android/iOS build. Single-maintainer, 19 stars,
  1 fork, 632 commits, MIT.
- Purpose: live mic transcription with global hotkeys + auto-paste into the
  focused field, file/video upload, local searchable history, AI cleanup.
- Intended for a desktop user/agent CLI, not an embedded assistant surface.

## Engines (the actual quality/price axis)

| Engine | Where | Cost | Offline? |
|--------|-------|------|----------|
| Local Whisper | on the desktop machine | free | yes |
| Deepgram Nova-3 | cloud API | **paid** ($200/mo quota = the friend's number) | no |
| OpenRouter | cloud API | paid / pay-per-token | no |

The "enormous recording window / best understanding" the friend describes
is **Deepgram Nova-3** — a cloud transcription API. That is exactly the path
our privacy/ADR stance rules out for raw audio: transcripts/audio must not
leave the device without explicit approval. The offline Local Whisper path
is what we already have (and beat on Android via sherpa-onnx).

## Mismatch vs Pip-Boy

1. **Platform**: our runtime is Android (phone + laptop peer), offline-first.
   This is a desktop-only Electron tool — cannot drop into our Gradle/Kotlin
   stack or the widget/foreground-service pipeline.
2. **Privacy**: the standout feature is cloud (Deepgram). Our ADR: raw audio
   local by default, export requires approval. Adopting Deepgram inverts the
   core posture.
3. **Runtime**: nothing here improves on-device Russian STT (we use sherpa-onnx
   + T-one ru CTC, see stt-engine-selection.md; device-verified).
4. **Exit path**: single maintainer, no releases on GitHub (build from source
   only), no Android story. High risk, low fit.

## Reusable ideas (not the app itself)

- **Global-hotkey capture + auto-paste to focused field** is a great desktop
  UX pattern; our Android equivalent is the widget command path + foreground
  service, which we already have.
- **CLI exposure for agents** (a tokenized subcommand that only reaches the
  transcription routes) is a clean pattern worth mirroring if we ever expose
  a desktop peer transcription CLI.

## Verdict

Do **not** integrate Voice-Transcriptor. It is a desktop wrapper around cloud
STT; our on-device sherpa-onnx already covers the offline need on the phone
that mattershare. Keep the audit for the pattern ideas only. If the user ever
wants a desktop peer transcription tool, we'd build a thin Kotlin/JVM CLI over
our own offline engine rather than adopt an Electron app whose only quality
edge is cloud-only.

## Evidence
- https://github.com/leoerdman/Voice-Transcriptor (README, engines, platforms, pricing model)