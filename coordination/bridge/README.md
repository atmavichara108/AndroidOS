---
type: coordination-bridge
id: AOS-BRIDGE-README
task_id: AOS-BRIDGE-001
project: AndroidOS
status: blocked
owner: builder
scope: cross-repo
---

# AndroidOS Coordination Bridge

This directory is one Git-backed protocol and contract for coordination across
AndroidOS, Vault and dotfiles. It is not a separate bridge-agent. The files in
this directory are the canonical source of truth for task envelopes, ownership
transfers, append-only evidence and decisions. Vault and dotfiles remain
authoritative for strategy and host facts respectively; this protocol stores
references and operational metadata, not copies.

Any named OpenCode role, or the user when explicitly approving a scoped change,
may write through this protocol. No participant writes around it: all task,
handoff, evidence and decision changes use the same contract and remain
reviewable in Git. A future agent facade or command may simplify access, but it
does not become a second protocol or source of truth.

## Layout

- `tasks/<task-id>.md`: one mutable envelope per task.
- `handoffs/<handoff-id>.md`: explicit ownership transfer and route.
- `evidence/<evidence-id>.md`: append-only observations; corrections create a
  new artifact.
- `decisions/<decision-id>.md`: decision and acceptance disposition.

## Contract

- IDs are globally unique within the bridge. Task, handoff and decision status
  values are limited to `planned`, `ready`, `in_progress`, `blocked`, `review`,
  `verify`, `closed`, and `stale`; evidence uses `pass`, `fail` or `partial`.
- Every owner and route target is a named role. `general` is never a silent
  fallback. Missing named capability is recorded as `UNROUTABLE`.
- An active task has one owner. Other roles work through a handoff; an active
  envelope is not edited by a non-owner.
- Evidence and historical decisions are append-only. No silent overwrite or
  deletion of provenance.
- Cross-repository references contain the repository, full commit SHA and path.
  A path without a verifiable SHA is `planned`, not an implementation claim.
- This bridge is documentation-only in this bootstrap. It does not implement
  application code, an API, MCP facade, event bus, CRDT, Telegram, live DB
  synchronization, profile copy, secrets or raw audio.
- The bridge artifacts are currently uncommitted/untracked in the AndroidOS
  working tree. The pre-bridge AndroidOS baseline is only an observation of the
  repository state; it is not provenance for these bridge files.

### Internal rules

- Every change declares its scope and one current owner; a non-owner uses a
  claim/handoff rather than silently editing an active envelope.
- Missing named capability or an unavailable route is `UNROUTABLE`; no silent
  fallback to `general` is allowed.
- Evidence is append-only. Corrections, conflicts and decisions add a new
  artifact; they do not overwrite or delete provenance.
- Cross-repository references require `repo=`, a verifiable full 40-character
  SHA and `path=`. A short SHA, branch or uncommitted state is not provenance.
- Secrets, credentials and sensitive payloads are never written to the
  protocol.
- Review precedes verify. Only separate named reviewer and verifier outputs
  can satisfy those gates; structural smoke is not acceptance.
- A blocked, stale or conflicting task remains visibly so: do not merge
  conflicting ownership, hide stale work or infer `PASS` from missing gates.
- Commit policy is explicit: commit only the scoped, reviewed and verified
  change; push only with user or task-boundary authorization; never stage
  unrelated repository work.

## Current route

T-109 uses the available named AndroidOS roles `planner -> builder -> reviewer
-> verifier`. The read-only host/toolchain route is `sysop` in dotfiles. These
roles are present in the inspected local agent configuration, but local named
runtime dispatch is not proven: the `reviewer` attempt fell back to the default
agent. Therefore reviewer/verifier acceptance is absent and the linked
decision is `blocked`, not `closed`.

## OpenCode workflow across related repositories

Use this bridge from OpenCode as a Git-backed working agreement. It does not
require MCP, an API, an event bus or any other live coordination service.

1. **Start in the target repository.** Set the OpenCode working directory to
   the repository being changed (`AndroidOS`, Vault or dotfiles). Do not rely on
   the caller's directory. Read that repository's `README.md` and `AGENTS.md`
   first, then read the bridge task envelope, its handoff and the linked
   evidence/decision files. Treat each repository's own instructions as a
   boundary, not as a reason to copy files between repositories.
2. **Route by named role.** Select the explicitly requested local role, such as
   `planner`, `builder`, `reviewer`, `verifier` or `sysop`. A default/general
   agent is not equivalent to a named role. If the named role cannot be proven
   to have run, record `UNROUTABLE` or the observed fallback in a new evidence
   artifact and keep the task `blocked`; do not infer a verdict from a default
   run.
3. **Claim through Git.** Before editing, inspect branch, `git status` and the
   task envelope. Claim an active task by changing its owner/status only within
   the allowed workflow, on a branch or commit that can be reviewed. Make the
   smallest scoped update, inspect `git diff`, and leave unrelated worktree
   changes untouched. One active owner edits the envelope; other roles use a
   handoff.
4. **Update and hand off explicitly.** Record status, owner, next action and
   timestamp in the task envelope. Create or update a handoff with `from`, `to`,
   scope, inputs and expected output. A handoff does not transfer ownership
   implicitly: the receiving named role must be identifiable in Git history or
   in a new evidence artifact.
5. **Write evidence, do not overwrite history.** Add an append-only evidence
   artifact for each meaningful observation or check, including command,
   repository, result, limitation and producer. Corrections are new artifacts.
   Structural smoke, implementation evidence, reviewer findings and verifier
   acceptance are different claims and must not be merged into one `PASS`.
6. **Review, then verify.** The named `reviewer` performs an independent
   review; the named `verifier` checks acceptance criteria after review. Both
   outputs must be recorded before changing a task to `closed`/Done. A runtime
   fallback, missing dispatch trace or unexecuted gate keeps the task
   `blocked`, even when the files look structurally correct.
7. **Use full SHA references.** Every cross-repository reference must include
   `repo=<name>`, a 40-character commit SHA and `path=<path>`. Resolve and
   verify the SHA in the referenced repository before claiming provenance. A
   path, branch name, abbreviated SHA or current uncommitted state is not
   sufficient; use `planned` when provenance is not yet verifiable.
8. **Commit and push deliberately.** Commit only the scoped bridge change after
   review/verifier requirements and local checks are satisfied. Push only when
   the task boundary or user explicitly authorizes it. Until then, do not
   commit or push; report uncommitted/untracked state accurately. Never stage
   unrelated Vault, dotfiles or project changes.

This workflow is operational documentation only. It creates no agent
configuration and does not make MCP a prerequisite for reading, updating,
reviewing or verifying bridge artifacts.

## Conflict and stale example

If two owners edit an active envelope, or a cross-repo reference lacks a full
SHA, the task becomes `blocked`; changes are not merged silently. An
`in_progress` task with no update for more than seven calendar days becomes
`stale` until its owner extends it with a reason or returns it to `planned`.
The T-109 evidence and decision artifacts record these rules as a structural
smoke example, not as a claim that such a conflict occurred.

## Scope boundary

The bridge is not a runtime dependency for the phone and is not a second
canonical store. The optional thin local MCP facade is a separate future task
after this file bridge and its smoke test.
