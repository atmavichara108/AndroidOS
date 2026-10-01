# Plan — Next slice: Silero RU punctuation + Laya ExtractionEngine

Scope: two parallel, independent workstreams on the **builder lane** (domain/, android-app data/,
sherpa/onnx packaging). No UI/peer-lane files touched. Both are provisional (`[проверить]` on
real device before commit, same as STT was). Read-only planning output; implementation is the
builder session's.

Lane ownership: builder owns `android-app/src/main/kotlin/.../data/`, `domain/src/main/kotlin/...`,
`android-app/build.gradle.kts`, model provisioning. Peer owns `ui/`, `MainActivity.kt` UI-render —
**do not** edit MainActivity's Compose UI; a small host wiring in MainActivity is required, coordinate
via peer_message before touching that shared file.

---

## WORKSTREAM A — Silero RU punctuation/caps restoration

### A1. What problem it solves
sherpa T-one ru CTC emits raw, unpunctuated, lower-case text (`SherpaTranscriber.transcribe`,
SherpaTranscriber.kt:62). User's stated pain is manually re-punctuating every transcript.
Silero `silero_te` v2_4lang (en/de/ru/es) restores `. , - ! ? —` + capitalization on-device:
RU punct WER_p 13/21, caps WER_c 6/7 (vs 18/17, 20/11 baseline). Quantized model <100 MB.

### A2. Where in the pipeline to run it
Insert a punctuation pass **between** ASR decode and persisting the Transcript, so the user sees
punctuated text immediately (RAW already punctuated), then may still edit (→ EDITED).

Concretely, the cleanest single seam is inside `SherpaTranscriber.transcribe()` (SherpaTranscriber.kt:83-88):
after `val text = rec.getResult(stream).text`, run `text = punctuationRestorer.restore(text)` before
building the `Transcript`. This keeps the raw-vs-punctuated decision local to the STT engine and the
`Transcript.status = RAW` semantics unchanged (RAW = engine output; here engine output is post-processed).

Alternative (more explicit, keeps engine pure): a separate `PunctuationRestorer` invoked in
`MainActivity`'s `Transcribe` branch (MainActivity.kt:316) right after `transcriber.transcribe(...)`
and before `storeTranscript`. **Recommend A2-primary (in SherpaTranscriber)** because the restore is
model-provisioning-dependent and belongs with the engine; it also makes the raw text reachable if we
later want to show "before/after".

Decision to leave to builder: keep both the raw and punctuated text? If yes, add a new column
`rawText` to `TranscriptRow` (Rows.kt) + migration. Default recommend: **do not add a column yet**;
store punctuated text, and if a later slice needs raw, that's a small migration. Avoids schema churn
now.

### A3. Which ONNX runtime
sherpa-onnx v1.13.5 AAR **already bundles a native onnxruntime** (`com.github.k2-fsa.sherpa-onnx`).
Silero `silero_te` is PyTorch/TorchScript, **not ONNX by default** — must export to ONNX.
Two runtime options:
- **(rec) Reuse the onnxruntime already in the sherpa-onnx AAR** via the same native JNI
  (`onnxruntime::Ort::GetApi`) that sherpa uses. This adds zero new native dependency. Packaging cost:
  sherpa-onnx already ships the .so; calling into onnxruntime directly from Kotlin needs a small JNI
  shim OR use sherpa's own punctuation example — sherpa-onnx **has a native C++ punctuation example**
  (`k2-fsa/sherpa-onnx` `examples/.../punctuation`) that wraps the ONNX model; exposing it through
  sherpa's existing JNI is the lowest-dependency path.
- **(alt) Add the official `com.microsoft.onnxruntime:onnxruntime-android`** Gradle dep (multi-arch
  AAR, ~10-20 MB per ABI) and call it directly from Kotlin. Cleanest API surface (`OrtSession.run`),
  but duplicates a native runtime alongside sherpa's — larger APK, two ONNX runtimes in one process.

**Recommend the sherpa-native path** first (zero new native dep): either call sherpa's bundled
onnxruntime via a tiny JNI shim, or — simpler — check whether sherpa-onnx v1.13.5 already ships a
punctuation JNI class (k2-fsa has added `Punctuation` to the Android samples). If `com.k2fsa.sherpa.onnx.Punctuation`
exists, use it directly; if not, [проверить] and fall back to the standalone onnxruntime-android AAR.

### A4. Model file to add
Silero `silero_te` — `v2_4lang_q.pt` (quantized, en/de/ru/es, punct `. , - ! ? —`, <100 MB).
- Export to ONNX (Silero repo has an ONNX export script), OR
- [проверить] if a ready ONNX build exists on HF (`snakers4/silero-models` or mirrors); prefer an
  existing ONNX to avoid writing an exporter.
- Provision out-of-band to `files/stt/silero/{model.onnx}` (exact pattern already used for
  `files/stt/t-one/` — gitignored, no blob in Git, matches "no artifacts in repo" rule).
- RU only is enough for this device, but the 4-lang quantized file is the same cost; keep the whole
  `v2_4lang_q` so a later en/de device needs no new model.

### A5. Estimated size / deps
- Model: `v2_4lang_q.pt`/`.onnx` **<100 MB** (quantized); typical ~30-90 MB. Provisioned out-of-band,
  so APK size unchanged.
- Deps: **zero new Gradle deps if the sherpa-native path is used** (reuses bundled onnxruntime);
  `com.microsoft.onnxruntime:onnxruntime-android` only if the native path is unavailable.
- Runtime: Silero TE is a small recurrent net — single pass over transcript text, sub-100 ms on
  modern SoC; [проверить] RSS on the Redmi 15.

### A6. On-device test strategy (user-driven; MIUI blocks adb input)
1. Provision `files/stt/silero/model.onnx` (push via adb or app bundle; MIUI allows adb push).
2. Build + install debug APK.
3. User flow: widget/Record → Stop → Transcribe → **assert** output text now contains `.`, `,`, `!`, `?`
   and capitalized words (visually, in InboxScreen).
4. User then edits a char → confirm status becomes EDITED and punctuation survives edit.
5. Log the RA_PUNCT pass duration; compare against the pre-silero timing in
   `docs/research/stt-benchmark-t-one.md` (RSS ~627 MB first decode; ensure silero adds no second
   high-RSS spike).
6. Capture a screenshot; I (planner) will re-read it to confirm punctuation rendering if asked.
No automated adb tap (INJECT_EVENTS blocked) — all verification is user-tap + logcat
(`adb logcat -s PA_STT PA_PUNCT`), plus `adb pull` of the Room DB to assert `transcripts.text`
contains punctuation.

---

## WORKSTREAM B — Laya as on-device ExtractionEngine

### B0. VERIFIED (web, 2026-10-01) + mandatory staging
Repo and model confirmed real (not hallucinated): `github.com/NandhaKishorM/laya`
(29.5k★, Apache-2.0); HF `convaiinnovations/laya-multilingual` = mmBERT-base 322M,
51 languages incl. Russian, 1024 ctx (→8192), ~33 ms/T4. ONNX path is real:
`laya[onnx]` extra, `ONNXAgent`, `scripts/export_onnx.py --quantize` → per-channel
INT8 for CPU. 28 quantizations + 38 finetunes already on HF.

**Critical limits that change the plan (from the model card):**
- Ships **uncalibrated** and systematically **over-confident** (mean conf 0.75–0.83
  vs much lower accuracy). Raw `answer_confidence` is therefore **not** trustworthy
  for confidence-gated approval until a temperature is refit per (question type,
  option count) on held-out data (card: ECE 0.314 → 0.106 after refit).
- **Near-chance zero-shot on typed-decisions (0.342)** — real capability comes from
  fine-tuning on our own approval labels, which we do not have yet.
- `choice` ≤ ~20 options; `score` is the weakest primitive with a position bias.

**Consequence — staged, gated approach (do NOT wire approval first):**
1. **B-spike (device feasibility, FIRST, blocking):** export `laya-multilingual`
   INT8 ONNX on the laptop peer, provision to `files/laya/`, run ONE real Russian
   transcript through ONNX Runtime on the Redmi 15, measure RTF + RSS. This is the
   acceptance gate (audit's stated device risk). If RAM/latency is unviable on the
   phone, B stops here and becomes laptop-side only — decide then.
2. **B-propose (domain impl):** only after the spike is green — `LayaExtractionEngine`
   behind the port, mapping typed answers → `ExtractionProposal`.
3. **B-wire (approval):** last, and only with calibration — gate on a validated
   cutoff, not raw confidence. Touches MainActivity (shared) → coordinate with peer.

### B1. What it replaces
`ExtractionEngine` port (Ports.kt:15) has **no production implementation** and no committed cloud
Jev adapter. Laya (`github.com/NandhaKishorM/laya`, Apache-2.0, ~29k stars) is the on-device,
privacy-preserving substitute: System-1 typed decisions (choice/score/noul), Jev-compatible wire,
RU via `laya-multilingual` (mmBERT-base 322M). Bypasses the TypeSafe cloud entirely → satisfies the
"no raw transcript leaves device" ADR that the cloud Jev path violated.

### B2. ONNX int8 model acquisition
Laya provides `ONNXAgent` + `scripts/export_onnx.py --quantize` → per-channel INT8 for CPU.
- Checkpoint: `convaiinnovations/laya-multilingual` (mmBERT-base 322M, RU-capable, 1024 ctx).
- Export to **INT8 ONNX** (per-channel, CPU) on the laptop peer; push to phone
  `files/laya/{model_int8.onnx}` out-of-band (gitignored, same as STT).
- [проверить] whether a pre-exported INT8 ONNX already exists on HF to skip the exporter.
- Size: mmBERT-base 322M → INT8 ≈ **~85-110 MB** on disk; [проверить] RSS/RAM and first-inference
  latency on the Redmi 15 (the audit flags this as the key device risk).

### B3. JVM/Android inference bridge
sherpa-onnx AAR already bundles onnxruntime native for arm64. Two bridges:
- **(rec if sherpa-native punct path lands)** add a second ONNX session behind the same bundled
  onnxruntime (or via a small JNI shim) — zero new native dep. Laya is a non-autoregressive
  transformer encoder-only; it runs a single forward pass over the encoded state + option labels
  (~33 ms on T4; [проверить] on phone).
- **(alt) `com.microsoft.onnxruntime:onnxruntime-android`** direct `OrtSession.run` — cleanest JVM
  API, but adds a second runtime. Acceptable if sherpa-native proves too coupled.
- **(rejected) laya-ts via V8/J2V8** — laya-ts targets browser/WASM ONNX; J2V8 adds a whole JS engine
  just to reach ONNX that onnxruntime gives natively. Not worth it on Android.

**Recommend: same native onnxruntime as Workstream A** so both models share one runtime. If A picks
the standalone onnxruntime-android AAR, use that for B too.

### B4. Where the ExtractionEngine port lives / wiring
- Port: `domain/src/main/kotlin/.../port/Ports.kt:15` — `interface ExtractionEngine` unchanged
  (`engineId(): String`, `propose(transcript): List<ExtractionProposal>`).
- New domain-side pure logic if needed (e.g. label→entityType mapping, confidence gating) stays in
  `domain/intent/` (builder lane; matches IntentClassifier/ProjectResolver there).
- New android-side impl `android-app/.../data/LayaExtractionEngine.kt`:
  - `engineId()` → `"laya-multilingual-mmbert-int8"`.
  - `propose(transcript)`: feed transcript text + candidate option labels (TASK/EVENT/CONTACT/… from
    `DefaultEntitySchemas`, existing in domain) through the ONNX session; map `score`/`choice` +
    `answer_confidence` → `ExtractionProposal(entityType, attributes, confidence, sourceTranscriptId, fieldPaths)`.
- Wire into the existing approval flow: `RequestApprove`/`ConfirmApproval` (MainActivity.kt:326-346)
  currently build PendingApproval from `effectiveTitle`. Enhancement (coordinated, peer-lane for UI):
  run `extractionEngine.propose(...)` after transcription and use the top proposal + confidence to
  prefill `entityType`/`attributes`, gating auto-approval when confidence ≥ threshold. This touches
  MainActivity (shared file) → **coordinate with peer before editing**.

### B5. Test strategy
- **Domain unit tests** (pure, no Android): new `domain/src/test/...` asserting that a
  `LayaExtractionEngine`-produced proposal maps score/confidence → `ExtractionProposal` shape, and
  that `min_confidence` gating abstains below threshold. Run: `export JAVA_HOME=$HOME/Android/jdk17;
  ./gradlew :domain:test :android-app:compileDebugKotlin :android-app:lintDebug --console=plain`.
- **On-device (user-driven)**:
  1. Provision `files/laya/model_int8.onnx`; build+install.
  2. User speaks a clear TASK ("создай задачу позвонить маме завтра"), Stop → Transcribe.
  3. Assert: an `ExtractionProposal` with entityType=TASK and confidence logged
     (`adb logcat -s PA_LAYA`); after ConfirmApproval the created Entity has title from speech text
     and correct type (already the effectiveTitle behavior).
  4. Compare proposal accuracy vs expected label; record RTF/RSS.
- Real-device benchmark is the **acceptance gate** (audit's stated risk), same rigor as STT was.

---

## Shared / cross-cutting
- One runtime for A+B if both use bundled onnxruntime → smaller APK than two runtimes.
- Both models provisioned out-of-band; **no binaries in Git** (ADR + AGENTS.md "no model artifacts").
- Both provisional `[проверить]` until device acceptance; keep the current T-one + cloud-free posture.
- Gates before any commit: domain:test + compileDebugKotlin + lintDebug + assembleRelease (release
  must stay clean of `studio/*` per isolation requirement).
- Commit ownership: builder commits only its lane (`domain/`, `android-app/.../data/`,
  `build.gradle.kts`, provisioning docs); never `git add -A`. MainActivity edits go through the peer.

## Suggested order
1. A (Silero) first — small, directly fixes the user's stated pain, reuses existing STT seam.
2. B (Laya) second — bigger (new model, inference bridge), but shares the runtime choice made in A.
3. If A's sherpa-native punctuation route is a dead end (no JNI class), the fallback
   onnxruntime-android AAR becomes the shared runtime and B rides on it.