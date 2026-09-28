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
- `domain/src/test/.../sync/ConflictPolicyTest.kt` (6 tests): optimistic
  concurrency decide() across all branches (no-version, matching base, null
  base, stale base -> Loser, ahead base -> catch up, create-on-missing).

## Materialization of received changes (P2-пункт, done)

Раньше принятый change оседал только в `changes`-логе. Теперь `RoomLocalStore.applyChange`
материализует его в доменные таблицы в той же транзакции (entity / inbox / transcript /
tombstone) через чистый `domain/sync/ChangeMaterializer` (12 тестов). Отправители
(`capture`, `approve`, `storeTranscript`, laptop `seed`) кладут в patch самодостаточное
состояние (kind/title/status/body/state/transcript*...).

Device-проверка (2026-09-27, Redmi 3c3da9f8): laptop `seed mat1` -> export 1 change ->
phone import `applied=1` -> entity `peer-entity-mat1` (TASK, «задача от ноутбука mat1»,
APPROVED) создана в таблице entities (26 -> 27); повторный import `applied=0
duplicates=1` оставил entities = 27. Идемпотентность материализации сохранена.

Известно: главный экран рендерит inbox, а не список entities — материализованные
сущности доступны через `EntityDao.approved()`, но видимость в UI (экран задач/событий)
— отдельная P2-задача.

## P2-пункт: conflict policy по baseVersion (done)

Optimistic-concurrency конфликт-политика: per-entity version counter. При
материализации изменения над существующей строкой `RoomLocalStore` сверяет
`change.baseVersion` с текущей версией через чистый `domain/sync/ConflictPolicy`
(6 тестов): совпадение -> применить и инкрементировать; устаревшая база -> Loser
(строка не перезаписывается, но change остаётся в append-only логе — история
конфликта сохранена, без слепого last-writer-wins). CREATE/новые строки -> version=1.
`ChangeMaterializer.update` теперь распознаёт и entity-UPDATE (kind/title/status),
не только транскрипт (11 тестов materializer).

Device-проверка (2026-09-27, Redmi 3c3da9f8), оба пути:
- Loser: CREATE entity `peer-entity-new1` (version=1) -> UPDATE c устаревшим
  baseVersion=0 -> лог `conflict: entity peer-entity-new1 not updated (stale
  base 0 vs current 1)`; title остался «задача от ноутбука new1», version=1,
  проигравший change записан в лог (2 changes). История конфликта сохранена.
- Accepted: UPDATE c валидным baseVersion=1 -> title применён
  «ПЕРЕЗАПИСЬ-ВАЛИДНАЯ», version вырос 1 -> 2. Принятое обновление
  материализуется и счётчик версии продвигается (реальный UPDATE, а не
  IGNORE-insert).

## P2-пункт: pairing / revocation / per-device salt (done)

Прежде salt для PBKDF2 был фиксированным (`androidos-sync:<keyId>`) — одна
парольная фраза = одинаковые ключи на всех устройствах. Теперь:

- `PairingManager` (domain): per-device random-salt (32 B, создаётся один раз),
  immutable identity; `pair`/`revoke`; `sharedKeys` = HMAC-labelled expand over
  SHA-256 двух PBKDF2 device-ключей, упорядоченных по deviceId — обе стороны
  выводят один и тот же общий ключ без передачи ключа по каналу. 5 доменных тестов.
- `FilePairStore` (domain, JVM): file-backed, synchronized, persist через
  temp+rename; `AndroidPairStore` — обёртка над ним в app-private files.
- CLI/хуки: телефон `pairinfo|pair|revoke|export|import` (SyncHooks), ноутбук
  `pairinfo|pair|revoke|export|apply/...` (PeerMain). Обмен salt — out-of-band
  (adb/руками), парольная фраза — общий secret (P2 provisional).

Device-матрица (2026-09-28, Redmi 3c3da9f8 + laptop):

| Шаг | Результат |
|---|---|
| phone pairinfo | `salt=NpBafz...` (случайный, per-device) |
| laptop pairinfo | `salt=lztiIB...` (другой случайный) |
| pair в обе стороны | `paired with laptop-peer` / `paired with phone-3c3da9f8` |
| phone export (shared key) | 72 change(s), envelope b62e79ee |
| laptop apply | `applied=72 duplicates=0 rejected=false` — **общий ключ выведен с разных salt** |
| laptop seed+export → phone import | `applied=1 duplicates=72` — dedup поверх shared-key transport |
| phone revoke | `revoked laptop-peer`; export → `failed: not paired with laptop-peer` |
| re-pair + export | снова работает (73 change(s)) |
| повторный import phone export (b62e79ee, 72 changes) | `applied=0 duplicates=72` — идемпотентность |

## Known limits (P2 scope, stated honestly)

- `ChangeRow` does not persist `logicalClock`/`provenance`; export sends
  `provenance=[]` and `logicalClock=null`. The canonical `bundleHash` covers
  `logicalClock` but **not** `provenance`, so provenance loss is both a data
  and a tamper-detection gap against docs/privacy-and-sync.md; close in P2
  (schema + hash coverage).
- Pairing: salt exchange is out-of-band (adb/manual) and unauthenticated —
  MITM resistance assumes a trusted channel for the exchange; session keys are
  deterministic per device-pair. **Revoke is an access kill-switch, not a key
  rotation**: it only drops the partner entry — both salts persist, so re-pair
  reproduces bit-identical shared keys, and traffic recorded before revoke
  stays decryptable after re-pair (no in-band rotation exists yet; losing the
  local pairing file is the only path that re-rolls a salt, and it fails
  closed). Secure provisioning/UI is P2/P3.
- Debug-only sync commands (pair/revoke/export/import via intent extra on an
  exported activity) are driveable by any on-device app in debug builds; worst
  case is DoS (overwriting/revoking pairing) — salts are public, keys still
  need the passphrase from app-private storage, and release builds disable the
  hook entirely.
- Bundles sealed before pairing (legacy fixed-salt key) no longer open —
  expected, re-export after pairing.
- The plaintext routing header (id/sender/sequence/keyId) is not covered by
  the MAC and must be treated as untrusted display metadata.
- The peer's change log grows without a watermark or compaction, so repeated
  exchanges re-send the whole history (O(n²) transfer in the long run).
- Expiry (`expiresAt`) is implemented and unit-tested but no production caller
  sets it yet.
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
