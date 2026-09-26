#!/usr/bin/env python3
"""Strict validator for the LiveTube TV channel catalogue.

Schema version 2 stores the TV category hierarchy plus language and region.
Legacy schema version 1 documents are accepted and normalized to version 2.
Unknown fields are rejected so a misspelled key cannot silently disappear.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.error
import urllib.request
from collections.abc import Iterable
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any
from urllib.parse import urlsplit

SCHEMA_VERSION = 2
LEGACY_SCHEMA_VERSION = 1
MAX_FILE_BYTES = 2 * 1024 * 1024
ROOT_KEYS = frozenset({"schema_version", "data_version", "updated_at", "channels"})
LEGACY_CHANNEL_KEYS = frozenset(
    {
        "id",
        "name",
        "category",
        "logo",
        "youtube_handle",
        "live_url",
        "enabled",
        "sort_order",
    }
)
CHANNEL_KEYS = frozenset(
    {
        "id",
        "name",
        "category",
        "subcategory",
        "language",
        "region",
        "logo",
        "youtube_handle",
        "live_url",
        "enabled",
        "sort_order",
    }
)
SUBCATEGORIES_BY_CATEGORY = {
    "News": (
        "Hindi News",
        "English & Global News",
        "Business & Market",
        "International News",
        "Debate & Digital Media",
    ),
    "Regional": (
        "Uttar Pradesh & Uttarakhand",
        "Bihar & Jharkhand",
        "Punjab & Haryana",
        "Bhojpuri",
        "Marathi",
        "Bengali",
        "Telugu",
        "Tamil",
        "Kannada",
        "Malayalam",
        "Gujarati",
        "Odia & North-East",
    ),
    "Devotional": (
        "Hindu Devotional",
        "Live Darshan & Aarti",
        "Gurbani & Sikh",
        "Islamic",
        "Christian",
    ),
    "Kids & Family": ("Cartoons & Animation", "Rhymes & Nursery", "Kids Learning"),
    "Knowledge": (
        "Science & Technology",
        "Space",
        "History & Nature",
        "Travel",
        "Documentaries",
    ),
    "Music & Entertainment": (
        "Bollywood & Retro",
        "Indie & Pop",
        "Bhakti & Classical",
        "Youth & Entertainment",
    ),
    "Sports & Live": (
        "Sports News",
        "Cricket",
        "Fitness & Yoga",
        "Gaming & Esports",
        "Parliament & Governance",
        "Weather & Live Events",
    ),
}
LEGACY_CLASSIFICATION = {
    "English News": ("News", "English & Global News", "English", "National"),
    "National Hindi News": ("News", "Hindi News", "Hindi", "National"),
    "Business News": ("News", "Business & Market", "Hindi", "National"),
    "Devotional & Spiritual": ("Devotional", "Hindu Devotional", "Hindi", "National"),
    "Entertainment": ("Music & Entertainment", "Youth & Entertainment", "Hindi", "National"),
    "Sports": ("Sports & Live", "Sports News", "Hindi", "National"),
    "Regional News": ("Regional", "Uttar Pradesh & Uttarakhand", "Hindi", "North India"),
    "Documentary": ("Knowledge", "Documentaries", "English", "National"),
    "Music": ("Music & Entertainment", "Bollywood & Retro", "Hindi", "National"),
    "Other": ("News", "Hindi News", "Hindi", "National"),
}

ID_RE = re.compile(r"^[a-z0-9][a-z0-9_-]{0,63}$")
YOUTUBE_HANDLE_RE = re.compile(r"^@[A-Za-z0-9._-]{1,100}$")
UTC_TIMESTAMP_RE = re.compile(
    r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$"
)
CONTROL_CHAR_RE = re.compile(r"[\x00-\x1f\x7f]")


class ChannelValidationError(ValueError):
    """Raised when a catalogue does not conform to a supported schema."""


class DuplicateKeyError(ChannelValidationError):
    """Raised when a JSON object contains the same member more than once."""


@dataclass(frozen=True)
class ValidatedCatalogue:
    path: Path
    document: dict[str, Any]

    @property
    def channels(self) -> list[dict[str, Any]]:
        return self.document["channels"]


def _reject_constant(value: str) -> None:
    raise ChannelValidationError(f"non-standard JSON number {value!r} is not allowed")


def _unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise DuplicateKeyError(f"duplicate JSON object key {key!r}")
        result[key] = value
    return result


def _type_name(value: Any) -> str:
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "boolean"
    if isinstance(value, str):
        return "string"
    if isinstance(value, list):
        return "array"
    if isinstance(value, dict):
        return "object"
    if isinstance(value, int):
        return "integer"
    if isinstance(value, float):
        return "number"
    return type(value).__name__


def _require_string(
    value: Any, location: str, *, max_length: int
) -> str:
    if not isinstance(value, str):
        raise ChannelValidationError(
            f"{location} must be a string, got {_type_name(value)}"
        )
    if not value.strip() or value != value.strip():
        raise ChannelValidationError(f"{location} must be non-empty and trimmed")
    if len(value) > max_length:
        raise ChannelValidationError(
            f"{location} must contain at most {max_length} characters"
        )
    if CONTROL_CHAR_RE.search(value):
        raise ChannelValidationError(f"{location} must not contain control characters")
    return value


def _require_integer(value: Any, location: str, *, minimum: int = 1) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise ChannelValidationError(
            f"{location} must be an integer, got {_type_name(value)}"
        )
    if value < minimum:
        raise ChannelValidationError(f"{location} must be at least {minimum}")
    return value


def _validate_logo(value: Any, location: str) -> str:
    logo = _require_string(value, location, max_length=2048)
    try:
        parsed = urlsplit(logo)
        _ = parsed.port  # Force validation of an invalid port.
    except ValueError as exc:
        raise ChannelValidationError(f"{location} is not a valid URL: {exc}") from exc
    if (
        parsed.scheme != "https"
        or not parsed.hostname
        or parsed.username is not None
        or parsed.password is not None
    ):
        raise ChannelValidationError(
            f"{location} must be an HTTPS URL without embedded credentials"
        )
    if parsed.fragment:
        raise ChannelValidationError(f"{location} must not contain a URL fragment")
    return logo


def _canonical_live_url(handle: str) -> str:
    return f"https://www.youtube.com/{handle}/live"


def _validate_timestamp(value: Any, location: str) -> str:
    timestamp = _require_string(value, location, max_length=40)
    if not UTC_TIMESTAMP_RE.fullmatch(timestamp):
        raise ChannelValidationError(
            f"{location} must be an ISO-8601 UTC timestamp such as 2026-09-25T00:00:00Z"
        )
    try:
        datetime.fromisoformat(timestamp.removesuffix("Z") + "+00:00")
    except ValueError as exc:
        raise ChannelValidationError(f"{location} is not a real UTC timestamp") from exc
    return timestamp


def _validate_channel(
    channel: Any,
    index: int,
    seen: dict[tuple[str, str], int],
    schema_version: int,
) -> dict[str, Any]:
    location = f"channels[{index}]"
    if not isinstance(channel, dict):
        raise ChannelValidationError(
            f"{location} must be an object, got {_type_name(channel)}"
        )

    expected_keys = CHANNEL_KEYS if schema_version == SCHEMA_VERSION else LEGACY_CHANNEL_KEYS
    actual_keys = set(channel)
    missing = sorted(expected_keys - actual_keys)
    unknown = sorted(actual_keys - expected_keys)
    if missing or unknown:
        details: list[str] = []
        if missing:
            details.append(f"missing {', '.join(missing)}")
        if unknown:
            details.append(f"unknown {', '.join(unknown)}")
        raise ChannelValidationError(f"{location} has invalid fields: {'; '.join(details)}")

    channel_id = _require_string(channel["id"], f"{location}.id", max_length=64)
    if not ID_RE.fullmatch(channel_id):
        raise ChannelValidationError(
            f"{location}.id must match lowercase [a-z0-9][a-z0-9_-]{{0,63}}"
        )

    name = _require_string(channel["name"], f"{location}.name", max_length=160)
    stored_category = _require_string(
        channel["category"], f"{location}.category", max_length=64
    )
    if schema_version == LEGACY_SCHEMA_VERSION:
        try:
            category, subcategory, language, region = LEGACY_CLASSIFICATION[stored_category]
        except KeyError as exc:
            raise ChannelValidationError(
                f"{location}.category is not a supported legacy category: {stored_category}"
            ) from exc
    else:
        # Categories and subcategories are free-form. A newer data_version may introduce new
        # ones, and rejecting them here would block a valid catalogue from shipping, so only
        # the text itself is checked. Everything structural below stays strict.
        category = stored_category
        subcategory = _require_string(
            channel["subcategory"], f"{location}.subcategory", max_length=96
        )
        language = _require_string(
            channel["language"], f"{location}.language", max_length=64
        )
        region = _require_string(channel["region"], f"{location}.region", max_length=64)

    logo = _validate_logo(channel["logo"], f"{location}.logo")
    handle = _require_string(
        channel["youtube_handle"], f"{location}.youtube_handle", max_length=101
    )
    if not YOUTUBE_HANDLE_RE.fullmatch(handle):
        raise ChannelValidationError(
            f"{location}.youtube_handle must start with '@' and use only "
            "letters, digits, '.', '_' or '-'"
        )

    live_url = _require_string(channel["live_url"], f"{location}.live_url", max_length=256)
    try:
        parsed_live_url = urlsplit(live_url)
        _ = parsed_live_url.port
    except ValueError as exc:
        raise ChannelValidationError(
            f"{location}.live_url is not a valid URL: {exc}"
        ) from exc
    expected_live_url = _canonical_live_url(handle)
    if live_url != expected_live_url:
        raise ChannelValidationError(
            f"{location}.live_url must be the canonical live URL {expected_live_url!r}"
        )
    if (
        parsed_live_url.scheme != "https"
        or parsed_live_url.hostname != "www.youtube.com"
        or parsed_live_url.query
        or parsed_live_url.fragment
    ):
        raise ChannelValidationError(
            f"{location}.live_url must use only https://www.youtube.com/@handle/live"
        )

    enabled = channel["enabled"]
    if not isinstance(enabled, bool):
        raise ChannelValidationError(
            f"{location}.enabled must be a boolean, got {_type_name(enabled)}"
        )
    sort_order = _require_integer(
        channel["sort_order"], f"{location}.sort_order", minimum=1
    )

    for field, folded_value in (
        ("id", channel_id.casefold()),
        ("youtube_handle", handle.casefold()),
        ("live_url", live_url.casefold()),
        ("sort_order", str(sort_order)),
    ):
        uniqueness_key = (field, folded_value)
        if uniqueness_key in seen:
            previous = seen[uniqueness_key]
            raise ChannelValidationError(
                f"{location}.{field} duplicates channels[{previous}].{field}"
            )
        seen[uniqueness_key] = index

    return {
        "id": channel_id,
        "name": name,
        "category": category,
        "subcategory": subcategory,
        "language": language,
        "region": region,
        "logo": logo,
        "youtube_handle": handle,
        "live_url": live_url,
        "enabled": enabled,
        "sort_order": sort_order,
    }


def validate_document(document: Any) -> dict[str, Any]:
    """Validate an already-decoded catalogue and return a normalized copy."""

    if not isinstance(document, dict):
        raise ChannelValidationError(
            f"catalogue root must be an object, got {_type_name(document)}"
        )
    actual_keys = set(document)
    missing = sorted(ROOT_KEYS - actual_keys)
    unknown = sorted(actual_keys - ROOT_KEYS)
    if missing or unknown:
        details: list[str] = []
        if missing:
            details.append(f"missing {', '.join(missing)}")
        if unknown:
            details.append(f"unknown {', '.join(unknown)}")
        raise ChannelValidationError(f"catalogue root has invalid fields: {'; '.join(details)}")

    schema_version = document["schema_version"]
    if isinstance(schema_version, bool) or not isinstance(schema_version, int):
        raise ChannelValidationError("schema_version must be an integer 1 or 2")
    if schema_version not in (LEGACY_SCHEMA_VERSION, SCHEMA_VERSION):
        raise ChannelValidationError(
            f"unsupported schema_version {schema_version}; expected 1 or {SCHEMA_VERSION}"
        )
    data_version = _require_integer(document["data_version"], "data_version", minimum=1)
    updated_at = _validate_timestamp(document["updated_at"], "updated_at")

    channels = document["channels"]
    if not isinstance(channels, list):
        raise ChannelValidationError(
            f"channels must be an array, got {_type_name(channels)}"
        )
    if not channels:
        raise ChannelValidationError("channels must contain at least one channel")

    seen: dict[tuple[str, str], int] = {}
    validated_channels = [
        _validate_channel(channel, index, seen, schema_version)
        for index, channel in enumerate(channels)
    ]
    return {
        "schema_version": SCHEMA_VERSION,
        "data_version": data_version,
        "updated_at": updated_at,
        "channels": validated_channels,
    }


def load_and_validate(path: Path) -> ValidatedCatalogue:
    """Read, decode, and strictly validate a catalogue file."""

    try:
        size = path.stat().st_size
    except OSError as exc:
        raise ChannelValidationError(f"cannot stat {path}: {exc}") from exc
    if size > MAX_FILE_BYTES:
        raise ChannelValidationError(
            f"{path} is too large ({size} bytes; maximum is {MAX_FILE_BYTES})"
        )

    try:
        text = path.read_text(encoding="utf-8", errors="strict")
    except (OSError, UnicodeError) as exc:
        raise ChannelValidationError(f"cannot read UTF-8 JSON from {path}: {exc}") from exc
    if text.startswith("\ufeff"):
        raise ChannelValidationError(f"{path} must not start with a UTF-8 BOM")
    try:
        document = json.loads(
            text,
            object_pairs_hook=_unique_object,
            parse_constant=_reject_constant,
        )
    except (json.JSONDecodeError, DuplicateKeyError) as exc:
        raise ChannelValidationError(f"invalid JSON in {path}: {exc}") from exc
    return ValidatedCatalogue(path=path, document=validate_document(document))


def _probe_url(url: str, timeout: float) -> tuple[str, str | None]:
    request = urllib.request.Request(
        url,
        method="GET",
        headers={
            "User-Agent": "LiveTubeTV-catalogue-validator/1.0",
            "Range": "bytes=0-1023",
            "Accept": "text/html,application/xhtml+xml",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            final_url = response.geturl()
            if urlsplit(final_url).hostname not in {
                "www.youtube.com",
                "youtube.com",
                "m.youtube.com",
            }:
                return final_url, "redirected outside YouTube"
            if response.status >= 400:
                return final_url, f"HTTP {response.status}"
    except urllib.error.HTTPError as exc:
        return url, f"HTTP {exc.code}"
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        return url, str(exc)
    return url, None


def check_live_urls(
    channels: Iterable[dict[str, Any]], *, timeout: float = 12.0, workers: int = 4
) -> list[tuple[str, str]]:
    """Best-effort online check that never follows or contacts arbitrary hosts."""

    unique = {channel["live_url"]: channel["id"] for channel in channels}
    failures: list[tuple[str, str]] = []
    with ThreadPoolExecutor(max_workers=max(1, min(workers, 16))) as executor:
        futures = {
            executor.submit(_probe_url, url, timeout): url for url in unique
        }
        for future in as_completed(futures):
            url = futures[future]
            try:
                _, error = future.result()
            except Exception as exc:  # noqa: BLE001 - one URL must not abort validation.
                error = str(exc)
            if error:
                failures.append((url, error))
    return sorted(failures)


def _default_paths() -> list[Path]:
    return [Path(__file__).resolve().parents[1] / "data" / "channels.json"]


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Strictly validate one or more LiveTube TV channel catalogues."
    )
    parser.add_argument(
        "paths",
        nargs="*",
        type=Path,
        help="JSON files to validate (default: data/channels.json)",
    )
    parser.add_argument(
        "--check-network",
        action="store_true",
        help="also make best-effort HTTPS requests to each canonical live URL",
    )
    parser.add_argument(
        "--timeout", type=float, default=12.0, help="per-URL timeout in seconds"
    )
    parser.add_argument(
        "--workers", type=int, default=4, help="network workers (default: 4)"
    )
    parser.add_argument(
        "--json", action="store_true", help="emit a machine-readable validation report"
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _build_parser().parse_args(argv)
    paths = args.paths or _default_paths()
    reports: list[dict[str, Any]] = []
    failed = False

    for path in paths:
        report: dict[str, Any] = {"path": str(path), "valid": False}
        try:
            catalogue = load_and_validate(path)
        except ChannelValidationError as exc:
            report["error"] = str(exc)
            failed = True
        else:
            report.update(
                {
                    "valid": True,
                    "schema_version": catalogue.document["schema_version"],
                    "data_version": catalogue.document["data_version"],
                    "channel_count": len(catalogue.channels),
                    "enabled_channel_count": sum(
                        bool(channel["enabled"]) for channel in catalogue.channels
                    ),
                }
            )
            if args.check_network:
                enabled_channels = [
                    channel for channel in catalogue.channels if channel["enabled"]
                ]
                failures = check_live_urls(
                    enabled_channels, timeout=args.timeout, workers=args.workers
                )
                report["network_failures"] = [
                    {"url": url, "error": error} for url, error in failures
                ]
                failed = failed or bool(failures)
        reports.append(report)

    if args.json:
        print(json.dumps({"valid": not failed, "files": reports}, indent=2))
    else:
        for report in reports:
            if report["valid"]:
                suffix = ""
                if args.check_network:
                    suffix = f"; {len(report.get('network_failures', []))} online failure(s)"
                print(
                    f"PASS {report['path']}: schema v{report['schema_version']}, "
                    f"data v{report['data_version']}, {report['channel_count']} channel(s)"
                    f" ({report['enabled_channel_count']} enabled){suffix}"
                )
                for failure in report.get("network_failures", []):
                    print(f"  FAIL {failure['url']}: {failure['error']}", file=sys.stderr)
            else:
                print(f"FAIL {report['path']}: {report['error']}", file=sys.stderr)

    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
