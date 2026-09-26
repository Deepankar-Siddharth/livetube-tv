from __future__ import annotations

import json
import tempfile
import unittest
from copy import deepcopy
from pathlib import Path

from scripts.validate_channels import (
    LEGACY_CLASSIFICATION,
    ChannelValidationError,
    load_and_validate,
    validate_document,
)


class ValidateChannelsTests(unittest.TestCase):
    def setUp(self) -> None:
        self.channel = {
            "id": "example_news",
            "name": "Example News",
            "category": "News",
            "subcategory": "English & Global News",
            "language": "English",
            "region": "National",
            "logo": "https://cdn.example.com/example.png",
            "youtube_handle": "@exampleNews",
            "live_url": "https://www.youtube.com/@exampleNews/live",
            "enabled": True,
            "sort_order": 1,
        }

    def catalogue(self) -> dict:
        return {
            "schema_version": 2,
            "data_version": 2,
            "updated_at": "2026-09-25T00:00:00Z",
            "channels": [deepcopy(self.channel)],
        }

    def test_repository_catalogue_is_valid(self) -> None:
        root = Path(__file__).resolve().parents[1]
        path = root / "data" / "channels.json"
        result = load_and_validate(path)
        stored = json.loads(path.read_text(encoding="utf-8"))
        # Counts come from the file itself so the check cannot go stale when the catalogue is
        # reworked; the point of the test is that the shipped catalogue is structurally valid.
        self.assertEqual(2, result.document["schema_version"])
        self.assertGreater(len(result.channels), 0)
        self.assertEqual(len(stored["channels"]), len(result.channels))
        self.assertEqual(
            sum(channel["enabled"] for channel in stored["channels"]),
            sum(channel["enabled"] for channel in result.channels),
        )

    def test_valid_document_is_normalized(self) -> None:
        result = validate_document(self.catalogue())
        self.assertEqual(self.channel, result["channels"][0])
        self.assertEqual(2, result["data_version"])

    def test_unknown_field_is_rejected(self) -> None:
        catalogue = self.catalogue()
        catalogue["channels"][0]["epg_id"] = "legacy.in"
        with self.assertRaisesRegex(ChannelValidationError, "unknown"):
            validate_document(catalogue)

    def test_missing_field_is_rejected(self) -> None:
        catalogue = self.catalogue()
        del catalogue["channels"][0]["enabled"]
        with self.assertRaisesRegex(ChannelValidationError, "missing"):
            validate_document(catalogue)

    def test_noncanonical_youtube_url_is_rejected(self) -> None:
        catalogue = self.catalogue()
        catalogue["channels"][0]["live_url"] = "https://youtube.com/@exampleNews/live"
        with self.assertRaisesRegex(ChannelValidationError, "canonical"):
            validate_document(catalogue)

    def test_handle_and_url_must_match(self) -> None:
        catalogue = self.catalogue()
        catalogue["channels"][0]["youtube_handle"] = "@different"
        with self.assertRaisesRegex(ChannelValidationError, "canonical"):
            validate_document(catalogue)

    def test_duplicate_handle_and_order_are_rejected(self) -> None:
        for field, value in (("youtube_handle", "@examplenews"), ("sort_order", 1)):
            with self.subTest(field=field):
                catalogue = self.catalogue()
                duplicate = deepcopy(self.channel)
                duplicate["id"] = "another"
                duplicate[field] = value
                if field == "youtube_handle":
                    duplicate["live_url"] = "https://www.youtube.com/@examplenews/live"
                catalogue["channels"].append(duplicate)
                with self.assertRaisesRegex(ChannelValidationError, "duplicates"):
                    validate_document(catalogue)

    def test_new_categories_and_subcategories_are_accepted(self) -> None:
        # The catalogue is allowed to grow new categories in a later data_version; the app
        # derives its guide rows from whatever the document contains.
        catalogue = self.catalogue()
        catalogue["channels"][0]["category"] = "Nature & Science"
        catalogue["channels"][0]["subcategory"] = "Ocean Exploration"
        result = validate_document(catalogue)
        self.assertEqual("Nature & Science", result["channels"][0]["category"])
        self.assertEqual("Ocean Exploration", result["channels"][0]["subcategory"])

    def test_blank_and_oversized_category_text_is_rejected(self) -> None:
        for value in ("   ", "x" * 65):
            with self.subTest(value=value[:8]):
                catalogue = self.catalogue()
                catalogue["channels"][0]["category"] = value
                with self.assertRaises(ChannelValidationError):
                    validate_document(catalogue)

        for value in ("", "x" * 97):
            with self.subTest(value=value[:8]):
                catalogue = self.catalogue()
                catalogue["channels"][0]["subcategory"] = value
                with self.assertRaises(ChannelValidationError):
                    validate_document(catalogue)

    def test_legacy_schema_is_normalized(self) -> None:
        catalogue = self.catalogue()
        channel = catalogue["channels"][0]
        for field in ("subcategory", "language", "region"):
            del channel[field]
        channel["category"] = "National Hindi News"
        catalogue["schema_version"] = 1
        result = validate_document(catalogue)
        self.assertEqual(2, result["schema_version"])
        self.assertEqual("News", result["channels"][0]["category"])
        self.assertEqual("Hindi News", result["channels"][0]["subcategory"])
        self.assertEqual("Hindi", result["channels"][0]["language"])
        self.assertEqual("National", result["channels"][0]["region"])

    def test_every_legacy_category_has_a_deterministic_mapping(self) -> None:
        for legacy, expected in LEGACY_CLASSIFICATION.items():
            with self.subTest(legacy=legacy):
                catalogue = self.catalogue()
                channel = catalogue["channels"][0]
                for field in ("subcategory", "language", "region"):
                    del channel[field]
                channel["category"] = legacy
                catalogue["schema_version"] = 1
                result = validate_document(catalogue)["channels"][0]
                self.assertEqual(expected, (
                    result["category"],
                    result["subcategory"],
                    result["language"],
                    result["region"],
                ))

    def test_duplicate_json_key_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "channels.json"
            path.write_text(
                '{"schema_version": 1, "schema_version": 1, "data_version": 1, '
                '"updated_at": "2026-09-25T00:00:00Z", "channels": []}',
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ChannelValidationError, "duplicate"):
                load_and_validate(path)

    def test_http_logo_is_rejected(self) -> None:
        catalogue = self.catalogue()
        catalogue["channels"][0]["logo"] = "http://cdn.example.com/logo.png"
        with self.assertRaisesRegex(ChannelValidationError, "HTTPS"):
            validate_document(catalogue)

    def test_boolean_schema_version_is_rejected(self) -> None:
        catalogue = self.catalogue()
        catalogue["schema_version"] = True
        with self.assertRaisesRegex(ChannelValidationError, "integer"):
            validate_document(catalogue)

    def test_empty_catalogue_is_rejected(self) -> None:
        catalogue = self.catalogue()
        catalogue["channels"] = []
        with self.assertRaisesRegex(ChannelValidationError, "at least one"):
            validate_document(catalogue)

    def test_json_file_must_be_utf8_without_bom(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "channels.json"
            path.write_text(json.dumps(self.catalogue()), encoding="utf-8-sig")
            with self.assertRaisesRegex(ChannelValidationError, "BOM"):
                load_and_validate(path)


if __name__ == "__main__":
    unittest.main()
