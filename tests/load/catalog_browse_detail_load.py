#!/usr/bin/env python3
"""Bounded Gateway load driver for Catalog browse/detail traffic."""

from __future__ import annotations

import argparse
import concurrent.futures
import json
import math
import statistics
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run a bounded Catalog browse/detail load scenario through Gateway."
    )
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--token", required=True)
    parser.add_argument("--product-id", required=True)
    parser.add_argument("--query", required=True)
    parser.add_argument("--virtual-users", type=int, required=True)
    parser.add_argument("--duration-seconds", type=int, required=True)
    parser.add_argument("--browse-every", type=int, default=4)
    parser.add_argument("--request-timeout-seconds", type=float, default=5.0)
    parser.add_argument("--max-p95-ms", type=float, required=True)
    parser.add_argument("--max-error-rate", type=float, required=True)
    parser.add_argument("--min-throughput-rps", type=float, required=True)
    parser.add_argument("--think-ms", type=float, default=0.0)
    return parser.parse_args()


def percentile(sorted_values: list[float], percent: float) -> float:
    if not sorted_values:
        return 0.0
    if len(sorted_values) == 1:
        return sorted_values[0]
    position = (len(sorted_values) - 1) * (percent / 100.0)
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return sorted_values[int(position)]
    weight = position - lower
    return sorted_values[lower] * (1.0 - weight) + sorted_values[upper] * weight


def request_json(url: str, token: str, timeout_seconds: float) -> tuple[int, bytes]:
    request = urllib.request.Request(
        url,
        headers={
            "Accept": "application/json",
            "Authorization": f"Bearer {token}",
            "X-Correlation-Id": f"load-{threading.get_ident()}-{time.time_ns()}",
        },
        method="GET",
    )
    with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
        return response.status, response.read()


def validate_response(kind: str, body: bytes, product_id: str) -> bool:
    try:
        payload = json.loads(body.decode("utf-8"))
    except json.JSONDecodeError:
        return False

    if kind == "detail":
        return payload.get("id") == product_id and payload.get("status") == "ACTIVE"

    content = payload.get("content")
    if not isinstance(content, list):
        return False
    return any(item.get("id") == product_id for item in content if isinstance(item, dict))


def run_request(
    *,
    base_url: str,
    token: str,
    product_id: str,
    query: str,
    browse: bool,
    timeout_seconds: float,
) -> tuple[bool, float, str]:
    if browse:
        encoded_query = urllib.parse.quote(query)
        url = f"{base_url}/api/catalog/products?q={encoded_query}&size=10"
        kind = "browse"
    else:
        url = f"{base_url}/api/catalog/products/{product_id}"
        kind = "detail"

    started = time.perf_counter()
    try:
        status, body = request_json(url, token, timeout_seconds)
        latency_ms = (time.perf_counter() - started) * 1000.0
        ok = 200 <= status < 300 and validate_response(kind, body, product_id)
        return ok, latency_ms, kind if ok else f"{kind}:invalid-response"
    except urllib.error.HTTPError as exc:
        latency_ms = (time.perf_counter() - started) * 1000.0
        return False, latency_ms, f"{kind}:http-{exc.code}"
    except Exception as exc:  # noqa: BLE001 - the summary reports the error class.
        latency_ms = (time.perf_counter() - started) * 1000.0
        return False, latency_ms, f"{kind}:{exc.__class__.__name__}"


def worker(args: argparse.Namespace, worker_id: int) -> list[tuple[bool, float, str]]:
    deadline = time.monotonic() + args.duration_seconds
    sequence = worker_id
    results: list[tuple[bool, float, str]] = []
    base_url = args.base_url.rstrip("/")

    while time.monotonic() < deadline:
        browse = sequence % args.browse_every == 0
        results.append(
            run_request(
                base_url=base_url,
                token=args.token,
                product_id=args.product_id,
                query=args.query,
                browse=browse,
                timeout_seconds=args.request_timeout_seconds,
            )
        )
        sequence += args.virtual_users
        if args.think_ms > 0:
            time.sleep(args.think_ms / 1000.0)

    return results


def main() -> int:
    args = parse_args()
    if args.virtual_users < 1:
        print("virtual-users must be positive", file=sys.stderr)
        return 2
    if args.duration_seconds < 1:
        print("duration-seconds must be positive", file=sys.stderr)
        return 2
    if args.browse_every < 1:
        print("browse-every must be positive", file=sys.stderr)
        return 2

    started = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.virtual_users) as executor:
        futures = [executor.submit(worker, args, index) for index in range(args.virtual_users)]
        results = [
            item
            for future in concurrent.futures.as_completed(futures)
            for item in future.result()
        ]
    elapsed_seconds = max(time.perf_counter() - started, 0.001)

    total = len(results)
    successes = sum(1 for ok, _, _ in results if ok)
    errors = total - successes
    error_rate = errors / total if total else 1.0
    throughput = total / elapsed_seconds
    latencies = sorted(latency_ms for ok, latency_ms, _ in results if ok)
    p50 = percentile(latencies, 50)
    p90 = percentile(latencies, 90)
    p95 = percentile(latencies, 95)
    p99 = percentile(latencies, 99)
    maximum = max(latencies) if latencies else 0.0
    minimum = min(latencies) if latencies else 0.0
    mean = statistics.fmean(latencies) if latencies else 0.0

    error_kinds: dict[str, int] = {}
    for ok, _, label in results:
        if not ok:
            error_kinds[label] = error_kinds.get(label, 0) + 1

    print("Catalog Gateway load scenario v1")
    print(f"profile_virtual_users={args.virtual_users}")
    print(f"duration_seconds={args.duration_seconds}")
    print(f"requests_total={total}")
    print(f"requests_success={successes}")
    print(f"requests_error={errors}")
    print(f"throughput_rps={throughput:.2f}")
    print(f"error_rate={error_rate:.4f}")
    print(
        "latency_ms "
        f"min={minimum:.1f} mean={mean:.1f} p50={p50:.1f} p90={p90:.1f} "
        f"p95={p95:.1f} p99={p99:.1f} max={maximum:.1f}"
    )
    print(
        "thresholds "
        f"p95_ms<={args.max_p95_ms:.1f} "
        f"error_rate<={args.max_error_rate:.4f} "
        f"throughput_rps>={args.min_throughput_rps:.2f}"
    )
    if error_kinds:
        print("error_kinds=" + json.dumps(error_kinds, sort_keys=True))

    failed = False
    if total == 0:
        print("No requests were completed.", file=sys.stderr)
        failed = True
    if p95 > args.max_p95_ms:
        print(
            f"p95 latency {p95:.1f} ms exceeded threshold {args.max_p95_ms:.1f} ms.",
            file=sys.stderr,
        )
        failed = True
    if error_rate > args.max_error_rate:
        print(
            f"error rate {error_rate:.4f} exceeded threshold {args.max_error_rate:.4f}.",
            file=sys.stderr,
        )
        failed = True
    if throughput < args.min_throughput_rps:
        print(
            f"throughput {throughput:.2f} rps was below threshold {args.min_throughput_rps:.2f} rps.",
            file=sys.stderr,
        )
        failed = True

    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
