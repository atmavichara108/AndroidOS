---
type: Agent Instructions
description: Границы, workflow и правила AndroidOS.
---

# AndroidOS — Agent Instructions

AndroidOS — modular umbrella/hybrid project. Первый flagship — Personal Assistant (PA). Этот репозиторий содержит контракты, документацию и реализацию AndroidOS; он не заменяет Vault, dotfiles, ChaT или global `profile-governor`.

## Границы

- Владелец доменных контрактов и approval flow — AndroidOS; конкретные runtime и UI должны оставаться заменяемыми через порты.
- `profile-governor` — глобальная роль/адаптер, не локальный агент. Не создавать локальную копию профиля и не писать в canonical profile молча.
- Vault и dotfiles — внешние проекты. Агент не редактирует их напрямую и не вызывает внешние записи без отдельного явно подтвержденного задания.
- Не хранить в Git секреты, credentials, tokens, raw audio, реальные transcripts, private profile facts или model artifacts.
- Private data local by default; экспорт, sync, profile proposals, external actions и destructive deletion требуют явного approval.

## OSS-first

Сначала исследовать зрелые компоненты и их лицензии, offline behavior, maintenance, security, resource use и exit path. Собственный код ограничивать adapters, contracts, UX, domain/privacy policy и отсутствующей интеграцией. Любой выбор кандидата требует evidence и ADR; provisional означает `[проверить]`, а не Done.

## Память и источники

- Источники истины: `docs/` и связанные Vault ADRs: `03-Projects/AndroidOS.md`, `06-Audits/2026-08-22-androidos-pa-mvp-architecture-adr.md`, `06-Audits/2026-08-22-androidos-open-source-first.md`.
- Контракт профиля: `/home/rudra/Projects/OpenCode-Vault/01-Reference/user-profile-contract.md`. Читать только минимум нужного scope; факты профиля сюда не копировать.
- Все hypotheses и gaps явно маркировать `[проверить]`/`[уточнить]`.

## Workflow

Перед execution изменением вызови `/spec <selector>` после чтения локальных
`AGENTS.md` и `README.md`. Canonical specs находятся в
`/home/rudra/Projects/OpenCode-Vault/06-Specs/AndroidOS/`; не создавай копии и
не используй случайный локальный fallback. Недоступность Vault = `BLOCKED`.

1. `planner` фиксирует scope, контракты, roadmap и acceptance, не пишет application code.
2. `researcher` проверяет OSS/device/runtime claims read-only и возвращает evidence.
3. `builder` реализует только согласованный scope AndroidOS и добавляет проверяемые тесты/документацию.
4. `reviewer` проверяет качество, privacy, security, лицензии и соответствие ADR.
5. `verifier` выполняет acceptance checks, включая real-device evidence когда это требуется.
6. Только после PASS reviewer и verifier пользователь отдельно принимает решение о commit. Агенты сами не commit/push.

Проверять laptop и phone как равноправные peers. Live SQLite file не синхронизировать: только authenticated encrypted change bundles. Widget dispatches idempotent commands; запись живет в foreground service.
