"""Offline tests for tools/wiki_api.py: python -m unittest discover -s tools -p "test_*.py" """
import datetime
import email.message
import io
import json
import os
import pathlib
import sys
import tempfile
import unittest
import urllib.error
from unittest import mock

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import wiki_api  # noqa: E402


class FakeResponse(io.BytesIO):
    def __init__(self, body, headers=None):
        super().__init__(json.dumps(body).encode("utf-8"))
        self.headers = email.message.Message()
        for key, value in (headers or {}).items():
            self.headers[key] = value


def opener(*responses):
    """Serve the given bodies (or exceptions) in order, recording each request."""
    queue = list(responses)
    requests = []

    def open_(request, timeout):
        requests.append(request)
        item = queue.pop(0)
        if isinstance(item, Exception):
            raise item
        return item
    open_.requests = requests
    return open_


@mock.patch.dict(os.environ, {wiki_api.CONTACT_ENV: "maintainer@example.org"})
class WikiApiTest(unittest.TestCase):
    def test_http_200_error_raises_instead_of_returning_no_rows(self):
        fake = opener(FakeResponse({"error": {"code": "internal_api_error", "info": "boom"}}))
        with self.assertRaises(wiki_api.WikiError):
            wiki_api.bucket_rows("bucket('x').select('a').run()", "test", opener=fake, sleep=lambda s: None)

    def test_bucket_error_string_and_missing_rows_raise(self):
        for body in ({"error": "Bucket query failed"}, {}, {"bucket": "nope"}, {"bucket": [1, 2]}):
            with self.assertRaises(wiki_api.WikiError, msg=body):
                wiki_api.bucket_rows("q", "test", opener=opener(FakeResponse(body)), sleep=lambda s: None)

    def test_maxlag_waits_for_retry_after_then_succeeds(self):
        waits = []
        fake = opener(FakeResponse({"error": {"code": "maxlag", "info": "lagged"}}, {"Retry-After": "7"}),
                      FakeResponse({"bucket": [{"a": 1}]}))
        rows = wiki_api.bucket_rows("q", "test", opener=fake, sleep=waits.append)
        self.assertEqual([{"a": 1}], rows)
        self.assertEqual([7], waits)

    def test_rate_limit_and_server_errors_honour_retry_after(self):
        headers = email.message.Message()
        headers["Retry-After"] = "3"
        waits = []
        fake = opener(urllib.error.HTTPError("u", 429, "slow down", headers, None),
                      urllib.error.HTTPError("u", 503, "busy", None, None), FakeResponse({"bucket": []}))
        self.assertEqual([], wiki_api.bucket_rows("q", "test", opener=fake, sleep=waits.append))
        self.assertEqual([3, 10], waits)

    def test_retry_after_accepts_delay_seconds_and_http_dates(self):
        now = datetime.datetime(2026, 10, 1, 12, 0, 0, tzinfo=datetime.timezone.utc)
        clock = lambda: now  # noqa: E731

        def headers(value):
            message = email.message.Message()
            message["Retry-After"] = value
            return message
        self.assertEqual(42, wiki_api.retry_after(headers("42"), 0, clock))
        self.assertEqual(90, wiki_api.retry_after(headers("Thu, 01 Oct 2026 12:01:30 GMT"), 0, clock))
        self.assertEqual(1, wiki_api.retry_after(headers("Thu, 01 Oct 2026 11:00:00 GMT"), 0, clock))
        self.assertEqual(10, wiki_api.retry_after(headers("soon"), 1, clock))
        self.assertEqual(15, wiki_api.retry_after(None, 2, clock))
        for too_long in ("600", "Thu, 01 Oct 2026 12:10:00 GMT"):
            with self.assertRaises(wiki_api.WikiError, msg=too_long):
                wiki_api.retry_after(headers(too_long), 0, clock)

    def test_excessive_retry_after_aborts_instead_of_retrying_early(self):
        headers = email.message.Message()
        headers["Retry-After"] = "600"
        waits = []
        fake = opener(urllib.error.HTTPError("u", 503, "busy", headers, None), FakeResponse({"bucket": []}))
        with self.assertRaises(wiki_api.WikiError):
            wiki_api.bucket_rows("q", "test", opener=fake, sleep=waits.append)
        self.assertEqual([], waits)
        self.assertEqual(1, len(fake.requests))

        fake = opener(FakeResponse({"error": {"code": "maxlag", "info": "lagged"}}, {"Retry-After": "900"}))
        with self.assertRaises(wiki_api.WikiError):
            wiki_api.bucket_rows("q", "test", opener=fake, sleep=waits.append)
        self.assertEqual([], waits)

    def test_client_errors_are_not_retried(self):
        fake = opener(urllib.error.HTTPError("u", 403, "forbidden", None, None))
        with self.assertRaises(urllib.error.HTTPError):
            wiki_api.get_json({"action": "query"}, "test", opener=fake, sleep=lambda s: None)
        self.assertEqual(1, len(fake.requests))

    def test_requests_carry_contact_and_maxlag(self):
        fake = opener(FakeResponse({"query": {}}))
        wiki_api.get_json({"action": "query"}, "test", opener=fake)
        request = fake.requests[0]
        self.assertIn("maintainer@example.org", request.get_header("User-agent"))
        self.assertIn("maxlag=5", request.full_url)
        self.assertIn("format=json", request.full_url)

    def test_missing_contact_refuses_to_download(self):
        with mock.patch.dict(os.environ, {wiki_api.CONTACT_ENV: ""}):
            with self.assertRaises(SystemExit):
                wiki_api.get_json({"action": "query"}, "test", opener=opener())

    def test_failed_or_truncated_refresh_keeps_previous_cache(self):
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "cache.json"
            previous = [{"id": i} for i in range(10)]
            path.write_text(json.dumps(previous), encoding="utf-8")

            def fail():
                raise wiki_api.WikiError("mocked API error")
            with self.assertRaises(wiki_api.WikiError):
                wiki_api.cached(path, True, fail)
            with self.assertRaises(wiki_api.WikiError):
                wiki_api.cached(path, True, lambda: [])
            with self.assertRaises(wiki_api.WikiError):
                wiki_api.cached(path, True, lambda: previous[:4])
            self.assertEqual(previous, json.loads(path.read_text(encoding="utf-8")))
            self.assertEqual(["cache.json"], os.listdir(directory))

            fresh = previous + [{"id": 10}]
            self.assertEqual(fresh, wiki_api.cached(path, True, lambda: fresh))
            self.assertEqual(fresh, json.loads(path.read_text(encoding="utf-8")))


if __name__ == "__main__":
    unittest.main()
