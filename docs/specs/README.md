---
type: Index
title: AndroidOS — canonical execution specs
project: AndroidOS
repo: /home/rudra/Projects/AndroidOS
status: active
timestamp: 2026-09-18
---

# AndroidOS — canonical execution specs

> Canonical source of truth для execution-решений по проекту AndroidOS, **локально
> в этом репозитории** (`docs/specs/`). Локальный `/spec` резолвит спеки отсюда.
> Правило владения (Vault `docs/specs/README`): execution spec живёт в репозитории
> агента, который его исполняет — AndroidOS-спеки исполняет AndroidOS-агент,
> поэтому они здесь, а не в Vault.

## Specs

- [androidos-return-to-implementation.md](androidos-return-to-implementation.md) —
  возврат к реализации после freeze Coordination Bridge (canonical, T-109).

## Границы каталога

- Код, контекст и изменения — в `/home/rudra/Projects/AndroidOS`.
- Канонические спеки — здесь (`docs/specs/`).
- Остальные проект-доки (`architecture.md`, `roadmap.md`, `privacy-and-sync.md`,
  `development-workflow.md`) — в `docs/`, это не execution specs.