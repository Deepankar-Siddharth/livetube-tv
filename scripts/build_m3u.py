#!/usr/bin/env python3
"""Build the deterministic IPTV playlist from data/channels.json."""

from __future__ import annotations

import argparse
import os
import sys
import tempfile
from collections.abc import Iterable
from pathlib import Path
from typing import Any

try:
    from validate_channels import (
        ChannelValidationError,
        ValidatedCatalogue,
        load_and_validate,
    )
except ImportError:  # Supports ``python -m scripts.build_m3u``.
    from scripts.validate_channels import (  # type: ignore[no-redef]
        ChannelValidationError,
        ValidatedCatalogue,
        load_and_validate,
    )

REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_INPUT = REPOSITORY_ROOT / "data" / "channels.json"
DEFAULT_OUTPUT = REPOSITORY_ROOT / "data" / "playlist.m3u8"


def _m3u_escape(value: str) -> str:
    """Escape an M3U extended-info attribute or display value."""

    return (
        value.replace("\\", "\\\\")
        .replace('"', '\\"')
        .replace("\r", " ")
        .replace("\n", " ")
    )


def render_playlist(catalogue: ValidatedCatalogue | Iterable[dict[str, Any]]) -> str:
    """Return an LF-normalized M3U playlist for a validated catalogue."""

    channels = (
        catalogue.channels
        if isinstance(catalogue, ValidatedCatalogue)
        else list(catalogue)
    )
    lines = ["#EXTM3U"]
    enabled_channels = sorted(
        (channel for channel in channels if channel["enabled"]),
        key=lambda channel: channel["sort_order"],
    )
    for number, channel in enumerate(enabled_channels, start=1):
        channel_id = _m3u_escape(channel["id"])
        logo = _m3u_escape(channel["logo"])
        category = _m3u_escape(channel["category"])
        name = _m3u_escape(channel["name"])
        lines.append(
            "#EXTINF:-1 "
            f'tvg-chno="{number}" '
            f'tvg-id="{channel_id}" '
            f'tvg-logo="{logo}" '
            f'group-title="{category}",{name}'
        )
        lines.append(channel["live_url"])
    return "\n".join(lines) + "\n"


def _atomic_write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{path.name}.", suffix=".tmp", dir=path.parent
    )
    temporary_path = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as output:
            output.write(content)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary_path, path)
    finally:
        try:
            temporary_path.unlink()
        except FileNotFoundError:
            pass


def build(
    input_path: Path = DEFAULT_INPUT,
    output_path: Path = DEFAULT_OUTPUT,
    *,
    check: bool = False,
) -> str:
    """Build the playlist.

    With ``check=True`` no file is changed and the command fails when the
    committed playlist is stale.  The rendered bytes are returned in both
    modes so callers and tests can inspect the result.
    """

    catalogue = load_and_validate(input_path)
    content = render_playlist(catalogue)
    if check:
        try:
            current = output_path.read_text(encoding="utf-8", errors="strict")
        except FileNotFoundError as exc:
            raise ChannelValidationError(f"generated playlist is missing: {output_path}") from exc
        except (OSError, UnicodeError) as exc:
            raise ChannelValidationError(
                f"cannot read generated playlist {output_path}: {exc}"
            ) from exc
        if current != content:
            raise ChannelValidationError(
                f"generated playlist is stale: run `python scripts/build_m3u.py` "
                f"to regenerate {output_path}"
            )
    else:
        _atomic_write(output_path, content)
    return content


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--input", type=Path, default=DEFAULT_INPUT, help="channel catalogue JSON"
    )
    parser.add_argument(
        "--output", type=Path, default=DEFAULT_OUTPUT, help="output M3U playlist"
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="verify that the output is current without writing it",
    )
    parser.add_argument(
        "--quiet", action="store_true", help="suppress the success summary"
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _build_parser().parse_args(argv)
    try:
        content = build(args.input, args.output, check=args.check)
    except ChannelValidationError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    if not args.quiet:
        channel_count = sum(1 for line in content.splitlines() if line.startswith("#EXTINF:"))
        action = "Verified" if args.check else "Generated"
        print(
            f"{action} {args.output} with {channel_count} channel(s) "
            f"from {args.input}."
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
