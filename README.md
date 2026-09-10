# Wallet Transfer Service

Wallet & P2P transfer API — concurrent-safe balances, exactly-once transfers, Docker + Postgres, burst-tested.

Money is integer paise only. Auth: `Authorization: Bearer <user-id>`.

**Live URL:** https://wallet-transfer-service-nooq.onrender.com  
**Repo:** https://github.com/rudransh1/wallet-transfer-service  
**Logs:** https://drive.google.com/file/d/1O1IicXbKtFe0gzxUZlGD0Qb8SI7DmoCZ/view?usp=sharing  
**Burst:** `python3 scripts/burst.py https://wallet-transfer-service-nooq.onrender.com`

## Run locally

```bash
docker compose up --build
```

In a second terminal:

```bash
python3 scripts/burst.py http://localhost:8081
```

Expect three `PASS` lines. Logs: `docker compose logs -f app` (JSON + `correlation_id`).

- Health: `/actuator/health/readiness`
- Metrics: `/actuator/prometheus`  
  (`http.server.requests`, p99 via histogram, plus `wallet_transfer_created_events`, `wallet_transfers_declined_insufficient_funds_total`, `wallet_transfers_idempotent_replays_total`)

Local compose uses host port **8081**. The container still listens on 8080.

## API

| Method | Path | Body / notes |
| --- | --- | --- |
| `POST` | `/wallets` | get-or-create for the bearer user. Optional `{ "initial_balance_paise": 100000 }` (issuance for tests, not a transfer). |
| `GET` | `/wallets/{id}` | current balance |
| `POST` | `/transfers` | `{ "from", "to", "amount_paise", "idempotency_key" }` |
| `GET` | `/transfers/{id}` | status |

`201` completed. `422` declined (insufficient funds). Same key + same body → original result. Same key + different body → `409`.

## Write-up

**Data model.** Two tables. `wallets`: `id`, unique `user_id`, `balance_paise BIGINT CHECK (>= 0)`. `transfers`: `id`, unique `idempotency_key`, `from_wallet_id`, `to_wallet_id`, `amount_paise`, `status` (`COMPLETED` / `DECLINED`). Unique indexes do the race work; the app does not check-then-insert.

**Simplest-correct mechanism.** One Postgres transaction: claim the idempotency row, `SELECT … FOR UPDATE` both wallets in **ascending UUID order**, then debit/credit on those locked rows (or leave the transfer `DECLINED` if `balance < amount`). Sorted lock order is the deadlock story — A→B and B→A always lock the lower UUID first. I rejected read-balance-in-app-then-write (lost updates). I rejected unsorted `FOR UPDATE` (deadlock under A→B + B→A). I rejected `SERIALIZABLE` — extra retries, no simpler here. Conditional `UPDATE … WHERE balance >= amount` is also valid; sorted locks keep the paired debit + credit explicit.

**Where idempotency lives.** Unique constraint on `transfers.idempotency_key`. The insert is `INSERT … ON CONFLICT DO NOTHING` **in the same transaction** as the ledger movement. A concurrent retry waits and reads the committed row, or loses the insert and returns that row. Same key + different `from` / `to` / `amount_paise` is `409`. Checking the key in a separate transaction is TOCTOU and can double-apply.

**Consistency vs availability.** Consistency wins. If Postgres or a row lock is unavailable, the request fails. Callers retry with the same key. I gave up “always accept the write” and any eventually-consistent ledger.

**AI directed vs decided.** I directed: integer paise, unique `user_id`, same-txn idempotency, sorted `FOR UPDATE`, JSON logs + correlation id, Compose + non-root Dockerfile, the three burst probes. AI typed the Spring/Docker/script files. I did not accept serializable-everything or an in-memory idempotency map.

**Cost.** Local Compose is ₹0. Render free web service + free Postgres. No card.
