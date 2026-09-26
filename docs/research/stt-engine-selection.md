---
type: research
title: STT engine selection evidence — Russian, on-device, Android
project: AndroidOS
status: provisional
timestamp: 2026-09-26
---

# STT engine research (Russian, offline, on-device)

Candidate set per ADR: Vosk / sherpa-onnx / whisper.cpp. Question: which
runtime + model for a Russian-speaking personal assistant on a mid-range
Xiaomi (Redmi, Android ~14/15, ARM64)?

## Evidence collected (external, reproducible sources)

1. **VoicePing benchmark (2026-02, open-source app + Samsung S10)** — 16
   models, 6 engines, Android. Key numbers for 22s English audio, CPU:
   - Whisper Tiny via sherpa-onnx: RTF 0.07 (2.0s), ~100MB model
   - Whisper Base via sherpa-onnx: RTF 0.13, ~160MB
   - Whisper Small via sherpa-onnx: RTF 0.41, ~490MB
   - **Whisper Tiny via whisper.cpp: RTF 3.52 (105s!)** — same model,
     51x slower than sherpa-onnx backend on Android
   - No OOM across all 16; model downloads user-initiated from HF.
   Limitation: benchmark is **speed-only, no WER for Russian**.

2. **Korean mobile study (2026-04, Galaxy A35/Exynos1380/Termux)** —
   quality ranking: whisper.cpp ≫ sherpa-onnx ≫ Vosk for STT accuracy;
   Vosk "word salad" on long context, sherpa-onnx quantized models break
   proper nouns; whisper.cpp stable, no OOM, RTF 0.88 for 60s audio.
   Caveat: Termux/Termux-pipeline, not a native app; Korean not Russian.

3. **VoxRT comparison (vendor, directional)** — Vosk Small en 9.85% WER;
   whisper.cpp base.en ~4.4%; sherpa-onnx Zipformer 20M 3.88% (HF card
   only). Mobile RTF for whisper.cpp base estimated ~1.8 on cheap Android
   (slower than real-time streaming).

4. **Russian model availability (sherpa-onnx issue #2435 + PR #2502)** —
   **Russian is offline-only in sherpa-onnx** (no streaming ru models from
   sherpa itself); added Aug 2025: Vosk streaming ru models via sherpa
   (alphacep vosk-model-small-streaming-ru) and T-one Russian CTC
   streaming model (2025-09). So: Russian streaming exists via sherpa-onnx
   runtime (T-one / vosk-ru), offline batch has more options.

5. **Crawpress guidance (2026-05)** — mobile-first route: sherpa-onnx with
   small streaming model; Vosk small = smallest stable offline (~40MB ru
   small ~48MB); whisper.cpp tiny/base realistic on phones, small is the
   upper edge; real test = 10 min on a real phone offline, watch thermals.

## Interpretation for PA

- **Engine**: sherpa-onnx is the strongest Android runtime candidate
  (51x faster whisper.cpp path on Android for same model; official AAR
  for arm64; Apache-2.0). whisper.cpp = quality fallback/second opinion.
  Vosk = smallest footprint, weakest quality; its streaming ru models run
  inside sherpa-onnx anyway.
- **Russian model candidates to benchmark** (all offline, user-downloaded
  from HF, not bundled in repo/APK):
  1. sherpa-onnx + T-one Russian CTC streaming (small, 2025-09)
  2. sherpa-onnx + alphacep vosk-model-small-streaming-ru (via sherpa)
  3. whisper.cpp / sherpa-onnx Whisper small or base (99 languages,
     batch, quality baseline) — quality anchor
- **Benchmark protocol on Redmi 3c3da9f8**: fixed Russian corpus (not in
  Git), offline mode, measure RTF, RAM peak, battery/thermal, WER by
  hand-check against corpus; retention: model files in app-external dir.

## Provisional decision (to be confirmed by on-device benchmark)

sherpa-onnx runtime + Russian model (T-one CTC streaming as primary
candidate; vosk-ru streaming as second; Whisper batch as quality anchor).
Per AGENTS.md this stays provisional until P0-07 real-device benchmark.

## Links

- https://voiceping.net/en/blog/research-offline-speech-transcription-benchmark/
- https://github.com/k2-fsa/sherpa-onnx/issues/2435 (ru streaming status)
- https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-ctc/t-one-ctc-models.html
- https://crawpress.com/articles/choosing-an-on-device-speech-to-text-model-funasr-whisper-vosk-xiaomi-mimo-and-the-open-asr-field
- https://voxrt.com/asr-comparison
