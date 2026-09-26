---
type: evidence
title: P1-02 encrypted change-bundle round-trip (phone ↔ laptop peer)
project: AndroidOS
status: done
timestamp: 2026-09-26
device: Redmi flourite (3c3da9f8)
---

# P1-02 evidence — encrypted sync round-trip

## What was proven

An encrypted change bundle produced by the phone was applied by a laptop peer,
the peer's own change travelled back, the phone applied exactly the new change
and deduplicated the rest, a duplicate delivery was a no-op, and a tampered
bundle was rejected without leaking plaintext.

## Setup

- Crypto: AES-256-GCM + HMAC-SHA256 (encrypt-then-MAC), keys derived by PBKDF2
  (210k iterations) from a passphrase; passphrase lives in the app's private
  `files/sync_pass.txt`, never on a command line.
- Envelope: canonical order by (occurredAt, id), SHA-256 `bundleHash` over the
  canonical form; transport file `PA-SYNC-1` = plaintext routing header +
  sealed payload.
- Laptop peer: `:peer` JVM CLI (`export`/`apply`/`show`/`selftest`/`seed`),
  file-backed state, never touches a live SQLite file.
- Phone hook: `adb shell am start -n ru.rudra.androidos.pa/.MainActivity
  --es pa_sync export|import`; results appended to
  `files/sync/last_result.txt` and logged as `PA_SYNC`.

## Observed results (device logs, 2026-09-26)

| Step | Command | Result |
|---|---|---|
| Phone export | `--es pa_sync export` | `exported 54 change(s) envelope=c497ad0d hash=eb664d36…` |
| Pull + laptop apply | `peer apply phone-out.pa-sync` | `applied=54 duplicates=0 rejected=false` |
| Laptop seed + export | `peer seed rt1; peer export` | `exported 55 change(s) envelope=53d960a1` |
| Push + phone import | `--es pa_sync import` | `applied=1 duplicates=54 rejected=false` |
| Duplicate delivery | same file imported again | `applied=0 duplicates=55 rejected=false` |
| Tamper | one payload byte flipped | `import failed: MAC/tag verification failed` |
| Reorder + duplicates (laptop) | `peer selftest` | `order A/B applied=2 converged=true; duplicate delivery applied=0 duplicates=1` |

Phone `changes` table after the round-trip: **55 rows**, including the peer's
`peer-seed-rt1` with its Russian title — the laptop-originated change is
persisted on the phone.

## Automated coverage

- `domain/src/test/.../sync/SyncEngineTest.kt` (8 tests): deterministic order
  and hash, hash stability under input reordering, idempotent re-apply,
  duplicate-within-envelope, reorder convergence, tamper rejection, expiry.
- `domain/src/test/.../sync/CryptoBoxTest.kt` (7 tests): round trip, fresh
  nonce per seal, tampered payload/MAC/nonce rejection, wrong-key rejection,
  deterministic derivation.
- `domain/src/test/.../sync/EnvelopeCodecTest.kt` (6 tests): envelope and
  change-list codec round trip, exchange-file round trip through crypto,
  wrong-key and tampered-payload rejection, non-PA-SYNC files refused.

## Known limits (P2 scope, stated honestly)

- `ChangeRow` does not persist `logicalClock`/`provenance`; export sends
  `provenance=[]` and `logicalClock=null`. The canonical `bundleHash` covers
  `logicalClock` but **not** `provenance`, so provenance loss is both a data
  and a tamper-detection gap against docs/privacy-and-sync.md; close in P2
  (schema + hash coverage).
- Keys: PBKDF2 uses a fixed, non-secret salt derived from the keyId, so two
  installs with the same passphrase derive the same keys and weak passphrases
  are precomputable. Real pairing/revocation and per-device salts are P2.
- No conflict resolution: two changes touching the same field are both
  applied (append-both), `baseVersion` is ignored, and ordering relies on
  wall-clock `occurredAt`. Deterministic, but not a conflict policy — P2.
- The plaintext routing header (id/sender/sequence/keyId) is not covered by
  the MAC and must be treated as untrusted display metadata.
- The peer's change log grows without a watermark or compaction, so repeated
  exchanges re-send the whole history (O(n²) transfer in the long run).
- Expiry (`expiresAt`) is implemented and unit-tested but no production caller
  sets it yet.
- Received changes are recorded in the local change log but are not yet
  materialised into entities/reminders automatically.
- Transport in this evidence is adb file push/pull, not a background channel
  (delayed transport is P2).
- The scriptable hook is **debug-only**: `SyncHooks.run` returns
  "sync hooks disabled in release builds" when `BuildConfig.DEBUG` is false,
  so the exported launcher activity cannot drive sync in a release APK.

## Reproduce

```sh
# phone side (build + install first); the passphrase touches the adb shell
# command line here for the test only — production must provision the file
# without it (e.g. an in-app entry field).
adb shell "run-as ru.rudra.androidos.pa sh -c 'echo <passphrase> > files/sync_pass.txt'"
adb shell am start -n ru.rudra.androidos.pa/.MainActivity --es pa_sync export
adb pull /storage/emulated/0/Android/data/ru.rudra.androidos.pa/files/sync/out.pa-sync
# laptop side
PA_SYNC_PASSPHRASE=<passphrase> JAVA_HOME=$HOME/Android/jdk17 \
  ./peer/build/install/peer/bin/peer apply out.pa-sync <state-dir>
```
