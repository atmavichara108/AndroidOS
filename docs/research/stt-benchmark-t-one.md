---
type: benchmark
title: STT engine on-device benchmark — sherpa-onnx T-one Russian CTC
project: AndroidOS
status: done (device-measured, provisional per ADR)
timestamp: 2026-09-26
device: Redmi flourite (3c3da9f8), Android 14/15, ARM64
---

# On-device STT benchmark

## Setup
- Engine: sherpa-onnx v1.13.5 (JitPack AAR, arm64-v8a native libs).
- Model: T-one Russian CTC (t-tech/T-one, int8 ~144 MB), files on device at
  files/stt/t-one/{model.onnx,tokens.txt} (provisioned via USB, NOT in git).
- Pipeline: m4a recording -> M4aToPcm (MediaExtractor+MediaCodec AAC->float32
  mono) -> stream.acceptWaveform -> inputFinished -> decode -> text.
- Input: user recordings (Record->Stop) in Recordings/ dir.

## Confirmed observations (user-run)
- Short recordings (~2-10 s audio): transcription appears essentially
  instantly after pressing Transcribe (fits "near-instant on short audio").
- Russian speech recognized correctly in practice (user read "один два три
  четыре пять точка пять четыре три два один поехали" and it transcribed
  verbatim).
- Model loads once and stays in RAM (process RSS grew 317->~620 MB on first
  decode, then stable; recognizer is kept alive in SherpaTranscriber).
- Deletion (tombstone + file), edit-to-EDITED, scroll all work on device.

## Measured run (long audio, user-read Russian)
- Audio: f9fe67a6-...m4a, duration **23.94 s** (parsed from mvhd timescale),
  38 KB AAC file.
- Wall time: inbox row capturedAt (Stop) `07:14:42.932Z` -> transcript row
  committed in Room WAL `07:14:52.465Z` = **9.53 s** total (includes the user
  tapping Transcribe, so this is an upper bound on decode time).
- **RTF <= 0.40** (worst case, immediate tap); ~0.27–0.36 for a 1–3 s human tap
  delay. Comfortably below the ~0.5 real-time-ish target.
- RAM: first decode grew process RSS 317 MB -> ~627 MB (HyperSentinel logs);
  stable afterwards with recognizer kept alive.
- Quality (manual WER against the read text): model transcribed the counted
  Russian numbers correctly ("... одиннадцать двенадцать тринадцать ...",
  311 chars RAW). No OOM, no crash; transcribe->RAW->EDITED flow verified.
- Thermals: no thermal throttling or OOM observed during the run (device did
  not report distress; exact temp not instrumented).

## Provisional verdict
Functional PASS + performance PASS for this device: RTF <= 0.40 on ~24 s
Russian speech with the T-one CTC model. T-one remains the provisional
default per ADR; whisper.cpp/small and vosk-ru stay as candidates only if
future corpus/quality runs disprove it.

## Links
- docs/research/stt-engine-selection.md (engine choice rationale)