# Architecture

AndroidOS — modular umbrella/hybrid project. PA использует provisional native Kotlin Android runtime и отдельный laptop runtime поверх общих domain contracts. Конкретный UI toolkit, API floor и laptop framework пока не выбраны.

## Ports and boundaries

Порты: `Transcriber`, `ExtractionEngine`, `LocalStore`, `SyncTransport`, `Interchange`. Android widget только dispatches idempotent commands; capture выполняется foreground service, edit/approval — app UI.

Local store: Room/SQLite-compatible schema с FTS5 и provisional encrypted-at-rest path. Domain data — materialized records плюс append-only Change/Event. Live SQLite database file никогда не sync.

Sync: authenticated encrypted `SyncEnvelope` с signed/idempotent changes, tombstones, provenance и user-controlled delayed transport. Transport replaceable; domain владеет ordering, conflicts, retention и deletion propagation.

Extraction returns schema-validated proposals, не writes. STT transcript сначала редактируется человеком. Новые `Entity.type` регистрируются через schema/validation/UI registry без migration каждой сущности.

## Profile boundary

PA читает только approved local scoped subset. Изменение профиля — `ProfileProposal` с field path, reason, source, confidence и approval state. Global `profile-governor` применяет canonical diff только после явного approval; локального profile-governor нет.

## Decision links

- [Vault project card](../../OpenCode-Vault/03-Projects/AndroidOS.md)
- [MVP architecture ADR](../../OpenCode-Vault/06-Audits/2026-08-22-androidos-pa-mvp-architecture-adr.md)
- [OSS-first research](../../OpenCode-Vault/06-Audits/2026-08-22-androidos-open-source-first.md)
- [Profile contract](../../OpenCode-Vault/01-Reference/user-profile-contract.md)

Это ссылки на источники истины, а не копии private profile facts.
