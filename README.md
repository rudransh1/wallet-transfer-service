# Wallet Transfer Service

Wallet & P2P transfer API — concurrent-safe balances, exactly-once transfers, Docker + Postgres, burst-tested.

Money is integer paise only. Auth: `Authorization: Bearer <user-id>`.

**Live URL:** _add after deploy_  
**Repo:** https://github.com/rudransh1/wallet-transfer-service  
**Logs:** https://drive.google.com/file/d/1O1IicXbKtFe0gzxUZlGD0Qb8SI7DmoCZ/view?usp=sharing  
**Burst:** `python3 scripts/burst.py <BASE_URL>`

## Run locally

```bash
docker compose up --build
```

Leave that terminal open. In a **second** terminal:

```bash
python3 scripts/burst.py http://localhost:8081
```

Compose stays in the foreground. The burst is a separate command — it will not run until you start it yourself. You should see three `PASS` lines.

Watch logs during the burst (third terminal, or split the compose window):

```bash
docker compose logs -f app
```

You will see JSON lines with `correlation_id` and events: `transfer created`, `wallet debited`, `wallet credited`, `transfer declined`, `idempotent replay hit`.

- Health: `/actuator/health/readiness`
- Metrics: `/actuator/prometheus`  
  (`http.server.requests`, p99 via histogram, plus `wallet_transfer_created_events`, `wallet_transfers_declined_insufficient_funds_total`, `wallet_transfers_idempotent_replays_total`)

Local compose uses host port **8081** so it does not clash with a JAR on 8080. Inside the container the app still listens on 8080.

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

**Simplest-correct mechanism.** One Postgres transaction: claim the idempotency row, `SELECT … FOR UPDATE` both wallets in **ascending UUID order**, then debit/credit in memory on those locked rows (or leave the transfer `DECLINED` if `balance < amount`). Sorted lock order is the whole deadlock story — A→B and B→A always lock the lower UUID first, so they cannot wait on each other. I rejected read-balance-in-app-then-write (lost updates / money created or destroyed). I rejected unsorted `FOR UPDATE` (deadlock storm under A→B + B→A). I rejected `SERIALIZABLE` — correct, but it adds serialization-failure retries without making this two-row case simpler. Conditional `UPDATE … WHERE balance >= amount` is also valid; I kept sorted locks because the paired debit + credit is explicit in one place.

**Where idempotency lives.** Uniqueness is a database unique constraint on `transfers.idempotency_key`. The insert is `INSERT … ON CONFLICT DO NOTHING` **in the same transaction** as the ledger movement. A concurrent retry either waits and reads the committed row, or loses the insert and returns that row. Same key + different `from` / `to` / `amount_paise` is `409`, not a second debit. Checking the key in a separate transaction would be TOCTOU and can double-apply under a retry storm.

**Consistency vs availability.** This is money, so consistency wins. If Postgres or a row lock is unavailable, the request fails. Callers retry with the same key. I gave up “always accept the write” and any multi-region / eventually-consistent ledger.

**AI directed vs decided.** I directed: integer paise, unique `user_id`, same-txn idempotency, sorted `FOR UPDATE`, JSON logs + correlation id, Compose + non-root Dockerfile, the three burst probes. AI typed the Spring/Docker/script files from that plan. I did not accept an AI design of serializable-everything or an in-memory idempotency map.

**Cost.** Local Compose is ₹0. Deploy on a free host (Render / Railway / Fly.io / Koyeb) + free managed Postgres. No card. Live URL / public logs cost ₹0 on those free tiers.

## What to send back

| Item | Status | How |
| --- | --- | --- |
| Burst script | Done | `scripts/burst.py` |
| This write-up | Done | this page |
| Public repo | Done | https://github.com/rudransh1/wallet-transfer-service |
| Live URL | You | deploy the image + managed Postgres (Render is the least friction) → send `https://….onrender.com` |
| Public logs | Done | https://drive.google.com/file/d/1O1IicXbKtFe0gzxUZlGD0Qb8SI7DmoCZ/view?usp=sharing |

### Public repo

1. Create an empty **public** GitHub repo (no README if this folder already has one).
2. From this folder:

```bash
git init
git add .
git commit -m "Wallet transfer service"
git branch -M main
git remote add origin https://github.com/<you>/<repo>.git
git push -u origin main
```

Send that GitHub URL.

### Live URL (deploy)

Pick one free host. Render is enough:

1. New **PostgreSQL** (free).
2. New **Web Service** from the GitHub repo.
3. Docker runtime. Env: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` from the Render DB.
4. Wait until `/actuator/health/readiness` returns `{"status":"UP"}`.
5. Send that public base URL. They will run the burst against it.

### Public logs (or a recording)

The host dashboard logs are usually login-only, so either:

- **Recording (easiest, accepted):** one screen with `docker compose logs -f app` (or Render Logs), another with `python3 scripts/burst.py http://localhost:8081`. Record until you see the three `PASS` lines and the JSON events. Upload the video (Drive / unlisted YouTube) and send the link.
- **Public link:** ship logs to a free viewer (Better Stack / Axiom / Grafana Cloud) and send a **public** dashboard URL.

### How you see the burst

The burst is not a UI. It is terminal output.

```text
PASS concurrent get-or-create: <one wallet id>
PASS idempotency storm: <one transfer id>
PASS contention: total=400000 balances=[...]
```

If a line is missing or you get an `AssertionError`, an invariant failed. After deploy, run the same file against the live URL:

```bash
python3 scripts/burst.py https://your-service.onrender.com
```
