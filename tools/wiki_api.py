"""Polite, validated access to the OSRS Wiki MediaWiki API for the snapshot generators.

Follows https://www.mediawiki.org/wiki/API:Etiquette and API:Errors_and_warnings:
  * a descriptive User-Agent with contact information (set BGS_WIKI_CONTACT, e.g. an email or repository URL);
  * serial requests with a pause between batches, maxlag=5, and server-directed retries (Retry-After on
    maxlag errors, HTTP 429 and 5xx, as delay-seconds or an HTTP-date per RFC 9110 section 10.2.3; a run
    aborts rather than retrying early when the server asks for longer than MAX_RETRY_WAIT);
  * API errors are raised even when they arrive with HTTP 200, and responses must have the expected shape;
  * downloads are cached, and a cache file is only replaced after a complete, validated download, so a
    failed or truncated refresh keeps the previous snapshot.
"""
import datetime
import email.utils
import json
import math
import os
import pathlib
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

API = "https://oldschool.runescape.wiki/api.php"
CONTACT_ENV = "BGS_WIKI_CONTACT"
MAXLAG = 5
ATTEMPTS = 5
BATCH_PAUSE = 1.0
MAX_RETRY_WAIT = 300


class WikiError(RuntimeError):
    """The API answered with an error, or with a response that does not have the expected structure."""


def user_agent(tool):
    contact = os.environ.get(CONTACT_ENV, "").strip()
    if not contact:
        raise SystemExit(f"Set {CONTACT_ENV} to an email address or repository URL before downloading from the "
                         "Wiki; it is sent in the User-Agent as the API etiquette asks.")
    return f"Best-Gear-Setup/1.0 ({tool}; RuneLite plugin data snapshot; {contact}) Python-urllib"


def _now():
    return datetime.datetime.now(datetime.timezone.utc)


def retry_after(headers, attempt, now=_now):
    """Seconds to wait before retrying: Retry-After as delay-seconds or an HTTP-date, else a backoff.

    Raises WikiError when the server asks for more than MAX_RETRY_WAIT, so the run stops instead of
    retrying before the server is ready.
    """
    value = headers.get("Retry-After") if headers is not None else None
    wait = 5 * (attempt + 1)
    if value is not None:
        value = value.strip()
        if value.isdigit():
            wait = int(value)
        else:
            try:
                when = email.utils.parsedate_to_datetime(value)
            except (TypeError, ValueError, IndexError):
                when = None
            if when is not None:
                if when.tzinfo is None:
                    when = when.replace(tzinfo=datetime.timezone.utc)
                wait = math.ceil((when - now()).total_seconds())
    if wait > MAX_RETRY_WAIT:
        raise WikiError(f"The Wiki asked to wait {wait} s before retrying (Retry-After: {value}), longer than "
                        f"the {MAX_RETRY_WAIT} s allowed; try again later")
    return max(wait, 1)


def _error(data):
    """MediaWiki's {"error": {"code", "info"}}, or the bucket extension's plain error string."""
    if not isinstance(data, dict) or "error" not in data:
        return None
    error = data["error"]
    if isinstance(error, dict):
        return error.get("code", "unknown"), error.get("info", "")
    return "error", str(error)


def get_json(params, tool, timeout=90, sleep=time.sleep, opener=urllib.request.urlopen):
    """One API request; retries only where the server asks for it (or on a dropped connection)."""
    query = {"format": "json", "maxlag": MAXLAG, **params}
    url = API + "?" + urllib.parse.urlencode(query)
    request = urllib.request.Request(url, headers={"User-Agent": user_agent(tool)})
    for attempt in range(ATTEMPTS):
        last = attempt == ATTEMPTS - 1
        try:
            with opener(request, timeout=timeout) as response:
                headers = response.headers
                data = json.load(response)
        except urllib.error.HTTPError as error:
            if (error.code == 429 or error.code >= 500) and not last:
                sleep(retry_after(error.headers, attempt))
                continue
            raise
        except (urllib.error.URLError, TimeoutError, ConnectionError):
            if last:
                raise
            sleep(5 * (attempt + 1))
            continue
        except json.JSONDecodeError as error:
            raise WikiError(f"Response is not JSON: {error}") from error
        error = _error(data)
        if error is not None:
            code, info = error
            if code == "maxlag" and not last:
                sleep(retry_after(headers, attempt))
                continue
            raise WikiError(f"Wiki API error {code}: {info}")
        if isinstance(data, dict) and data.get("warnings"):
            print(f"Wiki API warnings: {json.dumps(data['warnings'])}", file=sys.stderr)
        return data
    raise WikiError("Wiki API retries exhausted")


def bucket_rows(query, tool, **kwargs):
    """Rows of a bucket query; anything but a list of objects is an error, never an empty result."""
    data = get_json({"action": "bucket", "query": query}, tool, **kwargs)
    rows = data.get("bucket") if isinstance(data, dict) else None
    if not isinstance(rows, list) or not all(isinstance(row, dict) for row in rows):
        raise WikiError(f"Bucket response has no list of rows: {json.dumps(data)[:300]}")
    return rows


def bucket_all(name, fields, tool, page=500, extra="", **kwargs):
    """Every row of a bucket, in serial pages of `page` rows with a pause between requests."""
    select = ",".join(f"'{f}'" for f in fields)
    rows, offset = [], 0
    while True:
        batch = bucket_rows(f"bucket('{name}').select({select}){extra}.limit({page}).offset({offset}).run()",
                            tool, **kwargs)
        rows += batch
        if len(batch) < page:
            return rows
        offset += page
        time.sleep(BATCH_PAUSE)


def query_pages(data):
    """The query.pages object of a prop query."""
    pages = data.get("query", {}).get("pages") if isinstance(data, dict) else None
    if not isinstance(pages, dict):
        raise WikiError(f"Query response has no pages: {json.dumps(data)[:300]}")
    return pages


def cached(path, refresh, fetch, minimum=1, shrink=0.5):
    """Load `path`, or download with `fetch` and replace it only if the result is complete and plausible.

    A download is rejected (and the previous cache kept) if it has fewer than `minimum` entries or fewer than
    `shrink` times the previous cache's entries.
    """
    path = pathlib.Path(path)
    if path.exists() and not refresh:
        return json.loads(path.read_text(encoding="utf-8"))
    data = fetch()
    previous = json.loads(path.read_text(encoding="utf-8")) if path.exists() else None
    validate_size(path.name, data, previous, minimum, shrink)
    write_atomic(path, json.dumps(data))
    return data


def validate_size(name, data, previous, minimum=1, shrink=0.5):
    count = len(data)
    if count < minimum:
        raise WikiError(f"{name}: downloaded {count} entries, expected at least {minimum}; keeping the previous cache")
    if previous is not None and count < len(previous) * shrink:
        raise WikiError(f"{name}: downloaded {count} entries against {len(previous)} cached; refusing to replace "
                        "the cache with a much smaller download")


def write_atomic(path, text, mode="w"):
    """Write through a temporary file in the same directory, so readers never see a partial file."""
    path = pathlib.Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temp = tempfile.mkstemp(dir=path.parent, prefix=path.name + ".", suffix=".tmp")
    try:
        with os.fdopen(fd, mode, encoding=None if "b" in mode else "utf-8", newline=None if "b" in mode else "") as out:
            out.write(text)
        os.replace(temp, path)
    except BaseException:
        os.unlink(temp)
        raise
