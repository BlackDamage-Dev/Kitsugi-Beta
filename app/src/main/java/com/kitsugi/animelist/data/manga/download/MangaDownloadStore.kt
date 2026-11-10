package com.kitsugi.animelist.data.manga.download

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** One atomic journal per chapter. Source URLs remain private to the application. */
class MangaDownloadStore private constructor(context: Context) {
    private val dir = File(context.noBackupFilesDir, "manga_downloads")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private var loaded = false
    private val mutable = MutableStateFlow<List<MangaDownload>>(emptyList())
    val downloads = mutable.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (loaded) return@withLock
            check(dir.isDirectory || dir.mkdirs()) { "Manga indirme kayıtları açılamadı" }
            mutable.value = dir.listFiles().orEmpty()
                .filter { it.name.endsWith(".json") || it.name.endsWith(".json.bak") }
                .map { it.name.removeSuffix(".bak") }.distinct().mapNotNull { name ->
                    try {
                        AtomicFile(File(dir, name)).openRead().use {
                            json.decodeFromString<MangaDownload>(it.readBytes().toString(Charsets.UTF_8))
                        }
                    } catch (e: Exception) {
                        Log.e("MangaDownloadStore", "Bozuk manga kaydı: $name", e)
                        null
                    }
                }.sortedByDescending { it.updatedAt }
            loaded = true
        }
    }

    suspend fun get(id: String): MangaDownload? { load(); return mutable.value.firstOrNull { it.id == id } }

    suspend fun put(record: MangaDownload) = withContext(Dispatchers.IO) {
        load()
        mutex.withLock { write(record) }
    }

    suspend fun update(id: String, transform: (MangaDownload) -> MangaDownload): MangaDownload = withContext(Dispatchers.IO) {
        load()
        mutex.withLock {
            val old = mutable.value.firstOrNull { it.id == id } ?: error("Manga indirme kaydı bulunamadı")
            transform(old).copy(updatedAt = System.currentTimeMillis()).also { write(it) }
        }
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        load()
        mutex.withLock {
            AtomicFile(File(dir, "$id.json")).delete()
            mutable.value = mutable.value.filterNot { it.id == id }
        }
    }

    private fun write(record: MangaDownload) {
        require(record.id.matches(Regex("[a-f0-9]{16}")))
        val file = AtomicFile(File(dir, "${record.id}.json"))
        val stream = file.startWrite()
        try {
            stream.write(json.encodeToString(record).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (e: Exception) { file.failWrite(stream); throw e }
        mutable.value = (mutable.value.filterNot { it.id == record.id } + record).sortedByDescending { it.updatedAt }
    }

    companion object {
        @Volatile private var instance: MangaDownloadStore? = null
        fun get(context: Context): MangaDownloadStore = instance ?: synchronized(this) {
            instance ?: MangaDownloadStore(context.applicationContext).also { instance = it }
        }
    }
}
