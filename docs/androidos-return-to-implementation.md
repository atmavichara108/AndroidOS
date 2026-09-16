---
type: Execution Spec
title: AndroidOS return to implementation
project: AndroidOS
status: planned
timestamp: 2026-08-30
---
# AndroidOS: return to implementation

## Scope следующей сессии

Вернуться к локальной реализации Personal Assistant MVP (PA MVP) в репозитории
AndroidOS. Coordination Bridge не является обязательным шагом и не вызывается.
Vault хранит стратегию и canonical specs, но обычная AndroidOS-сессия не требует
копирования отчётов, handoff или evidence в Vault.

## Порядок работы

1. Read-only status: открыть репозиторий, прочитать локальные `AGENTS.md` и
   релевантные docs, определить фактический baseline и ограничения.
2. Plan: выполнить `/android-plan <intent>` либо сформулировать обычный свободный
   запрос с целью PA MVP.
3. User approval: показать scope, файлы, риски, acceptance gates и спросить
   явное подтверждение до любых изменений.
4. Implementation: после approval реализовать только утверждённый slice в
   AndroidOS и проверить его локальными gates проекта.

Exact next action: открыть `/home/rudra/Projects/AndroidOS`, запустить `opencode`
и выполнить `/android-plan <PA MVP intent>`.

## Acceptance gates

- План явно отделяет status от proposed work и не заявляет выполненную
  реализацию без evidence.
- PA MVP остаётся offline-first и local-first: phone/laptop, delayed sync,
  редактируемая расшифровка, подтверждение inbox-to-structure и локальные
  reminders входят в scope только по утверждённому slice.
- До implementation получено явное user approval; после implementation описаны
  выполненные проверки и residuals.
- Нет вызова bridge, cross-repo handoff или обязательного отчёта в Vault.
- Не используются Telegram, cloud-first transport, live DB payloads или копии
  профиля; credentials, raw audio и private data не переносятся между репозиториями.
- Никаких root/system changes, MCP, новых команд/skills/agents и application
  code в Vault-сессии.

## Boundary

Dotfiles открывается отдельно только для `/sysaudit <intent>` при самостоятельной
потребности в read-only audit. Vault используется через `/ask` для контекста и
через этот canonical spec для стратегии; он не становится обязательным
транспортом обычной AndroidOS работы.
