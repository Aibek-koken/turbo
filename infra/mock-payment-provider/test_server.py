import unittest

import server


class MockPaymentProviderHandlerTest(unittest.TestCase):

    def setUp(self):
        with server.REQUEST_LEDGER_LOCK:
            server.REQUEST_LEDGER.clear()
        self.handler = object.__new__(server.MockPaymentProviderHandler)

    def test_snake_case_payment_request_is_recorded_with_queryable_ids(self):
        request = {
            "provider_request_id": "request-1",
            "payment_id": "payment-1",
            "order_id": "order-1",
            "customer_id": "customer-1",
            "amount": 42,
            "currency": "USD",
        }

        self.handler._record_request(request, "approved", 200, "provider-1")

        ledger = self.handler._request_ledger("orderId=order-1&customerId=customer-1")
        self.assertEqual(1, ledger["count"])
        self.assertEqual(
            ["amount", "currency", "customer_id", "order_id", "payment_id", "provider_request_id"],
            ledger["requests"][0]["requestFields"],
        )
        self.assertEqual("request-1", ledger["requests"][0]["providerRequestId"])
        self.assertEqual("payment-1", ledger["requests"][0]["paymentId"])
        self.assertEqual("order-1", ledger["requests"][0]["orderId"])
        self.assertEqual("customer-1", ledger["requests"][0]["customerId"])

    def test_snake_case_request_uses_declined_amount_scenario(self):
        request = {
            "provider_request_id": "request-2",
            "order_id": "order-2",
            "customer_id": "customer-2",
            "amount": "400.00",
            "currency": "USD",
        }

        self.assertEqual("declined", self.handler._scenario_for(request))


if __name__ == "__main__":
    unittest.main()
