---
description: Выполнить reviewer/verifier acceptance path.
---

Вызови реальных агентов `reviewer`, затем `verifier`. Reviewer проверяет diff, privacy, security, OSS/license и ADR; verifier проверяет commands/evidence, `git diff --check` и device gates. Любой FAIL блокирует done.
