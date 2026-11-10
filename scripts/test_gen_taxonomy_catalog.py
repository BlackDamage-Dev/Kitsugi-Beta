#!/usr/bin/env python3
"""Offline regression tests for the search catalog generator (no Android/JDK needed)."""
import json
import re
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from io import StringIO
from pathlib import Path
from unittest.mock import patch

import gen_taxonomy_catalog as generator


class CatalogGenerationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw = generator.render()
        cls.catalog = json.loads(cls.raw)

    def test_coverage_and_no_foreign_display_names(self):
        data = self.catalog
        self.assertEqual(114, len(data["genres"]))
        self.assertGreaterEqual(len(data["tags"]), 600)
        self.assertEqual(9457, len(data["organizations"]))
        forbidden = re.compile(r"[\u3040-\u30ff\u3400-\u9fff\uac00-\ud7af\u0400-\u052f]")
        for row in data["genres"] + data["tags"]:
            self.assertFalse(forbidden.search(row["tr"] + row["en"]))
        tr = generator.android_strings(generator.TR)
        en = generator.android_strings(generator.EN)
        for row in data["organizations"]:
            self.assertFalse(forbidden.search(row["name"]))
            self.assertEqual(row["name"], tr[row["key"]])
            self.assertEqual(row["name"], en[row["key"]])

    def test_aliases_from_all_sources_are_searchable_but_not_visible(self):
        action = next(row for row in self.catalog["genres"] if row["key"] == "action")
        self.assertEqual(("Aksiyon", "Action"), (action["tr"], action["en"]))
        self.assertIn("Экшен", action["aliases"])
        mappa = next(row for row in self.catalog["organizations"]
                     if row["key"] == "bangumi_studio_mappa")
        self.assertEqual(("studio", "MAPPA"), (mappa["role"], mappa["name"]))
        kyoto = next(row for row in self.catalog["organizations"]
                     if row["key"] == "bangumi_studio_kyoto_animation")
        self.assertIn("京阿尼", kyoto["aliases"])
        self.assertNotIn("京阿尼", kyoto["name"])

    def test_byte_determinism_and_check_is_read_only(self):
        self.assertEqual(self.raw, generator.render())
        with tempfile.TemporaryDirectory() as folder:
            file = Path(folder) / "catalog.json"
            file.write_bytes(self.raw[:-1])  # deliberately stale
            with patch.object(generator, "OUTPUT", file), patch.object(sys, "argv", ["gen", "--check"]):
                with redirect_stderr(StringIO()), redirect_stdout(StringIO()):
                    self.assertEqual(1, generator.main())
            self.assertEqual(self.raw[:-1], file.read_bytes(), "--check modified its input")


if __name__ == "__main__":
    unittest.main()
