# Capture Widget Guide

The home-screen widget is the fastest entry point into the recording lifecycle.
It is deliberately a command surface, not a second recording state machine: all
four actions dispatch the same idempotent commands to `RecordingService`.

## Controls

- **Record** — starts a foreground recording (`start`).
- **Pause** — pauses an active recording (`pause`).
- **Resume** — resumes a paused recording (`resume`).
- **Stop** — finishes the recording and publishes the resulting audio item
  (`stop`).

The widget is labelled `PIP-BOY / CAPTURE` and `VOICE INPUT` so its purpose is
clear even when it is placed next to unrelated home-screen widgets. The widget
does not display authoritative state yet; the app screen reads
`RecordingBus.state` for that. A future iteration can add a state-aware widget
without changing the command contract.
