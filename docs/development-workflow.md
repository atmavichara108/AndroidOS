# Development Workflow

## Вход Max в AndroidOS

Для любого execution scope сначала вызвать `/spec <selector>` и прочитать
canonical spec из `/home/rudra/Projects/OpenCode-Vault/06-Specs/AndroidOS/`.
Spec задаёт authoritative approval/commit/verifier gates; локальные docs не
становятся второй версией.

1. `android-plan` вызывает реального `planner`. Он читает Vault links, текущий статус и задачу, выдаёт scope, contracts, risks и acceptance.
2. `android-research` вызывает `researcher` для OSS/device/runtime evidence. Research read-only; неизвестное остается `[проверить]`.
3. `android-build` передает утвержденный scope реальному `builder`. Builder не расширяет scope, не пишет во внешние проекты и не commit/push.
4. Builder передает результат `reviewer`; reviewer дает PASS/FAIL по quality, privacy, security, license и ADR.
5. `android-verify` вызывает `verifier`; он проверяет reproducible checks, lifecycle, sync/retention evidence и real device, если это acceptance gate.
6. `android-done` разрешает только собрать evidence и показать commit checklist. Commit выполняется пользователем после PASS обоих gates.

## Среда

Laptop — peer и место сборки/тестов contract/runtime; phone — consented real-device acceptance target. ADB используется только после environment preparation и подтверждения пользователя: `adb devices`, install/debug APK, logcat, dumpsys и controlled test data. Не извлекать private data и не помещать audio/transcripts в repo.

Emulator optional для быстрых lifecycle/UI checks, но не заменяет physical-device baseline. Device-specific claims остаются provisional до benchmark.

Каждый pipeline явно останавливается при missing permission, unknown license, secrets, destructive action или отсутствии acceptance evidence.
