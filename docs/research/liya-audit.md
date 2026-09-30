---
type: research
title: Liya — open-source Jev analog audit
project: AndroidOS
status: [проверить]  # user-claimed, not yet verified as real/discoverable
timestamp: 2026-09-30
---

# Liya — "open-source analog of Jev" audit

The user's friend claimed there is an open-source classifier analogous to
Jev (TypeSafe AI) called "liya". This audit records what we could and could
not verify.

## Baseline (Jev)

Jev (TypeSafe AI) is a proprietary "System One" classifier: typed inputs,
typed outputs + probabilities + confidence (Choice / Score / Noul primitives),
no chat/text. See jev-classifier.md. On OpenRouter as `typesafe/jev-latest`,
also Polza.AI (RU). No official open-source weights.

## Search performed (2026-09-30)

- GitHub repo search `liya classifier`: **0 results**.
- GitHub repo search `"Liya" model`: 5 results, all unrelated
  (Liya.jl Julia causal-reactive framework, an industrial-image
  drift-detection paper, etc.). No classifier/LLM project matching.
- Google and DuckDuckGo keyword searches: no usable hits for a Jev-like
  open-source "Liya".

## Conclusion

As of this date we could **not** find a mature, discoverable open-source
classifier model named "Liya" that is a drop-in Jev analog. Either it is
very new/obscure, a private/renamed project, or the name is misremembered.
Marked `[проверить]`: the friend likely knows a specific project — ask for a
URL/org before spending design time on it.

## If a specific project surfaces later

Re-screen it against the Jev fit criteria in jev-classifier.md:
- primitive set (Choice/Score/Noul) and typed I/O
- offline/on-device viability + runtime size (we are on-device-first)
- license + exit path (AGENTS.md OSS-first)
- whether raw data must leave the device (privacy gate)

## Evidence
- GitHub search (0 results for `liya classifier`)
- No authoritative source found yet — this is an open `[проверить]` fact.