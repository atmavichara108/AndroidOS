# Studio Interface Guide

Embedded UI lab for AndroidOS PA. It is a **separate debug-only screen**
(`src/debug`, launcher "AndroidOS Studio"). It renders synthetic, side-effect-free
scenarios and never touches Room, the microphone, alarms, or user data. It is not
yet the shared production Pip-Boy UI; production screens remain the runtime
owner's lane.

Run with `bash tools/gradle-java17.sh :android-app:assembleDebug`, install, then
open the **AndroidOS Studio** launcher (or `adb shell am start -n
ru.rudra.androidos.pa/.studio.StudioActivity`).

## Layout (top to bottom)

1. **Header** — `PIP-BOY / STUDIO` title, `UI scenario laboratory` subtitle, and a
   persistent `SIMULATED` indicator. The indicator is pinned outside the scrolling
   content so it stays visible while you use the preview.
2. **SCENARIOS** — scenario selector buttons and a description of the selected one.
3. **Controls** — `Reset fixture`, `Replay session`, theme toggle, `Inspector` toggle.
4. **SIMULATED PREVIEW** — the synthetic Pip-Boy inbox.
5. **INSPECTOR** — read-only state and action log (toggleable).

## Scenario buttons

| Button | What it shows | Why you'd use it |
|---|---|---|
| Empty inbox | No items, empty-state copy | Check the empty state hierarchy |
| Captured note | One normal inbox item awaiting approval | Check the everyday approval surface |
| Long transcript | A long Russian transcript | Check wrapping and dense text |
| Storage error | A recoverable error copy | Check failure copy and retry |
| Audio note | An audio item waiting for transcription | Check the explicit STT flow |

The selected scenario is the filled button; the others are outlined.

## Controls

- **Reset fixture** — returns to the selected scenario's original state, clearing
  approvals and error.
- **Replay session** — re-applies the recorded action list over the initial
  scenario, reconstructing the state deterministically.
- **Light theme / Pip-Boy theme** — toggles between the light color scheme and the
  dark Pip-Boy green scheme.
- **Inspector** — shows/hides the read-only inspector panel below the preview.

## Preview (inbox) items

Each inbox row shows:

- a `New note` field with a `Capture` button,
- a **Record / Stop** button next to the field (synthetic in Studio: toggling to
  Stop appends a voice-captured item; no MediaRecorder side effect; hidden in the
  error scenario),
- a timestamp and a state tag (`CAPTURED`, `TRANSCRIPT_EDIT`, ...),
- the transcript/note body,
- two actions: `→ Task` and `→ Event`,
- an `Approved: …` label once a destination has been approved.

For an `AUDIO` item, **Transcribe** creates a synthetic RAW transcript in Studio.
`Edit transcript` opens it for editing; changes become an `EDITED` transcript.
The same controls are intended for the production Room/Transcriber flow.

## Transcription Flow

1. Select **Audio note** in Studio.
2. Press **Transcribe** on the audio row.
3. The row shows the synthetic RAW transcript.
4. Press **Edit transcript**, change the text, and observe the EDITED status.

Production uses the same actions but delegates the work to the selected
`Transcriber`; Room remains the source of truth. No automatic/background
transcription is implied by this UI.

**Recordings** — a section under the note list showing finished recordings
(`capturedLabel · size KB`). In Studio the items are synthetic and the rows are
disabled (no `onPlay`); in production tapping a row plays the m4a.

`→ Task` and `→ Event` are the simulated approval actions. Approving a destination
adds it to `state.approvals`; the same destination cannot be approved twice for the
same item. The retry button (visible only in the error scenario) clears the error
and restores the captured fixture.

## Inspector

A read-only panel that reports the current scenario, `simulated = true`, item
count, approval set, error, and the full replayed action log. This is the
visibility layer that the UI/UX workflow relies on to confirm what the preview is
showing.

## Current limitations

- This screen is **not** the shared production inbox yet; UI changes here do not
  yet affect the real `MainActivity` inbox.
- Replay and state live in memory only (`remember`) and are lost when the activity
  is recreated.
- There is no visual model or desktop mirroring yet.
