---
type: task-list
project: AndroidOS
status: planning
---

# AndroidOS Tasks

Ниже только actionable planning items. Ничего не отмечено Done: репозиторий пока пуст.

- [ ] P0-01 Зафиксировать module/repository boundary и минимальные Kotlin/Android/laptop contracts.
- [ ] P0-02 Описать state machine для widget `start/pause/resume/stop`, foreground recording и process death.
- [ ] P0-03 Зафиксировать schema для InboxItem, Transcript, Entity, Reminder, Change/Event и SyncEnvelope.
- [ ] P0-04 Провести OSS/license research по STT, local extraction, DB, encryption, sync и Android lifecycle.
- [ ] P0-05 Составить threat model: unlocked/lost device, malicious peer, replay, revoked device, plaintext logs.
- [ ] P0-06 Подготовить consented benchmark corpus и report template без помещения corpus в Git/Vault.
- [ ] P0-07 После подготовки окружения выполнить реальный device benchmark на согласованном Redmi: STT, lifecycle, battery/thermal, retention и sync fault injection.
- [ ] P1-01 Собрать vertical slice: widget -> recording -> editable transcript -> approved Task/Event -> local reminder.
- [ ] P1-02 Доказать encrypted change-bundle round-trip на phone и laptop peer с duplicate/reorder tests.
- [ ] P2-01 Реализовать extensible entity registry, daily plan, views, FTS, retention и conflict UI.
- [ ] P2-02 Принять или отклонить provisional STT/LLM/DB/sync defaults по reproducible evidence.
- [ ] P3-01 Спроектировать scoped adapters для profile-governor, Vault, ChaT и standards export.
- [ ] P4-01 Извлечь VibeAndroid methods только после evidence из PA slice/MVP.
- [ ] P5-01 Планировать BlogerAI только после полного PA MVP и отдельного consent/scope review.
