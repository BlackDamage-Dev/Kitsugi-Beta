#!/usr/bin/env python3
"""SQLite migration + source/resource integration contracts. Not Android rendering/type checks."""
import contextlib
import json
import hashlib
import re
import sqlite3
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "app/src/main/java/com/kitsugi/animelist"


def read(relative):
    return (JAVA / relative).read_text(encoding="utf-8-sig")


class MangaReaderContracts(unittest.TestCase):
    def test_migration_34_to_35_keeps_existing_history(self):
        schema = json.loads((ROOT / "app/schemas/com.kitsugi.animelist.data.local.KitsugiDatabase/34.json").read_text())
        table = next(t for t in schema["database"]["entities"] if t["tableName"] == "manga_chapter_progress")
        migration = read("data/local/KitsugiDatabase.kt").split("internal val MIGRATION_34_35", 1)[1].split("fun getDatabase", 1)[0]
        sql = re.search(r'db.execSQL\("([^"]+)"\)', migration)[1]
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "history.sqlite"
            with contextlib.closing(sqlite3.connect(path)) as db:
                with db:
                    db.execute(table["createSql"].replace("${TABLE_NAME}", "manga_chapter_progress"))
                    for index in table["indices"]:
                        db.execute(index["createSql"].replace("${TABLE_NAME}", "manga_chapter_progress"))
                    db.execute("INSERT INTO manga_chapter_progress (chapterUrl,mangaUrl,chapterName,lastPageIndex,totalPages,isCompleted,updatedAt) VALUES ('c','m','Chapter',7,20,0,123)")
                    db.execute(sql)
                    row = db.execute("SELECT lastPageIndex,totalPages,isCompleted,updatedAt,lastPageOffset FROM manga_chapter_progress").fetchone()
                    self.assertEqual(row, (7, 20, 0, 123, 0.0))
                    db.execute("UPDATE manga_chapter_progress SET lastPageOffset=0.625")
            with contextlib.closing(sqlite3.connect(path)) as db:
                self.assertEqual(db.execute("SELECT lastPageOffset FROM manga_chapter_progress").fetchone()[0], .625)
                columns = {r[1]: r for r in db.execute("PRAGMA table_info(manga_chapter_progress)")}
                self.assertEqual(columns["lastPageOffset"][2:5], ("REAL", 1, "0"))
                self.assertTrue(any(r[1] == "idx_manga_progress_mangaUrl" for r in db.execute("PRAGMA index_list(manga_chapter_progress)")))

    def test_migration_is_registered_and_entity_has_matching_default(self):
        db = read("data/local/KitsugiDatabase.kt")
        self.assertIn("version = 35", db)
        self.assertIn("MIGRATION_34_35", db.split(".addMigrations(", 1)[1])
        self.assertRegex(read("data/local/MangaChapterProgressEntity.kt"), r'@ColumnInfo\(defaultValue = "0"\)\s+val lastPageOffset: Float = 0f')

    def test_exported_schema_35_matches_migration_shape(self):
        folder = ROOT / "app/schemas/com.kitsugi.animelist.data.local.KitsugiDatabase"
        old = json.loads((folder / "34.json").read_text())["database"]
        new = json.loads((folder / "35.json").read_text())["database"]
        before = next(e for e in old["entities"] if e["tableName"] == "manga_chapter_progress")
        after = next(e for e in new["entities"] if e["tableName"] == "manga_chapter_progress")
        with sqlite3.connect(":memory:") as db:
            db.execute(before["createSql"].replace("${TABLE_NAME}", "manga_chapter_progress"))
            sql = re.search(r'db.execSQL\("([^"]+)"\)', read("data/local/KitsugiDatabase.kt").split("internal val MIGRATION_34_35", 1)[1])[1]
            db.execute(sql)
            actual = {r[1]: (r[2], bool(r[3]), r[4]) for r in db.execute("PRAGMA table_info(manga_chapter_progress)")}
            expected = {f["columnName"]: (f["affinity"], f.get("notNull", False), f.get("defaultValue")) for f in after["fields"]}
            self.assertEqual(actual, expected)
            self.assertEqual(before["indices"], after["indices"])

    def test_schema_identity_algorithm_reproduces_room_exports_25_through_35(self):
        # Room's SchemaIdentityKey/Entity/Field/Index/PrimaryKey identity contract.
        # Verified against existing KSP exports before deriving 35; not a random hash.
        def identity(db):
            def digest(parts): return hashlib.md5("".join(p + "?:?" for p in parts).encode()).hexdigest()
            def boolean(value): return str(value).lower()
            def ordered(parts): return sorted(parts, key=str.lower)
            tables = []
            for table in db["entities"]:
                self.assertFalse(table.get("foreignKeys"))
                primary = table["primaryKey"]
                parts = [table["tableName"], boolean(primary["autoGenerate"]) + "-[" + ", ".join(primary["columnNames"]) + "]"]
                parts += ordered([f["columnName"] + "-" + f["affinity"] + "-" + boolean(f.get("notNull", False)) + ("-defaultValue=" + f["defaultValue"] if f.get("defaultValue") is not None else "") for f in table["fields"]])
                parts += ordered([boolean(i["unique"]) + "-" + i["name"] + "-" + ",".join(i["columnNames"]) + ("-" + ",".join(i["orders"]) if i.get("orders") else "") for i in table.get("indices", [])])
                tables.append(digest(parts))
            self.assertFalse(db.get("views"))
            return digest(ordered(tables))
        for version in range(25, 36):
            db = json.loads((ROOT / f"app/schemas/com.kitsugi.animelist.data.local.KitsugiDatabase/{version}.json").read_text())["database"]
            self.assertEqual(identity(db), db["identityHash"])
            self.assertTrue(any(db["identityHash"] in query for query in db["setupQueries"]))

    def test_one_scroll_owner_no_zoomable_page_in_webtoon(self):
        web = read("ui/screens/manga/components/MangaWebtoonReader.kt")
        self.assertEqual(len(re.findall(r"\bLazyColumn\(", web)), 1)
        self.assertNotIn("MangaPageItem(", web)
        self.assertNotIn("ZoomableAsyncImage(", web)
        self.assertNotIn("verticalScroll(", web)
        self.assertNotIn("MangaFitMode", web)
        self.assertIn("preferences.pageGapDp > 0", web)

    def test_viewport_taps_do_not_skip_to_another_image(self):
        session = read("ui/screens/manga/reader/MangaReaderSession.kt")
        self.assertIn("webtoon.advance(direction, state.webtoon.scrollPercent", session)
        self.assertNotIn("firstVisibleItemIndex + 1", session)
        self.assertNotIn("scrollBy(400", session)

    def test_single_settings_entry_and_shared_tv_reader(self):
        reader = read("ui/screens/manga/MangaReaderScreen.kt")
        self.assertEqual(reader.count("Icons.Default.Settings"), 1)
        tv = read("ui/tv/manga/TvMangaReaderScreen.kt")
        self.assertIn("MangaReaderScreen(chapter, mangaDetails, source, onBack, modifier)", tv)
        self.assertNotIn("onPreviewKeyEvent", tv)
        self.assertFalse((JAVA / "ui/tv/manga/TvMangaReaderControls.kt").exists())

    def test_global_settings_only_emit_canonical_ids(self):
        settings = read("ui/components/KitsugiPreferencesSettingsDialog.kt").split("private fun MangaReaderTab(", 1)[1]
        for canonical in ("Webtoon", "Grayscale", "Invert", "Sepia"):
            self.assertIn(f'id = "{canonical}"', settings)
        for obsolete in ("WebtoonZoom", "GrayScale", "Inverted", "Warm", "Cool"):
            self.assertNotIn(f'id = "{obsolete}"', settings)

    def test_all_reader_string_references_exist_in_both_locales(self):
        names = []
        for locale in ("values", "values-en"):
            names.append({node.attrib.get("name") for file in (ROOT / "app/src/main/res" / locale).glob("*.xml") for node in ET.parse(file).getroot() if node.tag == "string"})
        files = list((JAVA / "ui/screens/manga").rglob("*.kt"))
        refs = {ref for file in files for ref in re.findall(r"R\.string\.(\w+)", file.read_text(encoding="utf-8-sig"))}
        for locale, values in zip(("TR", "EN"), names):
            self.assertFalse(refs - values, f"Missing {locale} resources: {sorted(refs - values)}")

    def test_real_vertical_pager_is_not_a_horizontal_alias(self):
        pager = read("ui/screens/manga/components/MangaPagerReader.kt")
        self.assertIn("VerticalPager(", pager)
        self.assertIn("HorizontalPager(", pager)

    def test_telephoto_uses_its_own_coil_version_and_source_cache(self):
        item = read("ui/screens/manga/components/MangaPageItem.kt")
        self.assertIn("import coil3.request.ImageRequest", item)
        self.assertIn(".data(file)", item)
        self.assertIn("resetZoomSignal", item)

    def test_metadata_is_published_before_ready_and_archive_streams_are_reusable(self):
        loader = read("data/manga/loader/MangaPageLoaderV2.kt")
        ready = loader.split("private fun publishReady(", 1)[1].split("// ───", 1)[0]
        self.assertLess(ready.index("page.imageInfo ="), ready.index("page.status = MangaPageStatus.Ready"))
        self.assertLess(ready.index("page.cachedImageFile ="), ready.index("page.status = MangaPageStatus.Ready"))
        self.assertNotIn("page.stream = { stream }", loader)
        self.assertIn("lastVisibleIndex", loader)

    def test_progress_observers_are_mode_guarded_and_flush_on_exit(self):
        session = read("ui/screens/manga/reader/MangaReaderSession.kt")
        self.assertIn("ui.readingMode == ReadingMode.Webtoon) return@LaunchedEffect", session)
        self.assertIn("ui.readingMode != ReadingMode.Webtoon) return@LaunchedEffect", session)
        model = read("ui/screens/manga/MangaReaderViewModel.kt")
        self.assertIn("MangaReadingProgressPolicy.isComplete", model)
        self.assertIn("progressWrites.close()", model)
        self.assertIn("chapterName = state.currentChapter.name", model)

    def test_auto_scroll_is_paused_for_lifecycle_overlays_and_network_wait(self):
        session = read("ui/screens/manga/reader/MangaReaderSession.kt")
        for marker in ("Lifecycle.Event.ON_PAUSE", "overlayOpen", "DragInteraction.Start", "val waiting =", "canScrollForward"):
            self.assertIn(marker, session)

    def test_image_filters_are_shared_and_inversion_uses_255(self):
        for component in ("MangaPageItem.kt", "MangaWebtoonReader.kt"):
            self.assertIn("mangaPageColorFilter(colorFilterType)", read("ui/screens/manga/components/" + component))
        self.assertEqual(read("ui/screens/manga/components/MangaPageColorFilter.kt").count("255f"), 3)


if __name__ == "__main__":
    unittest.main(verbosity=2)
