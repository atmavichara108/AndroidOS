# Roadmap

Статус всех фаз — planning; выбранные реализации provisional до real-device evidence.

## P0 — research and contracts

Сделать domain/schema/state/approval/retention/conflict contracts, threat model, OSS/license matrix и device benchmark protocol.

DoD: ADR reviewed; fixtures описывают contracts и invariants; evidence reproducible; secrets/audio/profile facts отсутствуют в repo.
Non-goals: application implementation, Telegram, TTS replies, cloud backend, final vendor/model selection.

## P1 — Personal Assistant vertical slice

Widget `start/pause/resume/stop`, foreground recording, Russian STT, editable transcript, inbox-to-structure с approval, одна Task/Event, local reminder и encrypted bundle round-trip.

DoD: демонстрация на phone и laptop peer, process/lifecycle checks и duplicate import evidence.
Non-goals: полный entity set, proactive automation, live SQLite sync.

## P2 — PA MVP

Entity registry для events/tasks/contacts/projects/meetings/promises/ideas/notes/habits/goals/relations, daily plan, reminders, kanban/views, FTS, retention, conflict UI, pairing/revocation и delayed sync.

DoD: acceptance criteria из Vault ADR выполнены на phone и laptop; fault injection не теряет и не дублирует data; export round-trip доказан.
Non-goals: Telegram, voice replies/TTS conversation, deep profile runtime integration.

## P3 — ecosystem adapters

Scoped profile-governor adapter, Vault/dotfiles/ChaT boundaries, ICS/VTODO/vCard/Markdown/JSON interoperability и optional Telegram adapter с отдельными credentials/scope.

DoD: permissions, provenance, approval и exit paths reviewed; ни один adapter не становится второй source of truth.
Non-goals: silent profile writes or autonomous external messages.

## P4 — VibeAndroid

Извлечь переиспользуемые Android methods, templates и agent tooling, подтвержденные фактическим PA development.

DoD: методы имеют evidence, owner и acceptance examples.

## P5 — BlogerAI

Слой storytelling/content поверх явно выбранной истории ecosystem.

DoD: отдельные consent, source-selection, privacy и publication gates.
Non-goals: автоматическая публикация и расширение scope.
