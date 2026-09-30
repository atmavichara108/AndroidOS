---
type: research
title: Laya — open-source Jev analog audit (corrected from "liya")
project: AndroidOS
status: confirmed
timestamp: 2026-09-30
---

# Laya — the open-source Jev analog

The user's friend mentioned an open-source classifier like Jev called "liya".
The name is actually **Laya** (Russian spelling "лия"). The canonical project is
mature and well-adopted. This is a strong candidate to back the `ExtractionEngine`
port that previously assumed a cloud Jev adapter.

## What Laya is

**Laya** (`github.com/NandhaKishorM/laya`, ~29k stars, Apache-2.0, `pip install laya`)
is a **multilingual, non-autoregressive System-1 decision engine** — the same
category as TypeSafe's proprietary Jev:

- Typed decisions over any state (text/email/ticket/JSON) in a **single forward
  pass** (~33 ms, ~7.2 ms/question batched on T4). No text generation, so nothing
  to parse and nothing to hallucinate.
- Three primitives exactly like Jev: **`choice` / `score` / `noul`**, with
  calibrated confidence (`answer_confidence`) and opt-in `min_confidence` abstention.
- Trained with reinforcement learning against strictly proper scoring rules
  (RLCD), same as Jev.

## Checkpoints (open weights on HF, `convaiinnovations/laya`)

| checkpoint | encoder | params | context | use for |
|------------|---------|--------|---------|---------|
| `laya` | ModernBERT-large | 421M | 512 | English |
| `laya-multilingual` | mmBERT-base | 322M | 1024 (to 8192) | **100+ languages incl. Russian**, 2x faster |
| `laya-typed-decisions` | ModernBERT-large | 421M | 1024 | the typed-decisions workflows |

The `Router` auto-detects script/language and dispatches to the right checkpoint.
Russian text routes to `laya-multilingual`.

## Why this beats the cloud Jev for Pip-Boy

1. **Local / private**: open Apache-2.0 weights can run on-device or self-hosted.
   No raw transcript leaves the device — matches the ADR privacy posture that the
   cloud Jev path violated (TypeSafe's US-hosted service + "do not submit
   confidential info" terms).
2. **Jev-compatible wire**: `laya.serve` exposes `POST /v1/systemone` on the same
   schema Jev returns; a Jev client just repoints its baseUrl. But we do not
   need the cloud at all — we can run the checkpoint locally.
3. **ONNX/on-device path**: `ONNXAgent` + `scripts/export_onnx.py --quantize`
   writes per-channel INT8 for CPU; the `laya-ts` SDK runs inference directly in
   the browser via local ONNX runtime (no Python server). This maps to our Android
   stack (sherpa-onnx already bundles ONNX Runtime for arm64), and to the
   laptop peer as a plain process.
4. **Fine-tunable**: domain decisions jump from 0.362 -> 0.766 accuracy when
   fine-tuned on the project's own decision labels — relevant as we collect
   real approval decisions.
5. **Honest limits**: option budget per question is finite and known (100 max on
   the server, ~20 with long labels); narrow large option sets with `predict_shortlist`.

## Fit vs the `ExtractionEngine` port (jev-classifier.md)

Replaces the provisional external-adapter design with an on-device model:

- typed `choice`/`score`/`noul` answers -> the `ExtractionProposal` + confidence
  contract
- runs behind the same `engineId`/propose port
- `min_confidence` gating -> the confidence-gated approval (RequestApprove /
  ConfirmApproval) we already built
- Russian supported natively via `laya-multilingual`

## Risk / to verify on device

- Runtime + RAM of the mmBERT-base 322M checkpoint on a mid-range Xiaomi —
  needs a real-device benchmark (RTF, RSS), same as STT was.
- INT8 ONNX export quality vs fp32 for Russian decisions.
- Whether `laya-ts`/ONNX agent is JVM-usable directly or needs a small native
  bridge (sherpa-onnx ships prebuilt .so; a similar packaging for Laya's ONNX
  session is the path).

## Evidence

- https://github.com/NandhaKishorM/laya (README, checkpoints, ONNX, Jev-compat)
- https://huggingface.co/convaiinnovations/laya and /laya-multilingual
- The earlier "liya-audit" conclusion (no Liya found) is superseded by this.