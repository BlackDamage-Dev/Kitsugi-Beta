#!/usr/bin/env python3
"""AST-assisted source migration for nonstandard controls.

Only concrete layouts, text nodes, and Material text fields are rewritten.
Custom composable calls are verified through their implementation, not guessed
API parameters. Every edit is recorded with path, function and semantic glyph.
"""
import json
import re
from collections import defaultdict
from pathlib import Path
from audit_interactions import ROOT, PARSER, walk, parts, text
from migrate_material_icons import add_import

# Function families define the action, not the screen's incidental visual styling.
FAMILIES = {
'Palette': 'PaletteGrid GradientSettingsPanel TargetSwatchButton PickerTabButton AppearanceTab ListScoreTab CustomColorPickerDialog HexField',
'Translate': 'KitsugiActivityDetailBottomSheet KitsugiAllReviewsBottomSheet KitsugiAllTopicsBottomSheet KitsugiReviewDetailBottomSheet KitsugiTopicDetailBottomSheet TvActivityDetailDialog TvReviewDetailDialog TvTopicDetailDialog',
'Filter': 'ApiSearchChip KitsugiChip FilterChip FilterChipTv FilterRow QuickFilterToggleChip ProfileFilterChip TvFilterChip ProfileFavoritesFloatingControls GridGenreChipRow GuideChip SearchFilterChip SourceStatusFilterChip PlatformErrorFilterChip RichMyListFilterPanel FilterChipItem SourceScopeChipsRow SourceEngineFilterSheet AniListFullFiltersContent MalFullFiltersContent KitsuFullFiltersContent ShikimoriFullFiltersContent SimklFullFiltersContent TmdbFullFiltersContent BangumiFullFiltersContent BangumiChipRow SearchFilterSheet SearchFilterBar HealthReportTab StreamSearchBar SourceSpecificFilterChipsRow',
'Calendar': 'DateQuickChip AiringDayTab DialogWithYearSeasonSelection',
'Book': 'ReaderBlock ReaderCardTile ReaderLinkGroup ReaderLinkChips WikiFieldRow MangaReaderScreen MangaPageItem TvMangaReaderControls',
'Globe': 'LanguageSelectionDialog StorageSettingsTab PluginTypeChip SourceHeader WikiSourceChips',
'Film': 'ExploreCategoryChip SearchTypeChip MyListContentTypeRow MediaTypeSwitcher KitsuStatsMediaSwitcher ProfileStatsMediaTypeSwitcher ShikimoriStatsMediaSwitcher',
'Rows': 'KitsugiRankingTabs MyListTabBar TvLibraryTabChip TvLibraryChip BangumiChip',
'Search': 'KitsugiApiSearchDialog AddonFloatingSearchBar AddonSearchDialog SearchBarSection SearchBarSection SearchBarSection TvSearchBar WikiFindBar MediaWikiSearchCard SearchBarSection',
'Plugin': 'AllPluginsListItem PluginListItem PluginPickerBottomSheet CloudstreamExtensionsTab StremioTab MangaExtensionsTab CsRepoCard',
'Play': 'HeroActionButton ApiActionButton ActionButton ApiTrailerCard GalleryImagePage InlineHeroBanner KitsugiHeroSection SharedTrailerOverlay InlinePlayerSeekBar GestureHandler KitsugiPlayerGestureWrapper PauseOverlay',
'Subtitles': 'SavedTextOption FontDropdown BorderStyleDropdown MoreSheet',
'Player': 'PlayerPickerCard TvSettingsActionRow',
'Image': 'GalleryLandscapeLayout GalleryPortraitLayout',
'Chart': 'StatsScreen ListStatsHeader MalStatisticsSection MalAboutExtras',
'Comment': 'KitsugiReplyEditorBottomSheet ActivityCard UserProfileActivityTab',
'Bookmark': 'CustomListRow CustomListEditorDialog ListFilterChip ListStatusRow',
'Tag': 'TagChip GenreChip KitsugiGenresCard',
'Info': 'DetailTabChip MediaWikiHeaderCard ArticleInfoboxCard AboutScreen MdbRatingBadge ImdbEpisodeRatingBadge',
'Feedback': 'FeedbackDialog',
'Profile': 'KitsugiAccountContent ProfileSelection CharacterDetailPage StaffDetailPage CharacterDetailStickyHeader StaffDetailStickyHeader',
'Settings': 'KitsugiClickableSettingItem KitsugiPlayerSettingsDialog TvSettingsScreen',
'Copy': 'KitsugiSimklLoginDialog',
'Key': 'AccountPasswordField KitsugiBangumiLoginDialog KitsugiShikimoriLoginDialog',
'Login': 'QrServiceRow TvServiceButton MalUnifiedSessionCard',
'Edit': 'KitsugiAniListMediaEntryEditorSheet KitsugiEditMediaSheet KitsugiMalMediaEntryEditorSheet KitsugiManualMediaEntryEditorSheet DialogTextField SheetRow',
'Numbers': 'KitsugiNumericInputDialog VideoTimer',
'Queue': 'CollectionEditorScreen CollectionSection AddActionButton QuickActions KitsugiMediaEntryCard',
'Sort': 'KitsugiSearchSortChip DeveloperLogsDialog LogHeader',
'Error': 'MangaSourceHealthScreen PlatformErrorCatalogScreen',
'Heart': 'FavouritesScreen AniListFavoritesTab UserProfileFavoritesTab FavoriteComfortableCard FavoriteCompactCard FavoriteLargeCard FavoriteMinimalistCard FavoritePosterGridCard',
'External': 'RelationCard RecommendationCard ApiSearchResultCard TvRelationCard TvSearchResultCard TvContentCard TvLibraryCard ModernHomeMediaCard',
'SkipNext': 'TvEpisodeCard PaginatedEpisodesList DetailContent',
'History': 'TvContinueWatchingCard KitsugiUserMediaListScreen',
'CloudSync': 'KitsugiExportBackupDialog KitsugiImportBackupDialog',
'Menu': 'MyListHeaderSection',
'Tune': 'MyListSingleSourceBar KitsugiMyListFilterSheet SearchFilterBar KitsugiChipWithMenu KitsugiChipWithRange KitsugiSearchFormatChip KitsugiSearchStatusChip KitsugiSearchSourceChip AniListFilterHeader MalFilterHeader ShikimoriRatesTab',
}
FUNCTION_GLYPH = {name: glyph for glyph,names in FAMILIES.items() for name in names.split()}

FUNCTION_GLYPH.update({
    'ApiResultDetailPage': 'Numbers', 'KitsugiStreamSelectorBottomSheet': 'Plugin',
    'StudioDetailSuccessContent': 'Filter', 'FullScreenMediaGridPage': 'Cleaning',
    'ResultsSummaryRow': 'Cleaning', 'AddonFullScreenGridPage': 'Cleaning',
    'ApiThemesList': 'Music', 'ChapterTrack': 'Bookmark', 'TopLeftPlayerControls': 'Info',
    'TvGridHomeContent': 'Film', 'TvDetailScreen': 'Film', 'TvDialogButton': 'Check',
    'TvStreamSettingsDialog': 'Save', 'GridHomeContent': 'External', 'ModernHomeRows': 'External', 'MalProfileContent': 'External', 'onCreate': 'History',
})

# Match actual callback/label semantics first, before falling back to its component family.
RULES = [
('Close', r'\b(?:onDismiss|onClose|dismiss|kapat|iptal|cancel)\b'),
('Profile', r'onUserProfileClick|username|onProfileClick'),
('ChevronDown', r'expand|Expand|showMore|ShowMore'),
('ChevronUp', r'collapse|Collapse|showLess|ShowLess'),
('Back', r'onPrevious|onPrev|previous|onceki'),
('Forward', r'onNext|nextPage|sonraki'),
('Refresh', r'retry|Retry|yeniden_dene|Tekrar|yeniden_kur|onRefresh'),
('Minus', r'onDecrement|"−"'),
('Plus', r'onIncrement|"\+"|onAdd|onCreate'),
('Download', r'onInstall|installPlugin|"Kur"|onDownload'),
('Sync', r'onUpdate|inline_update|tumunu_guncelle'),
('History', r'"Orijinal"|bangumi_tag_original'),
('Translate', r'targetLanguageName|inline_turkish|onTranslate'),
('Calendar', r'selectedYear|year\.toString|startDate|endDate'),
('Tag', r'genre|Genre|tagFilter'),
('Search', r'value\s*=\s*(?:query|searchQuery|searchText|searchTerm)|onSearch'),
('Key', r'password|Password|pinCode|apiKey|token'),
('Link', r'value\s*=\s*(?:url|repoUrl|link|domain)|onOpenUrl'),
('Palette', r'hex|Hex|colorPicker|ColorPicker'),
('Comment', r'value\s*=\s*(?:notes|comment|reply|message)'),
]

def choose(row):
    code=row['code']
    if re.search(r'R\.string\.(?:vazgec|iptal|kapat)\b', code): return 'Close'
    if re.search(r'R\.string\.(?:ekle)\b', code): return 'Plus'
    # Inputs: the value/label expresses the purpose; event callback names alone don't.
    for glyph, pattern in RULES:
        if re.search(pattern, code):return glyph
    return FUNCTION_GLYPH.get(row['function'])

PRIMITIVES={'Box','Row','Column','Card','Text','Button','TextButton','IconButton','OutlinedButton','AssistChip','FilterChip','InputChip','Tab','SuggestionChip','OutlinedCard','BoxWithConstraints','KitsugiButton'}
FIELDS={'TextField','OutlinedTextField'}
IMPORT='import com.kitsugi.animelist.ui.components.KitsugiMarkedText'

def main():
    import argparse
    ap=argparse.ArgumentParser();ap.add_argument('inventory');ap.add_argument('--report',default='/tmp/control-mark-edits.json');ap.add_argument('--apply',action='store_true');args=ap.parse_args()
    rows=json.loads(Path(args.inventory).read_text())
    grouped=defaultdict(list)
    for r in rows:
        if r['status']=='unresolved' and r['name'] in PRIMITIVES | FIELDS:grouped[r['path']].append(r)
    report=[];deferred=[]
    for rel,items in grouped.items():
        path=ROOT/rel;data=path.read_bytes();tree=PARSER.parse(data)
        nodes={}
        for n in walk(tree.root_node):
            if n.type == 'call_expression' and (n.start_byte not in nodes or n.end_byte > nodes[n.start_byte].end_byte):
                nodes[n.start_byte] = n
        edits={}
        for r in sorted(items,key=lambda r:r['end']-r['start']):
            if data[r['start']:r['end']].decode() != r['code']:
                raise ValueError('Stale inventory for ' + rel + '; rerun audit_interactions.py')
            node=nodes.get(r['start'])
            if node is None:continue
            glyph=choose(r)
            if not glyph:deferred.append(r);continue
            name,arg,body=parts(node,data)
            if name in FIELDS:
                if 'leadingIcon' in text(arg,data):continue
                pos=arg.end_byte-1
                contents=text(arg,data)[1:-1].rstrip()
                comma='' if not contents or contents.endswith(',') else ','
                insertion=comma+'\n leadingIcon = { com.kitsugi.animelist.ui.theme.gradient.Icon(KitsugiGlyphs.'+glyph+', contentDescription = null) }\n'
                edits[pos]=(pos,insertion.encode(),r,glyph)
            else:
                # Prefer a concrete text node; never wrap/reparent the layout or move its modifier.
                textnodes=[n for n in walk(node) if n.type=='call_expression' and parts(n,data) and parts(n,data)[0] in ('Text', 'KitsugiText')]
                # choose invocation itself, not a trailing-lambda wrapper of it
                if not textnodes:deferred.append(r);continue
                target=textnodes[0]
                _, textargs, _ = parts(target, data)
                if textargs is None: continue
                start=target.start_byte
                end=textargs.start_byte+1
                if start in edits:continue
                edits[start]=(end,('KitsugiMarkedText(KitsugiGlyphs.'+glyph+', ').encode(),r,glyph)
        if not edits:continue
        for start,(end,replacement,r,glyph) in sorted(edits.items(),reverse=True):
            data=data[:start]+replacement+data[end:]
            report.append({k:r[k] for k in ('path','line','function','name')} | {'glyph':glyph})
        result=add_import(data.decode())
        if IMPORT not in result:result=result.replace('import com.kitsugi.animelist.ui.theme.icons.KitsugiGlyphs',IMPORT+'\nimport com.kitsugi.animelist.ui.theme.icons.KitsugiGlyphs')
        if args.apply:path.write_text(result)
    Path(args.report).write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print('Edits:',len(report),'deferred:',len(deferred))
    for r in deferred:print(r['path']+':'+str(r['line']),r['function'],r['name'])

if __name__=='__main__':main()
