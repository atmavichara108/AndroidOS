# Nova-3 vs on-device Russian ASR — what drives quality and what is reachable offline

Date: 2026-09-30. Scope: explain Deepgram Nova-3's perceived quality, map each capability to
cloud-vs-on-device, and answer whether a Redmi 15 (Android, must NOT upload raw audio) can reach
near-Nova-3 quality for Russian.

TL;DR: Nova-3's perceived quality is mostly **(a) acoustic WER from a huge cloud transformer** plus
**(b) a stack of formatting/diarization/filler/keyterm features that are almost all cheap
post-processing** and are reproducible on-device. The single biggest, cheapest win for this project
is **not a bigger ASR model — it is punctuation restoration**. The user's current sherpa T-one ru CTC
is fine acoustically for clean dictation; the pain (manual punctuation) is fixed entirely on-device by
adding a Russian punctuation/capitalization model (Silero `silero_te`) + a number-formatting pass.
If more acoustic accuracy is needed later, whisper-large-v3-turbo (int8) is the realistic on-device
upgrade, but it is heavy for a phone.

---

## 1. What drives Nova-3's perceived quality (capability → cloud or on-device)

Evidence sources: Deepgram docs (models-languages-overview, keyterm, smart-format, punctuation,
diarization, numerals, filler-words, language-detection, measurements, model).

| Capability | Nova-3 mechanism | Cloud needed? | On-device equivalent |
|---|---|---|---|
| **Low WER / acoustic accuracy** | Large Transformer trained on huge curated data; domain options (`-medical`, `-pharma`, `-meeting`, `-phonecall`). Claims **54.2% lower WER streaming / 47.4% batch vs competitors**. | **Yes** (scale + data) | whisper-large-v3-turbo locally; sherpa Russian CTC/transducer (T-one, nemo-giga-am, zipformer-ru) |
| **Punctuation + capitalization** | `punctuate=true` / `smart_format=true`; model-trained, restores caps + `.,—!?` | Cheap post-process | **Yes, natively**: whisper emits punctuation in its seq2seq output; Silero `silero_te` restores it on-device for RU |
| **Diarization** | Separate v2 diarizer model (not Whisper-compatible); `diarize_model` | Compute-heavy | sherpa-onnx has local speaker diarization, but it is heavy for a phone; not needed for single-speaker dictation |
| **Filler-word removal** | `filler_words=false` (default) strips "uh"/"um" | No — trivial | Word-list regex; trivially on-device |
| **Numbers / currency / dates** | `numerals=true`, `smart_format=true` (formats 8:37 PM, $, phone, tracking) | No — rule-based | ICU `NumberFormatter` / date parser on-device; **RU supported by numerals** |
| **Measurements (mg, ml)** | `measurements=true` | No | Abbreviation mapping table |
| **Keyterm prompting** | `keyterm=` (up to 100 terms, 500 tokens); neural bias, self-serve customization **without retraining**; boosts KRR up to 90% | **Yes** (neural customization) | Partly: hotword/KWS vocab or small bias; **no true equivalent in sherpa T-one or whisper** |
| **Language autodetect** | `detect_language=true`, 35 langs incl `ru`; returns `language_confidence` | Cloud-native | whisper `detect_language()` runs locally |
| **Entity names / proper nouns** | Keyterm + entity detection + smart-format casing | Mostly cloud | Keyterm list from user's profile/contacts; on-device |

**The "huge recording window" perception.** Batch (`pre-recorded`) Nova-3 processes the whole
utterance, so all formatting features and the acoustic context apply to the full clip. In streaming,
smart-format **waits up to 3 seconds of silence** before finalizing an entity (docs/smart-format) —
so longer, whole-clip transcription is where Nova-3 "feels best": numbers, names and punctuation are
consistent across the full text. This is a UX property, not a fundamental model property; on-device
batch (record full clip, then transcribe) reproduces the same benefit with any engine.

---

## 2. WER evidence — Russian

**Deepgram does not publish per-language Russian WER.** The only public number is the pooled claim:
"54.2% reduction in WER for streaming and 47.4% for batch processing compared to competitors"
(docs/model, docs/models-languages-overview). Russian is supported by `nova-3` (`ru`) and by
`numerals` (`ru`), but no Russian WER is disclosed.

| Model | Russian WER (approx) | Dataset | Verifiable? |
|---|---|---|---|
| Deepgram Nova-3 | not disclosed | — | No per-language number published |
| whisper large-v3 | ~8–10% (figure in model card) | Common Voice 15 / Fleurs | Figure in openai/whisper model card; read approx [проверить] |
| whisper large-v3-turbo | ~large-v3 minus small delta ("minimal degradation") | same | openai/whisper README states turbo ≈ large-v3 |
| sherpa T-one ru CTC (current) | not published by sherpa | — | [проверить]; user-measured RTF ok, quality ok |
| sherpa nemo-giga-am-russian / zipformer-ru | not published | — | [проверить] |
| Vosk Russian | ~[проверить] | vosk blog | exists but not fetched here |
| FunASR SenseVoice / Paraformer | SenseVoice covers zh/en/ja/ko/yue **only**; Paraformer is zh-centric | — | Not a Russian option |

Note: raw WER ignores punctuation — punctuation is scored separately (see §3). So two models with
equal WER can feel very different in usability, which is exactly the friend's Nova-3 vs T-one gap.

---

## 3. On-device reachability for the Redmi 15 assistant

Constraint: **no raw audio may leave the device.** So all Deepgram cloud features are out unless
post-processing on the transcript only (which is fine — transcripts are not raw audio, and the project
already treats them as local).

(a) **Clean dictation (single speaker, phone mic).** Reachable. The current T-one ru CTC is adequate;
the stronger local options are `sherpa-onnx-zipformer-ru-2024-09-18` and `sherpa-onnx-nemo-*-giga-am-russian`.
For near-Nova-3 acoustic quality the realistic ceiling on a phone is **whisper-large-v3-turbo int8**
(809 M params, ~6 GB VRAM fp16, ~8× faster than large, minimal accuracy loss). On a midrange SoC this
is marginal (slow, several hundred MB, RAM pressure) — **[проверить] on the actual Redmi 15** before
committing.

(b) **Punctuation + capitalization.** Reachable and the highest-value fix. Two options:
- whisper-large-v3(-turbo) emits punctuation and capitalization **natively** in its seq2seq output — no extra model.
- Keep T-one (or any CTC) + **Silero `silero_te`** on-device Russian punctuation/capitalization
  restoration. Silero published explicit Russian metrics (habr post 581946, silero-models repo):
  punctuation WER_p **13 (validation) / 21 (books)** and capitalization WER_c **6 / 7**, versus
  trivial baseline 18 / 17 and 20 / 11. Quantized model <100 MB. This directly solves the user's
  stated pain (manually re-punctuating T-one transcripts) with minimal compute.

(c) **Diarization / filler.** Filler removal is a trivial on-device word-list (Nova itself just
strips "uh"/"um" by default). Diarization is not needed for single-speaker dictation; if ever needed,
sherpa-onnx has local diarization but it is heavy for a phone.

**Recommended path for this project (cheapest → most accurate):**
1. Keep T-one acoustics (or move to zipformer-ru for a small WER gain).
2. Add Silero `silero_te` (RU punctuation + caps) after transcription, on-device. ← fixes the actual pain.
3. Add a number/date/currency formatting pass (Android `NumberFormat`/`DateUtils`) to match smart-format.
4. Add a keyterm list built from the user's contacts/profile (like Keyterm Prompting) — approximate.
5. Only if acoustic WER is still not enough: evaluate whisper-large-v3-turbo int8 on the device
   (latency/RAM) and keep CTC as the fallback.

---

## Source URLs
- Deepgram Nova-3 model & language matrix: https://developers.deepgram.com/docs/models-languages-overview
- Nova-3 WER claim: https://developers.deepgram.com/docs/model
- Keyterm prompting: https://developers.deepgram.com/docs/keyterm
- Smart formatting (3 s silence finalization): https://developers.deepgram.com/docs/smart-format
- Punctuation: https://developers.deepgram.com/docs/punctuation
- Diarization: https://developers.deepgram.com/docs/diarization
- Numerals (RU supported): https://developers.deepgram.com/docs/numerals
- Filler words: https://developers.deepgram.com/docs/filler-words
- Language detection (RU supported): https://developers.deepgram.com/docs/language-detection
- Measurements: https://developers.deepgram.com/docs/measurements
- Silero punctuation/caps RU model (metrics): https://habr.com/ru/post/581946/ ; https://github.com/snakers4/silero-models
- sherpa-onnx (RU models, punctuation, diarization): https://github.com/k2-fsa/sherpa-onnx ; https://k2-fsa.github.io/sherpa/onnx/punctuation/index.html
- Whisper models & sizes (turbo 809 M, native punctuation): https://github.com/openai/whisper#available-models-and-languages