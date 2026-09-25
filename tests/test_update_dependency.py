from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from scripts.update_dependency import (
    DependencyUpdateError,
    apply_update,
    normalize_version,
    plan_update,
    replace_version,
)


class UpdateDependencyTests(unittest.TestCase):
    def test_normalize_requires_stable_semver(self) -> None:
        self.assertEqual("0.26.5", normalize_version("v0.26.5"))
        for value in ("0.26", "v0.26.5-rc1", "latest", "v01.2.3"):
            with self.subTest(value=value), self.assertRaises(DependencyUpdateError):
                normalize_version(value)

    def test_replaces_coordinate_and_version_catalog(self) -> None:
        coordinate = (
            'implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")\n'
        )
        catalog = 'newpipe = "0.26.5"\n'
        gradle_property = "newpipe.version=v0.26.5\n"
        gradle_fallback = 'val newPipeVersion = providers.gradleProperty("newpipe.version").getOrElse("v0.26.5")\n'
        updated_coordinate, coordinate_versions = replace_version(coordinate, "v0.26.6")
        updated_catalog, catalog_versions = replace_version(catalog, "0.26.6")
        updated_property, property_versions = replace_version(gradle_property, "0.26.6")
        updated_fallback, fallback_versions = replace_version(gradle_fallback, "0.26.6")
        self.assertIn(":v0.26.6", updated_coordinate)
        self.assertEqual('newpipe = "v0.26.6"\n', updated_catalog)
        self.assertEqual("newpipe.version=v0.26.6\n", updated_property)
        self.assertIn('getOrElse("v0.26.6")', updated_fallback)
        self.assertEqual(
            {"0.26.5"},
            coordinate_versions
            | catalog_versions
            | property_versions
            | fallback_versions,
        )

    def test_plan_and_atomic_write_are_scoped_to_gradle(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "app").mkdir()
            (root / "gradle").mkdir()
            app_build = root / "app" / "build.gradle.kts"
            catalog = root / "gradle" / "libs.versions.toml"
            gradle_properties = root / "gradle.properties"
            readme = root / "README.md"
            app_build.write_text(
                'implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")\n',
                encoding="utf-8",
            )
            catalog.write_text('newpipe = "v0.26.5"\n', encoding="utf-8")
            gradle_properties.write_text("newpipe.version=v0.26.5\n", encoding="utf-8")
            readme.write_text("NewPipeExtractor v0.26.5\n", encoding="utf-8")

            edits = apply_update(root, "0.26.6", write=True)
            self.assertEqual(3, len(edits))
            self.assertIn(":v0.26.6", app_build.read_text(encoding="utf-8"))
            self.assertIn('newpipe = "v0.26.6"', catalog.read_text(encoding="utf-8"))
            self.assertIn("newpipe.version=v0.26.6", gradle_properties.read_text(encoding="utf-8"))
            self.assertIn("v0.26.5", readme.read_text(encoding="utf-8"))
            self.assertTrue(all(not edit.changed for edit in apply_update(root, "0.26.6", write=True)))

    def test_dry_run_does_not_write(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            build_file = root / "build.gradle"
            original = 'implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")\n'
            build_file.write_text(original, encoding="utf-8")
            edits = apply_update(root, "0.26.6", write=False)
            self.assertTrue(edits[0].changed)
            self.assertEqual(original, build_file.read_text(encoding="utf-8"))

    def test_refuses_downgrade_and_mixed_versions(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            build_file = root / "build.gradle"
            build_file.write_text(
                'implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")\n',
                encoding="utf-8",
            )
            with self.assertRaisesRegex(DependencyUpdateError, "downgrade"):
                apply_update(root, "0.26.4", write=False)

            build_file.write_text(
                'implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")\n'
                'testImplementation("com.github.TeamNewPipe:NewPipeExtractor:v0.25.0")\n',
                encoding="utf-8",
            )
            with self.assertRaisesRegex(DependencyUpdateError, "multiple"):
                plan_update(root, "0.26.6")


if __name__ == "__main__":
    unittest.main()
