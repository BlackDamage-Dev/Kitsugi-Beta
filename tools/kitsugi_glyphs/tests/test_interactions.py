"""Expanded static regression suite. Run in a dev venv with requirements-dev.txt."""
import importlib.util
from pathlib import Path
import re
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'tools/kitsugi_glyphs'))
HAS_PARSER = importlib.util.find_spec('tree_sitter') is not None and importlib.util.find_spec('tree_sitter_kotlin') is not None

@unittest.skipUnless(HAS_PARSER, 'Install tools/kitsugi_glyphs/requirements-dev.txt for AST checks')
class InteractionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        import audit_interactions as audit
        cls.audit = audit
        cls.rows = audit.inventory()

    def test_no_unreviewed_interaction(self):
        failures = [(r['path'], r['line'], r['function'], r['name']) for r in self.rows if r['status'].startswith('unresolved')]
        self.assertEqual(failures, [])

    def test_covers_phone_tv_and_independent_modifiers(self):
        self.assertGreater(len(self.rows), 2400)
        self.assertTrue(any('/ui/tv/' in r['path'] for r in self.rows))
        self.assertTrue(any(r['status'] == 'modifier-owner-glyph' for r in self.rows))
        self.assertTrue(any(r['status'] == 'inline-link-glyph' for r in self.rows))

    def test_artwork_is_not_mistaken_for_an_action_logo(self):
        # Artwork-only rows must get a glyph too. Source identity marks are separate.
        missing = [(r['path'], r['line']) for r in self.rows if r['status'] == 'visual' and r['evidence'] == 'AsyncImage(']
        self.assertEqual(missing, [])

    def test_evidence_present_for_every_record(self):
        self.assertTrue(all(r['evidence'] for r in self.rows))

    def test_required_caller_glyphs(self):
        for path in self.audit.SRC.rglob('*.kt'):
            data = path.read_bytes()
            for node in self.audit.walk(self.audit.PARSER.parse(data).root_node):
                result = self.audit.parts(node, data)
                if not result: continue
                name, args, _ = result
                if args is None: continue
                if name in ('FilledControlsButton', 'TvDialogButton', 'TvExtDialogButton'):
                    self.assertRegex(self.audit.text(args, data), r'\bglyph\s*=', str(path))
                elif name == 'ControlsButton' and re.search(r'\btext\s*=', self.audit.text(args, data)):
                    self.assertRegex(self.audit.text(args, data), r'\bglyph\s*=', str(path))

    def test_icon_calls_have_no_text_only_parameters(self):
        for path in self.audit.SRC.rglob('*.kt'):
            data = path.read_bytes()
            for node in self.audit.walk(self.audit.PARSER.parse(data).root_node):
                result = self.audit.parts(node, data)
                if not result or result[0] != 'Icon' or result[1] is None: continue
                for arg in result[1].named_children:
                    if arg.type != 'value_argument' or len(arg.named_children) < 2: continue
                    key = self.audit.text(arg.named_children[0], data)
                    self.assertNotIn(key, ('text', 'fontSize', 'fontWeight', 'style', 'color'), str(path))

    def test_changed_kotlin_has_no_new_syntax_errors(self):
        changed = subprocess.check_output(['git', 'diff', '--name-only'], cwd=ROOT, text=True).splitlines()
        new = subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard'], cwd=ROOT, text=True).splitlines()
        def errors(data):
            return sum(n.type == 'ERROR' or n.is_missing for n in self.audit.walk(self.audit.PARSER.parse(data).root_node))
        for rel in set(changed + new):
            if not rel.endswith('.kt'): continue
            previous = subprocess.run(['git', 'show', 'HEAD:' + rel], cwd=ROOT, capture_output=True)
            old_count = errors(previous.stdout) if previous.returncode == 0 else 0
            self.assertLessEqual(errors((ROOT / rel).read_bytes()), old_count, rel)

    def test_mark_helpers_do_not_add_click_handlers(self):
        base = ROOT / 'app/src/main/java/com/kitsugi/animelist/ui/components'
        for name in ('KitsugiMarkedText.kt', 'KitsugiActionMark.kt', 'KitsugiArtworkAction.kt', 'KitsugiActionBadge.kt', 'KitsugiInputDecoration.kt'):
            code = (base / name).read_text()
            self.assertNotRegex(code, r'\.(?:clickable|combinedClickable|tvClickable)\s*\(')
        self.assertNotRegex('\n'.join(p.read_text() for p in self.audit.SRC.rglob('*.kt')), r'(?:material3|gradient)\.KitsugiMarkedText\(')

if __name__ == '__main__': unittest.main()
