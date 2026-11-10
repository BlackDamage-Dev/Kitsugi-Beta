# Wiki page tools — third-party notices

## Bundled data

- Project: StevenBlack/hosts — https://github.com/StevenBlack/hosts
- **Only** `data/StevenBlack/hosts` is included, not the aggregate `hosts` file containing other upstream projects.
- Pinned source revision and SHA-256 checksums: `hosts-source.json`.
- License: MIT. Full unchanged notice: `STEVENBLACK-LICENSE.txt` (also displayed in the page tools UI).
- Local copy: `stevenblack-hosts.txt`, unmodified from the pinned source.
- Kitsugi supplements this with its own advertising-domain rules and narrow cosmetic selectors.

Updates are an explicit reviewed maintenance operation via `scripts/update_wiki_ad_hosts.py`; the Android app does not download executable filters or run remote blocker scripts.

## Architectural references (not bundled code)

- Translate Web Pages: https://github.com/FilipePS/Traduzir-paginas-web — MPL-2.0.
  Reviewed DOM text-node restoration and dynamic-content handling. No implementation source copied.
- Ghostery Adblocker: https://github.com/ghostery/adblocker — MPL-2.0.
  Reviewed browser integration and separation of request filtering/cosmetic filtering. Not embedded as an engine or dependency.

`dom-translator.js`, `adblock-cosmetic.js`, `page-appearance.js`, `page-fit.js` and the Kotlin
coordinator/matcher were authored for Kitsugi. This implementation is not AdBlocker Ultimate, Chrome Translate or Brave Shields and does not claim their full coverage or rule-format compatibility.
