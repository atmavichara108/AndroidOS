# Daily Plan UI

`DailyPlanScreen` is the reusable presentation component for the "Today"
projection of the planner. It consumes `UiDailyPlanState` — four buckets
(Overdue / Today / Upcoming / No due date) of `UiPlanBucket`/`UiPlanItem` — and
has no dependency on Room, sync, domain `Entity`, or `java.time` classification.

The split mirrors the task board: `dailyPlanToUiState(plan, ...)` in
`DailyPlanMapper.kt` maps the pure domain `DailyPlanner.plan(...)` output
(`domain/plan/DailyPlan.kt`, committed `13f9b1e`) into the presentation state,
applying a host-supplied `dueFormat` for due dates. The domain decides the bucket
for every item, so no date logic is duplicated in the UI layer.

Studio supplies a synthetic plan for a fixed fixture day (two overdue items, one
today item, one upcoming, one without due date). `CompleteTask` is reducer-only:
it removes the item from its bucket so the empty-bucket rendering can be tested;
it does not touch production task data.

The first iteration is deliberately a read-only projection: it groups, labels and
reports `total`/`isEmpty`, and the host owns persistence, ordering policy,
recurrence and the transition from plan item to completed domain change.