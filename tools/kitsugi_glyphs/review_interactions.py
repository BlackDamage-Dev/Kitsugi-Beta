"""Explicitly reviewed non-button layers and modifier-owned visuals.

No wildcard 'everything is covered' fallback. Each exception has a constrained
component name, path and evidence anchor. Removing that evidence reopens the audit.
"""

def review(row, owner, source):
    path, fn, name, code = row['path'], row['function'], row['name'], row['code']
    ui = 'app/src/main/java/com/kitsugi/animelist/ui/'
    if path == ui+'components/KitsugiCosmicSearchBar.kt' and fn == 'CosmicCompanionButton' and 'icon()' in code:
        return 'icon-slot', 'icon() composable slot; caller renders its own Kitsugi vector'
    if path == ui+'components/KitsugiHeroSection.kt' and fn == 'KitsugiHeroSection' and '.drawBehind' in code:
        return 'custom-drawing', 'hand-drawn pager pill indicators; these are the page-selection marks'
    if path == ui+'components/KitsugiMediaEntryCard.kt' and fn == 'KitsugiMediaEntryCard' and name == 'Card':
        if all(s in source for s in ('KitsugiMarkedText(KitsugiGlyphs.Info', 'CompactMediaEntryCard(', 'LargeMediaEntryCard(', 'ComfortableMediaEntryCard(')):
            return 'delegated', 'each card layout renders a details-marked title; poster actions have Zoom badges'
    if path == ui+'components/KitsugiPremiumSheet.kt' and fn in ('KitsugiPremiumSheet', 'PremiumScrim') and (name == 'PremiumScrim' or '.fillMaxSize()' in code):
        return 'dismiss-layer', 'transparent sheet-dismiss backdrop, not a separately visible button'
    if path == ui+'screens/detail/ApiResultDetailComponents.kt' and fn == 'buildMdbRatingItems' and name == 'MdbRatingItem':
        return 'source-data', 'rating-provider data constructor, not a rendered control; source identity is preserved'
    if (path, fn) in {
        (ui+'screens/detail/KitsugiDetailPlayerComponents.kt', 'KitsugiPlayerGestureWrapper'),
        (ui+'screens/detail/components/SharedTrailerOverlay.kt', 'SharedTrailerOverlay'),
        (ui+'screens/fullscreen/controls/GestureHandler.kt', 'GestureHandler'),
    } and name == 'Box' and '.pointerInput' in code:
        return 'gesture-layer', 'invisible video tap/seek layer; visible player actions are audited separately'
    if path == ui+'screens/fullscreen/controls/PlayerSheetsHost.kt' and fn == 'PlayerSheetsHost' and name == 'Box' and 'onClick = onDismissRequest' in code:
        return 'dismiss-layer', 'dimmed sheet-dismiss backdrop; sheet controls are audited independently'
    if path == ui+'screens/fullscreen/controls/components/ControlsButton.kt' and fn == 'FilledControlsButton' and '.matchParentSize()' in code and 'KitsugiActionMark(glyph)' in owner:
        return 'gesture-layer', 'click/long-click overlay exactly matches the glyph-marked button beneath it'
    if path == ui+'screens/fullscreen/controls/components/sheets/MoreSheet.kt' and fn == 'MoreSheet' and '.matchParentSize()' in code and 'KitsugiMarkedText(KitsugiGlyphs.Code' in owner:
        return 'gesture-layer', 'long-click overlay on the custom-action chip; its label has the Code glyph'
    if path == ui+'screens/fullscreen/controls/components/SeekBar.kt' and fn == 'SeekbarWithTimers' and name == 'Seeker' and 'KitsugiGlyphs.Seek' in owner:
        return 'companion-glyph', 'Seek mark immediately adjacent to the timeline, between its two timer labels'
    if path == ui+'screens/search/PluginPickerScreen.kt' and fn == 'AlphabetScrollerRail' and name == 'BoxWithConstraints' and 'alphabet.forEach' in code:
        return 'custom-drawing', 'individual alphabet letters are the rail targets; active letter has its own enlarged mark'
    if path == ui+'screens/detail/wiki/FandomWikiReader.kt' and fn == 'ReaderBlock' and name == 'SubcomposeAsyncImage' and 'KitsugiArtworkAction(KitsugiGlyphs.Zoom)' in owner:
        return 'companion-glyph', 'image sits inside its Zoom-marked artwork action wrapper'
    if path == ui+'screens/detail/wiki/FandomWikiReader.kt' and fn == 'buildReaderLinkedText' and name == 'Clickable' and 'appendInlineContent("kitsugi_wiki_link"' in owner and 'KitsugiGlyphs.External' in source:
        return 'inline-link-glyph', 'each link range includes an External inline glyph; URL and range offsets are retained'
    if row['status'] == 'unresolved-modifier':
        if path == ui+'components/KitsugiEditMediaSheet.kt' and fn == 'KitsugiEditMediaSheet' and 'starModifier' in code and 'KitsugiGlyphs.StarFilled' in owner:
            return 'modifier-owner-glyph', 'score modifier is consumed by the filled/outline Kitsugi star'
        if path == ui+'components/KitsugiMarkdownText.kt' and fn == 'transform' and 'kitsugiActionBadge(KitsugiGlyphs.Zoom' in owner:
            return 'modifier-owner-glyph', 'Markdown image transformer applies a draw-only Zoom badge'
        if path == ui+'components/KitsugiMediaEntryCard.kt' and fn in ('PosterView','LargePosterView','PosterGridMediaEntryCard') and 'kitsugiActionBadge(KitsugiGlyphs.Zoom' in owner:
            return 'modifier-owner-glyph', 'poster modifier is consumed by an image surface with a Zoom badge'
        if path == ui+'screens/detail/KitsugiDetailHero.kt' and fn == 'KitsugiDetailHero' and 'kitsugiActionBadge(KitsugiGlyphs.Zoom' in owner and name != 'copyOnDoubleTap':
            return 'modifier-owner-glyph', 'hero artwork carries the Zoom badge; its click modifier is preserved'
        if name == 'copyOnDoubleTap' and ('KitsugiMarkedText(KitsugiGlyphs.Copy' in owner or 'kitsugiActionBadge(KitsugiGlyphs.Copy)' in owner):
            return 'modifier-owner-glyph', 'title-copy gesture is consumed by a Copy-marked title or title image'
        if path == ui+'screens/detail/DetailSharedComponents.kt' and fn == 'DetailInfoValueRow' and 'KitsugiMarkedText(KitsugiGlyphs.External' in owner:
            return 'modifier-owner-glyph', 'linked value uses the External-marked text node'
        if path == ui+'screens/settings/StorageSettingsContent.kt' and fn in ('StorageBucketRow','DownloadRow','DeleteAllDownloadsRow') and 'Icon(' in owner and 'KitsugiGlyphs.' in source:
            return 'modifier-owner-glyph', 'click modifier is consumed by the row; bucket/kind selector returns Kitsugi vectors'
        if path in (ui+'utils/DpadFastScrollModifier.kt',ui+'utils/DpadThrottleModifier.kt',ui+'utils/TvFocusModifier.kt'):
            return 'input-routing', 'shared DPAD/focus/click implementation, not an additional rendered target'
        if path == 'app/src/main/java/com/kitsugi/animelist/utils/CopyUtils.kt' and fn == 'copyOnDoubleTap':
            return 'input-routing', 'shared double-tap utility; all title callsites are inventoried separately'
    return None
