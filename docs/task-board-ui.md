# Task Board UI

`TaskBoardScreen` is the reusable presentation component for approved task
entities. It consumes `UiTaskBoardState`; it does not depend on Room, sync,
domain `Entity`, or persistence. The runtime host maps approved task entities to
`UiTaskCard`/`UiTaskColumn` and handles `MoveTask` as an approved domain change.

Studio currently supplies a synthetic four-column board (Backlog, Ready, In
progress, Done) with one sample AndroidOS task. Move actions are reducer-only and
exist to test the UI contract; they do not change production task data.

The first iteration is intentionally a board foundation, not a full kanban
implementation: columns are horizontally scrollable, cards show title/project/
due/priority, and the host owns persistence, permissions, WIP rules and conflict
handling.
