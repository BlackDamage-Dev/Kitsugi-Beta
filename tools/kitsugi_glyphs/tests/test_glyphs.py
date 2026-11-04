import importlib.util
import pathlib
import re
import unittest
import sys

ROOT = pathlib.Path(__file__).resolve().parents[3]
TOOLS = ROOT / 'tools/kitsugi_glyphs'
sys.path.insert(0, str(TOOLS))

def load(name):
    spec = importlib.util.spec_from_file_location(name, TOOLS / (name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

generator = load('generate_kitsugi_glyphs')
migration = load('migrate_material_icons')

class GlyphTests(unittest.TestCase):
    def test_generated_kotlin_is_current(self):
        path = ROOT / 'app/src/main/java/com/kitsugi/animelist/ui/theme/icons/KitsugiGlyphs.kt'
        self.assertEqual(path.read_text(), generator.generate_kotlin())

    def test_no_material_controls_remain_outside_source_marks(self):
        for path in (ROOT / 'app/src/main/java/com/kitsugi').rglob('*.kt'):
            if not migration.should_skip(str(path)):
                self.assertIsNone(migration.ICON_RE.search(path.read_text()), str(path))

    def test_simple_label_buttons_have_marks(self):
        audit = load('audit_controls')
        remaining = []
        for path in audit.SRC.rglob('*.kt'):
            remaining.extend((str(path), label) for _, _, label in audit.controls(path.read_text()))
        self.assertEqual(remaining, [])

    def test_unique_names(self):
        names = [name for name, _ in generator.GLYPHS]
        self.assertEqual(len(names), len(set(names)))

    def test_mapping_targets_exist(self):
        names = {name for name, _ in generator.GLYPHS}
        self.assertFalse(set(migration.MAP.values()) - names)

    def test_source_marks_excluded(self):
        for name in ('KitsugiPlatformLogo.kt', 'KitsugiSourceBadge.kt'):
            self.assertTrue(migration.should_skip(str(ROOT / 'app/src/main/java/com/kitsugi/animelist/ui/components' / name)))

    def test_state_variants_are_distinct(self):
        for a,b in [('VolumeUp','VolumeOff'), ('Repeat','RepeatOne'), ('Lock','LockOpen'), ('Fullscreen','FullscreenExit'), ('Subtitles','SubtitlesOff')]:
            self.assertNotEqual(migration.MAP[a], migration.MAP[b])

    def test_all_references_resolve(self):
        names = {name for name, _ in generator.GLYPHS}
        for path in (ROOT / 'app/src/main/java/com/kitsugi').rglob('*.kt'):
            text = path.read_text()
            refs = set(re.findall(r'KitsugiGlyphs\.(\w+)', text)) - {'kt', 'byNameOrNull'}
            self.assertFalse(refs - names, str(path))
            if refs and path.name != 'KitsugiGlyphs.kt':
                self.assertIn(migration.IMPORT_LINE, text, str(path))

if __name__ == '__main__':
    unittest.main()
