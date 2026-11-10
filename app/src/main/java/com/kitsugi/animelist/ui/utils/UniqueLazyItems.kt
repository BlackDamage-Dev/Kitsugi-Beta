package com.kitsugi.animelist.ui.utils

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable

/*
 * `items(...)` / `itemsIndexed(...)` yerine kullanılan ÇÖKMEZ sürümler.
 *
 * Parametre adları Compose'un resmi API'siyle birebir aynıdır (`items`, `key`,
 * `contentType`, `span`, `itemContent`); çağrı noktasında yalnızca fonksiyon adı değişir:
 *
 *     items(items = reviews, key = { it.stableKey() }) { rev -> … }
 *  →  uniqueItems(items = reviews, key = { it.stableKey() }) { rev -> … }
 *
 * Anahtarlar [uniqueLazyKeys] ile her liste değişiminde bir kez (O(n)) hesaplanır. Hesap,
 * LazyColumn/LazyRow içerik lambdası içinde yapıldığı için öğe listesiyle anahtar listesi
 * daima AYNI anlık görüntüden gelir (boyut uyuşmazlığı oluşamaz).
 */

/** [LazyListScope.items] — yinelenen/boş anahtarlarda çökmeyen sürüm. */
fun <T> LazyListScope.uniqueItems(
    items: List<T>,
    key: (item: T) -> Any?,
    contentType: (item: T) -> Any? = { null },
    itemContent: @Composable LazyItemScope.(item: T) -> Unit
) {
    val keys = items.uniqueLazyKeys(key)
    items(
        count = items.size,
        key = { index -> keys[index] },
        contentType = { index -> contentType(items[index]) }
    ) { index ->
        itemContent(items[index])
    }
}

/** [LazyListScope.itemsIndexed] — yinelenen/boş anahtarlarda çökmeyen sürüm. */
fun <T> LazyListScope.uniqueItemsIndexed(
    items: List<T>,
    key: (index: Int, item: T) -> Any?,
    contentType: (index: Int, item: T) -> Any? = { _, _ -> null },
    itemContent: @Composable LazyItemScope.(index: Int, item: T) -> Unit
) {
    val keys = items.uniqueLazyKeysIndexed(key)
    items(
        count = items.size,
        key = { index -> keys[index] },
        contentType = { index -> contentType(index, items[index]) }
    ) { index ->
        itemContent(index, items[index])
    }
}

/** [LazyGridScope.items] — yinelenen/boş anahtarlarda çökmeyen sürüm. */
fun <T> LazyGridScope.uniqueItems(
    items: List<T>,
    key: (item: T) -> Any?,
    span: (LazyGridItemSpanScope.(item: T) -> GridItemSpan)? = null,
    contentType: (item: T) -> Any? = { null },
    itemContent: @Composable LazyGridItemScope.(item: T) -> Unit
) {
    val keys = items.uniqueLazyKeys(key)
    val spanByIndex: (LazyGridItemSpanScope.(index: Int) -> GridItemSpan)? =
        if (span == null) null else { index -> span(items[index]) }
    items(
        count = items.size,
        key = { index -> keys[index] },
        span = spanByIndex,
        contentType = { index -> contentType(items[index]) }
    ) { index ->
        itemContent(items[index])
    }
}

/** [LazyGridScope.itemsIndexed] — yinelenen/boş anahtarlarda çökmeyen sürüm. */
fun <T> LazyGridScope.uniqueItemsIndexed(
    items: List<T>,
    key: (index: Int, item: T) -> Any?,
    span: (LazyGridItemSpanScope.(index: Int, item: T) -> GridItemSpan)? = null,
    contentType: (index: Int, item: T) -> Any? = { _, _ -> null },
    itemContent: @Composable LazyGridItemScope.(index: Int, item: T) -> Unit
) {
    val keys = items.uniqueLazyKeysIndexed(key)
    val spanByIndex: (LazyGridItemSpanScope.(index: Int) -> GridItemSpan)? =
        if (span == null) null else { index -> span(index, items[index]) }
    items(
        count = items.size,
        key = { index -> keys[index] },
        span = spanByIndex,
        contentType = { index -> contentType(index, items[index]) }
    ) { index ->
        itemContent(index, items[index])
    }
}
