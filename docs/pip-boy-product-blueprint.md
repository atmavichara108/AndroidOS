# Pip-Boy Product Blueprint

## Status

This document is the product direction for the Personal Assistant surface. The
current implementation is a validated vertical slice, not the full planner.
Concrete model and vendor choices remain provisional until device evidence.

## Product Shape

Pip-Boy is an offline-first personal operations console, not a transcript list.
The central loop is:

```text
capture -> understand -> propose -> approve -> plan -> remind -> review
```

The inbox is an intake surface. Tasks, projects, boards, reminders and daily
planning are the operational surface. A transcript must never be forced directly
into exactly one flat destination: one capture may produce a transcript, task
proposal, event proposal, reminder proposal and provenance links, each requiring
the appropriate approval.

## Canonical Objects

### Project

- stable id, title, description, lifecycle status;
- parent project and optional external references;
- owner/context, tags and preferred board;
- provenance and retention policy.

Projects are containers and planning contexts. They are not folders that hide
tasks: unassigned tasks remain valid and visible in a global view.

### Task

- stable id and title;
- `projectId` nullable;
- workflow status independent from board column;
- priority, estimate, energy/context and dependencies;
- `dueAt` and optional start window;
- recurrence rule plus timezone and exception dates;
- reminder policy and notification channel;
- source transcript/provenance and approval status;
- archive/deletion state and version.

Workflow status should support at least `BACKLOG`, `READY`, `IN_PROGRESS`,
`BLOCKED`, `DONE`, `CANCELLED`. A board maps statuses to columns, but the task
keeps its canonical status when shown in another view.

### Board and Kanban Column

Boards are saved views/configurations, not a second task store. A board defines:

- project/filter scope;
- ordered columns and their status mapping;
- WIP limits and optional swimlane grouping;
- card fields and sort order.

Moving a card creates a domain change to task workflow status and records actor,
time and provenance. It must be idempotent and conflict-aware. A task can appear
on multiple boards without being duplicated.

### Reminder

Reminder scheduling is separate from task due dates:

- one-time trigger;
- recurring rule (RRULE-like or an equivalent explicit rule);
- timezone and daylight-saving behavior;
- snooze history and next occurrence;
- notification policy, channel and exact-alarm permission state;
- target entity and provenance.

Examples: a task may be due Friday but remind every weekday at 09:00; a recurring
task may have one reminder per occurrence; a one-time reminder may target a
project review without creating a task.

### Relations and Dependencies

Relations connect tasks, projects, events, contacts, notes and promises. They
must be typed and navigable. Dependencies (`blocks`, `blocked-by`, `related`) are
not encoded in free-form text and are surfaced in task detail and kanban cards.

## UI Surface

The Pip-Boy navigation should grow around a stable set of workspaces:

- **Inbox** — unprocessed captures, transcripts and proposals;
- **Today** — due, scheduled and context-ranked work;
- **Projects** — project list, health and recent activity;
- **Boards** — saved kanban views;
- **Calendar** — events, deadlines and reminder occurrences;
- **Search** — FTS across approved local data;
- **Review** — proposals, conflicts, pending approvals and failed automation;
- **Studio** — synthetic scenarios and UI/state inspection, debug-only.

The current shared `InboxScreen` is the first production component. Future
screens must consume UI contracts instead of Room rows, as Inbox already does.

## Capture and Planning Pipeline

1. Capture text or audio into an immutable inbox item.
2. Transcribe audio explicitly; keep raw audio and raw transcript retention
   separate.
3. Extract schema-validated proposals. Extraction never writes an approved task
   or project directly.
4. Let the user edit transcript and proposal fields.
5. Approve one or more proposals, preserving links to the source capture.
6. Place approved tasks in a project/board or leave them unassigned.
7. Create due dates and reminders separately, with explicit recurrence and
   timezone semantics.
8. Surface the result in Today, project views, boards and reminders.

This allows one capture to become multiple coordinated objects without copying
the transcript into every object.

## Local Model Architecture

Models are capabilities behind ports, not UI dependencies. The initial registry
should distinguish:

- STT/transcription;
- extraction/proposal generation;
- task/project classification;
- scheduling and prioritization suggestions;
- semantic search/embeddings, only when useful;
- optional local planning assistant.

Every model profile records engine, model, quantization, language, memory
requirements, license, latency evidence and whether it can run offline. A model
may propose changes but cannot silently approve, delete, message externally or
write canonical profile data.

Quantization is an evidence-driven deployment choice (for example int8 or
int4), not a global default. The device benchmark should measure WER/quality,
RTF/latency, RAM, battery and thermal behavior per model profile. The current
sherpa-onnx + T-one choice is only the STT candidate; it does not decide the
future extraction/planning stack.

## Evolutions and JEV-like Systems

If `Jev` refers to an evolution/agent framework, it belongs behind a scoped
`EvolutionAdapter`, not in the domain core. An evolution may propose:

- a new extraction schema;
- a model profile;
- a board layout;
- a planning heuristic;
- a UI experiment.

Each proposal needs version, experiment scope, evidence, rollback path and user
approval. No self-modifying production behavior, silent model replacement or
unreviewed schema migration is allowed. The first implementation can be a local
fixture/evaluation runner; integration with a concrete JEV system should wait
until its protocol and trust boundary are known.

## Termux and Ecosystem Adapters

Termux is an optional local execution peer, not a second canonical store. The
adapter boundary should support:

- explicit command/job submission;
- capability discovery and version reporting;
- local-only file or loopback transport;
- structured result plus logs and provenance;
- cancellation, timeout and resource limits;
- user approval for commands, exports and destructive operations.

Vault, dotfiles, ChaT, profile-governor, ICS/VTODO/vCard and Markdown/JSON are
also adapters. Pip-Boy owns approved local domain data and change history; an
adapter never silently becomes the source of truth. Raw SQLite is never synced
or handed to Termux. Exchange uses scoped, authenticated, reviewable bundles.

## Delivery Sequence

### P1 completion

- capture, recording, STT, editable transcript and one approved Task/Event;
- one reminder and encrypted bundle round-trip;
- evidence for lifecycle, duplicate commands and deletion.

### P2 planner core

- Project and Task entities with typed relations;
- recurrence and reminder engine with timezone tests;
- Today view and task detail;
- one real Kanban board with saved filters and WIP limits;
- FTS/search and conflict review;
- domain registry for additional entity types.

### P2 model layer

- model registry and capability/quantization metadata;
- extraction proposals with approval;
- benchmark harness and device evidence;
- local ranking/planning suggestions, always reversible.

### P3 ecosystem

- Termux adapter with capability and approval gates;
- Vault/dotfiles/ChaT scoped adapters;
- standards export and import;
- delayed encrypted sync between phone and laptop.

## What Is True Today

Implemented and evidenced: recording, m4a retention/listing/playback, audio inbox
items, sherpa-onnx/T-one STT candidate, editable transcript flow, delete UI and
the debug Studio with synthetic sync/conflict states.

Not implemented yet: canonical Project/Task persistence, real kanban boards,
recurrence engine, Today planner, model registry beyond STT, evolution adapter,
Termux adapter and ecosystem sync. Those are now explicit follow-up slices,
rather than assumptions hidden inside the current inbox prototype.
