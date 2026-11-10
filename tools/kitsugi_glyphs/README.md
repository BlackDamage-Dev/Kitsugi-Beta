# Kitsugi control glyphs

195 hand-authored monochrome 24×24 vectors, rounded 1.75-unit strokes, compatible
with the app's gradient tinting. Same actions share a glyph; this is not a separate
logo for every instance of the same button. Platform/source identity marks are preserved.

## Source of truth

- `generate_kitsugi_glyphs.py`: core geometry and Kotlin/SVG emitters.
- `extra_glyphs.py`: additional control geometry and semantic name aliases.
- `migrate_material_icons.py`: explicit Material-name → Kitsugi-name migration.
- `add_action_marks.py`: explicit label-resource → action glyph insertion.
- `complete_control_marks.py`: AST-assisted migration of custom label controls and inputs.
- `mark_artwork_controls.py`: artwork actions; image content is retained.
- `audit_controls.py`: fast conservative simple-button inventory.
- `audit_interactions.py`: expanded AST inventory of calls, modifiers, keyboard handlers,
  gesture detectors, inline links and copy-on-double-tap targets.
- `review_interactions.py`: constrained evidence rules for invisible layers and delegation.
- `interaction_coverage.json`: per-target ledger (path, line, function, evidence).
- `VERIFICATION.md`: scope, results and environment limitations.

## Generate and verify

```sh
python3 tools/kitsugi_glyphs/generate_kitsugi_glyphs.py
python3 -m venv /tmp/kitsugi-glyphs-venv
/tmp/kitsugi-glyphs-venv/bin/pip install -r tools/kitsugi_glyphs/requirements-dev.txt
/tmp/kitsugi-glyphs-venv/bin/python -m unittest discover -s tools/kitsugi_glyphs/tests -v
/tmp/kitsugi-glyphs-venv/bin/python tools/kitsugi_glyphs/audit_interactions.py \
  --ledger tools/kitsugi_glyphs/interaction_coverage.json --check
```

Use an ignored/out-of-repository virtual environment; do not commit dependencies.
The generator emits Kotlin plus `gorseller/kitsugi_ikon_seti.png.svg`. Render that SVG
with resvg to update the matching PNG. No external icon assets are downloaded.

Migration scripts are one-time source-editing tools, not runtime heuristics. Regenerate
the AST inventory immediately before using an AST migration; stale byte positions are
rejected. Runtime icon selection does not depend on translated label matching.

## Coverage interpretation

The latest expanded audit has **2,523 records and zero unresolved records**. Not every
record is a button: this count also includes state controls, generic content slots,
provider data constructors, shared input utilities and invisible gesture/scrim layers.
Those are explicitly distinguished in the ledger, not silently counted as glyphs.

A poster/avatar alone is not accepted as an action glyph. Tappable artwork now has a
Zoom/details/profile mark; source-logo fallback artwork is not decorated when no avatar
is present. Inline Wiki links have a mark inside their own annotated link ranges.

Invisible input layers should not acquire extra independent icons or click handlers:
these are checked against the actual marked control beneath them. Shared wrappers
continue to receive their visual content from their caller.

Static checks do not certify Android rendering or compilation. Java and the Android SDK
are absent here; an APK/device pass remains necessary for clipping, TalkBack, TV focus,
RTL and small-screen layout validation. Third-party WebView document content is not
rewritten by this Compose-source audit.
