from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from scripts.build_m3u import build, render_playlist
from scripts.validate_channels import (
    ChannelValidationError,
    ValidatedCatalogue,
    validate_document,
)


class BuildM3uTests(unittest.TestCase):
    def setUp(self) -> None:
        self.channels = [
            {
                "id": "example_news",
                "name": "Example & News",
                "category": "News",
                "subcategory": "English & Global News",
                "language": "English",
                "region": "National",
                "logo": "https://cdn.example.com/logo.png?size=2x",
                "youtube_handle": "@exampleNews",
                "live_url": "https://www.youtube.com/@exampleNews/live",
                "enabled": True,
                "sort_order": 2,
            },
            {
                "id": "disabled_channel",
                "name": "Disabled Channel",
                "category": "News",
                "subcategory": "Hindi News",
                "language": "Hindi",
                "region": "National",
                "logo": "https://cdn.example.com/disabled.png",
                "youtube_handle": "@disabledChannel",
                "live_url": "https://www.youtube.com/@disabledChannel/live",
                "enabled": False,
                "sort_order": 1,
            },
        ]
        self.raw_document = {
            "schema_version": 2,
            "data_version": 3,
            "updated_at": "2026-09-25T00:00:00Z",
            "channels": self.channels,
        }
        self.catalogue = ValidatedCatalogue(
            path=Path("memory.json"), document=validate_document(self.raw_document)
        )

    def test_playlist_is_deterministic_and_omits_disabled_channels(self) -> None:
        rendered = render_playlist(self.catalogue)
        self.assertEqual(rendered, render_playlist(self.catalogue))
        self.assertTrue(rendered.startswith("#EXTM3U\n#EXTINF:-1 "))
        self.assertIn('tvg-chno="1"', rendered)
        self.assertIn('tvg-id="example_news"', rendered)
        self.assertIn('group-title="News",Example & News\n', rendered)
        self.assertIn("https://www.youtube.com/@exampleNews/live\n", rendered)
        self.assertNotIn("disabled_channel", rendered)
        self.assertNotIn("\r", rendered)
        self.assertTrue(rendered.endswith("\n"))

    def test_build_writes_and_check_verifies_exact_bytes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "channels.json"
            output = root / "playlist.m3u8"
            source.write_text(json.dumps(self.raw_document, indent=2) + "\n", encoding="utf-8")
            expected = build(source, output)
            self.assertEqual(expected, output.read_text(encoding="utf-8"))
            build(source, output, check=True)

            output.write_text("#EXTM3U\nstale\n", encoding="utf-8")
            with self.assertRaisesRegex(ChannelValidationError, "stale"):
                build(source, output, check=True)

    def test_invalid_source_never_writes_output(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "channels.json"
            output = root / "playlist.m3u8"
            invalid = json.loads(json.dumps(self.raw_document))
            invalid["channels"][0]["live_url"] = "https://youtu.be/not-canonical"
            source.write_text(json.dumps(invalid), encoding="utf-8")
            with self.assertRaises(ChannelValidationError):
                build(source, output)
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
