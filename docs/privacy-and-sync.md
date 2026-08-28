# Privacy And Sync

- Private data хранится local by default; raw audio и raw/superseded transcripts имеют configurable retention и должны удаляться вместе с blobs, FTS/index, caches и propagated deletion tombstones.
- Sync переносит только authenticated encrypted change bundles, не raw audio по умолчанию и никогда не live SQLite file.
- Changes idempotent; duplicate/reordered bundles — no-op или deterministic conflict result. Approved edits, reminders и destructive deletes не используют blind last-writer-wins.
- Provenance обязательна для generated/edited data: source, device, time, engine/model или human edit, confidence/status и canonical/derived distinction.
- STT -> transcript edit -> proposal diff -> explicit approval. Extraction, profile changes, exports, destructive delete и external actions требуют approval.
- Profile data — только minimal approved scoped subset. `profile-governor` глобален; PA не дублирует canonical facts и не пишет молча.
- Secrets, credentials, tokens, real corpus and private profile facts не входят в source control, logs, public ADRs или benchmark reports.
