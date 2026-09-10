#!/usr/bin/env python3
"""One-command live invariant probe: python3 scripts/burst.py http://localhost:8080"""
import concurrent.futures, json, sys, urllib.error, urllib.request, uuid

BASE = sys.argv[1].rstrip("/") if len(sys.argv) > 1 else "http://localhost:8080"

def call(method, path, user, body=None):
    payload = json.dumps(body).encode() if body else None
    req = urllib.request.Request(BASE + path, data=payload, method=method,
        headers={"Authorization": "Bearer " + user, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            return response.status, json.loads(response.read())
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read())

def create(user, initial=100000):
    status, response = call("POST", "/wallets", user, {"initial_balance_paise": initial})
    assert status == 201, (status, response)
    return response["id"]

def parallel(fn, n):
    with concurrent.futures.ThreadPoolExecutor(max_workers=n) as pool:
        return list(pool.map(fn, range(n)))

def main():
    suffix = uuid.uuid4().hex
    race_user = "race-" + suffix
    created = parallel(lambda _: create(race_user, 100000), 50)
    assert len(set(created)) == 1, f"duplicate wallets: {set(created)}"
    print("PASS concurrent get-or-create:", created[0])

    alice, bob = create("alice-" + suffix), create("bob-" + suffix)
    key = "retry-" + suffix
    body = {"from": alice, "to": bob, "amount_paise": 1000, "idempotency_key": key}
    results = parallel(lambda _: call("POST", "/transfers", "alice-" + suffix, body), 30)
    assert len(set(json.dumps(item, sort_keys=True) for item in results)) == 1, results
    assert results[0][0] == 201 and results[0][1]["status"] == "COMPLETED", results[0]
    conflict = call("POST", "/transfers", "alice-" + suffix, {**body, "amount_paise": 1001})
    assert conflict[0] == 409, conflict
    print("PASS idempotency storm:", results[0][1]["id"])

    users = ["u%d-%s" % (i, suffix) for i in range(4)]
    ids = [create(user, 100000) for user in users]
    before = sum(call("GET", "/wallets/" + wid, user)[1]["balance_paise"] for wid, user in zip(ids, users))
    jobs = []
    for i in range(240):
        source, destination = i % 4, (i + 1 + (i % 2)) % 4
        jobs.append((users[source], {"from": ids[source], "to": ids[destination],
                     "amount_paise": 7000, "idempotency_key": "load-%s-%d" % (suffix, i)}))
    outcomes = parallel(lambda i: call("POST", "/transfers", jobs[i][0], jobs[i][1]), len(jobs))
    assert all(status in (201, 422) for status, _ in outcomes), outcomes
    balances = [call("GET", "/wallets/" + wid, user)[1]["balance_paise"] for wid, user in zip(ids, users)]
    assert sum(balances) == before and min(balances) >= 0, (before, balances)
    print("PASS contention: total=%d balances=%s" % (sum(balances), balances))

if __name__ == "__main__":
    main()
