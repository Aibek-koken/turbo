import json
import os
import threading
import time
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse


AUTHORIZE_PATH = os.getenv("MOCK_PAYMENT_PROVIDER_AUTHORIZE_PATH", "/api/mock-payments/authorize")
PORT = int(os.getenv("MOCK_PAYMENT_PROVIDER_PORT", "8089"))
DECLINED_AMOUNT = Decimal(os.getenv("MOCK_PAYMENT_PROVIDER_DECLINED_AMOUNT", "400.00"))
TIMEOUT_AMOUNT = Decimal(os.getenv("MOCK_PAYMENT_PROVIDER_TIMEOUT_AMOUNT", "408.00"))
FIVE_XX_AMOUNT = Decimal(os.getenv("MOCK_PAYMENT_PROVIDER_5XX_AMOUNT", "500.00"))
TIMEOUT_SECONDS = float(os.getenv("MOCK_PAYMENT_PROVIDER_TIMEOUT_SECONDS", "5"))
LEDGER_LIMIT = int(os.getenv("MOCK_PAYMENT_PROVIDER_LEDGER_LIMIT", "1000"))

REQUEST_LEDGER = []
REQUEST_LEDGER_LOCK = threading.Lock()


class MockPaymentProviderHandler(BaseHTTPRequestHandler):

    server_version = "MockPaymentProvider/1.0"

    def do_GET(self):
        if self.path == "/actuator/health":
            self._write_json(200, {"status": "UP"})
            return
        parsed = urlparse(self.path)
        if parsed.path == "/api/mock-payments/requests":
            self._write_json(200, self._request_ledger(parsed.query))
            return
        self._write_json(404, {"error": "not_found"})

    def do_POST(self):
        if self.path != AUTHORIZE_PATH:
            self._write_json(404, {"error": "not_found"})
            return

        try:
            request = self._read_json()
        except ValueError:
            self._write_json(400, {"error": "invalid_json"})
            return

        scenario = self._scenario_for(request)
        provider_reference = self._provider_reference(scenario, request)

        if scenario == "timeout":
            self._record_request(request, scenario, 200, provider_reference)
            time.sleep(TIMEOUT_SECONDS)
            self._write_json(200, {
                "outcome": "APPROVED",
                "providerReference": provider_reference,
            })
            return

        if scenario == "5xx":
            self._record_request(request, scenario, 503, provider_reference)
            self._write_json(503, {
                "outcome": "PROVIDER_5XX",
                "providerReference": provider_reference,
            })
            return

        if scenario == "declined":
            self._record_request(request, scenario, 402, provider_reference)
            self._write_json(402, {
                "outcome": "DECLINED",
                "providerReference": provider_reference,
                "declineReason": "PAYMENT_DECLINED",
            })
            return

        self._record_request(request, scenario, 200, provider_reference)
        self._write_json(200, {
            "outcome": "APPROVED",
            "providerReference": provider_reference,
        })

    def log_message(self, format, *args):
        print("%s - %s" % (self.address_string(), format % args), flush=True)

    def _read_json(self):
        length = int(self.headers.get("content-length", "0"))
        raw_body = self.rfile.read(length)
        try:
            return json.loads(raw_body.decode("utf-8") or "{}")
        except json.JSONDecodeError as exc:
            raise ValueError("invalid json") from exc

    def _write_json(self, status, body):
        response = json.dumps(body, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("content-type", "application/json")
        self.send_header("content-length", str(len(response)))
        self.end_headers()
        try:
            self.wfile.write(response)
        except BrokenPipeError:
            pass

    def _request_ledger(self, raw_query):
        filters = {key: values[0] for key, values in parse_qs(raw_query).items() if values}
        with REQUEST_LEDGER_LOCK:
            requests = list(REQUEST_LEDGER)

        for key in ("customerId", "orderId", "paymentId", "providerRequestId", "scenario"):
            expected = filters.get(key)
            if expected:
                requests = [
                    request for request in requests
                    if str(request.get(key, "")) == expected
                ]

        return {
            "count": len(requests),
            "requests": requests,
        }

    def _record_request(self, request, scenario, response_status, provider_reference):
        with REQUEST_LEDGER_LOCK:
            sequence = int(REQUEST_LEDGER[-1]["sequence"]) + 1 if REQUEST_LEDGER else 1
            entry = {
                "sequence": sequence,
                "receivedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
                "requestFields": sorted(str(key) for key in request),
                "scenario": scenario,
                "responseStatus": response_status,
                "providerReference": provider_reference,
                "providerRequestId": self._safe_text(self._request_value(
                    request, "providerRequestId", "provider_request_id")),
                "paymentId": self._safe_text(self._request_value(request, "paymentId", "payment_id")),
                "orderId": self._safe_text(self._request_value(request, "orderId", "order_id")),
                "customerId": self._safe_text(self._request_value(request, "customerId", "customer_id")),
                "amount": self._safe_text(self._request_value(request, "amount")),
                "currency": self._safe_text(self._request_value(request, "currency")),
            }
            REQUEST_LEDGER.append(entry)
            if len(REQUEST_LEDGER) > LEDGER_LIMIT:
                del REQUEST_LEDGER[:len(REQUEST_LEDGER) - LEDGER_LIMIT]

    @staticmethod
    def _safe_text(value):
        if value is None:
            return None
        return str(value)

    def _scenario_for(self, request):
        explicit = str(self._request_value(request, "scenario") or "").strip().lower()
        if explicit in {"approved", "declined", "timeout", "5xx"}:
            return explicit

        customer_id = str(self._request_value(request, "customerId", "customer_id") or "").lower()
        if "provider-timeout" in customer_id or "payment-timeout" in customer_id:
            return "timeout"
        if "provider-5xx" in customer_id or "payment-5xx" in customer_id:
            return "5xx"
        if "provider-decline" in customer_id or "payment-decline" in customer_id:
            return "declined"

        amount = self._amount(self._request_value(request, "amount"))
        if amount == TIMEOUT_AMOUNT:
            return "timeout"
        if amount == FIVE_XX_AMOUNT:
            return "5xx"
        if amount == DECLINED_AMOUNT:
            return "declined"
        return "approved"

    @staticmethod
    def _request_value(request, *keys):
        for key in keys:
            value = request.get(key)
            if value is not None:
                return value
        return None

    @staticmethod
    def _amount(raw_amount):
        try:
            return Decimal(str(raw_amount))
        except (InvalidOperation, TypeError):
            return Decimal("0")

    @staticmethod
    def _provider_reference(scenario, request):
        provider_request_id = str(
            MockPaymentProviderHandler._request_value(
                request, "providerRequestId", "provider_request_id") or "missing").replace("-", "")
        suffix = provider_request_id[:24] or "missing"
        return f"mock-provider-{scenario}-{suffix}"


def main():
    server = ThreadingHTTPServer(("0.0.0.0", PORT), MockPaymentProviderHandler)
    print(f"mock payment provider listening on port {PORT}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
