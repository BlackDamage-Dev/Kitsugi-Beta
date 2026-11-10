#!/usr/bin/env bash
# Offline smoke tests for mapping, screen projection, navigation, scores and labels.
# Requires Java 17 and Kotlin 2.0.21. JUnit/navigation/resources are stand-ins.
# This does NOT replace the Android/Compose/Gradle tests or live API checks.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JAVA_HOME:?Set JAVA_HOME to a Java 17 runtime}"
: "${KOTLIN_HOME:?Set KOTLIN_HOME to the Kotlin 2.0.21 distribution}"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
export MAPPING_SMOKE_DIR="$work"
python3 - <<'PY'
import os, re
from pathlib import Path
out = Path(os.environ['MAPPING_SMOKE_DIR'])
source = Path('app/src/main/java/com/kitsugi/animelist/ui/screens/explore/AllSourcesExplore.kt').read_text()
start = source.index('internal fun JikanSearchResult.exploreIdentity()')
end = source.index('/** Keep every category under its own source', start)
(out/'Section.kt').write_text('package com.kitsugi.animelist.ui.screens.explore\nimport com.kitsugi.animelist.data.remote.JikanSearchResult\n\n' + source[start:end])
# Compile the actual model declarations rather than hand-maintained copies. JUnit,
# Android-facing PlatformErrorCode and navigation remain stubs; this is not a Gradle test.
models = Path('app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiModels.kt').read_text()
media = Path('app/src/main/java/com/kitsugi/animelist/model/MediaEntry.kt').read_text()
media_start = media.index('enum class MediaType {')
media_end = media.index('\n}', media_start) + 2
(out/'RealMediaType.kt').write_text('package com.kitsugi.animelist.model\n' + media[media_start:media_end] + '\n')
model_start = models.index('data class JikanSearchResult(')
model_end = models.index('\ndata class KitsugiTheme(', model_start)
(out/'RealSearchResult.kt').write_text('package com.kitsugi.animelist.data.remote\nimport com.kitsugi.animelist.model.MediaType\n' + models[model_start:model_end] + '\n')
state_start = source.index('data class ExploreSourceState(')
state_end = source.index('\n\ninternal suspend fun', state_start)
(out/'RealSourceState.kt').write_text('package com.kitsugi.animelist.ui.screens.explore\nimport com.kitsugi.animelist.data.platform.PlatformErrorCode\n' + source[state_start:state_end] + '\n')
(out/'StubPlatformError.kt').write_text('package com.kitsugi.animelist.data.platform\nclass PlatformErrorCode\n')
# Ensure every label ID used by the actual Kotlin file exists in both translations.
import xml.etree.ElementTree as ET
labels = Path('app/src/main/java/com/kitsugi/animelist/ui/screens/explore/mobile/MobileExploreLabels.kt').read_text()
ids = sorted(set(re.findall(r'R\.string\.(\w+)', labels)))
for locale in ('values', 'values-en'):
    present = set()
    for xml in Path('app/src/main/res', locale).glob('*.xml'):
        for element in ET.parse(xml).getroot():
            if element.tag == 'string' and element.get('name'):
                present.add(element.get('name'))
    missing = set(ids) - present
    if missing: raise SystemExit(f'{locale} missing resource IDs: {sorted(missing)}')
(out/'StubResources.kt').write_text('package com.kitsugi.animelist\nobject R { object string {\n' + ''.join(f'const val {name} = {i}\n' for i,name in enumerate(ids, 1)) + '} }\n')
(out/'StubNavigation.kt').write_text('''package com.kitsugi.animelist.ui.app
import com.kitsugi.animelist.DetailScreen
class AppNavigationState { var fullScreenGridState: FullScreenMediaGridState? = null; fun navigateToDetail(screen: DetailScreen) {} }
''')
(out/'StubDetail.kt').write_text('''package com.kitsugi.animelist
sealed interface DetailScreen { data object WatchHistory : DetailScreen }
''')
(out/'JunitAssert.kt').write_text('''package org.junit.Assert
fun assertEquals(expected: Any?, actual: Any?) { if (expected != actual) error("Expected $expected; got $actual") }
fun assertEquals(message: String, expected: Any?, actual: Any?) { if (expected != actual) error("$message: expected $expected; got $actual") }
fun assertTrue(value: Boolean) { if (!value) error("Expected true") }
fun assertTrue(message: String, value: Boolean) { if (!value) error("$message: expected true") }
fun assertFalse(value: Boolean) { if (value) error("Expected false") }
''')
(out/'JunitTest.kt').write_text('''package org.junit
import kotlin.reflect.KClass
annotation class Test(val expected: KClass<out Throwable> = None::class) { class None: Throwable() }
''')
runner = 'package com.kitsugi.animelist.ui.screens.explore.mobile\nfun main() {\n'
for name in ('MobileExploreMappingTest', 'MobileExploreContentTest', 'MobileExploreNavigationTest', 'MobileExploreScoreTest', 'MobileExploreLabelsTest'):
    test = Path(f'app/src/test/java/com/kitsugi/animelist/ui/screens/explore/mobile/{name}.kt').read_text()
    cases = re.findall(r'@Test(?:\(expected = (\w+)::class\))?\s+fun (\w+)\(', test)
    if not cases or test.count('@Test') != len(cases):
        raise SystemExit('Update the isolated runner for the current test annotations.')
    runner += f' val {name} = {name}()\n'
    for expected, method in cases:
        call = f'{name}.{method}()'
        if expected:
            runner += f' try {{ {call}; error("expected {expected}") }} catch (e: {expected}) {{ println("PASS {name}.{method}") }}\n'
        else:
            runner += f' {call}; println("PASS {name}.{method}")\n'
runner += '}\n'
(out/'Runner.kt').write_text(runner)
PY
"$KOTLIN_HOME/bin/kotlinc" "$work"/*.kt \
  app/src/main/java/com/kitsugi/animelist/ui/app/FullScreenMediaGridState.kt \
  app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreModels.kt \
  app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreCategoryType.kt \
  app/src/main/java/com/kitsugi/animelist/ui/screens/explore/mobile/MobileExploreMapping.kt \
  app/src/main/java/com/kitsugi/animelist/ui/screens/explore/mobile/MobileExploreContent.kt \
  app/src/main/java/com/kitsugi/animelist/ui/screens/explore/mobile/MobileExploreNavigation.kt \
  app/src/main/java/com/kitsugi/animelist/ui/screens/explore/mobile/MobileExploreScore.kt \
  app/src/main/java/com/kitsugi/animelist/ui/screens/explore/mobile/MobileExploreLabels.kt \
  app/src/test/java/com/kitsugi/animelist/ui/screens/explore/mobile/MobileExploreMappingTest.kt \
  app/src/test/java/com/kitsugi/animelist/ui/screens/explore/mobile/MobileExploreContentTest.kt \
  app/src/test/java/com/kitsugi/animelist/ui/screens/explore/mobile/MobileExploreNavigationTest.kt \
  app/src/test/java/com/kitsugi/animelist/ui/screens/explore/mobile/MobileExploreScoreTest.kt \
  app/src/test/java/com/kitsugi/animelist/ui/screens/explore/mobile/MobileExploreLabelsTest.kt \
  -include-runtime -d "$work/tests.jar"
"$JAVA_HOME/bin/java" -jar "$work/tests.jar"
