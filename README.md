# Wallet Transfer Service

P2P wallet API. Money is integer paise. Auth: `Authorization: Bearer <user-id>`.

| | |
| --- | --- |
| **Live URL** | https://wallet-transfer-service-nooq.onrender.com |
| **Repo** | https://github.com/rudransh1/wallet-transfer-service |
| **Logs** | [burst recording](https://drive.google.com/file/d/1O1IicXbKtFe0gzxUZlGD0Qb8SI7DmoCZ/view?usp=sharing) |
| **Burst** | `python3 scripts/burst.py https://wallet-transfer-service-nooq.onrender.com` |

The burst runs three live probes: concurrent get-or-create (one wallet), idempotent retry storm (one debit, identical responses; same key + different body → `409`), and contended A→B / B→A transfers (total conserved, no negatives). Expect three `PASS` lines. The free instance may sleep; the first request can take about a minute.

Health: `/actuator/health/readiness`  
Metrics: `/actuator/prometheus` — request rate, latency histogram (p99), errors, plus `wallet_transfer_created_events`, `wallet_transfers_declined_insufficient_funds_total`, `wallet_transfers_idempotent_replays_total`.

Logs are JSON with a `correlation_id` per request (`X-Correlation-Id`). Domain events: transfer created, wallet debited, wallet credited, transfer declined (`insufficient_funds`), idempotent replay hit.

```bash
docker compose up --build
```

brings up the app and Postgres locally (multi-stage image, non-root user, `HEALTHCHECK`).

## API

| Method | Path | Notes |
| --- | --- | --- |
| `POST` | `/wallets` | get-or-create for the caller. Optional `{ "initial_balance_paise": 100000 }` is test issuance, not a transfer. |
| `GET` | `/wallets/{id}` | current balance |
| `POST` | `/transfers` | `{ "from", "to", "amount_paise", "idempotency_key" }` |
| `GET` | `/transfers/{id}` | status |

`201` completed. `422` declined (insufficient funds). Same key + same body returns the original result. Same key + different body is `409`.

## Write-up

**Data model.** `wallets`: `id`, unique `user_id`, `balance_paise BIGINT CHECK (>= 0)`. `transfers`: `id`, unique `idempotency_key`, `from_wallet_id`, `to_wallet_id`, `amount_paise`, `status` (`COMPLETED` / `DECLINED`). Races are won by unique indexes, not check-then-insert.

**Simplest-correct mechanism.** One Postgres transaction: claim the idempotency row, `SELECT … FOR UPDATE` both wallets in **ascending UUID order**, then debit and credit those locked rows — or leave the row `DECLINED` if `balance < amount`. A→B and B→A both lock the lower UUID first, so they cannot deadlock. I rejected app-side read/subtract/write (lost updates; money created or destroyed). I rejected unsorted `FOR UPDATE` (deadlock under the cross). I rejected `SERIALIZABLE` (retry-on-conflict without making two-row transfers simpler). Conditional `UPDATE … WHERE balance >= amount` is also correct; sorted locks keep the paired movement explicit.

**Where idempotency lives.** Database unique constraint on `transfers.idempotency_key`. Insert is `INSERT … ON CONFLICT DO NOTHING` **in the same transaction** as the debit/credit. A concurrent retry either waits for that commit or loses the insert and returns the existing row. Same key + different `from` / `to` / `amount_paise` is `409`, not a second debit. A check in a separate transaction is TOCTOU and can double-apply under a retry storm.

**Consistency vs availability.** This is money, so consistency wins. If Postgres or a needed row lock is down, the request fails. The client retries with the same key. I gave up “always take the write” and any eventually-consistent ledger.

**AI.** I directed integer paise, unique `user_id`, same-txn idempotency, sorted `FOR UPDATE`, JSON logs + correlation id, Compose / non-root Dockerfile, and the three burst probes. AI typed the Spring, Docker, and script files from that plan. I did not accept serializable-everything or an in-memory idempotency map.

**Cost.** ₹0. Local Compose, Render free web service, Render free Postgres. No card.
