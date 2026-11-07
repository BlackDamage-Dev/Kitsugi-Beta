# Manga reader ambient-audio sources

**Updated:** 7 November 2026
**Scope:** title-first catalog search, context fallback, cross-source playback queues, and offline downloads where a provider exposes a direct file. This records API access and implementation behavior; it is not an item-by-item rights review.

## Summary

The reader uses the current manga scraper's `MangaDetails.title` as the first audio-catalog query. If fewer than five fresh, suitable title matches are found, it follows with a mood/context query derived locally from genre/story signals. Title matches receive a strong relevance priority; context results are relevance-ranked across the enabled providers. Recent-play suppression still applies across the combined queue, so a previously played title match may fall below fresh context results. The queue advances through results without repeat-one playback, skips a failed item when another item is queued, and carries duplicate checks across catalogs.

`MangaDetails` currently contains a title, description, genres, URL, and scraper-source name, but no MAL, Simkl, Kitsu, Shikimori, Bangumi, TMDB IDs or alternate-title list. `MangaSource` is a scraper/reader provider, not one of those metadata catalogs. The implementation therefore uses the active reader source's title and does not fabricate cross-catalog IDs or make seven additional metadata lookups. Supplying canonical/alternate titles from those catalog integrations requires a real mapping field on the reader model.

The title is sent to selected audio providers for the first search. Synopsis text, chapter name, page content, and account details are not sent; only the small context query derived from local genre/story signals is used in the fallback phase. Manual catalog search remains available. Search results are cached on-device for 12 hours.

A public or no-key API describes how a catalog can be queried; it does **not** guarantee every item is unrestricted, available forever, downloadable, or playable on every device. Provider and item terms vary. The app does not use unknown or missing license metadata as a playback gate and does not claim that every public API result is unrestricted.

## Connected catalogs

| Provider | Type and access | Search/playback path | Download behavior and limitations |
|---|---|---|---|
| **Openverse** (Freesound and Wikimedia Audio datasets) | On-demand audio index; anonymous read API | `GET /v1/audio/?q=…&source=freesound` or `source=wikimedia_audio`; direct media URL comes from each result | A secure direct result/download URL is offered for offline use where present. The app requests one dataset per call, spaces requests by about 3.1 seconds, and caches results. Jamendo is deliberately not queried. Direct Freesound API access is not used because its documented developer flow requires credentials. |
| **Internet Archive** | On-demand public audio collection | Advanced Search with `mediatype:audio`, followed by a bounded metadata lookup to select a direct audio file | The selected direct HTTPS file is downloadable. Metadata varies; only a small number of search hits are inspected per request. Files may be missing or slow. |
| **Audius** | On-demand, user-uploaded music; public read/search path | `/v1/tracks/search` with an app name, then `/v1/tracks/{id}/stream`; gated or explicitly non-streamable results are skipped | A download endpoint is exposed only when the track explicitly reports `is_downloadable`/`downloadable`. Stream URLs can redirect and depend on the underlying track/content nodes; this is not an availability SLA. |
| **Wikimedia Commons** | On-demand audio files, searched directly through the MediaWiki Action API | File-namespace search plus `imageinfo` URL, MIME/media type, size, duration, and descriptive metadata | Secure direct audio file URLs can be saved offline. Search is currently bounded to Ogg and MP3 MIME queries, with one-second spacing and an identifying User-Agent. Results overlap with Openverse's Wikimedia dataset and are deduplicated locally. |
| **ccMixter** | On-demand music; public Query API feed | Query API search returned as RSS (`f=rss`); playable media is read from feed enclosure/content URLs | Secure direct enclosure URLs can be saved offline. The beta feed may omit an enclosure or return an unavailable stream; XML parsing rejects external entities and is isolated to this provider. |
| **Free To Use** | On-demand music; public API | `GET https://api.freetouse.com/v3/music/tracks/search?query=…&limit=24`; MP3 URL is read from `files.mp3` | The app skips entries marked premium and offers the public MP3 file for offline use. The provider's separate use terms impose conditions on files and commercial apps, so confirm they fit the app's distribution context. |
| **Radio Browser** | Live radio-station directory, not an on-demand track catalog | `/json/stations/search` by a contextual genre/mood tag; play `url_resolved` | Live radio is stream-only and never shown as downloadable. Stations may be stale despite the directory health check; live streams have no reliable track duration or seek position. |

Jamendo remains excluded. Source toggles stay independent, and a failure or malformed response from one provider does not cancel the other enabled providers.

## Playback and format handling

The reader validates HTTPS stream URLs and known audio/HLS types before playback. Recognized direct audio types include MP3, Ogg/OGA, Opus, FLAC, M4A/M4B, AAC, WAV, AIFF, ALAC, AMR, 3GP, Matroska audio, WebM audio, APE, WavPack, TTA, CAF, AU, WMA, AC-3, and E-AC-3; Radio Browser may also return HLS. HLS can have a null duration and is not treated as a downloadable file. The reader's Media3 player prefers available extension decoders, including the project's FFmpeg decoder AAR. A catalog item can still fail if its stream is stale or the device/decoder cannot handle it.

Downloads are limited to sources with an HTTPS direct-file target; radio is excluded. Files are size-capped and saved to `Music/Kitsugi/Ambient` through MediaStore on Android 10+, or the app-specific Music directory on older Android. A device-format check attempts Media3 Transformer AAC conversion when an input is not a widely supported system format or has no system decoder. If the input cannot be decoded or the AAC encoder is unavailable, the original file is saved instead and the app does not claim conversion succeeded. Conversion support is best-effort and depends on device codecs and input container/codec; it is not universal.

An online-playback control prevents stream URLs from playing when disabled; results can still be searched, and locally downloaded items remain playable. When a user selects a downloaded result, the queue prefers its local URI. Download availability is provider-specific and is not inferred from public API access alone.

## Context matching, ranking, and deduplication

`AmbientAudioPlanner` maps genre/story signals into concise context terms (for example, action/fantasy toward cinematic/orchestral music, horror/mystery toward dark ambient/suspense, and romance/slice-of-life toward softer styles). Title candidates are searched first and boosted by title relevance. If fewer than five title matches survive safety filtering and recent-play suppression, the app searches all enabled catalogs again with the context query; otherwise only enabled live-radio context results are added. The combined queue strongly boosts true manga-title matches and applies recent-play suppression globally, allowing a fresh context item to outrank a recently played title match.

Results are deduplicated if either their provider-qualified IDs **or** their normalized title/creator fingerprints match, retaining the best-ranked copy. Recent-play history uses both identities across providers so a catalog copy is deprioritized. Strings parsed from provider scalar and array fields are capped at 500 characters before retention/cache.

Playback controls include play/pause, stop, previous/next, volume, queue position, and automatic advancement. Tapping Play replaces the active track or queue; the explicit Add-to-queue action appends without interrupting current playback. The queue does not use repeat-one. Failed entries are skipped when a next item exists. Radio Browser results are labeled live and have no download button.

## No-key and operational behavior

- Openverse is paced more conservatively than its observed anonymous response-header allowance. A maintainer has reported 20 requests/minute and 200/day for anonymous clients, but those observed values are not a permanent quota contract; caching and bounded fan-out reduce repeat requests.
- Wikimedia guidance uses variable rate limits. The Commons adapter sends an identifying User-Agent and applies a one-second inter-request delay.
- Internet Archive metadata lookup is bounded to four candidate items per search. ccMixter feed items and all other source page sizes are capped.
- Search, playback, download, and conversion failures are provider/device-specific; the UI keeps source warnings isolated rather than implying universal availability.

## Deliberate exclusions

- **Jamendo** remains excluded, as requested; it is not selected as an Openverse dataset.
- **Direct Freesound API** is not integrated because its documented developer API flow requires credentials; Freesound discovery is through Openverse.
- **Free Music Archive API** is not used: its developer page reports that the API was shut down. The app does not scrape FMA or proxy user searches to it.
- **Mixcloud** is not used because its API does not expose audio streams for this playback flow.
- **SoundCloud** is not used because its searchable playback API requires a registered application/authentication, contrary to the no-key preference.

## Verification status

Provider response parsers now have fixture-based unit coverage for Openverse, Internet Archive, Audius, Wikimedia Commons, ccMixter, Free To Use, and Radio Browser, including unknown license metadata, Audius downloadability, premium Free To Use items, secure direct files, and HLS without duration. Additional unit tests cover title/context ranking, cross-catalog ID/normalized-identity deduplication, recent-play ordering, safety limits, and audio format normalization. No live provider requests were made. Kotlin syntax, XML well-formedness, and diff checks can be run locally; Gradle compilation/unit execution remains blocked in this workspace because no Java runtime or `JAVA_HOME` is installed.

## Official references

- [Openverse audio API quickstart](https://docs.openverse.org/api/guides/quickstart.html)
- [Openverse authentication and throttling](https://docs.openverse.org/api/reference/authentication_and_throttling.html)
- [Openverse maintainer discussion of anonymous response-header limits](https://github.com/WordPress/openverse/issues/5315)
- [Freesound API authentication](https://freesound.org/docs/api/authentication.html)
- [Internet Archive metadata read API](https://archive.org/services/docs/api/md-read.html)
- [Audius API documentation](https://docs.audius.co/api/)
- [Wikimedia Commons machine-readable file metadata](https://commons.wikimedia.org/wiki/Commons:Machine-readable_data)
- [Wikimedia API rate limits](https://www.mediawiki.org/wiki/Wikimedia_APIs/Rate_limits)
- [ccMixter Query API](https://ccmixter.org/query-api)
- [Free To Use public API documentation](https://freetouse.com/api)
- [Free To Use OpenAPI schema](https://api.freetouse.com/v3/openapi.json)
- [Radio Browser API documentation](https://docs.radio-browser.info/)
- [AndroidX Media3 Transformer API](https://developer.android.com/reference/androidx/media3/transformer/Transformer)
- [AndroidX Media3 Transformer.Listener API](https://developer.android.com/reference/androidx/media3/transformer/Transformer.Listener)
- [Free Music Archive developer information](https://freemusicarchive.org/app-developers)
- [Mixcloud developer documentation](https://www.mixcloud.com/developers/)
- [SoundCloud API guide](https://developers.soundcloud.com/docs/api/guide)
