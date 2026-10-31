#!/usr/bin/env bash
# JVM-only checks when Android SDK/Gradle repositories are unavailable. Compiles the REAL
# filter/index/coroutine pipeline and JUnit tests. Android resource/Compose value APIs and
# the ViewModel cache holder are compile-only shims; this does NOT test Android rendering,
# IME interaction, recomposition, or the real ViewModel. Use Gradle + a device for those.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${KOTLINC:?Set KOTLINC to Kotlin 2.0.21+}"
: "${JUNIT_JAR:?Set JUNIT_JAR to JUnit 4.13+}"
: "${HAMCREST_JAR:?Set HAMCREST_JAR to hamcrest-core-1.3.jar}"
KOTLIN_LIB="$(cd "$(dirname "$KOTLINC")/../lib" && pwd)"
COROUTINES_JAR="${COROUTINES_JAR:-$KOTLIN_LIB/kotlinx-coroutines-core-jvm.jar}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
python3 - "$WORK" <<'PY'
from pathlib import Path
import re
import sys
work = Path(sys.argv[1])
root = Path('app/src/main/java/com/kitsugi/animelist')
def write(name, text):
    (work / name).write_text(text)

# Extract the real filter declarations; do not duplicate their IDs/status/type mappings.
options = (root / 'ui/screens/mylist/KitsugiMyListFilterComponents.kt').read_text()
options = (options[options.index('internal data class StatusFilter'):options.index('internal data class FavoriteFilter')]
           + options[options.index('internal val statusFilters'):options.index('internal val favoriteFilters')])
write('FilterOptions.kt', 'package com.kitsugi.animelist.ui.screens.mylist\n'
      'import com.kitsugi.animelist.model.*\nimport com.kitsugi.animelist.R\n' + options)
resource_sources = options + (root / 'model/MediaEntry.kt').read_text() + (root / 'ui/screens/mylist/MyListFilterHelpers.kt').read_text() + (root / 'ui/screens/mylist/MyListSections.kt').read_text()
names = sorted(set(re.findall(r'R\.string\.(\w+)', resource_sources)))
write('Resources.kt', 'package com.kitsugi.animelist\nobject R { object string {\n' +
      '\n'.join(f'const val {name} = {i}' for i, name in enumerate(names, 1)) + '\n} }\n' +
      'class KitsugiApplication { fun getString(id: Int): String = "Resource $id"\n'
      'companion object { fun getInstance(): KitsugiApplication? = null } }\n')
write('StringRes.kt', 'package androidx.annotation\nannotation class StringRes\n')
write('Runtime.kt', '''package androidx.compose.runtime
import kotlin.reflect.KProperty
import kotlinx.coroutines.flow.Flow
annotation class Composable
annotation class ReadOnlyComposable
class State<T>(val value: T)
operator fun <T> State<T>.getValue(thisRef: Any?, property: KProperty<*>): T = value
interface ProduceStateScope<T> { var value: T }
@Composable fun <T> rememberUpdatedState(newValue: T): State<T> = error("Compile-only")
@Composable fun <T> produceState(initialValue: T, vararg keys: Any?, producer: suspend ProduceStateScope<T>.() -> Unit): State<T> = error("Compile-only")
fun <T> snapshotFlow(block: () -> T): Flow<T> = error("Compile-only")
''')
write('StringResource.kt', '''package androidx.compose.ui.res
fun stringResource(id: Int): String = "Resource $id"
''')
write('Dp.kt', '''package androidx.compose.ui.unit
data class Dp(val value: Float)
val Int.dp get() = Dp(toFloat())
''')
# Pure grouping IDs and bounded edit distance are copied verbatim from production.
sections = (root / 'ui/screens/mylist/MyListSections.kt').read_text()
group_ids = sections[sections.index('object MyListGroupingIds'):sections.index('/** Eklenme tarihi kovaları')]
write('GroupingIds.kt', 'package com.kitsugi.animelist.ui.screens.mylist\nimport java.util.Locale\n' + group_ids)
prefs = (root / 'data/settings/KitsugiContentPrefs.kt').read_text()
lev = prefs[prefs.index('    internal fun levenshteinBounded'):prefs.rfind('\n}')]
write('ContentPrefs.kt', 'package com.kitsugi.animelist.data.settings\nobject KitsugiContentPrefs {\nval fuzzySearchEnabled = true\n' + lev + '\n}\n')
write('TitleHelpers.kt', '''package com.kitsugi.animelist.utils
import com.kitsugi.animelist.model.MediaEntry
object PreferenceHelpers {
    fun isLatinText(text: String?): Boolean = text?.any { it in 'A'..'Z' || it in 'a'..'z' } == true
    fun hasCjkCharacters(text: String?): Boolean = text?.any { it.code in 0x3040..0x9fff || it.code in 0xac00..0xd7af } == true
    fun MediaEntry.getDisplayTitle(titleLanguage: String): String = when (titleLanguage) {
        "ENGLISH" -> titleEnglish ?: title
        "NATIVE", "JAPANESE_STAFF" -> titleJapanese ?: title
        else -> title
    }
}
''')
write('BangumiShims.kt', '''package com.kitsugi.animelist.data.remote
object BangumiIdNamespace {
    fun rawIdOrNull(id: Int?): Int? = id?.takeIf { it in 500_000_001..599_999_999 }?.minus(500_000_000) ?: id?.takeIf { it in 1..499_999_999 }
}
object BangumiTitleCache {
    data class LatinTitles(val romaji: String? = null, val english: String? = null, val native: String? = null)
}
''')
write('TmdbContract.kt', '''package com.kitsugi.animelist.data.auth
const val TMDB_ACCOUNT_SOURCE: String = "tmdb_account"
fun isTmdbAccountSource(source: String): Boolean = source.trim().lowercase(java.util.Locale.ROOT) == TMDB_ACCOUNT_SOURCE
''')
write('CacheHolder.kt', '''package com.kitsugi.animelist.ui.screens.mylist
class MyListViewModel {
    internal var cachedPreparedSearch: PreparedMyListSearch? = null
    internal var cachedSearchResult: MyListSearchUiCache? = null
}
''')
PY
CP="$COROUTINES_JAR:$JUNIT_JAR:$HAMCREST_JAR"
"$KOTLINC" \
 "$WORK"/*.kt \
 app/src/main/java/com/kitsugi/animelist/model/MediaEntry.kt \
 app/src/main/java/com/kitsugi/animelist/model/MediaIdentity.kt \
 app/src/main/java/com/kitsugi/animelist/ui/utils/WatchStatusLabels.kt \
 app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListFilterHelpers.kt \
 app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListLibraryGrouping.kt \
 app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListSearchIndex.kt \
 app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListSearchPipeline.kt \
 app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListSearchState.kt \
 app/src/test/java/com/kitsugi/animelist/ui/screens/mylist/MyListFilterHelpersTest.kt \
 app/src/test/java/com/kitsugi/animelist/ui/screens/mylist/MyListLibraryGroupingTest.kt \
 app/src/test/java/com/kitsugi/animelist/ui/screens/mylist/MyListSearchIndexTest.kt \
 app/src/test/java/com/kitsugi/animelist/ui/screens/mylist/MyListSearchPipelineTest.kt \
 -jvm-target 17 -classpath "$CP" -include-runtime -d "$WORK/mylist-checks.jar"
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$WORK/mylist-checks.jar:$CP" org.junit.runner.JUnitCore \
 com.kitsugi.animelist.ui.screens.mylist.MyListFilterHelpersTest \
 com.kitsugi.animelist.ui.screens.mylist.MyListLibraryGroupingTest \
 com.kitsugi.animelist.ui.screens.mylist.MyListSearchIndexTest \
 com.kitsugi.animelist.ui.screens.mylist.MyListSearchPipelineTest
