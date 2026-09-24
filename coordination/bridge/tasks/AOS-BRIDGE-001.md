---
id: AOS-BRIDGE-001
title: Bootstrap Git-backed AndroidOS coordination bridge
source: vault
owner: builder
scope: cross-repo
inputs:
  - "repo=Vault; commit=ff99c576dc59581d9dc4d28f25b7225f99dca3ff; path=06-Audits/2026-08-28-androidos-coordination-bridge-spec.md"
  - "repo=AndroidOS; commit=c8c8ca896b77bf8374a915d4d0b40d99b201f054; path=AGENTS.md"
constraints:
  - docs-only bridge infrastructure; no application code
  - no API, MCP, event bus, CRDT, Telegram, live DB sync, profile copy, secrets or raw audio
acceptance:
  - linked Markdown/YAML task, handoff, evidence and decision artifacts
  - reproducible read-only structural smoke with no placeholder claims
  - independent reviewer and verifier disposition before close
status: blocked
artifacts:
  - ref: "coordination/bridge/handoffs/H-109-001.md"
  - ref: "coordination/bridge/evidence/E-109-001.md"
  - ref: "coordination/bridge/decisions/D-109-001.md"
evidence:
  - ref: "coordination/bridge/evidence/E-109-001.md"
blockers:
  - "Independent named reviewer/verifier dispatch and acceptance verdict were not executed in this session."
next_action: "Run named reviewer and verifier against the committed or user-approved bridge diff; close only after both PASS."
timestamps:
  created: "2026-08-28T13:45:18Z"
  updated: "2026-08-28T13:45:18Z"
  closed: null
---

## Context

T-109 is the first real task for the canonical AndroidOS bridge. This envelope
covers the documentation-only bootstrap and its structural smoke surface. It
does not claim application implementation, live host acceptance or runtime
agent dispatch.

## Route

```yaml
capability: vault-coordination + project-edit
role: builder
agent: AndroidOS local builder
mutability: docs-only
risk: read-only bootstrap / low-risk documentation edit
review: reviewer
acceptance: verifier
fallback: UNROUTABLE if the named local roles are unavailable at dispatch time
```

## Provenance boundary

The AndroidOS baseline reference above points only to the pre-existing
`AGENTS.md`. It does not contain or establish provenance for the untracked
`coordination/bridge/` artifacts.

## Links

- Vault source: `repo=Vault; commit=ff99c576dc59581d9dc4d28f25b7225f99dca3ff; path=06-Audits/2026-08-28-androidos-coordination-bridge-spec.md`
- AndroidOS source: `repo=AndroidOS; commit=c8c8ca896b77bf8374a915d4d0b40d99b201f054; path=AGENTS.md`
- Dotfiles sysop role: `repo=dotfiles; commit=a0a1ecb3fcf32d17fcaeb5fc54e4a23169e20cd5; path=.opencode/agent/sysop.md`
