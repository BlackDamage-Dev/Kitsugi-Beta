package com.kitsugi.animelist.ui.screens.manga.components

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext

internal data class ReaderCroppedImage(val file: File, val width: Int, val height: Int)

/** The temporary file remains available for as long as its page is composed. */
@Composable
internal fun rememberReaderCrop(source: File, enabled: Boolean, rotation: String = "Off"): ReaderCroppedImage? {
    val context = LocalContext.current
    return key(source.path, enabled, rotation) {
        val result by produceState<ReaderCroppedImage?>(null, source.path, enabled, rotation) {
            value = null
            if ((!enabled && rotation == "Off") || !source.isFile) return@produceState
            var temporary: File? = null
            try {
                val file = createCroppedReaderImage(source, context.cacheDir, crop = enabled, rotation = rotation) { temporary = it }
                if (file != null) {
                    val dimensions = withContext(Dispatchers.IO) {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.path, options)
                        options.outWidth to options.outHeight
                    }
                    if (dimensions.first > 0 && dimensions.second > 0) {
                        value = ReaderCroppedImage(file, dimensions.first, dimensions.second)
                        awaitCancellation()
                    }
                }
            } finally { temporary?.delete() }
        }
        result
    }
}
