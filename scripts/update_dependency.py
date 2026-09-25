#!/usr/bin/env python3
"""Safely update the NewPipeExtractor coordinate in Gradle files.

The updater discovers only conventional Gradle/Kotlin files, requires a stable
semantic version, refuses a default downgrade, and writes atomically.  Running
without ``--write`` is always a dry run.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import tempfile
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
UPSTREAM_REPOSITORY = "TeamNewPipe/NewPipeExtractor"
BASELINE_VERSION = "v0.26.5"
SEMVER_RE = re.compile(r"^v?(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$")

# TeamNewPipe publishes NewPipeExtractor through JitPack.  The coordinate form
# is matched in addition to common version-catalog variable names.
COORDINATE_RE = re.compile(
    r"(?P<prefix>com\.github\.TeamNewPipe:NewPipeExtractor:)v?"
    r"(?P<version>\d+\.\d+\.\d+)(?P<suffix>\b)"
)
VERSION_CATALOG_RE = re.compile(
    r"(?P<prefix>\b(?:newpipe(?:Extractor)?|NewPipeExtractor))"
    r"(?P<between>\s*=\s*)(?P<quote>[\"'])v?"
    r"(?P<version>\d+\.\d+\.\d+)(?P<suffix>(?P=quote))",
    re.IGNORECASE,
)
GRADLE_PROPERTY_RE = re.compile(
    r"(?P<prefix>\bnewpipe\.version\s*=\s*)v?"
    r"(?P<version>\d+\.\d+\.\d+)(?P<suffix>\b)",
    re.IGNORECASE,
)
GRADLE_FALLBACK_RE = re.compile(
    r"(?P<prefix>\bnewPipeVersion[^;\n]*?getOrElse\(\s*(?P<quote>[\"']))v?"
    r"(?P<version>\d+\.\d+\.\d+)(?P<suffix>(?P=quote))",
    re.IGNORECASE,
)

EXCLUDED_PARTS = {
    ".git",
    ".gradle",
    ".idea",
    "build",
    "node_modules",
    "portal",
    "scripts",
    "tests",
}
ALLOWED_NAMES = {"build.gradle", "build.gradle.kts", "libs.versions.toml"}


@dataclass(frozen=True)
class DependencyEdit:
    path: Path
    before: str
    after: str
    old_version: str
    new_version: str

    @property
    def changed(self) -> bool:
        return self.before != self.after


class DependencyUpdateError(RuntimeError):
    """Raised when dependency discovery or replacement is unsafe."""


def normalize_version(version: str) -> str:
    """Validate and normalize a stable ``vX.Y.Z`` version."""

    candidate = version.strip()
    if not SEMVER_RE.fullmatch(candidate):
        raise DependencyUpdateError(
            f"invalid or unstable NewPipeExtractor version {version!r}; expected vX.Y.Z"
        )
    return candidate.removeprefix("v")


def _semver_key(version: str) -> tuple[int, int, int]:
    major, minor, patch = normalize_version(version).split(".")
    return int(major), int(minor), int(patch)


def current_dependency_version(root: Path = REPOSITORY_ROOT) -> str:
    """Read the single version currently declared by the Gradle build."""

    edits = plan_update(root)
    versions = {edit.old_version for edit in edits}
    if len(versions) != 1:
        raise DependencyUpdateError("the Gradle build has inconsistent NewPipeExtractor versions")
    return next(iter(versions))


def is_compatible_version(current: str, candidate: str) -> bool:
    """Use a conservative same-major/minor compatibility window.

    NewPipeExtractor is rapidly evolving and its 0.x releases can contain API
    breaks. A scheduled updater therefore advances patch releases inside the
    current compatibility line and requires an explicit maintainer change for a
    new line. This prevents an unattended job from publishing a blind upgrade.
    """

    current_key = _semver_key(current)
    candidate_key = _semver_key(candidate)
    return candidate_key[:2] == current_key[:2]


def _release_is_compatible(payload: dict[str, object], current: str) -> bool:
    tag = payload.get("tag_name")
    body = payload.get("body")
    if not isinstance(tag, str):
        return False
    try:
        candidate = normalize_version(tag)
    except DependencyUpdateError:
        return False
    if not is_compatible_version(current, candidate):
        return False
    if isinstance(body, str) and re.search(r"\bbreaking\b", body, re.IGNORECASE):
        return False
    return True


def fetch_latest_stable(
    repository: str = UPSTREAM_REPOSITORY,
    *,
    timeout: float = 15.0,
    current: str | None = None,
) -> str:
    """Return the newest stable release in the current compatibility line.

    The GitHub releases endpoint is queried instead of trusting a mutable
    branch or a hard-coded version. Releases marked as breaking are skipped;
    a maintainer must explicitly change the compatibility policy in a reviewed
    pull request before crossing a major/minor line.
    """

    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise DependencyUpdateError(f"invalid upstream repository {repository!r}")
    if current is None:
        current = current_dependency_version()
    current = normalize_version(current)
    url = f"https://api.github.com/repos/{repository}/releases?per_page=100"
    request = urllib.request.Request(
        url,
        headers={
            "Accept": "application/vnd.github+json",
            "User-Agent": "LiveTubeTV-dependency-updater/1.0",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            payload = json.load(response)
    except urllib.error.HTTPError as exc:
        if exc.code == 403:
            raise DependencyUpdateError(
                "GitHub API rate limit or permission denied while checking NewPipeExtractor"
            ) from exc
        raise DependencyUpdateError(
            f"GitHub returned HTTP {exc.code} while checking {repository}"
        ) from exc
    except (urllib.error.URLError, TimeoutError, OSError, json.JSONDecodeError) as exc:
        raise DependencyUpdateError(f"could not check {repository}: {exc}") from exc

    if not isinstance(payload, list):
        raise DependencyUpdateError("GitHub returned malformed release metadata")
    candidates: list[str] = []
    for release in payload:
        if not isinstance(release, dict) or release.get("draft") or release.get("prerelease"):
            continue
        if not _release_is_compatible(release, current):
            continue
        try:
            candidates.append(normalize_version(str(release["tag_name"])))
        except (KeyError, DependencyUpdateError):
            continue
    return max([current, *candidates], key=_semver_key)


def discover_dependency_files(root: Path = REPOSITORY_ROOT) -> list[Path]:
    """Find only files that can legitimately declare the dependency."""

    files: set[Path] = set()
    for direct in (root / "build.gradle", root / "build.gradle.kts"):
        if direct.is_file():
            files.add(direct)
    for gradle_file in root.rglob("*.gradle*"):
        relative = gradle_file.relative_to(root)
        if any(part in EXCLUDED_PARTS for part in relative.parts):
            continue
        if gradle_file.name in ALLOWED_NAMES:
            files.add(gradle_file)
    for configuration in (
        root / "gradle" / "libs.versions.toml",
        root / "gradle.properties",
    ):
        if configuration.is_file():
            files.add(configuration)
    return sorted(files, key=lambda path: path.relative_to(root).as_posix())


def replace_version(content: str, new_version: str) -> tuple[str, set[str]]:
    """Replace all recognized declarations and return the prior versions."""

    normalized = normalize_version(new_version)
    versions: set[str] = set()

    def coordinate_replacement(match: re.Match[str]) -> str:
        versions.add(normalize_version(match.group("version")))
        return f"{match.group('prefix')}v{normalized}"

    updated = COORDINATE_RE.sub(coordinate_replacement, content)
    updated = VERSION_CATALOG_RE.sub(
        _catalog_replacement(normalized, versions), updated
    )

    def simple_replacement(match: re.Match[str]) -> str:
        versions.add(normalize_version(match.group("version")))
        return f"{match.group('prefix')}v{normalized}{match.group('suffix')}"

    updated = GRADLE_PROPERTY_RE.sub(simple_replacement, updated)
    updated = GRADLE_FALLBACK_RE.sub(simple_replacement, updated)
    return updated, versions


def _catalog_replacement(new_version: str, versions: set[str]):
    def replacement(match: re.Match[str]) -> str:
        versions.add(normalize_version(match.group("version")))
        prefix = match.group("prefix")
        quote = match.group("quote")
        between = match.group("between")
        # Keep the project baseline's conventional v prefix in version catalogs.
        return f"{prefix}{between}{quote}v{new_version}{quote}"

    return replacement


def plan_update(
    root: Path = REPOSITORY_ROOT, new_version: str = BASELINE_VERSION
) -> list[DependencyEdit]:
    normalized = normalize_version(new_version)
    edits: list[DependencyEdit] = []
    for path in discover_dependency_files(root):
        try:
            before = path.read_text(encoding="utf-8", errors="strict")
        except (OSError, UnicodeError) as exc:
            raise DependencyUpdateError(f"cannot read {path}: {exc}") from exc
        after, versions = replace_version(before, normalized)
        if not versions:
            continue
        if len(versions) != 1:
            raise DependencyUpdateError(
                f"{path} declares multiple NewPipeExtractor versions: "
                + ", ".join(sorted(versions))
            )
        edits.append(DependencyEdit(path, before, after, next(iter(versions)), normalized))

    all_versions = {version for edit in edits for version in (edit.old_version,)}
    if not edits:
        raise DependencyUpdateError(
            "no NewPipeExtractor dependency was found in a Gradle build file"
        )
    if len(all_versions) != 1:
        raise DependencyUpdateError(
            "the project declares inconsistent NewPipeExtractor versions: "
            + ", ".join(sorted(all_versions))
        )
    return edits


def _atomic_write(path: Path, content: str) -> None:
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


def _bump_patch(version: str) -> str:
    major, minor, patch = (int(part) for part in normalize_version(version).split("."))
    return f"{major}.{minor}.{patch + 1}"


def _update_app_version(root: Path, version: str) -> None:
    properties = root / "gradle.properties"
    if not properties.is_file():
        return
    content = properties.read_text(encoding="utf-8")
    pattern = re.compile(r"(?m)^(?P<prefix>app\.version\s*=\s*)(?P<version>v?\d+\.\d+\.\d+)(?P<suffix>.*)$")
    match = pattern.search(content)
    if not match:
        raise DependencyUpdateError("gradle.properties does not declare app.version")
    updated = pattern.sub(
        lambda value: f"{value.group('prefix')}{version}{value.group('suffix')}",
        content,
        count=1,
    )
    _atomic_write(properties, updated)


def _update_build_metadata(root: Path, app_version: str, extractor_version: str) -> None:
    metadata_path = root / "data" / "build-metadata.json"
    if not metadata_path.is_file():
        return
    try:
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise DependencyUpdateError(f"cannot read build metadata: {exc}") from exc
    if not isinstance(metadata, dict):
        raise DependencyUpdateError("build metadata must be a JSON object")
    now = datetime.now(timezone.utc)
    metadata.update(
        {
            "app_version": app_version,
            "newpipe_extractor_version": normalize_version(extractor_version),
            "build_date": now.date().isoformat(),
            "version_code": (
                int(app_version.split(".")[0]) * 1_000_000
                + int(app_version.split(".")[1]) * 1_000
                + int(app_version.split(".")[2])
            ),
            "published": False,
            "published_at": None,
            "release_tag": "",
            "apk_url": "",
            "sha256": None,
            "signature_sha256": None,
        }
    )
    _atomic_write(
        metadata_path,
        json.dumps(metadata, indent=2, ensure_ascii=False) + "\n",
    )


def apply_update(
    root: Path = REPOSITORY_ROOT,
    new_version: str = BASELINE_VERSION,
    *,
    allow_downgrade: bool = False,
    allow_breaking: bool = False,
    write: bool = False,
) -> list[DependencyEdit]:
    normalized = normalize_version(new_version)
    edits = plan_update(root, normalized)
    current = edits[0].old_version
    if _semver_key(normalized) < _semver_key(current) and not allow_downgrade:
        raise DependencyUpdateError(
            f"refusing to downgrade NewPipeExtractor from v{current} to v{normalized}"
        )
    if not is_compatible_version(current, normalized) and not allow_breaking:
        raise DependencyUpdateError(
            f"refusing incompatible NewPipeExtractor update v{current} -> v{normalized}; "
            "a reviewed compatibility change is required"
        )
    if write:
        for edit in edits:
            _atomic_write(edit.path, edit.after)
        if normalized != current:
            properties = root / "gradle.properties"
            has_app_version = properties.is_file() and bool(
                re.search(r"(?m)^app\.version\s*=\s*v?\d+\.\d+\.\d+\s*$", properties.read_text(encoding="utf-8"))
            )
            if has_app_version:
                app_version = _bump_patch(_read_app_version(root))
                _update_app_version(root, app_version)
                _update_build_metadata(root, app_version, normalized)
    return edits


def _read_app_version(root: Path) -> str:
    properties = root / "gradle.properties"
    try:
        content = properties.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as exc:
        raise DependencyUpdateError(f"cannot read app.version: {exc}") from exc
    match = re.search(r"(?m)^app\.version\s*=\s*v?(\d+\.\d+\.\d+)\s*$", content)
    if not match:
        raise DependencyUpdateError("gradle.properties does not declare a semantic app.version")
    return match.group(1)


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--version",
        help="target stable release, for example v0.26.5 (default: query upstream)",
    )
    parser.add_argument(
        "--write", action="store_true", help="atomically apply the planned edit"
    )
    parser.add_argument(
        "--allow-downgrade", action="store_true", help="permit an older stable release"
    )
    parser.add_argument(
        "--allow-breaking",
        action="store_true",
        help="permit a reviewed major/minor compatibility change",
    )
    parser.add_argument(
        "--timeout", type=float, default=15.0, help="GitHub API timeout in seconds"
    )
    parser.add_argument(
        "--print-latest",
        action="store_true",
        help="print only the latest stable version (used by automation)",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _build_parser().parse_args(argv)
    try:
        target = args.version or fetch_latest_stable(timeout=args.timeout)
        if args.print_latest:
            print(normalize_version(target))
            return 0
        edits = apply_update(
            REPOSITORY_ROOT,
            target,
            allow_downgrade=args.allow_downgrade,
            allow_breaking=args.allow_breaking,
            write=args.write,
        )
    except DependencyUpdateError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    changed = [edit for edit in edits if edit.changed]
    mode = "updated" if args.write else "would update"
    print(
        f"NewPipeExtractor v{edits[0].old_version} {mode} to "
        f"v{normalize_version(target)} in {len(changed)} file(s):"
    )
    for edit in changed:
        print(f"  - {edit.path.relative_to(REPOSITORY_ROOT).as_posix()}")
    if not changed:
        print("  - already current")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
